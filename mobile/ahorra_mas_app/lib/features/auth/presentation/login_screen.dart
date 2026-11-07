import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/api/api_exception.dart';
import '../../../core/di/providers.dart';
import '../../../core/money/dinero.dart';
import '../../../core/session/session_controller.dart';
import '../../../core/storage/almacen_seguro.dart';
import '../../../core/theme/app_theme.dart';
import '../../../core/util/fechas.dart';
import '../../../core/widgets/comunes.dart';
import '../../../core/widgets/libreta.dart';
import '../../../core/widgets/teclado_pin.dart';
import '../domain/recuerdo_equipo.dart';
import '../domain/saldo_rapido_service.dart';
import 'auth_providers.dart';

/// Ingreso en dos pasos: 1) cédula; 2) "Mis bancos" (desplegable) y PIN.
///
/// Los bancos que se listan son los que este equipo recuerda del último ingreso (sin montos).
/// Los montos por banco solo aparecen si el saldo rápido está activo en este equipo de confianza.
class LoginScreen extends ConsumerStatefulWidget {
  const LoginScreen({super.key});

  @override
  ConsumerState<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends ConsumerState<LoginScreen> {
  final _cedula = TextEditingController();
  String _nacionalidad = 'V';
  int _paso = 0;
  String _pin = '';
  bool _ocupado = false;
  String? _error;
  RecuerdoEquipo? _recuerdo;

  @override
  void initState() {
    super.initState();
    _cargarUltimaCedula();
  }

  Future<void> _cargarUltimaCedula() async {
    final almacen = ref.read(almacenSeguroProvider);
    final c = await almacen.leer(ClavesAlmacen.ultimaCedula);
    final n = await almacen.leer(ClavesAlmacen.ultimaNacionalidad);
    final r = await ref.read(recuerdoEquipoServiceProvider).leer();
    if (!mounted) return;
    setState(() {
      if (c != null && _cedula.text.isEmpty) _cedula.text = c;
      if (n != null) _nacionalidad = n;
      _recuerdo = r;
    });
  }

  @override
  void dispose() {
    _cedula.dispose();
    super.dispose();
  }

  int? get _cedulaValida {
    final c = int.tryParse(_cedula.text.trim());
    return c == null || c <= 0 ? null : c;
  }

  /// El recuerdo del equipo solo se usa si es de la cédula escrita.
  RecuerdoEquipo? get _recuerdoDeEsta {
    final c = _cedulaValida;
    final r = _recuerdo;
    return c != null && r != null && r.esDe(_nacionalidad, c) ? r : null;
  }

  void _continuar() {
    if (_cedulaValida == null) {
      setState(() => _error = 'Escribe tu número de cédula.');
      return;
    }
    FocusScope.of(context).unfocus();
    setState(() {
      _paso = 1;
      _error = null;
      _pin = '';
    });
  }

  void _volver() => setState(() {
        _paso = 0;
        _error = null;
        _pin = '';
      });

  void _digito(String d) {
    if (_pin.length >= 6) return;
    setState(() {
      _pin += d;
      _error = null;
    });
  }

  void _borrar() {
    if (_pin.isEmpty) return;
    setState(() => _pin = _pin.substring(0, _pin.length - 1));
  }

  Future<void> _entrar() async {
    final cedula = _cedulaValida;
    if (cedula == null) {
      _volver();
      return;
    }
    if (_pin.length < 4) {
      setState(() => _error = 'El PIN tiene entre 4 y 6 dígitos.');
      return;
    }
    setState(() {
      _ocupado = true;
      _error = null;
    });
    try {
      final dispositivo = await ref.read(dispositivoProvider.future);
      final sesion = await ref.read(authRepositoryProvider).login(
            nacionalidadDoc: _nacionalidad,
            cedula: cedula,
            pin: _pin,
            dispositivo: dispositivo,
          );
      final almacen = ref.read(almacenSeguroProvider);
      // Si entra otra persona en este equipo, el saldo rápido del usuario anterior se anula aquí.
      if (_recuerdo != null && !_recuerdo!.esDe(_nacionalidad, cedula)) {
        await ref.read(saldoRapidoServiceProvider).limpiarLocal();
      }
      await almacen.escribir(ClavesAlmacen.ultimaCedula, '$cedula');
      await almacen.escribir(ClavesAlmacen.ultimaNacionalidad, _nacionalidad);
      await ref.read(recuerdoEquipoServiceProvider).recordarUsuario(
            nacionalidad: _nacionalidad,
            cedula: cedula,
            primerNombre: sesion.usuario.primerNombre,
          );
      ref.read(sessionProvider.notifier).iniciar(sesion);
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _error = mensajeDeError(e);
        _pin = '';
        _ocupado = false;
      });
      // Si se bloqueó el dispositivo, el saldo rápido queda anulado.
      ref.invalidate(saldoRapidoActivoProvider);
    }
  }

  @override
  Widget build(BuildContext context) {
    final reducir = MediaQuery.maybeDisableAnimationsOf(context) ?? false;
    return PopScope(
      canPop: _paso == 0,
      onPopInvokedWithResult: (hecho, _) {
        if (!hecho && _paso == 1) _volver();
      },
      child: Scaffold(
        body: SafeArea(
          child: Center(
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 440),
              child: AnimatedSwitcher(
                duration: reducir ? Duration.zero : const Duration(milliseconds: 260),
                switchInCurve: Curves.easeOutCubic,
                switchOutCurve: Curves.easeInCubic,
                transitionBuilder: (hijo, anim) => FadeTransition(
                  opacity: anim,
                  child: SlideTransition(
                    position: Tween(begin: const Offset(0.06, 0), end: Offset.zero).animate(anim),
                    child: hijo,
                  ),
                ),
                child: _paso == 0 ? _pasoCedula(context) : _pasoPin(context),
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _marca(BuildContext context) {
    final config = ref.watch(appConfigProvider);
    final c = AhorraColores.of(context);
    return Row(
      children: [
        Container(
          width: 44,
          height: 44,
          decoration: BoxDecoration(color: c.tapa, borderRadius: BorderRadius.circular(14)),
          child: Icon(Icons.savings_rounded, color: c.sobreTapa),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: Text('Ahorra+',
              overflow: TextOverflow.ellipsis,
              style: Theme.of(context).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w800)),
        ),
        if (config.fakeApi)
          Flexible(
            child: EtiquetaEstado(
              texto: 'Modo demo',
              color: Theme.of(context).colorScheme.onSurfaceVariant,
              icono: Icons.science_outlined,
            ),
          ),
      ],
    );
  }

  Widget _pasoCedula(BuildContext context) {
    final t = Theme.of(context).textTheme;
    final cs = Theme.of(context).colorScheme;
    final config = ref.watch(appConfigProvider);
    final expirada = ref.read(sessionProvider.notifier).expiradaPorServidor;
    return ListView(
      key: const ValueKey('paso-cedula'),
      padding: const EdgeInsets.fromLTRB(24, 16, 24, 24),
      children: [
        _marca(context),
        const SizedBox(height: 40),
        if (expirada)
          Padding(
            padding: const EdgeInsets.only(bottom: 12),
            child: Text('Tu sesión expiró. Vuelve a ingresar.', style: TextStyle(color: cs.error)),
          ),
        Text('Hola de nuevo', style: t.headlineLarge),
        const SizedBox(height: 6),
        Text('Escribe tu cédula para empezar.', style: t.bodyLarge?.copyWith(color: cs.onSurfaceVariant)),
        const SizedBox(height: 24),
        Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            SegmentedButton<String>(
              segments: const [
                ButtonSegment(value: 'V', label: Text('V')),
                ButtonSegment(value: 'E', label: Text('E')),
              ],
              selected: {_nacionalidad},
              showSelectedIcon: false,
              onSelectionChanged: (s) => setState(() => _nacionalidad = s.first),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: TextField(
                key: const ValueKey('login-cedula'),
                controller: _cedula,
                keyboardType: TextInputType.number,
                textInputAction: TextInputAction.next,
                onSubmitted: (_) => _continuar(),
                onChanged: (_) {
                  if (_error != null) setState(() => _error = null);
                },
                inputFormatters: [FilteringTextInputFormatter.digitsOnly, LengthLimitingTextInputFormatter(9)],
                decoration: InputDecoration(labelText: 'Cédula', errorText: _error),
              ),
            ),
          ],
        ),
        const SizedBox(height: 20),
        FilledButton(
          key: const ValueKey('login-continuar'),
          onPressed: _continuar,
          child: const Text('Continuar'),
        ),
        const SizedBox(height: 8),
        TextButton(
          onPressed: () => context.push('/registro'),
          child: const Text('¿No tienes cuenta? Regístrate'),
        ),
        const SizedBox(height: 28),
        const AvisoSinClaves(compacto: true),
        if (config.fakeApi) ...[
          const SizedBox(height: 10),
          Text('Demo: V-12345678 · PIN 1234',
              textAlign: TextAlign.center, style: t.bodySmall?.copyWith(color: cs.outline)),
        ],
      ],
    );
  }

  Widget _pasoPin(BuildContext context) {
    final t = Theme.of(context).textTheme;
    final cs = Theme.of(context).colorScheme;
    final recuerdo = _recuerdoDeEsta;
    final saldoActivo = (ref.watch(saldoRapidoActivoProvider).value ?? false) && recuerdo != null;
    return ListView(
      key: const ValueKey('paso-pin'),
      padding: const EdgeInsets.fromLTRB(12, 8, 24, 24),
      children: [
        Row(
          children: [
            IconButton(
              key: const ValueKey('login-volver'),
              tooltip: 'Cambiar cédula',
              onPressed: _ocupado ? null : _volver,
              icon: const Icon(Icons.arrow_back_rounded),
            ),
            const SizedBox(width: 4),
            Chip(
              backgroundColor: AhorraColores.of(context).superficie,
              avatar: const Icon(Icons.badge_outlined, size: 18),
              label: Text('$_nacionalidad-${_cedula.text.trim()}'),
            ),
          ],
        ),
        Padding(
          padding: const EdgeInsets.only(left: 12),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              const SizedBox(height: 16),
              Text(
                recuerdo == null || recuerdo.primerNombre.isEmpty
                    ? 'Ingresa tu PIN'
                    : 'Hola de nuevo, ${recuerdo.primerNombre}',
                style: t.headlineMedium,
              ),
              const SizedBox(height: 16),
              _MisBancos(recuerdo: recuerdo, saldoActivo: saldoActivo),
              const SizedBox(height: 24),
              Center(child: Text('Tu PIN de Ahorra+', style: t.titleSmall?.copyWith(color: cs.onSurfaceVariant))),
              const SizedBox(height: 12),
              PuntosPin(longitud: _pin.length, error: _error != null),
              if (_error != null) ...[
                const SizedBox(height: 10),
                Text(_error!, textAlign: TextAlign.center, style: TextStyle(color: cs.error)),
              ],
              const SizedBox(height: 12),
              TecladoPin(alDigito: _digito, alBorrar: _borrar, habilitado: !_ocupado),
              const SizedBox(height: 16),
              FilledButton(
                key: const ValueKey('login-entrar'),
                onPressed: _ocupado ? null : _entrar,
                child: _ocupado
                    ? const SizedBox(width: 22, height: 22, child: CircularProgressIndicator(strokeWidth: 2.5))
                    : const Text('Entrar'),
              ),
            ],
          ),
        ),
      ],
    );
  }
}

