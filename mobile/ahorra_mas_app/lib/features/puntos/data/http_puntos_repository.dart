import '../../../core/api/api_client.dart';
import '../domain/puntos.dart';

class PuntosDto {
  PuntosDto._();

  static ResumenPuntos resumen(Map<String, dynamic> j) {
    final nivel = j.mapa('nivel');
    final sig = j.mapa('siguienteNivel');
    return ResumenPuntos(
      saldo: j.enteroO('saldo', 0),
      puntosGanadosTotal: j.enteroO('puntosGanadosTotal', 0),
      nivel: nivel?.textoONulo('nombre') ?? 'Base',
      nivelDesde: nivel?.fecha('desde'),
      siguienteNivel: sig == null
          ? null
          : SiguienteNivel(
              nombre: sig.texto('nombre'),
              puntosMinimos: sig.enteroO('puntosMinimos', 0),
              puntosFaltantes: sig.enteroO('puntosFaltantes', 0),
            ),
    );
  }

  static MovimientoPuntos movimiento(Map<String, dynamic> j) => MovimientoPuntos(
        idTransaccion: j.entero('idTransaccion'),
        fechaHora: j.fecha('fechaHora'),
        concepto: j.textoONulo('concepto') ?? 'meta',
        descripcion: j.textoONulo('descripcion'),
        puntos: j.enteroO('puntos', 0),
      );

  static Recompensa recompensa(Map<String, dynamic> j) => Recompensa(
        idRecompensa: j.entero('idRecompensa'),
        descripcion: j.textoONulo('descripcion') ?? '',
        costoPuntos: j.enteroO('costoPuntos', 0),
        comercio: j.textoONulo('comercio'),
        nivelMinimo: j.textoONulo('nivelMinimo') ?? 'Base',
        stockDisponible: j.enteroONulo('stockDisponible'),
      );

  static Canje canje(Map<String, dynamic> j) => Canje(
        idCanje: j.entero('idCanje'),
        codigoCanje: j.textoONulo('codigoCanje') ?? '',
        puntosCanjeados: j.enteroO('puntosCanjeados', 0),
        estatus: j.textoONulo('estatus') ?? 'emitido',
        fechaHora: j.fecha('fechaHora'),
        descripcion: j.textoONulo('descripcion'),
      );
}

class HttpPuntosRepository implements PuntosRepository {
  HttpPuntosRepository(this._api);

  final ApiClient _api;

  @override
  Future<ResumenPuntos> resumenPuntos() async => PuntosDto.resumen(await _api.get('/puntos'));

  @override
  Future<List<MovimientoPuntos>> movimientosPuntos() async =>
      listaJson(await _api.get('/puntos/movimientos'), PuntosDto.movimiento);

  @override
  Future<List<Recompensa>> recompensas() async =>
      listaJson(await _api.get('/recompensas'), PuntosDto.recompensa);

  @override
  Future<Canje> canjear(int idRecompensa) async =>
      PuntosDto.canje(await _api.post('/recompensas/$idRecompensa/canjes'));

  @override
  Future<List<Canje>> canjes() async => listaJson(await _api.get('/canjes'), PuntosDto.canje);
}
