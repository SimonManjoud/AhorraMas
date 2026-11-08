import 'dart:async';
import 'dart:math';

import '../core/api/api_exception.dart';
import '../core/money/dinero.dart';
import '../core/util/fechas.dart';
import '../features/asistente/domain/asistente.dart';
import '../features/auth/domain/auth_models.dart';
import '../features/auth/domain/auth_repository.dart';
import '../features/cofre/domain/cofre_models.dart';
import '../features/cofre/domain/cofre_repository.dart';
import '../features/consolidacion/domain/consolidacion_models.dart';
import '../features/consolidacion/domain/consolidacion_repository.dart';
import '../features/notificaciones/domain/notificaciones.dart';
import '../features/pagos/domain/pagos_models.dart';
import '../features/pagos/domain/pagos_repository.dart';
import '../features/puntos/domain/puntos.dart';

/// API falsa en memoria (`--dart-define=FAKE_API=true`).
///
/// Implementa **todos** los puertos de la app con datos de ejemplo realistas para poder
/// hacer demos y pruebas de widgets sin backend. Las reglas imitan el contrato:
///
/// * Usuario demo: **V-12345678**, PIN **1234** (KYC aprobado).
/// * Pagos TDC: si los céntimos del monto son `77` (p. ej. `Bs 1.000,77`) el pago queda
///   `EN_VERIFICACION` y pasa a `CAPTURADO` tras consultarlo un par de veces; si supera el
///   saldo de la cuenta origen queda `RECHAZADO`. La misma `Idempotency-Key` devuelve el
///   mismo pago.
/// * Vincular bancos: abrir la `urlAutorizacion` (falsa) activa el consentimiento.
class FakeApi
    implements
        AuthRepository,
        ConsolidacionRepository,
        CofreRepository,
        PagosRepository,
        PuntosRepository,
        AsistenteRepository,
        NotificacionesRepository,
        DemoRepository {
  FakeApi({this.latencia = const Duration(milliseconds: 350), DateTime? ahora})
      : _ahoraBase = ahora {
    _sembrar();
  }

  final Duration latencia;
  final DateTime? _ahoraBase;
  int _diasAvanzados = 0;
  final _rnd = Random(42);
  int _secuencia = 1000;

  static const pinDemo = '1234';
  static const cedulaDemo = 12345678;
  static const tasaBase = 4987321; // 498,7321 Bs/USD

  DateTime get _ahora => (_ahoraBase ?? DateTime.now()).add(Duration(days: _diasAvanzados));
  DateTime get _hoy => Fechas.soloFecha(_ahora);
  int _id() => ++_secuencia;

  Future<void> _esperar() async {
    if (latencia > Duration.zero) await Future<void>.delayed(latencia);
  }

  static ApiException _err(int status, String codigo, String mensaje) =>
      ApiException(statusCode: status, codigo: codigo, mensaje: mensaje);

  // ---------------------------------------------------------------------------
  // Estado
  // ---------------------------------------------------------------------------
  final Map<int, _UsuarioF> _usuarios = {};
  _UsuarioF? _actual;
  int _fallosPin = 0;
  final Map<String, NivelStepUp> _stepUps = {};
  final Map<String, String> _desafios = {};
  String? _tokenSaldo;
  String? _huellaSaldo;

  /// En la demo web la API falsa se recrea al recargar; un token `saldo-demo-` emitido antes se
  /// sigue aceptando (como haría el servidor real) salvo que se haya desactivado en esta sesión.
  bool _saldoAnulado = false;

  final List<Institucion> _instituciones = const [
    Institucion(idInstitucion: 1, codigo: '0901', nombre: 'Banco Ávila', tipo: 'banco', estatusIntegracion: 'sandbox'),
    Institucion(idInstitucion: 2, codigo: '0902', nombre: 'Banco Orinoco', tipo: 'banco', estatusIntegracion: 'sandbox'),
    Institucion(idInstitucion: 3, codigo: '0903', nombre: 'Banco Catatumbo', tipo: 'banco', estatusIntegracion: 'sandbox'),
    Institucion(idInstitucion: 4, codigo: '0904', nombre: 'Banco Roraima', tipo: 'banco', estatusIntegracion: 'sandbox'),
    Institucion(idInstitucion: 5, codigo: 'C001', nombre: 'CriptoAndes Exchange', tipo: 'plataforma_cripto', estatusIntegracion: 'sandbox'),
    Institucion(idInstitucion: 6, codigo: 'C002', nombre: 'Billetera Tepuy', tipo: 'plataforma_cripto', estatusIntegracion: 'sandbox'),
  ];
  final List<_ConsentimientoF> _consentimientos = [];
  final Map<int, List<_CuentaF>> _cuentasPorInstitucion = {};
  final Map<int, List<Movimiento>> _movimientos = {};
  DateTime? _ultimaSincronizacion;

  final List<Tarjeta> _tarjetas = [];
  final List<_PagoF> _pagos = [];
  final Map<String, _PagoF> _pagosPorClave = {};

  final List<_MetaF> _metas = [];
  final List<Regla> _reglas = [];
  late _RachaF _racha;

  int _puntosSaldo = 1240;
  int _puntosTotal = 1500;
  final List<MovimientoPuntos> _movPuntos = [];
  final List<Recompensa> _recompensas = [];
  final List<Canje> _canjes = [];

  final List<Recomendacion> _recomendaciones = [];
  final List<Notificacion> _notificaciones = [];

  // ---------------------------------------------------------------------------
  // Datos de ejemplo
  // ---------------------------------------------------------------------------
  void _sembrar() {
    final u = _UsuarioF(
      idUsuario: 1,
      nacionalidad: 'V',
      cedula: cedulaDemo,
      pin: pinDemo,
      primerNombre: 'María',
      primerApellido: 'Pérez',
      correo: 'maria@correo.com',
      telefono: '04141234567',
      kycAprobado: true,
    );
    _usuarios[u.cedula] = u;

    _activarInstitucion(1, debito: true, fecha: _hoy.subtract(const Duration(days: 40)));
    _activarInstitucion(2, debito: true, fecha: _hoy.subtract(const Duration(days: 30)));
    _activarInstitucion(5, debito: false, fecha: _hoy.subtract(const Duration(days: 12)));
    _ultimaSincronizacion = _ahora.subtract(const Duration(minutes: 7));

    _tarjetas.addAll([
      Tarjeta(
        idTarjeta: 4,
        idInstitucion: 2,
        banco: 'Banco Orinoco',
        ultimos4: '7781',
        titularConfirmado: 'MARIA PEREZ',
        esPropia: true,
        marca: 'mastercard',
        estadoCuenta: EstadoCuentaTdc(
          fechaCorte: _hoy.subtract(const Duration(days: 6)),
          fechaLimitePago: _hoy.add(const Duration(days: 9)),
          saldoTotalCts: 8754320,
          pagoMinimoCts: 1312000,
        ),
      ),
      Tarjeta(
        idTarjeta: 5,
        idInstitucion: 1,
        banco: 'Banco Ávila',
        ultimos4: '3310',
        alias: 'Visa Ávila',
        titularConfirmado: 'MARIA PEREZ',
        esPropia: true,
        marca: 'visa',
        estadoCuenta: EstadoCuentaTdc(
          fechaCorte: _hoy.subtract(const Duration(days: 12)),
          fechaLimitePago: _hoy.add(const Duration(days: 2)),
          saldoTotalCts: 2310050,
          pagoMinimoCts: 346500,
        ),
      ),
    ]);

    // Pago histórico ya conciliado.
    final p = _PagoF(
      idPago: 20,
      idTarjeta: 4,
      estado: EstadoPago.conciliado,
      montoCts: 1500000,
      comisionCts: 3000,
      creado: _ahora.subtract(const Duration(days: 20)),
      referencia: 'S7B-20A91',
      autorizacion: 'AUT448120',
    );
    p.historial.addAll([
      _cambio(1, null, EstadoPago.pendiente, p.creado, 'usuario'),
      _cambio(2, EstadoPago.pendiente, EstadoPago.autorizado, p.creado, 'suiche'),
      _cambio(3, EstadoPago.autorizado, EstadoPago.capturado, p.creado, 'suiche'),
      _cambio(4, EstadoPago.capturado, EstadoPago.conciliado,
          p.creado.add(const Duration(days: 1)), 'conciliacion'),
    ]);
    _pagos.add(p);

    // Metas del Cofre Protector.
    final viaje = _MetaF(
      idMeta: 1,
      nombre: 'Viaje en marzo',
      categoria: CategoriaMeta.viaje,
      objetivo: 30000,
      fechaObjetivo: DateTime(_hoy.year + (_hoy.month >= 3 ? 1 : 0), 3, 15),
      frecuencia: FrecuenciaCuota.diaria,
      creada: _ahora.subtract(const Duration(days: 16)),
    );
    for (var i = 16; i >= 1; i--) {
      if (i == 5 || i == 11) continue; // días colchón
      viaje.aportes.add(_aporte(
          _ahora.subtract(Duration(days: i)), i.isEven ? 'cuota_del_dia' : 'manual', i == 16 ? 2400 : 680));
    }
    viaje.sello = _SelloF(idSello: 2, monto: 5000, liberacion: viaje.fechaObjetivo);
    final emergencia = _MetaF(
      idMeta: 2,
      nombre: 'Fondo de emergencia',
      categoria: CategoriaMeta.emergencia,
      objetivo: 50000,
      fechaObjetivo: _hoy.add(const Duration(days: 240)),
      frecuencia: FrecuenciaCuota.semanal,
      creada: _ahora.subtract(const Duration(days: 45)),
    );
    for (var i = 6; i >= 1; i--) {
      emergencia.aportes.add(_aporte(_ahora.subtract(Duration(days: i * 7)), 'regla', 1400));
    }
    _metas.addAll([viaje, emergencia]);
    _reglas.add(Regla(
      idRegla: 1,
      idMeta: 2,
      tipo: TipoRegla.fijoQuincenal,
      montoFijoUsdCts: 1500,
      estatus: 'activa',
      fechaAprobacion: _ahora.subtract(const Duration(days: 45)),
    ));

    _racha = _RachaF(
      inicio: _hoy.subtract(const Duration(days: 16)),
      dias: [
        for (var i = 16; i >= 1; i--)
          DiaRacha(
            fecha: _hoy.subtract(Duration(days: i)),
            tipo: (i == 5 || i == 11) ? TipoDiaRacha.colchon : TipoDiaRacha.aporte,
          ),
      ],
      colchonesDisponibles: 1,
    );

    _movPuntos.addAll([
      MovimientoPuntos(idTransaccion: 1, fechaHora: _ahora.subtract(const Duration(days: 20)), concepto: 'pago_puntual', descripcion: 'Pago puntual TDC •••• 7781', puntos: 50),
      MovimientoPuntos(idTransaccion: 2, fechaHora: _ahora.subtract(const Duration(days: 9)), concepto: 'racha', descripcion: 'Racha de 7 días', puntos: 100),
      MovimientoPuntos(idTransaccion: 3, fechaHora: _ahora.subtract(const Duration(days: 3)), concepto: 'canje', descripcion: 'Canje: café de especialidad', puntos: -260),
      MovimientoPuntos(idTransaccion: 4, fechaHora: _ahora.subtract(const Duration(days: 1)), concepto: 'meta', descripcion: 'Aporte a «Viaje en marzo»', puntos: 9),
    ]);
    _recompensas.addAll(const [
      Recompensa(idRecompensa: 1, descripcion: 'Café de especialidad', costoPuntos: 260, comercio: 'Café Tepuy', nivelMinimo: 'Base', stockDisponible: 40),
      Recompensa(idRecompensa: 2, descripcion: '10 % en mercado', costoPuntos: 800, comercio: 'Mercado La Candelaria', nivelMinimo: 'Base', stockDisponible: 15),
      Recompensa(idRecompensa: 3, descripcion: 'Entrada de cine 2x1', costoPuntos: 1200, comercio: 'Cines Ávila', nivelMinimo: 'Base', stockDisponible: 8),
      Recompensa(idRecompensa: 4, descripcion: 'Recarga de datos 5 GB', costoPuntos: 3000, comercio: 'Operadora Móvil', nivelMinimo: 'Gold', stockDisponible: 20),
      Recompensa(idRecompensa: 5, descripcion: 'Noche de hotel en Mérida', costoPuntos: 15000, comercio: 'Posada Sierra Nevada', nivelMinimo: 'Platinum', stockDisponible: 2),
    ]);
    _canjes.add(Canje(
      idCanje: 1,
      codigoCanje: 'CJ-7Q2K9',
      puntosCanjeados: 260,
      estatus: 'emitido',
      fechaHora: _ahora.subtract(const Duration(days: 3)),
      descripcion: 'Café de especialidad',
    ));

    _notificaciones.addAll([
      Notificacion(idNotificacion: 1, canal: 'push', tipo: 'cofre', titulo: 'Tu Ruta del día', mensaje: 'Hoy toca apartar tu cuota para «Viaje en marzo». ¡Mantén tu racha!', fechaHoraEnvio: _ahora.subtract(const Duration(hours: 2)), estatus: 'enviada', leida: false),
      Notificacion(idNotificacion: 2, canal: 'push', tipo: 'racha', titulo: 'Usamos un día colchón', mensaje: 'Ayer no pudiste aportar y tu racha sigue viva gracias a un día colchón.', fechaHoraEnvio: _ahora.subtract(const Duration(days: 5)), estatus: 'enviada', leida: true),
      Notificacion(idNotificacion: 3, canal: 'sms', tipo: 'seguridad', titulo: 'Nuevo dispositivo', mensaje: 'Se inició sesión desde un nuevo dispositivo. Si no fuiste tú, revócalo en Ajustes.', fechaHoraEnvio: _ahora.subtract(const Duration(days: 12)), estatus: 'enviada', leida: true),
      Notificacion(idNotificacion: 4, canal: 'push', tipo: 'pago', titulo: 'Pago conciliado', mensaje: 'Tu pago a la TDC •••• 7781 fue conciliado.', fechaHoraEnvio: _ahora.subtract(const Duration(days: 19)), estatus: 'enviada', leida: true),
    ]);
  }

  _AporteF _aporte(DateTime fecha, String origen, int usdCts, {String tipo = 'aporte'}) {
    final tasa = _tasaEn(fecha);
    return _AporteF(
      idAporte: _id(),
      fecha: fecha,
      tipo: tipo,
      origen: origen,
      usdCts: usdCts,
      bsCts: Dinero.usdABs(usdCts, tasa),
    );
  }

  /// Tasa BCV simulada: devaluación lineal ≈ 0,18 %/día hacia atrás desde la tasa base.
  int _tasaEn(DateTime fecha) {
    final dias = _hoy.difference(Fechas.soloFecha(fecha)).inDays;
    return tasaBase - dias * 8977;
  }

  CambioEstadoPago _cambio(int s, EstadoPago? a, EstadoPago n, DateTime f, String actor) =>
      CambioEstadoPago(secuencia: s, estadoAnterior: a, estadoNuevo: n, fechaHora: f, actor: actor);

  void _activarInstitucion(int idInstitucion, {required bool debito, required DateTime fecha}) {
    final inst = _instituciones.firstWhere((i) => i.idInstitucion == idInstitucion);
    final existente = _consentimientos.where((c) => c.idInstitucion == idInstitucion && c.estatus != 'revocado');
    final c = existente.isNotEmpty
        ? existente.first
        : (_ConsentimientoF(
            id: _id(),
            idInstitucion: idInstitucion,
            institucion: inst.nombre,
            debito: debito && !inst.esCripto,
          )..estatus = 'pendiente');
    if (!_consentimientos.contains(c)) _consentimientos.add(c);
    c.estatus = 'activo';
    c.otorgado = fecha;
    c.vence = fecha.add(const Duration(days: 180));
    _cuentasPorInstitucion.putIfAbsent(idInstitucion, () => _generarCuentas(inst));
  }

  List<_CuentaF> _generarCuentas(Institucion inst) {
    final cuentas = switch (inst.idInstitucion) {
      1 => [
          _CuentaF(_id(), 'Nómina', '4821', 'corriente', 'VES', 1543022),
          _CuentaF(_id(), 'Ahorro en divisas', '0937', 'ahorro', 'USD', 20050),
        ],
      2 => [
          _CuentaF(_id(), 'Gastos del mes', '1166', 'corriente', 'VES', 4877310),
          _CuentaF(_id(), null, '5520', 'ahorro', 'VES', 912045),
        ],
      5 => [_CuentaF(_id(), 'USDT', '9F2A', 'billetera', 'USDT', 8540, soloLectura: true)],
      6 => [_CuentaF(_id(), 'Tepuy', 'A71C', 'billetera', 'USDT', 3125, soloLectura: true)],
      _ => [
          _CuentaF(_id(), null, (1000 + _rnd.nextInt(8999)).toString(), 'corriente', 'VES',
              200000 + _rnd.nextInt(3000000)),
        ],
    };
    for (final c in cuentas) {
      _movimientos[c.idCuenta] = _generarMovimientos(c);
    }
    return cuentas;
  }

  List<Movimiento> _generarMovimientos(_CuentaF c) {
    const descripciones = [
      ('credito', 'ingreso', 'Pago de nómina'),
      ('debito', 'gasto', 'Supermercado'),
      ('debito', 'gasto', 'Pago móvil a Juan'),
      ('debito', 'gasto', 'Farmacia'),
      ('credito', 'ingreso', 'Transferencia recibida'),
      ('debito', 'gasto', 'Transporte'),
    ];
    return [
      for (var i = 0; i < 8; i++)
        Movimiento(
          idMovimiento: _id(),
          fechaHora: _ahora.subtract(Duration(days: i * 2, hours: i)),
          naturaleza: descripciones[i % descripciones.length].$1,
          clasificacion: descripciones[i % descripciones.length].$2,
          montoCts: c.moneda == 'VES' ? 150000 + _rnd.nextInt(2500000) : 500 + _rnd.nextInt(5000),
          moneda: c.moneda,
          descripcion: descripciones[i % descripciones.length].$3,
        ),
    ];
  }

  void _requiereSesion() {
    if (_actual == null) throw _err(401, 'NO_AUTENTICADO', 'Tu sesión expiró. Vuelve a ingresar.');
  }

  void _requiereKyc() {
    _requiereSesion();
    if (!_actual!.kycAprobado) {
      throw _err(403, 'KYC_PENDIENTE', 'Completa la verificación de identidad para continuar.');
    }
  }

  void _requiereStepUp(String token, NivelStepUp nivel) {
    final n = _stepUps[token];
    if (n == null || (nivel == NivelStepUp.pinOtp && n != NivelStepUp.pinOtp)) {
      throw _err(401, 'STEP_UP_REQUERIDO',
          nivel == NivelStepUp.pinOtp ? 'Confirma con tu PIN y el código OTP.' : 'Confirma con tu PIN.');
    }
  }

  // ---------------------------------------------------------------------------
  // Identidad
  // ---------------------------------------------------------------------------
  Sesion _sesionPara(_UsuarioF u) => Sesion(
        accessToken: 'fake-${u.idUsuario}-${_id()}',
        expiraEn: _ahora.add(const Duration(minutes: 15)),
        usuario: Usuario(
          idUsuario: u.idUsuario,
          primerNombre: u.primerNombre,
          primerApellido: u.primerApellido,
          kycAprobado: u.kycAprobado,
          estatus: 'activo',
        ),
        idDispositivo: 3,
      );

  @override
  Future<Sesion> registrar(SolicitudRegistro s) async {
    await _esperar();
    if (_usuarios.containsKey(s.cedula)) {
      throw _err(409, 'CONFLICTO', 'Ya existe un usuario con esa cédula.');
    }
    if (!RegExp(r'^\d{6}$').hasMatch(s.pin)) {
      throw _err(400, 'VALIDACION', 'El PIN debe tener exactamente 6 dígitos.');
    }
    final edad = _hoy.difference(s.fechaNacimiento).inDays ~/ 365;
    if (edad < 18) throw _err(400, 'VALIDACION', 'Debes ser mayor de edad.');
    final u = _UsuarioF(
      idUsuario: _id(),
      nacionalidad: s.nacionalidadDoc,
      cedula: s.cedula,
      pin: s.pin,
      primerNombre: s.primerNombre,
      primerApellido: s.primerApellido,
      correo: s.correo,
      telefono: s.telefonoMovil,
      kycAprobado: false,
    );
    _usuarios[u.cedula] = u;
    _actual = u;
    return _sesionPara(u);
  }

  @override
  Future<Sesion> login({
    required String nacionalidadDoc,
    required int cedula,
    required String pin,
    required DispositivoInfo dispositivo,
  }) async {
    await _esperar();
    final u = _usuarios[cedula];
    if (u == null || u.pin != pin) {
      _fallosPin++;
      if (_fallosPin >= 3) {
        _fallosPin = 0;
        _tokenSaldo = null;
        _saldoAnulado = true;
        throw _err(401, 'NO_AUTENTICADO',
            'Bloqueamos este dispositivo por 3 intentos fallidos. Contacta a soporte.');
      }
      throw _err(401, 'NO_AUTENTICADO', 'Cédula o PIN incorrectos. Te quedan ${3 - _fallosPin} intentos.');
    }
    _fallosPin = 0;
    _actual = u;
    return _sesionPara(u);
  }

  @override
  Future<Perfil> perfil() async {
    await _esperar();
    _requiereSesion();
    final u = _actual!;
    return Perfil(
      idUsuario: u.idUsuario,
      nacionalidadDoc: u.nacionalidad,
      cedula: u.cedula,
      primerNombre: u.primerNombre,
      primerApellido: u.primerApellido,
      correo: u.correo,
      telefonoMovil: u.telefono,
      kycAprobado: u.kycAprobado,
      estatus: 'activo',
      fechaRegistro: _hoy.subtract(const Duration(days: 60)),
    );
  }

  @override
  Future<DesafioOtp> solicitarOtp(String proposito) async {
    await _esperar();
    _requiereSesion();
    final id = 'desafio-${_id()}';
    final otp = (100000 + _rnd.nextInt(899999)).toString();
    _desafios[id] = otp;
    return DesafioOtp(idDesafio: id, expiraEn: _ahora.add(const Duration(minutes: 5)), otpDemo: otp);
  }

  @override
  Future<StepUp> stepUp({required String pin, String? idDesafio, String? otp}) async {
    await _esperar();
    _requiereSesion();
    if (pin != _actual!.pin) throw _err(401, 'NO_AUTENTICADO', 'PIN incorrecto.');
    var nivel = NivelStepUp.pin;
    if (idDesafio != null) {
      if (_desafios[idDesafio] != otp) {
        throw _err(400, 'VALIDACION', 'El código OTP no es válido o expiró.');
      }
      _desafios.remove(idDesafio);
      nivel = NivelStepUp.pinOtp;
    }
    final token = 'stepup-${_id()}';
    _stepUps[token] = nivel;
    return StepUp(token: token, nivel: nivel, expiraEn: _ahora.add(const Duration(minutes: 5)));
  }

  @override
  Future<ResultadoKyc> enviarKyc(List<DocumentoKyc> documentos) async {
    await _esperar();
    _requiereSesion();
    final completo = documentos.any((d) => d.tipo == 'cedula' && d.contenidoBase64.isNotEmpty) &&
        documentos.any((d) => d.tipo == 'selfie' && d.contenidoBase64.isNotEmpty);
    if (completo) _actual!.kycAprobado = true;
    return ResultadoKyc(
      idVerificacion: _id(),
      resultado: completo ? 'aprobado' : 'rechazado',
      pruebaVidaSuperada: completo,
      coincideListaSanciones: false,
      motivoRechazo: completo ? null : 'Faltan la foto de la cédula o la selfie.',
    );
  }

  @override
  Future<TokenSaldo> activarSaldoRapido(String stepUpToken) async {
    await _esperar();
    _requiereStepUp(stepUpToken, NivelStepUp.pin);
    _tokenSaldo = 'saldo-demo-${_id()}';
    _huellaSaldo = null;
    _saldoAnulado = false;
    return TokenSaldo(tokenSaldo: _tokenSaldo!, expiraEn: _ahora.add(const Duration(days: 90)));
  }

  @override
  Future<SaldoRapido> consultarSaldoRapido({required String tokenSaldo, required String huella}) async {
    await _esperar();
    final recordado = _tokenSaldo == null && !_saldoAnulado && tokenSaldo.startsWith('saldo-demo-');
    if (recordado) _tokenSaldo = tokenSaldo;
    if (_tokenSaldo == null || tokenSaldo != _tokenSaldo) {
      throw _err(401, 'NO_AUTENTICADO', 'El saldo rápido fue desactivado.');
    }
    _huellaSaldo ??= huella;
    if (_huellaSaldo != huella) throw _err(403, 'PROHIBIDO', 'Dispositivo no autorizado.');
    final c = _construirConsolidado();
    return SaldoRapido(
      totalBsCts: c.totalBsCts,
      totalUsdCts: c.totalUsdCts,
      actualizadoEn: c.actualizadoEn,
      bancos: [
        for (final i in c.instituciones)
          SaldoBanco(nombre: i.nombre, tipo: i.tipo, totalBsCts: i.totalBsCts, totalUsdCts: i.totalUsdCts),
      ],
    );
  }

  @override
  Future<void> desactivarSaldoRapido() async {
    await _esperar();
    _tokenSaldo = null;
    _saldoAnulado = true;
  }

  @override
  Future<void> revocarDispositivoActual(String motivo) async {
    await _esperar();
    _tokenSaldo = null;
    _saldoAnulado = true;
    _actual = null;
  }

  // ---------------------------------------------------------------------------
  // Consolidación
  // ---------------------------------------------------------------------------
  @override
  Future<List<Institucion>> instituciones() async {
    await _esperar();
    return _instituciones;
  }

  @override
  Future<List<ConsentimientoCreado>> crearConsentimientos(
    List<int> idsInstituciones, {
    required bool permiteDebitoTdc,
  }) async {
    await _esperar();
    _requiereKyc();
    if (idsInstituciones.isEmpty) throw _err(400, 'VALIDACION', 'Elige al menos una institución.');
    final creados = <ConsentimientoCreado>[];
    for (final id in idsInstituciones) {
      final inst = _instituciones.firstWhere((i) => i.idInstitucion == id,
          orElse: () => throw _err(404, 'NO_ENCONTRADO', 'Institución no encontrada.'));
      final c = _ConsentimientoF(
        id: _id(),
        idInstitucion: id,
        institucion: inst.nombre,
        debito: permiteDebitoTdc && !inst.esCripto,
      );
      _consentimientos.add(c);
      creados.add(ConsentimientoCreado(
        idConsentimiento: c.id,
        idInstitucion: id,
        institucion: inst.nombre,
        estatus: 'pendiente',
        urlAutorizacion: 'https://banco-simulado.ahorramas.test/ob/${inst.codigo}/oauth/authorize?state=${c.id}',
      ));
    }
    return creados;
  }

  /// Simula que el usuario autorizó en la página del banco (la "abre" el [AbridorEnlaces] falso).
  Future<void> simularAutorizacion(Uri url) async {
    final id = int.tryParse(url.queryParameters['state'] ?? '');
    final c = _consentimientos.where((c) => c.id == id).firstOrNull;
    if (c == null) return;
    await Future<void>.delayed(latencia * 4);
    // Desactiva duplicados previos de la misma institución.
    for (final otro in _consentimientos) {
      if (otro != c && otro.idInstitucion == c.idInstitucion && otro.estatus == 'activo') {
        otro.estatus = 'revocado';
      }
    }
    c.estatus = 'activo';
    c.otorgado = _ahora;
    c.vence = _ahora.add(const Duration(days: 180));
    final inst = _instituciones.firstWhere((i) => i.idInstitucion == c.idInstitucion);
    _cuentasPorInstitucion.putIfAbsent(c.idInstitucion, () => _generarCuentas(inst));
    for (final cu in _cuentasPorInstitucion[c.idInstitucion]!) {
      cu.activa = true;
    }
  }

  @override
  Future<List<Consentimiento>> consentimientos() async {
    await _esperar();
    _requiereSesion();
    return [
      for (final c in _consentimientos)
        Consentimiento(
          idConsentimiento: c.id,
          idInstitucion: c.idInstitucion,
          institucion: c.institucion,
          estatus: c.estatus,
          permiteLectura: true,
          permiteDebitoTdc: c.debito,
          fechaOtorgamiento: c.otorgado,
          fechaVencimiento: c.vence,
        ),
    ];
  }

  @override
  Future<void> revocarConsentimiento(int idConsentimiento) async {
    await _esperar();
    final c = _consentimientos.firstWhere((c) => c.id == idConsentimiento,
        orElse: () => throw _err(404, 'NO_ENCONTRADO', 'Consentimiento no encontrado.'));
    c.estatus = 'revocado';
    for (final cu in _cuentasPorInstitucion[c.idInstitucion] ?? const <_CuentaF>[]) {
      cu.activa = false;
    }
  }

  @override
  Future<void> sincronizar() async {
    await _esperar();
    _requiereSesion();
    _ultimaSincronizacion = _ahora;
  }

  Consolidado _construirConsolidado() {
    final tasa = _tasaEn(_hoy);
    final instituciones = <InstitucionConsolidada>[];
    var totalBs = 0;
    var totalUsd = 0;
    for (final c in _consentimientos.where((c) => c.estatus == 'activo')) {
      final inst = _instituciones.firstWhere((i) => i.idInstitucion == c.idInstitucion);
      final cuentas = (_cuentasPorInstitucion[c.idInstitucion] ?? const <_CuentaF>[])
          .where((cu) => cu.activa)
          .map((cu) {
        final esBs = cu.moneda == 'VES';
        return Cuenta(
          idCuenta: cu.idCuenta,
          alias: cu.alias,
          ultimos4: cu.ultimos4,
          tipo: cu.tipo,
          moneda: cu.moneda,
          saldoCts: cu.saldoCts,
          saldoBsCts: esBs ? cu.saldoCts : Dinero.usdABs(cu.saldoCts, tasa),
          saldoUsdCts: esBs ? Dinero.bsAUsd(cu.saldoCts, tasa) : cu.saldoCts,
          soloLectura: cu.soloLectura,
        );
      }).toList();
      if (cuentas.isEmpty) continue;
      final bs = cuentas.fold<int>(0, (a, c) => a + c.saldoBsCts);
      final usd = cuentas.fold<int>(0, (a, c) => a + c.saldoUsdCts);
      totalBs += bs;
      totalUsd += usd;
      instituciones.add(InstitucionConsolidada(
        idInstitucion: inst.idInstitucion,
        nombre: inst.nombre,
        tipo: inst.tipo,
        totalBsCts: bs,
        totalUsdCts: usd,
        cuentas: cuentas,
      ));
    }
    return Consolidado(
      tasa: Tasa(fecha: _hoy, valorBsX10000: tasa),
      totalBsCts: totalBs,
      totalUsdCts: totalUsd,
      actualizadoEn: _ultimaSincronizacion,
      instituciones: instituciones,
    );
  }

  @override
  Future<Consolidado> consolidado() async {
    await _esperar();
    _requiereSesion();
    return _construirConsolidado();
  }

  _CuentaF? _buscarCuenta(int idCuenta) {
    for (final l in _cuentasPorInstitucion.values) {
      for (final c in l) {
        if (c.idCuenta == idCuenta) return c;
      }
    }
    return null;
  }

  @override
  Future<void> renombrarCuenta(int idCuenta, String alias) async {
    await _esperar();
    final c = _buscarCuenta(idCuenta);
    if (c == null) throw _err(404, 'NO_ENCONTRADO', 'Cuenta no encontrada.');
    c.alias = alias;
  }

  @override
  Future<List<Movimiento>> movimientosCuenta(int idCuenta) async {
    await _esperar();
    return _movimientos[idCuenta] ?? const [];
  }

  @override
  Future<Tasa> tasaHoy() async {
    await _esperar();
    return Tasa(fecha: _hoy, valorBsX10000: _tasaEn(_hoy));
  }

  // ---------------------------------------------------------------------------
  // Cofre Protector
  // ---------------------------------------------------------------------------
  _MetaF _buscarMeta(int id) => _metas.firstWhere((m) => m.idMeta == id,
      orElse: () => throw _err(404, 'NO_ENCONTRADO', 'Meta no encontrada.'));

  int _acumulado(_MetaF m) =>
      m.aportes.fold(0, (a, x) => a + (x.tipo == 'retiro' ? -x.usdCts : x.usdCts));

  int _aportadoHoy(_MetaF m) => m.aportes
      .where((a) => a.tipo == 'aporte' && Fechas.soloFecha(a.fecha) == _hoy)
      .fold(0, (a, x) => a + x.usdCts);

  RutaDelDia _ruta(_MetaF m) {
    final tasa = _tasaEn(_hoy);
    final dias = max(1, m.fechaObjetivo.difference(_hoy).inDays);
    final periodos = m.frecuencia == FrecuenciaCuota.diaria ? dias : max(1, (dias + 6) ~/ 7);
    final hoy = _aportadoHoy(m);
    final faltante = max(0, m.objetivo - (_acumulado(m) - hoy));
    final sugerido = (faltante + periodos - 1) ~/ periodos; // hacia arriba al céntimo (R-09)
    final bs = Dinero.usdABs(sugerido, tasa);
    final estatus = sugerido == 0 || hoy >= sugerido
        ? 'cumplida'
        : (hoy > 0 ? 'parcial' : 'pendiente');
    final cuando = m.frecuencia == FrecuenciaCuota.diaria ? 'Hoy' : 'Esta semana';
    final mensaje = estatus == 'cumplida'
        ? '¡Listo! Ya apartaste tu cuota de hoy para «${m.nombre}».'
        : '$cuando toca apartar ${Dinero.usd(sugerido)} (${Dinero.bs(bs)}) para «${m.nombre}»';
    return RutaDelDia(
      idMeta: m.idMeta,
      nombreMeta: m.nombre,
      fecha: _hoy,
      montoSugeridoUsdCts: sugerido,
      montoSugeridoBsCts: bs,
      tasaValorBsX10000: tasa,
      aportadoHoyUsdCts: hoy,
      estatus: estatus,
      periodosRestantes: periodos,
      mensaje: mensaje,
    );
  }

  Meta _meta(_MetaF m) {
    final acumulado = _acumulado(m);
    return Meta(
      idMeta: m.idMeta,
      nombre: m.nombre,
      categoria: m.categoria,
      montoObjetivoUsdCts: m.objetivo,
      fechaObjetivo: m.fechaObjetivo,
      frecuenciaCuota: m.frecuencia,
      estatus: m.estatus,
      acumuladoUsdCts: acumulado,
      progresoPb: m.objetivo == 0 ? 0 : min(10000, acumulado * 10000 ~/ m.objetivo),
      fechaCreacion: m.creada,
      sello: m.sello == null
          ? null
          : Sello(
              idSello: m.sello!.idSello,
              montoSelladoUsdCts: m.sello!.monto,
              fechaLiberacion: m.sello!.liberacion,
              estatus: m.sello!.estatus,
            ),
      cuotaHoy: m.estatus == 'activa' ? _ruta(m) : null,
    );
  }

  @override
  Future<List<Meta>> metas() async {
    await _esperar();
    _requiereKyc();
    return _metas.where((m) => m.estatus != 'cancelada').map(_meta).toList();
  }

  @override
  Future<Meta> meta(int idMeta) async {
    await _esperar();
    return _meta(_buscarMeta(idMeta));
  }

  @override
  Future<Meta> crearMeta(SolicitudMeta s) async {
    await _esperar();
    _requiereKyc();
    if (s.montoObjetivoUsdCts <= 0) throw _err(400, 'VALIDACION', 'El monto debe ser mayor a cero.');
    if (!s.fechaObjetivo.isAfter(_hoy)) {
      throw _err(400, 'VALIDACION', 'La fecha de la meta debe ser futura.');
    }
    final m = _MetaF(
      idMeta: _id(),
      nombre: s.nombre,
      categoria: s.categoria,
      objetivo: s.montoObjetivoUsdCts,
      fechaObjetivo: s.fechaObjetivo,
      frecuencia: s.frecuenciaCuota,
      creada: _ahora,
    );
    _metas.add(m);
    return _meta(m);
  }

  @override
  Future<void> cambiarEstatusMeta(int idMeta, String estatus) async {
    await _esperar();
    _buscarMeta(idMeta).estatus = estatus;
  }

  @override
  Future<RutaDelDia> rutaDelDia(int idMeta) async {
    await _esperar();
    return _ruta(_buscarMeta(idMeta));
  }

  @override
  Future<List<RutaDelDia>> rutasDelDia() async {
    await _esperar();
    _requiereSesion();
    return _metas.where((m) => m.estatus == 'activa').map(_ruta).toList();
  }

  static int _multiplicadorPara(int dias) {
    if (dias >= 60) return 200;
    if (dias >= 30) return 150;
    if (dias >= 14) return 125;
    if (dias >= 7) return 110;
    return 100;
  }

  static TramoRacha? _siguienteTramo(int dias) {
    for (final t in const [(7, 110), (14, 125), (30, 150), (60, 200)]) {
      if (dias < t.$1) return TramoRacha(diasMinimos: t.$1, multiplicadorX100: t.$2);
    }
    return null;
  }

  Racha _rachaActual() {
    final r = _racha;
    final diasConsecutivos = r.dias.length;
    final hoy = r.dias.where((d) => d.fecha == _hoy).firstOrNull;
    return Racha(
      idRacha: r.cerrada ? null : 3,
      fechaInicio: r.dias.isEmpty ? null : r.inicio,
      diasConsecutivos: diasConsecutivos,
      diasAporte: r.dias.where((d) => d.tipo == TipoDiaRacha.aporte).length,
      diasColchonUsados: r.dias.where((d) => d.tipo == TipoDiaRacha.colchon).length,
      colchonesDisponibles: r.colchonesDisponibles,
      multiplicadorX100: _multiplicadorPara(diasConsecutivos),
      siguienteTramo: _siguienteTramo(diasConsecutivos),
      aporteHoy: hoy?.tipo == TipoDiaRacha.aporte,
      ultimosDias: [
        ...r.historialPrevio,
        ...r.dias,
      ].reversed.take(14).toList(),
    );
  }

  @override
  Future<ResultadoAporte> aportar(
    int idMeta, {
    required int montoUsdCts,
    required String origen,
    int? idCuentaOrigen,
  }) async {
    await _esperar();
    _requiereKyc();
    final m = _buscarMeta(idMeta);
    if (m.estatus != 'activa') throw _err(422, 'REGLA_NEGOCIO', 'La meta no está activa.');
    if (montoUsdCts <= 0) throw _err(400, 'VALIDACION', 'El monto debe ser mayor a cero.');
    final a = _aporte(_ahora, origen, montoUsdCts);
    m.aportes.add(a);
    if (!_racha.dias.any((d) => d.fecha == _hoy)) {
      if (_racha.dias.isEmpty) _racha.inicio = _hoy;
      _racha.dias.add(DiaRacha(fecha: _hoy, tipo: TipoDiaRacha.aporte));
      _racha.cerrada = false;
      // Cada 7 días de aporte se gana un día colchón (máx. 3).
      if (_racha.dias.length % 7 == 0) {
        _racha.colchonesDisponibles = min(3, _racha.colchonesDisponibles + 1);
      }
    }
    final racha = _rachaActual();
    final puntos = montoUsdCts * racha.multiplicadorX100 ~/ 1000;
    _sumarPuntos(puntos, 'meta', 'Aporte a «${m.nombre}»');
    return ResultadoAporte(
      aporte: _aporteDominio(a),
      meta: _meta(m),
      racha: racha,
      puntosGanados: puntos,
    );
  }

  Aporte _aporteDominio(_AporteF a) => Aporte(
        idAporte: a.idAporte,
        fechaHora: a.fecha,
        tipo: a.tipo,
        origen: a.origen,
        montoUsdCts: a.usdCts,
        montoBsCts: a.bsCts,
      );

  void _cerrarRacha() {
    _racha.historialPrevio
      ..addAll(_racha.dias)
      ..removeRange(0, max(0, _racha.historialPrevio.length - 14));
    _racha.dias.clear();
    _racha.cerrada = true;
  }

  @override
  Future<ResultadoAporte> retirar(int idMeta, {required int montoUsdCts, required String stepUp}) async {
    await _esperar();
    _requiereStepUp(stepUp, NivelStepUp.pin);
    final m = _buscarMeta(idMeta);
    final acumulado = _acumulado(m);
    if (montoUsdCts <= 0 || montoUsdCts > acumulado) {
      throw _err(422, 'REGLA_NEGOCIO', 'No puedes retirar más de lo acumulado en la meta.');
    }
    final a = _aporte(_ahora, 'manual', montoUsdCts, tipo: 'retiro');
    m.aportes.add(a);
    var selloRoto = false;
    final s = m.sello;
    if (s != null && s.estatus == 'activo' && acumulado - montoUsdCts < s.monto && _hoy.isBefore(s.liberacion)) {
      s.estatus = 'roto';
      selloRoto = true;
      _cerrarRacha();
    }
    return ResultadoAporte(aporte: _aporteDominio(a), meta: _meta(m), selloRoto: selloRoto, racha: _rachaActual());
  }

  @override
  Future<List<Aporte>> aportes(int idMeta) async {
    await _esperar();
    return _buscarMeta(idMeta).aportes.reversed.map(_aporteDominio).toList();
  }

  @override
  Future<Regla> crearRegla(int idMeta, SolicitudRegla r, {required String stepUp}) async {
    await _esperar();
    _requiereStepUp(stepUp, NivelStepUp.pin);
    _buscarMeta(idMeta);
    final regla = Regla(
      idRegla: _id(),
      idMeta: idMeta,
      tipo: r.tipo,
      porcentajePb: r.porcentajePb,
      multiploRedondeoCts: r.multiploRedondeoCts,
      montoFijoUsdCts: r.montoFijoUsdCts,
      estatus: 'activa',
      fechaAprobacion: _ahora,
    );
    _reglas.add(regla);
    return regla;
  }

  @override
  Future<List<Regla>> reglas(int idMeta) async {
    await _esperar();
    return _reglas.where((r) => r.idMeta == idMeta).toList();
  }

  @override
  Future<void> cambiarEstatusRegla(int idRegla, String estatus) async {
    await _esperar();
    final i = _reglas.indexWhere((r) => r.idRegla == idRegla);
    if (i < 0) throw _err(404, 'NO_ENCONTRADO', 'Regla no encontrada.');
    final r = _reglas[i];
    _reglas[i] = Regla(
      idRegla: r.idRegla,
      idMeta: r.idMeta,
      tipo: r.tipo,
      porcentajePb: r.porcentajePb,
      multiploRedondeoCts: r.multiploRedondeoCts,
      montoFijoUsdCts: r.montoFijoUsdCts,
      estatus: estatus,
      fechaAprobacion: r.fechaAprobacion,
    );
  }

  @override
  Future<Racha> racha() async {
    await _esperar();
    _requiereSesion();
    return _rachaActual();
  }

  @override
  Future<Sello> sellar(int idMeta, {required int montoUsdCts, required String stepUp}) async {
    await _esperar();
    _requiereStepUp(stepUp, NivelStepUp.pin);
    final m = _buscarMeta(idMeta);
    if (montoUsdCts <= 0 || montoUsdCts > _acumulado(m)) {
      throw _err(422, 'REGLA_NEGOCIO', 'Solo puedes sellar hasta lo que ya tienes acumulado.');
    }
    m.sello = _SelloF(idSello: _id(), monto: montoUsdCts, liberacion: m.fechaObjetivo);
    return _meta(m).sello!;
  }

  @override
  Future<Sello> romperSello(int idMeta, {required String stepUp}) async {
    await _esperar();
    _requiereStepUp(stepUp, NivelStepUp.pin);
    final m = _buscarMeta(idMeta);
    final s = m.sello;
    if (s == null || s.estatus != 'activo') {
      throw _err(409, 'CONFLICTO', 'La meta no tiene un sello activo.');
    }
    s.estatus = 'roto';
    _cerrarRacha();
    return _meta(m).sello!;
  }

  @override
  Future<EspejoImpacto> espejoImpacto({int? anio, int? mes}) async {
    await _esperar();
    _requiereSesion();
    final a = anio ?? _hoy.year;
    final me = mes ?? _hoy.month;
    final inicioMes = DateTime(a, me, 1);
    final finMes = DateTime(a, me + 1, 0);
    final esActual = a == _hoy.year && me == _hoy.month;
    final fechaFin = esActual ? _hoy : finMes;
    final tasaInicio = _tasaEn(inicioMes);
    final tasaFin = _tasaEn(fechaFin);
    var saldo = 0;
    var perdidaBs = 0;
    for (final m in _metas.where((m) => m.estatus != 'cancelada')) {
      for (final ap in m.aportes) {
        if (ap.fecha.isAfter(fechaFin.add(const Duration(days: 1)))) continue;
        final signo = ap.tipo == 'retiro' ? -1 : 1;
        saldo += signo * ap.usdCts;
        final ref = ap.fecha.isAfter(inicioMes) ? _tasaEn(ap.fecha) : tasaInicio;
        perdidaBs += signo * Dinero.dividirRedondeando(ap.usdCts * (tasaFin - ref), 10000);
      }
    }
    perdidaBs = max(0, perdidaBs);
    final devaluacionPb = Dinero.dividirRedondeando((tasaFin - tasaInicio) * 10000, tasaInicio);
    final c = _construirConsolidado();
    final cuando = esActual ? 'este mes' : 'en ${Fechas.mesAnio(a, me)}';
    return EspejoImpacto(
      anio: a,
      mes: me,
      provisional: esActual,
      saldoCofresUsdCts: saldo,
      perdidaEvitadaBsCts: perdidaBs,
      perdidaEvitadaUsdCts: Dinero.bsAUsd(perdidaBs, tasaFin),
      tasaInicio: TasaFechada(fecha: inicioMes, valorBsX10000: tasaInicio),
      tasaFin: TasaFechada(fecha: fechaFin, valorBsX10000: tasaFin),
      devaluacionPb: devaluacionPb,
      inflacionMensualPb: esActual ? null : 1240,
      totalBsCts: c.totalBsCts,
      totalUsdCts: c.totalUsdCts,
      frase: 'Tu cofre de ${Dinero.usd(saldo)} te protegió de perder ${Dinero.bs(perdidaBs)} $cuando.',
      disclaimer:
          'Simulación informativa basada en la tasa oficial BCV; no constituye asesoría financiera.',
    );
  }

  // ---------------------------------------------------------------------------
  // Pagos TDC
  // ---------------------------------------------------------------------------
  @override
  Future<List<Tarjeta>> tarjetas() async {
    await _esperar();
    _requiereKyc();
    // El emisor conoce a su cliente: las tarjetas propias llevan el nombre de quien inició sesión.
    final titular = _titularActual();
    return [
      for (final t in _tarjetas)
        Tarjeta(
          idTarjeta: t.idTarjeta,
          idInstitucion: t.idInstitucion,
          banco: t.banco,
          ultimos4: t.ultimos4,
          alias: t.alias,
          titularConfirmado: t.esPropia ? titular : t.titularConfirmado,
          esPropia: t.esPropia,
          estadoCuenta: t.estadoCuenta,
          marca: t.marca,
        ),
    ];
  }

  /// "MARIA PEREZ" a partir del usuario en sesión (sin acentos, como lo imprime un banco).
  String _titularActual() {
    final u = _actual;
    if (u == null) return 'TITULAR';
    const conAcento = 'ÁÉÍÓÚÜáéíóúü';
    const sinAcento = 'AEIOUUaeiouu';
    final nombre = '${u.primerNombre} ${u.primerApellido}'.trim().split('').map((c) {
      final i = conAcento.indexOf(c);
      return i < 0 ? c : sinAcento[i];
    }).join();
    return nombre.toUpperCase();
  }

  @override
  Future<Beneficiario> beneficiario(int idTarjeta) async {
    await _esperar();
    final t = _tarjetas.firstWhere((t) => t.idTarjeta == idTarjeta,
        orElse: () => throw _err(404, 'NO_ENCONTRADO', 'Tarjeta no encontrada.'));
    return Beneficiario(titular: t.esPropia ? _titularActual() : (t.titularConfirmado ?? 'TITULAR'), ultimos4: t.ultimos4);
  }

  @override
  Future<List<CuentaOrigen>> cuentasOrigen() async {
    await _esperar();
    final out = <CuentaOrigen>[];
    for (final c in _consentimientos.where((c) => c.estatus == 'activo' && c.debito)) {
      for (final cu in _cuentasPorInstitucion[c.idInstitucion] ?? const <_CuentaF>[]) {
        if (cu.moneda == 'VES' && cu.activa) {
          out.add(CuentaOrigen(
              idCuenta: cu.idCuenta, banco: c.institucion, ultimos4: cu.ultimos4, alias: cu.alias, saldoCts: cu.saldoCts));
        }
      }
    }
    return out;
  }

  static int _comision(int monto) => Dinero.dividirRedondeando(monto * 20, 10000); // 0,2 %

  @override
  Future<Cotizacion> cotizacion(int montoCts) async {
    await _esperar();
    if (montoCts <= 0) throw _err(400, 'VALIDACION', 'El monto debe ser mayor a cero.');
    final c = _comision(montoCts);
    return Cotizacion(montoCts: montoCts, comisionCts: c, totalCts: montoCts + c);
  }

  Limites _limites() {
    final hoy = _pagos.where((p) =>
        Fechas.soloFecha(p.creado) == _hoy && p.estado != EstadoPago.rechazado);
    return Limites(
      montoMaxDiarioCts: 50000000,
      maxOperacionesDiarias: 5,
      usadoHoyCts: hoy.fold(0, (a, p) => a + p.montoCts),
      operacionesHoy: hoy.length,
    );
  }

  @override
  Future<Pago> pagar(SolicitudPagoTdc s, {required String idempotencyKey, required String stepUp}) async {
    await _esperar();
    final existente = _pagosPorClave[idempotencyKey];
    if (existente != null) return existente.dominio(conHistorial: true);
    _requiereKyc();
    _requiereStepUp(stepUp, NivelStepUp.pinOtp);
    if (!s.beneficiarioConfirmado) {
      throw _err(422, 'REGLA_NEGOCIO', 'Debes confirmar el beneficiario.');
    }
    final l = _limites();
    if (l.operacionesHoy >= l.maxOperacionesDiarias || l.usadoHoyCts + s.montoCts > l.montoMaxDiarioCts) {
      throw _err(422, 'LIMITE_EXCEDIDO', 'Superas tu límite diario de pagos.');
    }
    final cuenta = _buscarCuenta(s.idCuentaOrigen);
    if (cuenta == null) throw _err(404, 'NO_ENCONTRADO', 'Cuenta de origen no encontrada.');
    final tarjeta = _tarjetas.firstWhere((t) => t.idTarjeta == s.idTarjeta);
    final comision = _comision(s.montoCts);
    final p = _PagoF(
      idPago: _id(),
      idTarjeta: s.idTarjeta,
      estado: EstadoPago.pendiente,
      montoCts: s.montoCts,
      comisionCts: comision,
      creado: _ahora,
    );
    p.historial.add(_cambio(1, null, EstadoPago.pendiente, _ahora, 'usuario'));
    if (s.montoCts + comision > cuenta.saldoCts) {
      p.estado = EstadoPago.rechazado;
      p.motivo = 'Fondos insuficientes en la cuenta de origen.';
      p.mensaje = 'El banco rechazó el pago. No se debitó dinero de tu cuenta.';
      p.historial.add(_cambio(2, EstadoPago.pendiente, EstadoPago.rechazado, _ahora, 'suiche'));
    } else if (s.montoCts % 100 == 77) {
      p.estado = EstadoPago.enVerificacion;
      p.referencia = 'S7B-${_id()}';
      p.mensaje = 'Tu pago está en proceso. Te avisaremos cuando el banco lo confirme.';
      p.historial.add(_cambio(2, EstadoPago.pendiente, EstadoPago.enVerificacion, _ahora, 'suiche'));
      cuenta.saldoCts -= s.montoCts + comision;
    } else {
      p.estado = EstadoPago.capturado;
      p.referencia = 'S7B-${_id()}';
      p.autorizacion = 'AUT${100000 + _rnd.nextInt(899999)}';
      p.mensaje = 'Pago aplicado a tu TDC terminada en ${tarjeta.ultimos4}';
      p.historial
        ..add(_cambio(2, EstadoPago.pendiente, EstadoPago.autorizado, _ahora, 'suiche'))
        ..add(_cambio(3, EstadoPago.autorizado, EstadoPago.capturado, _ahora, 'suiche'));
      cuenta.saldoCts -= s.montoCts + comision;
      final limite = tarjeta.estadoCuenta?.fechaLimitePago;
      p.esPuntual = limite == null || !_hoy.isAfter(limite);
      if (p.esPuntual!) _sumarPuntos(50, 'pago_puntual', 'Pago puntual TDC •••• ${tarjeta.ultimos4}');
    }
    _pagos.add(p);
    _pagosPorClave[idempotencyKey] = p;
    return p.dominio(conHistorial: true);
  }

  @override
  Future<List<Pago>> pagos() async {
    await _esperar();
    _requiereSesion();
    return _pagos.reversed.map((p) => p.dominio(conHistorial: false)).toList();
  }

  @override
  Future<Pago> pago(int idPago) async {
    await _esperar();
    final p = _pagos.firstWhere((p) => p.idPago == idPago,
        orElse: () => throw _err(404, 'NO_ENCONTRADO', 'Pago no encontrado.'));
    if (p.estado == EstadoPago.enVerificacion && ++p.consultas >= 2) {
      p.estado = EstadoPago.capturado;
      p.autorizacion = 'AUT${100000 + _rnd.nextInt(899999)}';
      p.mensaje = 'El banco confirmó tu pago.';
      p.historial.add(_cambio(p.historial.length + 1, EstadoPago.enVerificacion, EstadoPago.capturado, _ahora, 'conciliacion'));
    }
    return p.dominio(conHistorial: true);
  }

  @override
  Future<Limites> limites() async {
    await _esperar();
    return _limites();
  }

  // ---------------------------------------------------------------------------
  // Puntos
  // ---------------------------------------------------------------------------
  void _sumarPuntos(int puntos, String concepto, String descripcion) {
    if (puntos == 0) return;
    _puntosSaldo += puntos;
    if (puntos > 0) _puntosTotal += puntos;
    _movPuntos.add(MovimientoPuntos(
        idTransaccion: _id(), fechaHora: _ahora, concepto: concepto, descripcion: descripcion, puntos: puntos));
  }

  String _nivel() => _puntosTotal >= 15000 ? 'Platinum' : (_puntosTotal >= 5000 ? 'Gold' : 'Base');

  @override
  Future<ResumenPuntos> resumenPuntos() async {
    await _esperar();
    _requiereSesion();
    final nivel = _nivel();
    final sig = switch (nivel) {
      'Base' => SiguienteNivel(nombre: 'Gold', puntosMinimos: 5000, puntosFaltantes: 5000 - _puntosTotal),
      'Gold' => SiguienteNivel(nombre: 'Platinum', puntosMinimos: 15000, puntosFaltantes: 15000 - _puntosTotal),
      _ => null,
    };
    return ResumenPuntos(
      saldo: _puntosSaldo,
      puntosGanadosTotal: _puntosTotal,
      nivel: nivel,
      nivelDesde: _hoy.subtract(const Duration(days: 60)),
      siguienteNivel: sig,
    );
  }

  @override
  Future<List<MovimientoPuntos>> movimientosPuntos() async {
    await _esperar();
    return _movPuntos.reversed.toList();
  }

  @override
  Future<List<Recompensa>> recompensas() async {
    await _esperar();
    return _recompensas;
  }

  @override
  Future<Canje> canjear(int idRecompensa) async {
    await _esperar();
    final r = _recompensas.firstWhere((r) => r.idRecompensa == idRecompensa,
        orElse: () => throw _err(404, 'NO_ENCONTRADO', 'Recompensa no encontrada.'));
    if (ordenNivel(_nivel()) < ordenNivel(r.nivelMinimo)) {
      throw _err(422, 'REGLA_NEGOCIO', 'Esta recompensa requiere nivel ${r.nivelMinimo}.');
    }
    if (_puntosSaldo < r.costoPuntos) {
      throw _err(422, 'REGLA_NEGOCIO', 'No tienes puntos suficientes para este canje.');
    }
    _sumarPuntos(-r.costoPuntos, 'canje', 'Canje: ${r.descripcion}');
    final c = Canje(
      idCanje: _id(),
      codigoCanje: 'CJ-${_id().toRadixString(36).toUpperCase()}${_rnd.nextInt(999)}',
      puntosCanjeados: r.costoPuntos,
      estatus: 'emitido',
      fechaHora: _ahora,
      descripcion: r.descripcion,
    );
    _canjes.add(c);
    return c;
  }

  @override
  Future<List<Canje>> canjes() async {
    await _esperar();
    return _canjes.reversed.toList();
  }

  // ---------------------------------------------------------------------------
  // Asistente IA
  // ---------------------------------------------------------------------------
  @override
  Future<Evaluacion> evaluar(Cuestionario c) async {
    await _esperar();
    _requiereSesion();
    final (perfil, dist, pct) = switch (c.toleranciaRiesgo) {
      'alta' => ('dinamico', const [('USD', 4000, 'bajo'), ('USDT', 4500, 'medio'), ('Bs (liquidez)', 1500, 'alto')], 2000),
      'media' => ('moderado', const [('USD', 5500, 'bajo'), ('USDT', 3000, 'medio'), ('Bs (liquidez)', 1500, 'alto')], 1500),
      _ => ('conservador', const [('USD', 7000, 'bajo'), ('USDT', 2000, 'medio'), ('Bs (liquidez)', 1000, 'alto')], 1000),
    };
    final objetivo = switch (c.objetivoPrincipal) {
      'meta_corta' => 'alcanzar una meta cercana',
      'crecer' => 'hacer crecer tu ahorro',
      _ => 'proteger tu dinero de la devaluación',
    };
    final rec = Recomendacion(
      idRecomendacion: _id(),
      estatus: 'propuesta',
      explicacion:
          'Como tu prioridad es $objetivo en ${c.horizonteMeses} meses y tu tolerancia al riesgo es '
          '${c.toleranciaRiesgo}, te sugerimos mantener la mayor parte de tu cofre en dólares a tasa '
          'BCV y apartar automáticamente el ${Dinero.porcentajePb(pct)} de cada ingreso. Así conviertes '
          'tu meta en pasos pequeños y te anticipas a la devaluación.',
      distribucion: [
        for (final d in dist)
          ParticipacionInstrumento(instrumento: d.$1, porcentajePb: d.$2, nivelRiesgo: d.$3),
      ],
      reglaSugerida: SolicitudRegla.pctIngreso(pct),
      versionModelo: 'reglas-1.2 / modelo-0.3 (demo)',
      disclaimer: 'Recomendación orientativa generada con reglas e IA sobre datos seudonimizados. '
          'Simulación informativa, no asesoría financiera.',
      perfilResultante: perfil,
      fecha: _ahora,
    );
    _recomendaciones.add(rec);
    return Evaluacion(idEvaluacion: _id(), perfilResultante: perfil, recomendacion: rec);
  }

  @override
  Future<DecisionRecomendacion> decidir(
    int idRecomendacion, {
    required bool aceptar,
    required bool entendioExplicacion,
    int? idMeta,
    required String stepUp,
  }) async {
    await _esperar();
    _requiereStepUp(stepUp, NivelStepUp.pin);
    final i = _recomendaciones.indexWhere((r) => r.idRecomendacion == idRecomendacion);
    if (i < 0) throw _err(404, 'NO_ENCONTRADO', 'Recomendación no encontrada.');
    if (aceptar && !entendioExplicacion) {
      throw _err(400, 'VALIDACION', 'Confirma que entendiste la explicación.');
    }
    final r = _recomendaciones[i];
    final estatus = aceptar ? 'aceptada' : 'rechazada';
    _recomendaciones[i] = Recomendacion(
      idRecomendacion: r.idRecomendacion,
      estatus: estatus,
      explicacion: r.explicacion,
      distribucion: r.distribucion,
      reglaSugerida: r.reglaSugerida,
      versionModelo: r.versionModelo,
      disclaimer: r.disclaimer,
      perfilResultante: r.perfilResultante,
      fecha: r.fecha,
    );
    Regla? creada;
    if (aceptar && idMeta != null && r.reglaSugerida != null) {
      creada = Regla(
        idRegla: _id(),
        idMeta: idMeta,
        tipo: r.reglaSugerida!.tipo,
        porcentajePb: r.reglaSugerida!.porcentajePb,
        multiploRedondeoCts: r.reglaSugerida!.multiploRedondeoCts,
        montoFijoUsdCts: r.reglaSugerida!.montoFijoUsdCts,
        estatus: 'activa',
        fechaAprobacion: _ahora,
      );
      _reglas.add(creada);
    }
    return DecisionRecomendacion(estatus: estatus, reglaCreada: creada);
  }

  @override
  Future<List<Recomendacion>> historialRecomendaciones() async {
    await _esperar();
    return _recomendaciones.reversed.toList();
  }

  // ---------------------------------------------------------------------------
  // Notificaciones y reloj demo
  // ---------------------------------------------------------------------------
  @override
  Future<List<Notificacion>> notificaciones() async {
    await _esperar();
    _requiereSesion();
    return _notificaciones.reversed.toList()
      ..sort((a, b) => (b.fechaHoraEnvio ?? DateTime(0)).compareTo(a.fechaHoraEnvio ?? DateTime(0)));
  }

  @override
  Future<void> marcarLeida(int idNotificacion) async {
    await _esperar();
    final i = _notificaciones.indexWhere((n) => n.idNotificacion == idNotificacion);
    if (i < 0) return;
    final n = _notificaciones[i];
    _notificaciones[i] = Notificacion(
      idNotificacion: n.idNotificacion,
      canal: n.canal,
      tipo: n.tipo,
      titulo: n.titulo,
      mensaje: n.mensaje,
      fechaHoraEnvio: n.fechaHoraEnvio,
      estatus: n.estatus,
      leida: true,
    );
  }

  @override
  Future<DateTime?> fechaActual() async => _hoy;

  /// Cierre de día simulado: si no hubo aporte se usa un día colchón; sin colchones, la racha
  /// se cierra.
  @override
  Future<DateTime?> avanzarReloj(int dias) async {
    await _esperar();
    for (var i = 0; i < dias; i++) {
      final aporto = _racha.dias.any((d) => d.fecha == _hoy);
      if (!aporto && _racha.dias.isNotEmpty) {
        if (_racha.colchonesDisponibles > 0) {
          _racha.colchonesDisponibles--;
          _racha.dias.add(DiaRacha(fecha: _hoy, tipo: TipoDiaRacha.colchon));
          _notificaciones.add(Notificacion(
            idNotificacion: _id(),
            canal: 'push',
            tipo: 'racha',
            titulo: 'Usamos un día colchón',
            mensaje: 'Tu racha sigue viva aunque no pudiste aportar. ¡Mañana seguimos!',
            fechaHoraEnvio: _ahora,
            estatus: 'enviada',
            leida: false,
          ));
        } else {
          _racha.historialPrevio.add(DiaRacha(fecha: _hoy, tipo: TipoDiaRacha.sinAporte));
          _cerrarRacha();
        }
      }
      _diasAvanzados++;
    }
    return _hoy;
  }
}

