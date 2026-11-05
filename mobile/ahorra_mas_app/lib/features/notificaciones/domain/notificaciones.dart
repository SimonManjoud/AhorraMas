/// Notificaciones y reloj demo: modelos y puertos.
library;

class Notificacion {
  const Notificacion({
    required this.idNotificacion,
    required this.canal,
    required this.tipo,
    required this.titulo,
    required this.mensaje,
    required this.fechaHoraEnvio,
    required this.estatus,
    required this.leida,
  });

  final int idNotificacion;
  final String? canal;
  final String? tipo;
  final String titulo;
  final String mensaje;
  final DateTime? fechaHoraEnvio;
  final String? estatus;
  final bool leida;
}

abstract interface class NotificacionesRepository {
  Future<List<Notificacion>> notificaciones();
  Future<void> marcarLeida(int idNotificacion);
}

/// `/demo/reloj` (solo existe cuando el backend corre con el perfil `demo`).
abstract interface class DemoRepository {
  Future<DateTime?> fechaActual();
  Future<DateTime?> avanzarReloj(int dias);
}
