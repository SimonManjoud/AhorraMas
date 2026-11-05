import '../../../core/api/api_client.dart';
import '../domain/consolidacion_models.dart';
import '../domain/consolidacion_repository.dart';
import 'consolidacion_dto.dart';

class HttpConsolidacionRepository implements ConsolidacionRepository {
  HttpConsolidacionRepository(this._api);

  final ApiClient _api;

  @override
  Future<List<Institucion>> instituciones() async =>
      listaJson(await _api.get('/instituciones'), ConsolidacionDto.institucion);

  @override
  Future<List<ConsentimientoCreado>> crearConsentimientos(
    List<int> idsInstituciones, {
    required bool permiteDebitoTdc,
  }) async =>
      listaJson(
        await _api.post('/consentimientos', cuerpo: {
          'instituciones': idsInstituciones,
          'permiteDebitoTdc': permiteDebitoTdc,
        }),
        ConsolidacionDto.consentimientoCreado,
      );

  @override
  Future<List<Consentimiento>> consentimientos() async =>
      listaJson(await _api.get('/consentimientos'), ConsolidacionDto.consentimiento);

  @override
  Future<void> revocarConsentimiento(int idConsentimiento) =>
      _api.delete('/consentimientos/$idConsentimiento');

  @override
  Future<void> sincronizar() => _api.post('/consolidado/sincronizar');

  @override
  Future<Consolidado> consolidado() async =>
      ConsolidacionDto.consolidado(await _api.get('/consolidado'));

  @override
  Future<void> renombrarCuenta(int idCuenta, String alias) =>
      _api.patch('/cuentas/$idCuenta', cuerpo: {'alias': alias});

  @override
  Future<List<Movimiento>> movimientosCuenta(int idCuenta) async =>
      listaJson(await _api.get('/cuentas/$idCuenta/movimientos'), ConsolidacionDto.movimiento);

  @override
  Future<Tasa> tasaHoy() async => ConsolidacionDto.tasa(await _api.get('/tasas/hoy'));
}