// -----------------------------------------------------------------------------
// Estado interno mutable
// -----------------------------------------------------------------------------
class _UsuarioF {
  _UsuarioF({
    required this.idUsuario,
    required this.nacionalidad,
    required this.cedula,
    required this.pin,
    required this.primerNombre,
    required this.primerApellido,
    required this.correo,
    required this.telefono,
    required this.kycAprobado,
  });

  final int idUsuario;
  final String nacionalidad;
  final int cedula;
  final String pin;
  final String primerNombre;
  final String primerApellido;
  final String correo;
  final String telefono;
  bool kycAprobado;
}

class _ConsentimientoF {
  _ConsentimientoF({
    required this.id,
    required this.idInstitucion,
    required this.institucion,
    required this.debito,
  });

  final int id;
  final int idInstitucion;
  final String institucion;
  final bool debito;
  String estatus = 'pendiente';
  DateTime? otorgado;
  DateTime? vence;
}

class _CuentaF {
  _CuentaF(this.idCuenta, this.alias, this.ultimos4, this.tipo, this.moneda, this.saldoCts,
      {this.soloLectura = false});

  final int idCuenta;
  String? alias;
  final String ultimos4;
  final String tipo;
  final String moneda;
  int saldoCts;
  final bool soloLectura;
  bool activa = true;
}

