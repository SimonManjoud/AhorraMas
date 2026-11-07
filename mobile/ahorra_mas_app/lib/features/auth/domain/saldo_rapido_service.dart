import 'dart:convert';

import '../../../core/api/api_exception.dart';
import '../../../core/storage/almacen_seguro.dart';
import 'auth_models.dart';
import 'auth_repository.dart';

/// Resultado de consultar el saldo rápido desde el login (F3).
class ConsultaSaldoRapido {
  const ConsultaSaldoRapido({required this.saldo, required this.desdeCache});

  final SaldoRapido saldo;

  /// `true` si no hubo conexión y se muestra el último valor guardado.
  final bool desdeCache;
}

/// Caso de uso del saldo rápido: guarda el token de solo lectura (`saldo:leer`) y la última
/// instantánea en el almacén seguro (Keychain/Keystore) y consulta sin iniciar sesión.
class SaldoRapidoService {
  SaldoRapidoService(this._repo, this._almacen);

  final AuthRepository _repo;
  final AlmacenSeguro _almacen;

  Future<bool> activo() async => (await _almacen.leer(ClavesAlmacen.tokenSaldo)) != null;

  Future<void> activar(String stepUpToken) async {
    final t = await _repo.activarSaldoRapido(stepUpToken);
    await _almacen.escribir(ClavesAlmacen.tokenSaldo, t.tokenSaldo);
    if (t.expiraEn != null) {
      await _almacen.escribir(ClavesAlmacen.tokenSaldoExpira, t.expiraEn!.toIso8601String());
    }
  }

  Future<void> desactivar({bool avisarServidor = true}) async {
    if (avisarServidor) {
      try {
        await _repo.desactivarSaldoRapido();
      } on ApiException catch (e) {
        if (!e.noAutenticado && !e.esDeRed) rethrow;
      }
    }
    await limpiarLocal();
  }

  Future<void> limpiarLocal() async {
    await _almacen.borrar(ClavesAlmacen.tokenSaldo);
    await _almacen.borrar(ClavesAlmacen.tokenSaldoExpira);
    await _almacen.borrar(ClavesAlmacen.saldoInstantanea);
  }

  /// Consulta `GET /saldo-rapido`. Si el token fue anulado (401/403) lo borra y devuelve
  /// `null`; si no hay red devuelve la última instantánea guardada.
  Future<ConsultaSaldoRapido?> consultar(String huella) async {
    final token = await _almacen.leer(ClavesAlmacen.tokenSaldo);
    if (token == null) return null;
    try {
      final s = await _repo.consultarSaldoRapido(tokenSaldo: token, huella: huella);
      await _almacen.escribir(
        ClavesAlmacen.saldoInstantanea,
        jsonEncode({
          'totalBsCts': s.totalBsCts,
          'totalUsdCts': s.totalUsdCts,
          'actualizadoEn': s.actualizadoEn?.toIso8601String(),
          'bancos': [for (final b in s.bancos) b.aJson()],
        }),
      );
      return ConsultaSaldoRapido(saldo: s, desdeCache: false);
    } on ApiException catch (e) {
      if (e.statusCode == 401 || e.statusCode == 403) {
        await limpiarLocal();
        return null;
      }
      final cache = await _instantanea();
      if (cache == null) rethrow;
      return ConsultaSaldoRapido(saldo: cache, desdeCache: true);
    }
  }

  Future<SaldoRapido?> _instantanea() async {
    final raw = await _almacen.leer(ClavesAlmacen.saldoInstantanea);
    if (raw == null) return null;
    final j = jsonDecode(raw) as Map<String, dynamic>;
    return SaldoRapido(
      totalBsCts: (j['totalBsCts'] as num).toInt(),
      totalUsdCts: (j['totalUsdCts'] as num).toInt(),
      actualizadoEn: j['actualizadoEn'] == null ? null : DateTime.tryParse(j['actualizadoEn'] as String),
      bancos: [
        for (final b in (j['bancos'] as List?) ?? const []) SaldoBanco.desdeJson((b as Map).cast<String, dynamic>()),
      ],
    );
  }
}
