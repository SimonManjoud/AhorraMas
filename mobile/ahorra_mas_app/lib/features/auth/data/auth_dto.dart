import '../../../core/api/api_client.dart';
import '../../../core/util/fechas.dart';
import '../domain/auth_models.dart';

/// Conversión JSON ⇄ dominio del módulo de Identidad.
class AuthDto {
  AuthDto._();

  static Usuario usuario(Map<String, dynamic> j) => Usuario(
        idUsuario: j.entero('idUsuario'),
        primerNombre: j.texto('primerNombre'),
        primerApellido: j.texto('primerApellido'),
        kycAprobado: j.booleano('kycAprobado'),
        estatus: j.textoONulo('estatus') ?? 'activo',
      );

  static Sesion sesion(Map<String, dynamic> j) => Sesion(
        accessToken: j.texto('accessToken'),
        expiraEn: j.fecha('expiraEn'),
        usuario: usuario(j.mapa('usuario')!),
        idDispositivo: j.enteroONulo('idDispositivo'),
      );

  static Perfil perfil(Map<String, dynamic> j) => Perfil(
        idUsuario: j.entero('idUsuario'),
        nacionalidadDoc: j.textoONulo('nacionalidadDoc') ?? 'V',
        cedula: j.entero('cedula'),
        primerNombre: j.texto('primerNombre'),
        primerApellido: j.texto('primerApellido'),
        correo: j.textoONulo('correo'),
        telefonoMovil: j.textoONulo('telefonoMovil'),
        kycAprobado: j.booleano('kycAprobado'),
        estatus: j.textoONulo('estatus') ?? 'activo',
        fechaRegistro: j.fecha('fechaRegistro'),
      );

  static Map<String, dynamic> registroAJson(SolicitudRegistro s) => {
        'nacionalidadDoc': s.nacionalidadDoc,
        'cedula': s.cedula,
        'primerNombre': s.primerNombre,
        'segundoNombre': s.segundoNombre,
        'primerApellido': s.primerApellido,
        'segundoApellido': s.segundoApellido,
        'fechaNacimiento': Fechas.iso(s.fechaNacimiento),
        'correo': s.correo,
        'telefonoMovil': s.telefonoMovil,
        'pin': s.pin,
        'dispositivo': {
          'huella': s.dispositivo.huella,
          'plataforma': s.dispositivo.plataforma,
          'modelo': s.dispositivo.modelo,
        },
      };

  static DesafioOtp desafio(Map<String, dynamic> j) => DesafioOtp(
        idDesafio: j.texto('idDesafio'),
        expiraEn: j.fecha('expiraEn'),
        otpDemo: j.textoONulo('otpDemo'),
      );

  static StepUp stepUp(Map<String, dynamic> j) => StepUp(
        token: j.texto('stepUpToken'),
        nivel: NivelStepUp.desde(j.textoONulo('nivel') ?? 'pin'),
        expiraEn: j.fecha('expiraEn'),
      );

  static ResultadoKyc kyc(Map<String, dynamic> j) => ResultadoKyc(
        idVerificacion: j.entero('idVerificacion'),
        resultado: j.texto('resultado'),
        pruebaVidaSuperada: j.booleano('pruebaVidaSuperada'),
        coincideListaSanciones: j.booleano('coincideListaSanciones'),
        motivoRechazo: j.textoONulo('motivoRechazo'),
      );

  static TokenSaldo tokenSaldo(Map<String, dynamic> j) =>
      TokenSaldo(tokenSaldo: j.texto('tokenSaldo'), expiraEn: j.fecha('expiraEn'));

  static SaldoRapido saldoRapido(Map<String, dynamic> j) => SaldoRapido(
        totalBsCts: j.entero('totalBsCts'),
        totalUsdCts: j.entero('totalUsdCts'),
        actualizadoEn: j.fecha('actualizadoEn'),
      );
}