class _AporteF {
  _AporteF({
    required this.idAporte,
    required this.fecha,
    required this.tipo,
    required this.origen,
    required this.usdCts,
    required this.bsCts,
  });

  final int idAporte;
  final DateTime fecha;
  final String tipo;
  final String origen;
  final int usdCts;
  final int bsCts;
}

class _SelloF {
  _SelloF({required this.idSello, required this.monto, required this.liberacion});

  final int idSello;
  final int monto;
  final DateTime liberacion;
  String estatus = 'activo';
}

class _MetaF {
  _MetaF({
    required this.idMeta,
    required this.nombre,
    required this.categoria,
    required this.objetivo,
    required this.fechaObjetivo,
    required this.frecuencia,
    required this.creada,
  });

  final int idMeta;
  final String nombre;
  final CategoriaMeta categoria;
  final int objetivo;
  final DateTime fechaObjetivo;
  final FrecuenciaCuota frecuencia;
  final DateTime creada;
  String estatus = 'activa';
  final List<_AporteF> aportes = [];
  _SelloF? sello;
}

class _RachaF {
  _RachaF({required this.inicio, required this.dias, required this.colchonesDisponibles});

  DateTime inicio;
  final List<DiaRacha> dias;
  final List<DiaRacha> historialPrevio = [];
  int colchonesDisponibles;
  bool cerrada = false;
}

class _PagoF {
  _PagoF({
    required this.idPago,
    required this.idTarjeta,
    required this.estado,
    required this.montoCts,
    required this.comisionCts,
    required this.creado,
    this.referencia,
    this.autorizacion,
  });

  final int idPago;
  final int idTarjeta;
  EstadoPago estado;
  final int montoCts;
  final int comisionCts;
  final DateTime creado;
  String? referencia;
  String? autorizacion;
  String? motivo;
  String? mensaje;
  bool? esPuntual = true;
  int consultas = 0;
  final List<CambioEstadoPago> historial = [];

  Pago dominio({required bool conHistorial}) => Pago(
        idPago: idPago,
        estado: estado,
        montoCts: montoCts,
        comisionCts: comisionCts,
        referenciaSuiche: referencia,
        codigoAutorizacion: autorizacion,
        esPuntual: esPuntual,
        motivoRechazo: motivo,
        fechaHoraCreacion: creado,
        mensaje: mensaje,
        historial: conHistorial ? List.unmodifiable(historial) : const [],
      );
}
