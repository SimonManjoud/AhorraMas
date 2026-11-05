import 'package:uuid/uuid.dart';

import '../../../core/api/api_exception.dart';
import 'pagos_models.dart';
import 'pagos_repository.dart';

typedef GeneradorClave = String Function();

/// Un intento de pago: la `Idempotency-Key` se genera **una sola vez** por intento y se
/// reutiliza en cada reintento (por red o por que el usuario toca "Reintentar").
class IntentoPago {
  IntentoPago(this.solicitud, {GeneradorClave? generador})
      : idempotencyKey = (generador ?? _uuidV4)();

  final SolicitudPagoTdc solicitud;
  final String idempotencyKey;
  int envios = 0;
  Pago? resultado;

  static String _uuidV4() => const Uuid().v4();
}

/// Caso de uso de pago de TDC que garantiza la reutilización de la clave de idempotencia.
///
/// * Si la solicitud (tarjeta, cuenta, monto) no cambia, todos los envíos usan la misma clave.
/// * Si cambia la solicitud, se crea un intento nuevo con clave nueva.
/// * [nuevoIntento] descarta el intento actual (p. ej. tras un pago terminado).
class ServicioPagoTdc {
  ServicioPagoTdc(this._repo, {GeneradorClave? generador}) : _generador = generador; // ignore: prefer_initializing_formals

  final PagosRepository _repo;
  final GeneradorClave? _generador;
  IntentoPago? _intento;

  IntentoPago? get intentoActual => _intento;

  IntentoPago prepararIntento(SolicitudPagoTdc solicitud) {
    final actual = _intento;
    if (actual != null && actual.solicitud == solicitud) return actual;
    return _intento = IntentoPago(solicitud, generador: _generador);
  }

  Future<Pago> pagar(SolicitudPagoTdc solicitud, {required String stepUp}) async {
    final intento = prepararIntento(solicitud);
    intento.envios++;
    final pago = await _repo.pagar(
      solicitud,
      idempotencyKey: intento.idempotencyKey,
      stepUp: stepUp,
    );
    intento.resultado = pago;
    return pago;
  }

  /// Indica si conviene reintentar con la misma clave (error de red / servidor).
  static bool esReintentable(Object error) =>
      error is ApiException && (error.esDeRed || (error.statusCode ?? 0) >= 500);

  void nuevoIntento() => _intento = null;
}
