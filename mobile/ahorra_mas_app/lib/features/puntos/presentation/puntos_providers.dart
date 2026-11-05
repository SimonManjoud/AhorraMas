import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/di/providers.dart';
import '../domain/puntos.dart';

final resumenPuntosProvider = FutureProvider.autoDispose<ResumenPuntos>(
    (ref) => ref.watch(puntosRepositoryProvider).resumenPuntos());

final movimientosPuntosProvider = FutureProvider.autoDispose<List<MovimientoPuntos>>(
    (ref) => ref.watch(puntosRepositoryProvider).movimientosPuntos());

final recompensasProvider = FutureProvider.autoDispose<List<Recompensa>>(
    (ref) => ref.watch(puntosRepositoryProvider).recompensas());

final canjesProvider =
    FutureProvider.autoDispose<List<Canje>>((ref) => ref.watch(puntosRepositoryProvider).canjes());

void refrescarPuntos(WidgetRef ref) {
  ref.invalidate(resumenPuntosProvider);
  ref.invalidate(movimientosPuntosProvider);
  ref.invalidate(canjesProvider);
}
