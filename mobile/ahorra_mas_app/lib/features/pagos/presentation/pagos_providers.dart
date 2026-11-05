import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/di/providers.dart';
import '../domain/pagos_models.dart';

final tarjetasProvider =
    FutureProvider.autoDispose<List<Tarjeta>>((ref) => ref.watch(pagosRepositoryProvider).tarjetas());

final cuentasOrigenProvider = FutureProvider.autoDispose<List<CuentaOrigen>>(
    (ref) => ref.watch(pagosRepositoryProvider).cuentasOrigen());

final pagosProvider =
    FutureProvider.autoDispose<List<Pago>>((ref) => ref.watch(pagosRepositoryProvider).pagos());

final pagoProvider = FutureProvider.autoDispose.family<Pago, int>(
    (ref, id) => ref.watch(pagosRepositoryProvider).pago(id));

final limitesProvider =
    FutureProvider.autoDispose<Limites>((ref) => ref.watch(pagosRepositoryProvider).limites());