/// Desplegable "Mis bancos": nombres que el equipo recuerda y, con saldo rápido activo,
/// el monto de cada institución (consultado con el token ligado a este equipo).
class _MisBancos extends ConsumerStatefulWidget {
  const _MisBancos({required this.recuerdo, required this.saldoActivo});

  final RecuerdoEquipo? recuerdo;
  final bool saldoActivo;

  @override
  ConsumerState<_MisBancos> createState() => _MisBancosState();
}

class _MisBancosState extends ConsumerState<_MisBancos> {
  bool _abierto = false;
  bool _cargando = false;
  ConsultaSaldoRapido? _consulta;
  String? _error;

  Future<void> _alternar() async {
    setState(() => _abierto = !_abierto);
    if (_abierto && widget.saldoActivo && _consulta == null && !_cargando) await _consultar();
  }

  Future<void> _consultar() async {
    setState(() {
      _cargando = true;
      _error = null;
    });
    try {
      final huella = (await ref.read(dispositivoProvider.future)).huella;
      final r = await ref.read(saldoRapidoServiceProvider).consultar(huella);
      if (!mounted) return;
      if (r == null) {
        ref.invalidate(saldoRapidoActivoProvider);
      } else {
        setState(() => _consulta = r);
      }
    } catch (e) {
      if (mounted) setState(() => _error = mensajeDeError(e));
    } finally {
      if (mounted) setState(() => _cargando = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final c = AhorraColores.of(context);
    final t = Theme.of(context).textTheme;
    final recordados = widget.recuerdo?.bancos ?? const <BancoRecordado>[];
    final consulta = _consulta;
    final n = consulta?.saldo.bancos.length ?? recordados.length;
    final reducir = MediaQuery.maybeDisableAnimationsOf(context) ?? false;

    final String resumen;
    if (widget.saldoActivo) {
      resumen = 'Saldo rápido activo · toca para ver tus montos';
    } else if (recordados.isEmpty) {
      resumen = 'Se verán tras tu primer ingreso';
    } else {
      resumen = recordados.length <= 2
          ? recordados.map((b) => b.nombre).join(' y ')
          : '${recordados.take(2).map((b) => b.nombre).join(', ')} y ${recordados.length - 2} más';
    }

    return Lamina(
      relleno: EdgeInsets.zero,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          InkWell(
            key: const ValueKey('mis-bancos'),
            onTap: _alternar,
            child: Padding(
              padding: const EdgeInsets.fromLTRB(16, 14, 12, 14),
              child: Row(
                children: [
                  Container(
                    width: 40,
                    height: 40,
                    decoration: BoxDecoration(color: c.hojaHonda, borderRadius: BorderRadius.circular(12)),
                    child: Icon(Icons.account_balance_rounded, color: c.tinta, size: 20),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(n == 0 ? 'Mis bancos' : 'Mis bancos ($n)', style: t.titleSmall?.copyWith(fontWeight: FontWeight.w700)),
                        const SizedBox(height: 2),
                        Text(resumen,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: t.bodySmall?.copyWith(color: c.tintaGris)),
                      ],
                    ),
                  ),
                  AnimatedRotation(
                    turns: _abierto ? 0.5 : 0,
                    duration: reducir ? Duration.zero : const Duration(milliseconds: 200),
                    child: Icon(Icons.expand_more_rounded, color: c.tintaGris),
                  ),
                ],
              ),
            ),
          ),
          AnimatedSize(
            duration: reducir ? Duration.zero : const Duration(milliseconds: 240),
            curve: Curves.easeOutCubic,
            alignment: Alignment.topCenter,
            child: _abierto ? _contenido(context, recordados, consulta) : const SizedBox(width: double.infinity),
          ),
        ],
      ),
    );
  }

  Widget _contenido(BuildContext context, List<BancoRecordado> recordados, ConsultaSaldoRapido? consulta) {
    final c = AhorraColores.of(context);
    final t = Theme.of(context).textTheme;
    final nota = t.bodySmall?.copyWith(color: c.tintaGris);
    final filas = <Widget>[];

    if (widget.saldoActivo && _cargando) {
      filas.add(const Padding(
        padding: EdgeInsets.all(16),
        child: Center(child: SizedBox(width: 22, height: 22, child: CircularProgressIndicator(strokeWidth: 2.5))),
      ));
    } else if (widget.saldoActivo && consulta != null) {
      final s = consulta.saldo;
      filas.add(Padding(
        padding: const EdgeInsets.fromLTRB(16, 4, 16, 8),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.end,
          children: [
            Expanded(child: Text('Total', style: t.bodyMedium?.copyWith(color: c.tintaGris))),
            Column(
              crossAxisAlignment: CrossAxisAlignment.end,
              children: [
                Text(Dinero.usd(s.totalUsdCts), style: AppTheme.cifra(tamano: 18, peso: FontWeight.w800, color: c.tinta)),
                Text(Dinero.bs(s.totalBsCts), style: AppTheme.cifra(tamano: 12, peso: FontWeight.w500, color: c.tintaGris)),
              ],
            ),
          ],
        ),
      ));
      for (final b in s.bancos) {
        filas.add(_FilaBanco(nombre: b.nombre, cripto: b.esCripto, usd: Dinero.usd(b.totalUsdCts), bs: Dinero.bs(b.totalBsCts)));
      }
      filas.add(Padding(
        padding: const EdgeInsets.fromLTRB(16, 6, 16, 14),
        child: Text(
          '${consulta.desdeCache ? 'Sin conexión · último conocido' : 'Actualizado'} ${Fechas.conHora(s.actualizadoEn)}',
          style: nota,
        ),
      ));
    } else {
      for (final b in recordados) {
        filas.add(_FilaBanco(nombre: b.nombre, cripto: b.esCripto));
      }
      filas.add(Padding(
        padding: const EdgeInsets.fromLTRB(16, 6, 16, 14),
        child: Text(
          _error ??
              (recordados.isEmpty
                  ? 'Aún no conocemos tus bancos en este equipo. Aparecerán aquí después de tu primer ingreso.'
                  : 'Para ver cuánto tienes en cada banco sin ingresar, activa Saldo rápido en '
                      'Más → Perfil y seguridad. Solo funciona en este equipo.'),
          style: nota,
        ),
      ));
    }
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [Padding(padding: const EdgeInsets.symmetric(horizontal: 16), child: const Filete()), ...filas],
    );
  }
}

