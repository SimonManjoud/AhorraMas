import '../../../core/api/api_client.dart';
import '../domain/cofre_models.dart';
import '../domain/cofre_repository.dart';
import 'cofre_dto.dart';

class HttpCofreRepository implements CofreRepository {
  HttpCofreRepository(this._api);

  final ApiClient _api;

  Map<String, String> _stepUp(String t) => {'X-Step-Up': t};

  @override
  Future<List<Meta>> metas() async => listaJson(await _api.get('/metas'), CofreDto.meta);

  @override
  Future<Meta> meta(int idMeta) async => CofreDto.meta(await _api.get('/metas/$idMeta'));

  @override
  Future<Meta> crearMeta(SolicitudMeta solicitud) async =>
      CofreDto.meta(await _api.post('/metas', cuerpo: CofreDto.solicitudMeta(solicitud)));

  @override
  Future<void> cambiarEstatusMeta(int idMeta, String estatus) =>
      _api.patch('/metas/$idMeta', cuerpo: {'estatus': estatus});

  @override
  Future<RutaDelDia> rutaDelDia(int idMeta) async =>
      CofreDto.ruta(await _api.get('/metas/$idMeta/ruta-del-dia'));

  @override
  Future<List<RutaDelDia>> rutasDelDia() async =>
      listaJson(await _api.get('/ruta-del-dia'), CofreDto.ruta);

  @override
  Future<ResultadoAporte> aportar(
    int idMeta, {
    required int montoUsdCts,
    required String origen,
    int? idCuentaOrigen,
  }) async =>
      CofreDto.resultadoAporte(await _api.post('/metas/$idMeta/aportes', cuerpo: {
        'montoUsdCts': montoUsdCts,
        'origen': origen,
        'idCuentaOrigen': ?idCuentaOrigen,
      }));

  @override
  Future<ResultadoAporte> retirar(
    int idMeta, {
    required int montoUsdCts,
    required String stepUp,
  }) async =>
      CofreDto.resultadoAporte(await _api.post(
        '/metas/$idMeta/retiros',
        cuerpo: {'montoUsdCts': montoUsdCts},
        headers: _stepUp(stepUp),
      ));

  @override
  Future<List<Aporte>> aportes(int idMeta) async =>
      listaJson(await _api.get('/metas/$idMeta/aportes'), CofreDto.aporte);

  @override
  Future<Regla> crearRegla(int idMeta, SolicitudRegla regla, {required String stepUp}) async =>
      CofreDto.regla(await _api.post(
        '/metas/$idMeta/reglas',
        cuerpo: CofreDto.solicitudReglaAJson(regla),
        headers: _stepUp(stepUp),
      ));

  @override
  Future<List<Regla>> reglas(int idMeta) async =>
      listaJson(await _api.get('/metas/$idMeta/reglas'), CofreDto.regla);

  @override
  Future<void> cambiarEstatusRegla(int idRegla, String estatus) =>
      _api.patch('/reglas/$idRegla', cuerpo: {'estatus': estatus});

  @override
  Future<Racha> racha() async => CofreDto.racha(await _api.get('/racha') as Map<String, dynamic>?);

  @override
  Future<Sello> sellar(int idMeta, {required int montoUsdCts, required String stepUp}) async =>
      CofreDto.sello(await _api.post(
        '/metas/$idMeta/sello',
        cuerpo: {'montoSelladoUsdCts': montoUsdCts},
        headers: _stepUp(stepUp),
      ));

  @override
  Future<Sello> romperSello(int idMeta, {required String stepUp}) async =>
      CofreDto.sello(await _api.post('/metas/$idMeta/sello/romper', headers: _stepUp(stepUp)));

  @override
  Future<EspejoImpacto> espejoImpacto({int? anio, int? mes}) async =>
      CofreDto.espejo(await _api.get('/espejo-impacto', query: {
        if (anio != null) 'anio': '$anio',
        if (mes != null) 'mes': '$mes',
      }));
}
