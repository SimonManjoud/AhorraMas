/// Asistente de perfil de riesgo — IA (M4): modelos y puerto.
/// La IA **nunca** ejecuta nada sola: solo al aceptar con PIN se crea la regla sugerida.
library;

import '../../cofre/domain/cofre_models.dart';

class Cuestionario {
  const Cuestionario({
    required this.objetivoPrincipal,
    required this.horizonteMeses,
    required this.toleranciaRiesgo,
  });

  /// `proteger|meta_corta|crecer`
  final String objetivoPrincipal;
  final int horizonteMeses;

  /// `baja|media|alta`
  final String toleranciaRiesgo;
}

class ParticipacionInstrumento {
  const ParticipacionInstrumento({
    required this.instrumento,
    required this.porcentajePb,
    required this.nivelRiesgo,
  });

  final String instrumento;
  final int porcentajePb;

  /// `bajo|medio|alto`
  final String nivelRiesgo;
}

class Recomendacion {
  const Recomendacion({
    required this.idRecomendacion,
    required this.estatus,
    required this.explicacion,
    required this.distribucion,
    this.reglaSugerida,
    this.versionModelo,
    required this.disclaimer,
    this.perfilResultante,
    this.fecha,
  });

  final int idRecomendacion;

  /// `propuesta|aceptada|rechazada`
  final String estatus;
  final String explicacion;
  final List<ParticipacionInstrumento> distribucion;
  final SolicitudRegla? reglaSugerida;
  final String? versionModelo;
  final String disclaimer;
  final String? perfilResultante;
  final DateTime? fecha;
}

class Evaluacion {
  const Evaluacion({
    required this.idEvaluacion,
    required this.perfilResultante,
    required this.recomendacion,
  });

  final int idEvaluacion;

  /// `conservador|moderado|dinamico`
  final String perfilResultante;
  final Recomendacion recomendacion;
}

class DecisionRecomendacion {
  const DecisionRecomendacion({required this.estatus, this.reglaCreada});

  final String estatus;
  final Regla? reglaCreada;
}

abstract interface class AsistenteRepository {
  Future<Evaluacion> evaluar(Cuestionario cuestionario);

  /// Requiere step-up `pin`.
  Future<DecisionRecomendacion> decidir(
    int idRecomendacion, {
    required bool aceptar,
    required bool entendioExplicacion,
    int? idMeta,
    required String stepUp,
  });

  Future<List<Recomendacion>> historialRecomendaciones();
}
