import 'auth_models.dart';

/// Puerto del módulo de Identidad (§1 del contrato).
abstract interface class AuthRepository {
  Future<Sesion> registrar(SolicitudRegistro solicitud);

  Future<Sesion> login({
    required String nacionalidadDoc,
    required int cedula,
    required String pin,
    required DispositivoInfo dispositivo,
  });

  Future<Perfil> perfil();

  /// `POST /auth/otp` (envía OTP por SMS).
  Future<DesafioOtp> solicitarOtp(String proposito);

  /// `POST /auth/step-up`: solo PIN ⇒ nivel `pin`; PIN + desafío + OTP ⇒ `pin_otp`.
  Future<StepUp> stepUp({required String pin, String? idDesafio, String? otp});

  Future<ResultadoKyc> enviarKyc(List<DocumentoKyc> documentos);

  Future<TokenSaldo> activarSaldoRapido(String stepUpToken);

  /// `GET /saldo-rapido` con el token de solo lectura (no usa la sesión).
  Future<SaldoRapido> consultarSaldoRapido({required String tokenSaldo, required String huella});

  Future<void> desactivarSaldoRapido();

  /// `motivo`: `cambio_sim|root|robo|usuario`.
  Future<void> revocarDispositivoActual(String motivo);
}
