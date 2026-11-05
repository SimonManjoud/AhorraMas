/// Error de la API con el cuerpo del contrato: `{"codigo", "mensaje", "detalles"}`.
class ApiException implements Exception {
  const ApiException({
    required this.codigo,
    required this.mensaje,
    this.statusCode,
    this.detalles = const {},
  });

  /// Error sin respuesta del servidor (sin red, timeout, servidor caído).
  const ApiException.red([String? mensaje])
      : codigo = 'SIN_CONEXION',
        mensaje = mensaje ??
            'No pudimos conectar con Ahorra+. Revisa tu conexión e inténtalo de nuevo.',
        statusCode = null,
        detalles = const {};

  factory ApiException.desdeCuerpo(int statusCode, Object? cuerpo) {
    if (cuerpo is Map<String, dynamic>) {
      final detalles = cuerpo['detalles'];
      return ApiException(
        statusCode: statusCode,
        codigo: (cuerpo['codigo'] as String?) ?? 'ERROR_$statusCode',
        mensaje: (cuerpo['mensaje'] as String?) ?? _mensajeGenerico(statusCode),
        detalles: detalles is Map<String, dynamic> ? detalles : const {},
      );
    }
    return ApiException(
      statusCode: statusCode,
      codigo: 'ERROR_$statusCode',
      mensaje: _mensajeGenerico(statusCode),
    );
  }

  final int? statusCode;
  final String codigo;
  final String mensaje;
  final Map<String, dynamic> detalles;

  bool get esDeRed => statusCode == null;
  bool get noAutenticado => codigo == 'NO_AUTENTICADO';
  bool get stepUpRequerido => codigo == 'STEP_UP_REQUERIDO';
  bool get kycPendiente => codigo == 'KYC_PENDIENTE';

  static String _mensajeGenerico(int status) {
    if (status >= 500) return 'El servicio no está disponible en este momento. Intenta más tarde.';
    if (status == 401) return 'Tu sesión expiró. Vuelve a ingresar.';
    if (status == 403) return 'No tienes permiso para esta operación.';
    if (status == 404) return 'No encontramos lo que buscabas.';
    return 'No pudimos completar la operación.';
  }

  @override
  String toString() => mensaje;
}

/// Mensaje legible para cualquier error mostrado en pantalla.
String mensajeDeError(Object error) {
  if (error is ApiException) return error.mensaje;
  return 'Ocurrió un error inesperado. Intenta de nuevo.';
}
