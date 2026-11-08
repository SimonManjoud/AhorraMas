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
      if (c != null && _cedula.text.isEmpty) _cedula.text = _CedulaConPuntos.agrupar(c);
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
    final c = int.tryParse(_cedula.text.replaceAll(RegExp(r'\D'), ''));
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
          // La portada del paso de cédula pinta su propio fondo bajo la barra de estado.
          top: _paso == 1,
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

  /// Paso 1: portada vino a sangre (marca, saludo y lo que ofrece la app) y, montada sobre
  /// ella, la lámina con el campo de cédula. El pie (registro, regla de oro) queda abajo.
  Widget _pasoCedula(BuildContext context) {
    final t = Theme.of(context).textTheme;
    final cs = Theme.of(context).colorScheme;
    final c = AhorraColores.of(context);
    final config = ref.watch(appConfigProvider);
    final expirada = ref.read(sessionProvider.notifier).expiradaPorServidor;
    final nombre = _recuerdo?.primerNombre ?? '';
    return AnnotatedRegion<SystemUiOverlayStyle>(
      key: const ValueKey('paso-cedula'),
      value: SystemUiOverlayStyle.light.copyWith(statusBarColor: Colors.transparent),
      child: LayoutBuilder(
        builder: (context, limites) => SingleChildScrollView(
          child: ConstrainedBox(
            constraints: BoxConstraints(minHeight: limites.maxHeight),
            child: IntrinsicHeight(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  _PortadaLogin(
                    saludo: nombre.isEmpty ? 'Hola de nuevo' : 'Hola de nuevo, $nombre',
                    demo: config.fakeApi,
                  ),
                  Transform.translate(
                    offset: const Offset(0, -36),
                    child: Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 20),
                      child: Material(
                        type: MaterialType.transparency,
                        child: Container(
                          padding: const EdgeInsets.fromLTRB(20, 22, 20, 20),
                          decoration: BoxDecoration(
                            color: c.superficie,
                            borderRadius: BorderRadius.circular(28),
                            border: Border.all(color: c.reglaSuave),
                            boxShadow: [
                              BoxShadow(
                                color: c.tapaHonda.withValues(alpha: 0.10),
                                blurRadius: 28,
                                offset: const Offset(0, 12),
                              ),
                            ],
                          ),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.stretch,
                            children: [
                              if (expirada)
                                Padding(
                                  padding: const EdgeInsets.only(bottom: 14),
                                  child: Row(
                                    children: [
                                      Icon(Icons.schedule_rounded, size: 18, color: cs.error),
                                      const SizedBox(width: 8),
                                      Expanded(
                                        child: Text('Tu sesión expiró. Vuelve a ingresar.',
                                            style: t.bodyMedium?.copyWith(color: cs.error)),
                                      ),
                                    ],
                                  ),
                                ),
                              Text('Ingresa con tu cédula', style: t.titleMedium?.copyWith(fontWeight: FontWeight.w800)),
                              const SizedBox(height: 4),
                              Text('Luego te pediremos tu PIN de Ahorra+.',
                                  style: t.bodyMedium?.copyWith(color: c.tintaGris)),
                              const SizedBox(height: 18),
                              _CampoCedula(
                                controlador: _cedula,
                                nacionalidad: _nacionalidad,
                                error: _error,
                                alCambiarNacionalidad: (n) => setState(() => _nacionalidad = n),
                                alEscribir: () {
                                  if (_error != null) setState(() => _error = null);
                                },
                                alEnviar: _continuar,
                              ),
                              const SizedBox(height: 18),
                              FilledButton(
                                key: const ValueKey('login-continuar'),
                                onPressed: _continuar,
                                child: const Row(
                                  mainAxisSize: MainAxisSize.min,
                                  children: [
                                    Text('Continuar'),
                                    SizedBox(width: 8),
                                    Icon(Icons.arrow_forward_rounded, size: 20),
                                  ],
                                ),
                              ),
                            ],
                          ),
                        ),
                      ),
                    ),
                  ),
                  const Spacer(),
                  Padding(
                    padding: const EdgeInsets.fromLTRB(24, 0, 24, 20),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: [
                        Center(
                          child: TextButton(
                            onPressed: () => context.push('/registro'),
                            child: Text.rich(
                              TextSpan(children: [
                                TextSpan(
                                  text: '¿No tienes cuenta? ',
                                  style: TextStyle(color: c.tintaGris, fontWeight: FontWeight.w500),
                                ),
                                const TextSpan(text: 'Regístrate'),
                              ]),
                            ),
                          ),
                        ),
                        const SizedBox(height: 12),
                        const AvisoSinClaves(compacto: true),
                        if (config.fakeApi) ...[
                          const SizedBox(height: 10),
                          Text('Demo: V-12.345.678 - PIN 1234',
                              textAlign: TextAlign.center, style: t.bodySmall?.copyWith(color: cs.outline)),
                        ],
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
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
              label: Text('$_nacionalidad-${_CedulaConPuntos.agrupar(_cedula.text)}'),
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

/// Portada del ingreso: tapa vino a sangre, con la marca, el saludo y tres cosas que la app
/// hace por ti. Un degradado vino suave le da profundidad.
class _PortadaLogin extends StatelessWidget {
  const _PortadaLogin({required this.saludo, required this.demo});

  final String saludo;
  final bool demo;

  @override
  Widget build(BuildContext context) {
    final c = AhorraColores.of(context);
    final t = Theme.of(context).textTheme;
    final arriba = MediaQuery.paddingOf(context).top;
    return ClipRRect(
      borderRadius: const BorderRadius.vertical(bottom: Radius.circular(32)),
      child: DecoratedBox(
        decoration: BoxDecoration(
          // Degradado del mismo vino de la marca: un tono más cálido arriba que se
          // hunde hacia uno más profundo abajo, para que combine con el resto de la tapa.
          gradient: LinearGradient(
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
            colors: [
              Color.lerp(c.tapa, const Color(0xFFB0405C), 0.45)!,
              c.tapa,
              Color.lerp(c.tapa, Colors.black, 0.35)!,
            ],
            stops: const [0.0, 0.5, 1.0],
          ),
        ),
        child: Stack(
          children: [
            Padding(
              padding: EdgeInsets.fromLTRB(24, arriba + 18, 24, 64),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Container(
                        width: 42,
                        height: 42,
                        decoration: BoxDecoration(color: c.sobreTapa, borderRadius: BorderRadius.circular(14)),
                        child: Icon(Icons.savings_rounded, color: c.tapa, size: 24),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: Text('Ahorra+',
                            overflow: TextOverflow.ellipsis,
                            style: t.titleLarge?.copyWith(fontWeight: FontWeight.w800, color: c.sobreTapa)),
                      ),
                      if (demo)
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
                          decoration: BoxDecoration(
                            color: Colors.white.withValues(alpha: 0.12),
                            borderRadius: BorderRadius.circular(20),
                          ),
                          child: Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              Icon(Icons.science_outlined, size: 14, color: c.sobreTapa),
                              const SizedBox(width: 6),
                              Text('Modo demo', style: t.labelMedium?.copyWith(color: c.sobreTapa)),
                            ],
                          ),
                        ),
                    ],
                  ),
                  const SizedBox(height: 40),
                  Text(saludo, style: t.headlineLarge?.copyWith(color: c.sobreTapa)),
                  const SizedBox(height: 8),
                  Text(
                    'Todo tu dinero en Bs y USD, a tasa BCV, en una sola libreta.',
                    style: t.bodyLarge?.copyWith(color: c.sobreTapaTenue, height: 1.35),
                  ),
                  const SizedBox(height: 20),
                  const Wrap(
                    spacing: 8,
                    runSpacing: 8,
                    children: [
                      _Beneficio(icono: Icons.currency_exchange_rounded, texto: 'Tasa BCV del día'),
                      _Beneficio(icono: Icons.shield_outlined, texto: 'Cofre Protector'),
                      _Beneficio(icono: Icons.visibility_outlined, texto: 'Solo lectura'),
                    ],
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _Beneficio extends StatelessWidget {
  const _Beneficio({required this.icono, required this.texto});

  final IconData icono;
  final String texto;

  @override
  Widget build(BuildContext context) {
    final c = AhorraColores.of(context);
    return Container(
      padding: const EdgeInsets.fromLTRB(10, 6, 12, 6),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: c.sobreTapa.withValues(alpha: 0.22)),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icono, size: 15, color: c.sobreTapa),
          const SizedBox(width: 6),
          Text(texto,
              style: Theme.of(context).textTheme.labelMedium?.copyWith(color: c.sobreTapa, fontWeight: FontWeight.w600)),
        ],
      ),
    );
  }
}

/// Un solo campo para la cédula: el selector V/E vive dentro, a la izquierda, y el número
/// se escribe en cifras grandes con puntos de miles (12.345.678).
class _CampoCedula extends StatelessWidget {
  const _CampoCedula({
    required this.controlador,
    required this.nacionalidad,
    required this.error,
    required this.alCambiarNacionalidad,
    required this.alEscribir,
    required this.alEnviar,
  });

  final TextEditingController controlador;
  final String nacionalidad;
  final String? error;
  final ValueChanged<String> alCambiarNacionalidad;
  final VoidCallback alEscribir;
  final VoidCallback alEnviar;

  @override
  Widget build(BuildContext context) {
    final c = AhorraColores.of(context);
    final cs = Theme.of(context).colorScheme;
    final t = Theme.of(context).textTheme;
    final hayError = error != null;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Container(
          height: 64,
          padding: const EdgeInsets.fromLTRB(6, 6, 16, 6),
          decoration: BoxDecoration(
            color: c.hoja,
            borderRadius: BorderRadius.circular(18),
            border: Border.all(color: hayError ? cs.error : c.reglaSuave, width: hayError ? 1.6 : 1),
          ),
          child: Row(
            children: [
              Container(
                padding: const EdgeInsets.all(3),
                decoration: BoxDecoration(color: c.hojaHonda, borderRadius: BorderRadius.circular(14)),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    for (final n in const ['V', 'E'])
                      Semantics(
                        button: true,
                        selected: n == nacionalidad,
                        label: n == 'V' ? 'Venezolano' : 'Extranjero',
                        child: GestureDetector(
                          onTap: () => alCambiarNacionalidad(n),
                          child: AnimatedContainer(
                            duration: const Duration(milliseconds: 180),
                            width: 40,
                            height: 46,
                            alignment: Alignment.center,
                            decoration: BoxDecoration(
                              color: n == nacionalidad ? c.tapa : Colors.transparent,
                              borderRadius: BorderRadius.circular(11),
                            ),
                            child: Text(
                              n,
                              style: t.titleMedium?.copyWith(
                                fontWeight: FontWeight.w800,
                                color: n == nacionalidad ? c.sobreTapa : c.tintaGris,
                              ),
                            ),
                          ),
                        ),
                      ),
                  ],
                ),
              ),
              const SizedBox(width: 14),
              Expanded(
                child: TextField(
                  key: const ValueKey('login-cedula'),
                  controller: controlador,
                  keyboardType: TextInputType.number,
                  textInputAction: TextInputAction.next,
                  onSubmitted: (_) => alEnviar(),
                  onChanged: (_) => alEscribir(),
                  inputFormatters: [
                    FilteringTextInputFormatter.digitsOnly,
                    LengthLimitingTextInputFormatter(9),
                    _CedulaConPuntos(),
                  ],
                  style: AppTheme.cifra(tamano: 22, peso: FontWeight.w700, color: c.tinta),
                  cursorColor: c.tapa,
                  decoration: InputDecoration(
                    isDense: true,
                    contentPadding: EdgeInsets.zero,
                    filled: false,
                    border: InputBorder.none,
                    enabledBorder: InputBorder.none,
                    focusedBorder: InputBorder.none,
                    hintText: '12.345.678',
                    hintStyle: t.bodyLarge?.copyWith(color: c.tintaGris.withValues(alpha: 0.7)),
                  ),
                ),
              ),
            ],
          ),
        ),
        if (hayError)
          Padding(
            padding: const EdgeInsets.fromLTRB(6, 8, 6, 0),
            child: Row(
              children: [
                Icon(Icons.error_outline_rounded, size: 16, color: cs.error),
                const SizedBox(width: 6),
                Expanded(child: Text(error!, style: t.bodySmall?.copyWith(color: cs.error))),
              ],
            ),
          ),
      ],
    );
  }
}

/// Agrupa la cédula con puntos de miles mientras se escribe (12345678 → 12.345.678).
class _CedulaConPuntos extends TextInputFormatter {
  static String agrupar(String texto) {
    final d = texto.replaceAll(RegExp(r'\D'), '');
    final b = StringBuffer();
    for (var i = 0; i < d.length; i++) {
      if (i > 0 && (d.length - i) % 3 == 0) b.write('.');
      b.write(d[i]);
    }
    return b.toString();
  }

  @override
  TextEditingValue formatEditUpdate(TextEditingValue anterior, TextEditingValue nuevo) {
    final texto = agrupar(nuevo.text);
    return TextEditingValue(text: texto, selection: TextSelection.collapsed(offset: texto.length));
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
      resumen = 'Saldo rápido activo - toca para ver tus montos';
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
          '${consulta.desdeCache ? 'Sin conexión - último conocido' : 'Actualizado'} ${Fechas.conHora(s.actualizadoEn)}',
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
