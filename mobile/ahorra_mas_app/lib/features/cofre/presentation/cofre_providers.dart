import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/di/providers.dart';
import '../domain/cofre_models.dart';

final metasProvider =
    FutureProvider.autoDispose<List<Meta>>((ref) => ref.watch(cofreRepositoryProvider).metas());

final metaProvider = FutureProvider.autoDispose.family<Meta, int>(
    (ref, id) => ref.watch(cofreRepositoryProvider).meta(id));

final rutasDelDiaProvider = FutureProvider.autoDispose<List<RutaDelDia>>(
    (ref) => ref.watch(cofreRepositoryProvider).rutasDelDia());

final aportesProvider = FutureProvider.autoDispose.family<List<Aporte>, int>(
    (ref, id) => ref.watch(cofreRepositoryProvider).aportes(id));

final reglasProvider = FutureProvider.autoDispose.family<List<Regla>, int>(
    (ref, id) => ref.watch(cofreRepositoryProvider).reglas(id));

final rachaProvider =
    FutureProvider.autoDispose<Racha>((ref) => ref.watch(cofreRepositoryProvider).racha());

/// Mes consultado en el Espejo de Impacto: (año, mes).
typedef MesAnio = ({int anio, int mes});

final espejoProvider = FutureProvider.autoDispose.family<EspejoImpacto, MesAnio?>(
    (ref, m) => ref.watch(cofreRepositoryProvider).espejoImpacto(anio: m?.anio, mes: m?.mes));

/// Refresca todo lo que cambia tras un aporte, retiro, sello o regla.
void refrescarCofre(WidgetRef ref, int idMeta) {
  ref.invalidate(metasProvider);
  ref.invalidate(metaProvider(idMeta));
  ref.invalidate(rutasDelDiaProvider);
  ref.invalidate(aportesProvider(idMeta));
  ref.invalidate(reglasProvider(idMeta));
  ref.invalidate(rachaProvider);
  ref.invalidate(espejoProvider);
}
