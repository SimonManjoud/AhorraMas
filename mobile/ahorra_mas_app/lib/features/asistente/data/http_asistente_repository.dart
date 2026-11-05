import '../../../core/api/api_client.dart';
import '../../cofre/data/cofre_dto.dart';
import '../domain/asistente.dart';

class AsistenteDto {
  AsistenteDto._();

  static const disclaimerPorDefecto =
      'Recomendación orientativa generada por IA. Simulación informativa, no asesoría financiera.';

  static Recomendacion recomendacion(Map<String, dynamic> j, {String? perfil}) {
    final regla = j.mapa('reglaSugerida');
    return Recomendacion(
      idRecomendacion: j.entero('idRecomendacion'),
      estatus: j.textoONulo('estatus') ?? 'propuesta',
      explicacion: j.textoONulo('explicacion') ?? '',
      distribucion: j.lista(
        'distribucion',
        (d) => ParticipacionInstrumento(
          instrumento: d.texto('instrumento'),
          porcentajePb: d.enteroO('porcentajePb', 0),
          nivelRiesgo: d.textoONulo('nivelRiesgo') ?? 'bajo',
        ),
      ),
      reglaSugerida: regla == null ? null : CofreDto.solicitudRegla(regla),
      versionModelo: j.textoONulo('versionModelo'),
      disclaimer: j.textoONulo('disclaimer') ?? disclaimerPorDefecto,
      perfilResultante: perfil ?? j.textoONulo('perfilResultante'),
      fecha: j.fecha('fechaHora') ?? j.fecha('fechaCreacion'),
    );
  }

  static Evaluacion evaluacion(Map<String, dynamic> j) {
    final perfil = j.textoONulo('perfilResultante') ?? 'conservador';
    return Evaluacion(
      idEvaluacion: j.entero('idEvaluacion'),
      perfilResultante: perfil,
      recomendacion: recomendacion(j.mapa('recomendacion')!, perfil: perfil),
    );
  }

  static DecisionRecomendacion decision(Map<String, dynamic> j) => DecisionRecomendacion(
        estatus: j.textoONulo('estatus') ?? 'aceptada',
        reglaCreada: j.mapa('reglaCreada') == null ? null : CofreDto.regla(j.mapa('reglaCreada')!),
      );
}

class HttpAsistenteRepository implements AsistenteRepository {
  HttpAsistenteRepository(this._api);

  final ApiClient _api;

  @override
  Future<Evaluacion> evaluar(Cuestionario c) async =>
      AsistenteDto.evaluacion(await _api.post('/perfil/evaluaciones', cuerpo: {
        'objetivoPrincipal': c.objetivoPrincipal,
        'horizonteMeses': c.horizonteMeses,
        'toleranciaRiesgo': c.toleranciaRiesgo,
      }));

  @override
  Future<DecisionRecomendacion> decidir(
    int idRecomendacion, {
    required bool aceptar,
    required bool entendioExplicacion,
    int? idMeta,
    required String stepUp,
  }) async =>
      AsistenteDto.decision(await _api.post(
        '/recomendaciones/$idRecomendacion/decision',
        cuerpo: {
          'aceptar': aceptar,
          'entendioExplicacion': entendioExplicacion,
          'idMeta': ?idMeta,
        },
        headers: {'X-Step-Up': stepUp},
      ));

  @override
  Future<List<Recomendacion>> historialRecomendaciones() async =>
      listaJson(await _api.get('/recomendaciones'), (j) => AsistenteDto.recomendacion(j));
}
