import '../../../core/api/api_client.dart';
import '../domain/pagos_models.dart';
import '../domain/pagos_repository.dart';
import 'pagos_dto.dart';

class HttpPagosRepository implements PagosRepository {
  HttpPagosRepository(this._api);

  final ApiClient _api;

  @override
  Future<List<Tarjeta>> tarjetas() async => listaJson(await _api.get('/tarjetas'), PagosDto.tarjeta);

  @override
  Future<Beneficiario> beneficiario(int idTarjeta) async =>
      PagosDto.beneficiario(await _api.post('/tarjetas/$idTarjeta/beneficiario'));

  @override
  Future<List<CuentaOrigen>> cuentasOrigen() async =>
      listaJson(await _api.get('/pagos-tdc/cuentas-origen'), PagosDto.cuentaOrigen);

  @override
  Future<Cotizacion> cotizacion(int montoCts) async => PagosDto.cotizacion(
        await _api.get('/pagos-tdc/cotizacion', query: {'montoCts': '$montoCts'}),
      );

  @override
  Future<Pago> pagar(
    SolicitudPagoTdc solicitud, {
    required String idempotencyKey,
    required String stepUp,
  }) async =>
      PagosDto.pago(await _api.post(
        '/pagos-tdc',
        cuerpo: PagosDto.solicitud(solicitud),
        headers: {'Idempotency-Key': idempotencyKey, 'X-Step-Up': stepUp},
        // Seguro reintentar: la misma Idempotency-Key nunca duplica el pago.
        reintentable: true,
      ));

  @override
  Future<List<Pago>> pagos() async => listaJson(await _api.get('/pagos-tdc'), PagosDto.pago);

  @override
  Future<Pago> pago(int idPago) async => PagosDto.pago(await _api.get('/pagos-tdc/$idPago'));

  @override
  Future<Limites> limites() async => PagosDto.limites(await _api.get('/limites'));
}
