import 'consolidacion_models.dart';

/// Puerto de Consolidación multibanco (§2 del contrato).
abstract interface class ConsolidacionRepository {
  Future<List<Institucion>> instituciones();

  /// Selección múltiple: un consentimiento por institución.
  Future<List<ConsentimientoCreado>> crearConsentimientos(
    List<int> idsInstituciones, {
    required bool permiteDebitoTdc,
  });

  Future<List<Consentimiento>> consentimientos();

  Future<void> revocarConsentimiento(int idConsentimiento);

  Future<void> sincronizar();

  Future<Consolidado> consolidado();

  Future<void> renombrarCuenta(int idCuenta, String alias);

  Future<List<Movimiento>> movimientosCuenta(int idCuenta);

  Future<Tasa> tasaHoy();
}
