import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/api/api_exception.dart';
import '../../../core/di/providers.dart';
import '../../../core/money/dinero.dart';
import '../../../core/session/session_controller.dart';
import '../../../core/storage/almacen_seguro.dart';
import '../../../core/util/fechas.dart';
import '../../../core/widgets/comunes.dart';
import '../../../core/widgets/teclado_pin.dart';
import '../domain/saldo_rapido_service.dart';
import 'auth_providers.dart';

class LoginScreen extends ConsumerStatefulWidget {
  const LoginScreen({super.key});

  @override
  ConsumerState<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends ConsumerState<LoginScreen> {
  final _cedula = TextEditingController();
  String _nacionalidad = 'V';
  String _pin = '';
  bool _ocupado = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _cargarUltimaCedula();
  }

  Future<void> _cargarUltimaCedula() async {
    final almacen = ref.read(almacenSeguroProvider);
    final c = await almacen.leer(ClavesAlmacen.ultimaCedula);
    final n = await almacen.leer(ClavesAlmacen.ultimaNacionalidad);
    if (!mounted) return;
    setState(() {
      if (c != null && _cedula.text.isEmpty) _cedula.text = c;
      if (n != null) _nacionalidad = n;
    });
  }

  @override
  void dispose() {
    _cedula.dispose();
    super.dispose();
  }

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
    final cedula = int.tryParse(_cedula.text.trim());
    if (cedula == null || cedula <= 0) {
      setState(() => _error = 'Escribe tu número de cédula.');
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
      await almacen.escribir(ClavesAlmacen.ultimaCedula, '$cedula');
      await almacen.escribir(ClavesAlmacen.ultimaNacionalidad, _nacionalidad);
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
    final t = Theme.of(context).textTheme;
    final cs = Theme.of(context).colorScheme;
    final config = ref.watch(appConfigProvider);
    final saldoActivo = ref.watch(saldoRapidoActivoProvider).value ?? false;
    final expirada = ref.read(sessionProvider.notifier).expiradaPorServidor;

    return Scaffold(
      body: SafeArea(
        child: Center(
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 440),
            child: ListView(
              padding: const EdgeInsets.fromLTRB(24, 16, 24, 24),
              children: [
                Row(
                  children: [
                    Container(
                      padding: const EdgeInsets.all(10),
                      decoration: BoxDecoration(color: cs.primary, borderRadius: BorderRadius.circular(14)),
                      child: Icon(Icons.savings_outlined, color: cs.onPrimary),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Text('Ahorra+',
                          overflow: TextOverflow.ellipsis,
                          style: t.headlineSmall?.copyWith(fontWeight: FontWeight.w800)),
                    ),
                    if (config.fakeApi)
                      const Flexible(
                        child: EtiquetaEstado(texto: 'Modo demo', color: Colors.deepOrange, icono: Icons.science_outlined),
                      ),
                  ],
                ),
                const SizedBox(height: 20),
                if (saldoActivo) ...[
                  const TarjetaSaldoRapido(),
                  const SizedBox(height: 20),
                ],
                if (expirada)
                  Padding(
                    padding: const EdgeInsets.only(bottom: 12),
                    child: Text('Tu sesión expiró. Vuelve a ingresar.', style: TextStyle(color: cs.error)),
                  ),
                Text('Hola de nuevo', style: t.headlineMedium?.copyWith(fontWeight: FontWeight.w700)),
                const SizedBox(height: 4),
                Text('Ingresa con tu cédula y tu PIN de Ahorra+.',
                    style: t.bodyMedium?.copyWith(color: cs.onSurfaceVariant)),
                const SizedBox(height: 20),
                Row(
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
                        inputFormatters: [FilteringTextInputFormatter.digitsOnly, LengthLimitingTextInputFormatter(9)],
                        decoration: const InputDecoration(labelText: 'Cédula'),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 24),
                Center(child: Text('PIN', style: t.labelLarge)),
                const SizedBox(height: 10),
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
                const SizedBox(height: 8),
                TextButton(
                  onPressed: _ocupado ? null : () => context.push('/registro'),
                  child: const Text('¿No tienes cuenta? Regístrate'),
                ),
                const SizedBox(height: 8),
                const AvisoSinClaves(compacto: true),
                if (config.fakeApi) ...[
                  const SizedBox(height: 8),
                  Text('Demo: V-12345678 · PIN 1234',
                      textAlign: TextAlign.center, style: t.bodySmall?.copyWith(color: cs.outline)),
                ],
              ],
            ),
          ),
        ),
      ),
    );
  }
}

/// Saldo rápido (F3): totales enmascarados; se revelan al tocar sin iniciar sesión.
class TarjetaSaldoRapido extends ConsumerStatefulWidget {
  const TarjetaSaldoRapido({super.key});

  @override
  ConsumerState<TarjetaSaldoRapido> createState() => _TarjetaSaldoRapidoState();
}

class _TarjetaSaldoRapidoState extends ConsumerState<TarjetaSaldoRapido> {
  ConsultaSaldoRapido? _consulta;
  bool _cargando = false;
  String? _error;

  Future<void> _alternar() async {
    if (_consulta != null) {
      setState(() => _consulta = null);
      return;
    }
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
        return;
      }
      setState(() => _consulta = r);
    } catch (e) {
      if (mounted) setState(() => _error = mensajeDeError(e));
    } finally {
      if (mounted) setState(() => _cargando = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final t = Theme.of(context).textTheme;
    final c = _consulta;
    return Card(
      color: cs.primaryContainer,
      child: InkWell(
        borderRadius: BorderRadius.circular(20),
        onTap: _cargando ? null : _alternar,
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Icon(Icons.bolt, color: cs.onPrimaryContainer, size: 18),
                  const SizedBox(width: 6),
                  Text('Saldo rápido', style: t.labelLarge?.copyWith(color: cs.onPrimaryContainer)),
                  const Spacer(),
                  if (_cargando)
                    const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2))
                  else
                    Icon(c == null ? Icons.visibility_outlined : Icons.visibility_off_outlined,
                        color: cs.onPrimaryContainer, size: 20),
                ],
              ),
              const SizedBox(height: 8),
              Text(c == null ? 'Bs ••••••,••' : Dinero.bs(c.saldo.totalBsCts),
                  style: t.headlineSmall?.copyWith(color: cs.onPrimaryContainer, fontWeight: FontWeight.w700)),
              Text(c == null ? '\$ ••••,••' : Dinero.usd(c.saldo.totalUsdCts),
                  style: t.titleMedium?.copyWith(color: cs.onPrimaryContainer)),
              const SizedBox(height: 6),
              Text(
                _error ??
                    (c == null
                        ? 'Toca para ver tu posición global'
                        : '${c.desdeCache ? 'Sin conexión · último conocido' : 'Actualizado'} ${Fechas.conHora(c.saldo.actualizadoEn)}'),
                style: t.bodySmall?.copyWith(color: cs.onPrimaryContainer.withValues(alpha: 0.8)),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
