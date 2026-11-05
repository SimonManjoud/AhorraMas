import 'pagos_models.dart';

/// Puerto de Pago de TDC (§4 del contrato).
abstract interface class PagosRepository {
  Future<List<Tarjeta>> tarjetas();

  /// Consulta al suiche el titular de la tarjeta (paso 2 de F2).
  Future<Beneficiario> beneficiario(int idTarjeta);

  Future<List<CuentaOrigen>> cuentasOrigen();

  Future<Cotizacion> cotizacion(int montoCts);

  /// `POST /pagos-tdc` con `Idempotency-Key` y `X-Step-Up` (nivel `pin_otp`).
  /// Con la misma clave el backend devuelve el pago existente: **nunca duplica**.
  Future<Pago> pagar(
    SolicitudPagoTdc solicitud, {
    required String idempotencyKey,
    required String stepUp,
  });

  Future<List<Pago>> pagos();

  Future<Pago> pago(int idPago);

  Future<Limites> limites();
}
