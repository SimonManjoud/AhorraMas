import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/di/providers.dart';
import '../domain/consolidacion_models.dart';

final consolidadoProvider = FutureProvider.autoDispose<Consolidado>(
    (ref) => ref.watch(consolidacionRepositoryProvider).consolidado());

final institucionesProvider = FutureProvider.autoDispose<List<Institucion>>(
    (ref) => ref.watch(consolidacionRepositoryProvider).instituciones());

final consentimientosProvider = FutureProvider.autoDispose<List<Consentimiento>>(
    (ref) => ref.watch(consolidacionRepositoryProvider).consentimientos());

final movimientosCuentaProvider = FutureProvider.autoDispose.family<List<Movimiento>, int>(
    (ref, id) => ref.watch(consolidacionRepositoryProvider).movimientosCuenta(id));

final tasaHoyProvider =
    FutureProvider.autoDispose<Tasa>((ref) => ref.watch(consolidacionRepositoryProvider).tasaHoy());
