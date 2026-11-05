import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/api/api_exception.dart';
import '../../../core/di/providers.dart';
import '../../../core/money/dinero.dart';
import '../../../core/widgets/comunes.dart';
import '../../../core/widgets/dinero_widgets.dart';
import '../../../core/widgets/step_up.dart';
import '../../auth/domain/auth_models.dart';
import '../../consolidacion/presentation/consolidacion_providers.dart';
import '../../puntos/presentation/puntos_providers.dart';
import '../domain/intento_pago.dart';
import '../domain/pagos_models.dart';
import 'pagos_providers.dart';

/// Flujo F2 de pago de TDC:
/// 1. tarjeta → 2. cuenta origen + monto + cotización → 3. confirmar beneficiario →
/// 4. PIN + OTP (step-up `pin_otp`) → `POST /pagos-tdc` con `Idempotency-Key` → resultado.
class PagoTdcFlowScreen extends ConsumerStatefulWidget {
  const PagoTdcFlowScreen({super.key, this.tarjeta});

  final Tarjeta? tarjeta;

  @override
  ConsumerState<PagoTdcFlowScreen> createState() => _PagoTdcFlowScreenState();
}

class _PagoTdcFlowScreenState extends ConsumerState<PagoTdcFlowScreen> {
  late final ServicioPagoTdc _servicio;
  int _paso = 0;
  Tarjeta? _tarjeta;
  CuentaOrigen? _cuenta;
  final _monto = TextEditingController();
  final _formMonto = GlobalKey<FormState>();
  Cotizacion? _cotizacion;
  Beneficiario? _beneficiario;
  bool _beneficiarioOk = false;
  StepUp? _stepUp;
  bool _ocupado = false;
  String? _errorReintentable;

  @override
  void initState() {
    super.initState();
    _servicio = ServicioPagoTdc(ref.read(pagosRepositoryProvider));
    _tarjeta = widget.tarjeta;
    if (_tarjeta != null) _paso = 1;
  }

  @override
  void dispose() {
    _monto.dispose();
    super.dispose();
  }

  Future<void> _ejecutar(Future<void> Function() accion) async {
    setState(() => _ocupado = true);
    try {
      await accion();
    } catch (e) {
      if (mounted) mostrarError(context, e);
    } finally {
      if (mounted) setState(() => _ocupado = false);
    }
  }

  Future<void> _cotizar() async {
    if (_cuenta == null) {
      mostrarMensaje(context, 'Elige la cuenta de origen.');
      return;
    }
    if (!_formMonto.currentState!.validate()) return;
    final monto = Dinero.parsear(_monto.text)!;
    await _ejecutar(() async {
      final c = await ref.read(pagosRepositoryProvider).cotizacion(monto);
      if (c.totalCts > _cuenta!.saldoCts) {
        throw const ApiException(
            codigo: 'VALIDACION', mensaje: 'El total con comisión supera el saldo de la cuenta de origen.');
      }
      setState(() => _cotizacion = c);
    });
  }

  Future<void> _consultarBeneficiario() async {
    await _ejecutar(() async {
      final b = await ref.read(pagosRepositoryProvider).beneficiario(_tarjeta!.idTarjeta);
      setState(() {
        _beneficiario = b;
        _beneficiarioOk = false;
        _paso = 2;
      });
    });
  }

  SolicitudPagoTdc get _solicitud => SolicitudPagoTdc(
        idTarjeta: _tarjeta!.idTarjeta,
        idCuentaOrigen: _cuenta!.idCuenta,
        montoCts: _cotizacion!.montoCts,
        beneficiarioConfirmado: _beneficiarioOk,
      );

