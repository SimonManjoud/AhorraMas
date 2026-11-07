import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/di/providers.dart';
import '../domain/auth_models.dart';
import '../domain/recuerdo_equipo.dart';
import '../domain/saldo_rapido_service.dart';

final saldoRapidoServiceProvider = Provider<SaldoRapidoService>(
  (ref) => SaldoRapidoService(ref.watch(authRepositoryProvider), ref.watch(almacenSeguroProvider)),
);

/// ¿Hay token de saldo rápido guardado en este equipo?
final saldoRapidoActivoProvider =
    FutureProvider.autoDispose<bool>((ref) => ref.watch(saldoRapidoServiceProvider).activo());

final perfilProvider =
    FutureProvider.autoDispose<Perfil>((ref) => ref.watch(authRepositoryProvider).perfil());

final recuerdoEquipoServiceProvider =
    Provider<RecuerdoEquipoService>((ref) => RecuerdoEquipoService(ref.watch(almacenSeguroProvider)));