class _FilaBanco extends StatelessWidget {
  const _FilaBanco({required this.nombre, required this.cripto, this.usd, this.bs});

  final String nombre;
  final bool cripto;
  final String? usd;
  final String? bs;

  @override
  Widget build(BuildContext context) {
    final c = AhorraColores.of(context);
    final t = Theme.of(context).textTheme;
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 10, 16, 10),
      child: Row(
        children: [
          CircleAvatar(
            radius: 16,
            backgroundColor: c.hojaHonda,
            child: Icon(cripto ? Icons.currency_bitcoin_rounded : Icons.account_balance_outlined, size: 16, color: c.tinta),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(nombre, overflow: TextOverflow.ellipsis, style: t.bodyMedium?.copyWith(fontWeight: FontWeight.w600)),
                Text(cripto ? 'Plataforma cripto' : 'Banco', style: t.bodySmall?.copyWith(color: c.tintaGris)),
              ],
            ),
          ),
          const SizedBox(width: 8),
          if (usd != null)
            Column(
              crossAxisAlignment: CrossAxisAlignment.end,
              children: [
                Text(usd!, style: AppTheme.cifra(tamano: 14, peso: FontWeight.w700, color: c.tinta)),
                if (bs != null) Text(bs!, style: AppTheme.cifra(tamano: 11.5, peso: FontWeight.w500, color: c.tintaGris)),
              ],
            )
          else
            Text('••••', style: AppTheme.cifra(tamano: 14, color: c.tintaGris)),
        ],
      ),
    );
  }
}