  Future<void> _pagar() async {
    final solicitud = _solicitud;
    _servicio.prepararIntento(solicitud);
    var stepUp = _stepUp;
    if (stepUp == null || !stepUp.vigente(DateTime.now()) || stepUp.nivel != NivelStepUp.pinOtp) {
      stepUp = await solicitarStepUp(
        context,
        conOtp: true,
        motivo: 'Pagar ${Dinero.bs(_cotizacion!.totalCts)} a la TDC •••• ${_tarjeta!.ultimos4} '
            'desde ${_cuenta!.nombreVisible}.',
      );
      if (stepUp == null || !mounted) return;
      _stepUp = stepUp;
    }
    setState(() {
      _ocupado = true;
      _errorReintentable = null;
    });
    try {
      final pago = await _servicio.pagar(solicitud, stepUp: stepUp.token);
      ref.invalidate(pagosProvider);
      ref.invalidate(limitesProvider);
      ref.invalidate(tarjetasProvider);
      ref.invalidate(cuentasOrigenProvider);
      ref.invalidate(consolidadoProvider);
      refrescarPuntos(ref);
      if (mounted) context.pushReplacement('/pagar/pago/${pago.idPago}', extra: pago);
    } on ApiException catch (e) {
      if (!mounted) return;
      if (e.stepUpRequerido) {
        _stepUp = null;
        mostrarError(context, e);
      } else if (ServicioPagoTdc.esReintentable(e)) {
        // Mismo intento ⇒ misma Idempotency-Key: reintentar nunca duplica el pago.
        setState(() => _errorReintentable = e.mensaje);
      } else {
        mostrarError(context, e);
      }
    } finally {
      if (mounted) setState(() => _ocupado = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Pagar TDC')),
      body: Stepper(
        currentStep: _paso,
        controlsBuilder: (_, _) => const SizedBox.shrink(),
        onStepTapped: (i) {
          if (i < _paso && !_ocupado) setState(() => _paso = i);
        },
        steps: [
          Step(
            title: const Text('Tarjeta'),
            subtitle: _tarjeta == null ? null : Text('${_tarjeta!.banco} •••• ${_tarjeta!.ultimos4}'),
            isActive: _paso >= 0,
            state: _paso > 0 ? StepState.complete : StepState.indexed,
            content: _PasoTarjeta(
              alElegir: (t) => setState(() {
                _tarjeta = t;
                _paso = 1;
              }),
            ),
          ),
          Step(
            title: const Text('Origen y monto'),
            subtitle: _cotizacion == null ? null : Text(Dinero.bs(_cotizacion!.totalCts)),
            isActive: _paso >= 1,
            state: _paso > 1 ? StepState.complete : StepState.indexed,
            content: _tarjeta == null ? const SizedBox.shrink() : _pasoMonto(context),
          ),
          Step(
            title: const Text('Confirmar beneficiario'),
            isActive: _paso >= 2,
            state: _paso > 2 ? StepState.complete : StepState.indexed,
            content: _pasoBeneficiario(context),
          ),
          Step(
            title: const Text('Autorizar con PIN + SMS'),
            isActive: _paso >= 3,
            content: _pasoAutorizar(context),
          ),
        ],
      ),
    );
  }

  Widget _pasoMonto(BuildContext context) {
    final cuentas = ref.watch(cuentasOrigenProvider);
    final ec = _tarjeta!.estadoCuenta;
    return Form(
      key: _formMonto,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          VistaAsync<List<CuentaOrigen>>(
            valor: cuentas,
            alReintentar: () => ref.invalidate(cuentasOrigenProvider),
            datos: (lista) => lista.isEmpty
                ? const Text('No tienes cuentas en Bs con permiso de débito. Vincula un banco y '
                    'activa "Permitir débito para pagar mis TDC".')
                : DropdownButtonFormField<int>(
                    initialValue: _cuenta?.idCuenta,
                    isExpanded: true,
                    decoration: const InputDecoration(labelText: 'Cuenta de origen (Bs)'),
                    items: [
                      for (final c in lista)
                        DropdownMenuItem(
                          value: c.idCuenta,
                          child: Text('${c.nombreVisible} · ${Dinero.bs(c.saldoCts)}', overflow: TextOverflow.ellipsis),
                        ),
                    ],
                    onChanged: (id) => setState(() {
                      _cuenta = lista.firstWhere((c) => c.idCuenta == id);
                      _cotizacion = null;
                    }),
                  ),
          ),
          const SizedBox(height: 12),
          if (ec != null)
            Wrap(
              spacing: 8,
              children: [
                ActionChip(
                  label: Text('Mínimo ${Dinero.bs(ec.pagoMinimoCts)}'),
                  onPressed: () => setState(() {
                    _monto.text = Dinero.paraEditar(ec.pagoMinimoCts);
                    _cotizacion = null;
                  }),
                ),
                ActionChip(
                  label: Text('Total ${Dinero.bs(ec.saldoTotalCts)}'),
                  onPressed: () => setState(() {
                    _monto.text = Dinero.paraEditar(ec.saldoTotalCts);
                    _cotizacion = null;
                  }),
                ),
              ],
            ),
          const SizedBox(height: 8),
          CampoMonto(
            controller: _monto,
            etiqueta: 'Monto a pagar',
            prefijo: 'Bs ',
            alCambiar: (_) {
              if (_cotizacion != null) setState(() => _cotizacion = null);
            },
          ),
          const SizedBox(height: 12),
          if (_cotizacion != null) ...[
            Card(
              child: Padding(
                padding: const EdgeInsets.all(12),
                child: Column(
                  children: [
                    _Linea('Monto', Dinero.bs(_cotizacion!.montoCts)),
                    _Linea('Comisión', Dinero.bs(_cotizacion!.comisionCts)),
                    const Divider(),
                    _Linea('Total a debitar', Dinero.bs(_cotizacion!.totalCts), fuerte: true),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 12),
            FilledButton(
              onPressed: _ocupado ? null : _consultarBeneficiario,
              child: const Text('Continuar'),
            ),
          ] else
            FilledButton.tonal(
              onPressed: _ocupado ? null : _cotizar,
              child: _ocupado
                  ? const SizedBox(width: 20, height: 20, child: CircularProgressIndicator(strokeWidth: 2))
                  : const Text('Ver comisión'),
            ),
        ],
      ),
    );
  }

  Widget _pasoBeneficiario(BuildContext context) {
    final b = _beneficiario;
    if (b == null) return const SizedBox.shrink();
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Card(
          child: ListTile(
            leading: const Icon(Icons.person_outline),
            title: Text(b.titular, style: const TextStyle(fontWeight: FontWeight.w700)),
            subtitle: Text('Tarjeta terminada en ${b.ultimos4} · ${_tarjeta!.banco}'),
          ),
        ),
        CheckboxListTile(
          contentPadding: EdgeInsets.zero,
          value: _beneficiarioOk,
          onChanged: (v) => setState(() => _beneficiarioOk = v ?? false),
          title: Text('Confirmo que el titular es ${b.titular} y la tarjeta termina en ${b.ultimos4}'),
          controlAffinity: ListTileControlAffinity.leading,
        ),
        FilledButton(
          onPressed: _beneficiarioOk ? () => setState(() => _paso = 3) : null,
          child: const Text('Es correcto'),
        ),
      ],
    );
  }

