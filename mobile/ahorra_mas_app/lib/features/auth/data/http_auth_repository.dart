import '../../../core/api/api_client.dart';
import '../domain/auth_models.dart';
import '../domain/auth_repository.dart';
import 'auth_dto.dart';

/// Adaptador HTTP del puerto [AuthRepository].
class HttpAuthRepository implements AuthRepository {
  HttpAuthRepository(this._api);

  final ApiClient _api;

  @override
  Future<Sesion> registrar(SolicitudRegistro solicitud) async => AuthDto.sesion(
        await _api.post('/auth/registro', cuerpo: AuthDto.registroAJson(solicitud), auth: false),
      );

  @override
  Future<Sesion> login({
    required String nacionalidadDoc,
    required int cedula,
    required String pin,
    required DispositivoInfo dispositivo,
  }) async =>
      AuthDto.sesion(await _api.post('/auth/login', auth: false, cuerpo: {
        'nacionalidadDoc': nacionalidadDoc,
        'cedula': cedula,
        'pin': pin,
        'huellaDispositivo': dispositivo.huella,
        'plataforma': dispositivo.plataforma,
        'modelo': dispositivo.modelo,
      }));

  @override
  Future<Perfil> perfil() async => AuthDto.perfil(await _api.get('/me'));

  @override
  Future<DesafioOtp> solicitarOtp(String proposito) async =>
      AuthDto.desafio(await _api.post('/auth/otp', cuerpo: {'proposito': proposito}));

  @override
  Future<StepUp> stepUp({required String pin, String? idDesafio, String? otp}) async =>
      AuthDto.stepUp(await _api.post('/auth/step-up', cuerpo: {
        'pin': pin,
        'idDesafio': ?idDesafio,
        'otp': ?otp,
      }));

  @override
  Future<ResultadoKyc> enviarKyc(List<DocumentoKyc> documentos) async =>
      AuthDto.kyc(await _api.post('/kyc/verificaciones', cuerpo: {
        'documentos': [
          for (final d in documentos) {'tipo': d.tipo, 'contenidoBase64': d.contenidoBase64},
        ],
      }));

  @override
  Future<TokenSaldo> activarSaldoRapido(String stepUpToken) async => AuthDto.tokenSaldo(
        await _api.post('/auth/saldo-rapido', headers: {'X-Step-Up': stepUpToken}),
      );

  @override
  Future<SaldoRapido> consultarSaldoRapido({
    required String tokenSaldo,
    required String huella,
  }) async =>
      AuthDto.saldoRapido(await _api.get('/saldo-rapido', auth: false, headers: {
        'Authorization': 'Bearer $tokenSaldo',
        'X-Dispositivo': huella,
      }));

  @override
  Future<void> desactivarSaldoRapido() => _api.delete('/auth/saldo-rapido');

  @override
  Future<void> revocarDispositivoActual(String motivo) =>
      _api.post('/dispositivos/actual/revocar', cuerpo: {'motivo': motivo});
}
