import '../../../core/api/api_client.dart';
import '../domain/notificaciones.dart';

class NotificacionesDto {
  NotificacionesDto._();

  static Notificacion notificacion(Map<String, dynamic> j) => Notificacion(
        idNotificacion: j.entero('idNotificacion'),
        canal: j.textoONulo('canal'),
        tipo: j.textoONulo('tipo'),
        titulo: j.textoONulo('titulo') ?? '',
        mensaje: j.textoONulo('mensaje') ?? '',
        fechaHoraEnvio: j.fecha('fechaHoraEnvio'),
        estatus: j.textoONulo('estatus'),
        leida: j.booleano('leida'),
      );
}

class HttpNotificacionesRepository implements NotificacionesRepository, DemoRepository {
  HttpNotificacionesRepository(this._api);

  final ApiClient _api;

  @override
  Future<List<Notificacion>> notificaciones() async =>
      listaJson(await _api.get('/notificaciones'), NotificacionesDto.notificacion);

  @override
  Future<void> marcarLeida(int idNotificacion) =>
      _api.post('/notificaciones/$idNotificacion/leida');

  @override
  Future<DateTime?> fechaActual() async =>
      (await _api.get('/demo/reloj') as Map<String, dynamic>).fecha('fechaActual');

  @override
  Future<DateTime?> avanzarReloj(int dias) async =>
      (await _api.post('/demo/reloj/avanzar', cuerpo: {'dias': dias}) as Map<String, dynamic>)
          .fecha('fechaActual');
}