  Widget _pasoAutorizar(BuildContext context) {
    final c = _cotizacion;
    if (c == null || _cuenta == null || _tarjeta == null) return const SizedBox.shrink();
    final intento = _servicio.intentoActual;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Card(
          child: Padding(
            padding: const EdgeInsets.all(12),
            child: Column(
              children: [
                _Linea('Tarjeta', '${_tarjeta!.banco} •••• ${_tarjeta!.ultimos4}'),
                _Linea('Desde', _cuenta!.nombreVisible),
                _Linea('Total a debitar', Dinero.bs(c.totalCts), fuerte: true),
              ],
            ),
          ),
        ),
        const SizedBox(height: 8),
        const Text('Te enviaremos un código por SMS. Confirma con tu PIN y ese código.'),
        if (_errorReintentable != null) ...[
          const SizedBox(height: 8),
          Text(
            '$_errorReintentable\nPuedes reintentar con seguridad: usamos el mismo intento y nunca se cobra dos veces.',
            style: TextStyle(color: Theme.of(context).colorScheme.error),
          ),
        ],
        const SizedBox(height: 12),
        FilledButton.icon(
          key: const ValueKey('btn-pagar'),
          onPressed: _ocupado ? null : _pagar,
          icon: _ocupado
              ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2))
              : Icon(_errorReintentable == null ? Icons.lock_outline : Icons.refresh),
          label: Text(_errorReintentable == null ? 'Pagar ${Dinero.bs(c.totalCts)}' : 'Reintentar pago'),
        ),
        if (intento != null) ...[
          const SizedBox(height: 6),
          Text(
            'Intento ${intento.idempotencyKey.substring(0, 8)}… · envíos: ${intento.envios}',
            textAlign: TextAlign.center,
            style: Theme.of(context).textTheme.labelSmall,
          ),
        ],
      ],
    );
  }
}

class _PasoTarjeta extends ConsumerWidget {
  const _PasoTarjeta({required this.alElegir});

  final ValueChanged<Tarjeta> alElegir;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return VistaAsync<List<Tarjeta>>(
      valor: ref.watch(tarjetasProvider),
      alReintentar: () => ref.invalidate(tarjetasProvider),
      datos: (lista) => Column(
        children: [
          for (final t in lista)
            ListTile(
              contentPadding: EdgeInsets.zero,
              leading: const Icon(Icons.credit_card),
              title: Text('${t.banco} •••• ${t.ultimos4}'),
              subtitle: t.estadoCuenta == null
                  ? null
                  : Text('Mínimo ${Dinero.bs(t.estadoCuenta!.pagoMinimoCts)} · total ${Dinero.bs(t.estadoCuenta!.saldoTotalCts)}'),
              trailing: const Icon(Icons.chevron_right),
              onTap: () => alElegir(t),
            ),
        ],
      ),
    );
  }
}

class _Linea extends StatelessWidget {
  const _Linea(this.etiqueta, this.valor, {this.fuerte = false});

  final String etiqueta;
  final String valor;
  final bool fuerte;

  @override
  Widget build(BuildContext context) {
    final estilo = TextStyle(fontWeight: fuerte ? FontWeight.w800 : FontWeight.w500);
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 2),
      child: Row(children: [
        Expanded(child: Text(etiqueta)),
        Flexible(child: Text(valor, textAlign: TextAlign.end, style: estilo)),
      ]),
    );
  }
}
