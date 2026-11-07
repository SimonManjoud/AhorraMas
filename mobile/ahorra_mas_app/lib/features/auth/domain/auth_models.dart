/// Modelos del módulo de Identidad (M1).
library;

class Usuario {
  const Usuario({
    required this.idUsuario,
    required this.primerNombre,
    required this.primerApellido,
    required this.kycAprobado,
    required this.estatus,
  });

  final int idUsuario;
  final String primerNombre;
  final String primerApellido;
  final bool kycAprobado;
  final String estatus;

  Usuario copyWith({bool? kycAprobado}) => Usuario(
        idUsuario: idUsuario,
        primerNombre: primerNombre,
        primerApellido: primerApellido,
        kycAprobado: kycAprobado ?? this.kycAprobado,
        estatus: estatus,
      );
}

class Sesion {
  const Sesion({
    required this.accessToken,
    required this.expiraEn,
    required this.usuario,
    required this.idDispositivo,
  });

  final String accessToken;
  final DateTime? expiraEn;
  final Usuario usuario;
  final int? idDispositivo;

  Sesion copyWith({Usuario? usuario}) => Sesion(
        accessToken: accessToken,
        expiraEn: expiraEn,
        usuario: usuario ?? this.usuario,
        idDispositivo: idDispositivo,
      );
}

class Perfil {
  const Perfil({
    required this.idUsuario,
    required this.nacionalidadDoc,
    required this.cedula,
    required this.primerNombre,
    required this.primerApellido,
    required this.correo,
    required this.telefonoMovil,
    required this.kycAprobado,
    required this.estatus,
    required this.fechaRegistro,
  });

  final int idUsuario;
  final String nacionalidadDoc;
  final int cedula;
  final String primerNombre;
  final String primerApellido;
  final String? correo;
  final String? telefonoMovil;
  final bool kycAprobado;
  final String estatus;
  final DateTime? fechaRegistro;
}

/// Datos del equipo enviados en registro/login.
class DispositivoInfo {
  const DispositivoInfo({required this.huella, required this.plataforma, required this.modelo});

  final String huella;
  final String plataforma;
  final String modelo;
}

class SolicitudRegistro {
  const SolicitudRegistro({
    required this.nacionalidadDoc,
    required this.cedula,
    required this.primerNombre,
    this.segundoNombre,
    required this.primerApellido,
    this.segundoApellido,
    required this.fechaNacimiento,
    required this.correo,
    required this.telefonoMovil,
    required this.pin,
    required this.dispositivo,
  });

  final String nacionalidadDoc;
  final int cedula;
  final String primerNombre;
  final String? segundoNombre;
  final String primerApellido;
  final String? segundoApellido;
  final DateTime fechaNacimiento;
  final String correo;
  final String telefonoMovil;
  final String pin;
  final DispositivoInfo dispositivo;
}

class DesafioOtp {
  const DesafioOtp({required this.idDesafio, required this.expiraEn, this.otpDemo});

  final String idDesafio;
  final DateTime? expiraEn;

  /// Solo viene cuando el backend corre en modo demo.
  final String? otpDemo;
}

enum NivelStepUp {
  pin('pin'),
  pinOtp('pin_otp');

  const NivelStepUp(this.codigo);
  final String codigo;

  static NivelStepUp desde(String c) => c == 'pin_otp' ? pinOtp : pin;
}

class StepUp {
  const StepUp({required this.token, required this.nivel, required this.expiraEn});

  final String token;
  final NivelStepUp nivel;
  final DateTime? expiraEn;

  bool vigente(DateTime ahora) =>
      expiraEn == null || ahora.isBefore(expiraEn!.subtract(const Duration(seconds: 10)));
}

class DocumentoKyc {
  const DocumentoKyc({required this.tipo, required this.contenidoBase64});

  /// `cedula` o `selfie`.
  final String tipo;
  final String contenidoBase64;
}

class ResultadoKyc {
  const ResultadoKyc({
    required this.idVerificacion,
    required this.resultado,
    required this.pruebaVidaSuperada,
    required this.coincideListaSanciones,
    this.motivoRechazo,
  });

  final int idVerificacion;

  /// `aprobado|rechazado|pendiente`
  final String resultado;
  final bool pruebaVidaSuperada;
  final bool coincideListaSanciones;
  final String? motivoRechazo;

  bool get aprobado => resultado == 'aprobado';
}

class TokenSaldo {
  const TokenSaldo({required this.tokenSaldo, required this.expiraEn});

  final String tokenSaldo;
  final DateTime? expiraEn;
}

class SaldoRapido {
  const SaldoRapido({
    required this.totalBsCts,
    required this.totalUsdCts,
    required this.actualizadoEn,
    this.bancos = const [],
  });

  final int totalBsCts;
  final int totalUsdCts;
  final DateTime? actualizadoEn;

  /// Desglose por institución (sin cuentas ni movimientos).
  final List<SaldoBanco> bancos;
}

/// Total de una institución en el saldo rápido.
class SaldoBanco {
  const SaldoBanco({required this.nombre, required this.tipo, required this.totalBsCts, required this.totalUsdCts});

  final String nombre;

  /// `banco|plataforma_cripto`
  final String tipo;
  final int totalBsCts;
  final int totalUsdCts;

  bool get esCripto => tipo == 'plataforma_cripto';

  Map<String, Object> aJson() => {'nombre': nombre, 'tipo': tipo, 'totalBsCts': totalBsCts, 'totalUsdCts': totalUsdCts};

  static SaldoBanco desdeJson(Map<String, dynamic> j) => SaldoBanco(
        nombre: j['nombre'] as String? ?? '',
        tipo: j['tipo'] as String? ?? 'banco',
        totalBsCts: (j['totalBsCts'] as num?)?.toInt() ?? 0,
        totalUsdCts: (j['totalUsdCts'] as num?)?.toInt() ?? 0,
      );
}
