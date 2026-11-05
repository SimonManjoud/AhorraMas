import 'cofre_models.dart';

/// Puerto del Cofre Protector (§3 del contrato).
abstract interface class CofreRepository {
  Future<List<Meta>> metas();
  Future<Meta> meta(int idMeta);
  Future<Meta> crearMeta(SolicitudMeta solicitud);

  /// `pausada|activa|cancelada`
  Future<void> cambiarEstatusMeta(int idMeta, String estatus);

  Future<RutaDelDia> rutaDelDia(int idMeta);
  Future<List<RutaDelDia>> rutasDelDia();

  /// `origen`: `manual|cuota_del_dia`.
  Future<ResultadoAporte> aportar(
    int idMeta, {
    required int montoUsdCts,
    required String origen,
    int? idCuentaOrigen,
  });

  /// Requiere step-up `pin`.
  Future<ResultadoAporte> retirar(int idMeta, {required int montoUsdCts, required String stepUp});

  Future<List<Aporte>> aportes(int idMeta);

  /// Requiere step-up `pin`.
  Future<Regla> crearRegla(int idMeta, SolicitudRegla regla, {required String stepUp});
  Future<List<Regla>> reglas(int idMeta);
  Future<void> cambiarEstatusRegla(int idRegla, String estatus);

  Future<Racha> racha();

  /// Requiere step-up `pin`. Monto ≤ acumulado (R-12).
  Future<Sello> sellar(int idMeta, {required int montoUsdCts, required String stepUp});

  /// Requiere step-up `pin`. Rompe el sello: se pierde el multiplicador, nunca dinero.
  Future<Sello> romperSello(int idMeta, {required String stepUp});

  Future<EspejoImpacto> espejoImpacto({int? anio, int? mes});
}
