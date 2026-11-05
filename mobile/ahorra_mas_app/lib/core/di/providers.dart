import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:http/http.dart' as http;
import 'package:url_launcher/url_launcher.dart';
import 'package:uuid/uuid.dart';

import '../../fake/fake_api.dart';
import '../../features/asistente/data/http_asistente_repository.dart';
import '../../features/asistente/domain/asistente.dart';
import '../../features/auth/data/http_auth_repository.dart';
import '../../features/auth/domain/auth_models.dart';
import '../../features/auth/domain/auth_repository.dart';
import '../../features/cofre/data/http_cofre_repository.dart';
import '../../features/cofre/domain/cofre_repository.dart';
import '../../features/consolidacion/data/http_consolidacion_repository.dart';
import '../../features/consolidacion/domain/consolidacion_repository.dart';
import '../../features/notificaciones/data/http_notificaciones_repository.dart';
import '../../features/notificaciones/domain/notificaciones.dart';
import '../../features/pagos/data/http_pagos_repository.dart';
import '../../features/pagos/domain/pagos_repository.dart';
import '../../features/puntos/data/http_puntos_repository.dart';
import '../../features/puntos/domain/puntos.dart';
import '../api/api_client.dart';
import '../config/app_config.dart';
import '../session/session_controller.dart';
import '../storage/almacen_seguro.dart';

/// Composición de dependencias (adaptadores reales o falsos según [AppConfig.fakeApi]).

final appConfigProvider = Provider<AppConfig>((ref) => AppConfig.desdeEntorno());

final almacenSeguroProvider = Provider<AlmacenSeguro>((ref) => AlmacenSeguroPlataforma());

final httpClientProvider = Provider<http.Client>((ref) {
  final c = http.Client();
  ref.onDispose(c.close);
  return c;
});

final apiClientProvider = Provider<ApiClient>((ref) {
  final config = ref.watch(appConfigProvider);
  return ApiClient(
    baseUrl: config.apiBaseUrl,
    httpClient: ref.watch(httpClientProvider),
    token: () => ref.read(sessionProvider)?.accessToken,
    alNoAutenticado: () => ref.read(sessionProvider.notifier).expirar(),
  );
});

/// API falsa compartida por todos los puertos en modo demo.
final fakeApiProvider = Provider<FakeApi>((ref) => FakeApi());

T _elegir<T>(Ref ref, T Function(ApiClient api) real) {
  if (ref.watch(appConfigProvider).fakeApi) return ref.watch(fakeApiProvider) as T;
  return real(ref.watch(apiClientProvider));
}

final authRepositoryProvider =
    Provider<AuthRepository>((ref) => _elegir<AuthRepository>(ref, HttpAuthRepository.new));

final consolidacionRepositoryProvider = Provider<ConsolidacionRepository>(
    (ref) => _elegir<ConsolidacionRepository>(ref, HttpConsolidacionRepository.new));

final cofreRepositoryProvider =
    Provider<CofreRepository>((ref) => _elegir<CofreRepository>(ref, HttpCofreRepository.new));

final pagosRepositoryProvider =
    Provider<PagosRepository>((ref) => _elegir<PagosRepository>(ref, HttpPagosRepository.new));

final puntosRepositoryProvider =
    Provider<PuntosRepository>((ref) => _elegir<PuntosRepository>(ref, HttpPuntosRepository.new));

final asistenteRepositoryProvider = Provider<AsistenteRepository>(
    (ref) => _elegir<AsistenteRepository>(ref, HttpAsistenteRepository.new));

final notificacionesRepositoryProvider = Provider<NotificacionesRepository>(
    (ref) => _elegir<NotificacionesRepository>(ref, HttpNotificacionesRepository.new));

final demoRepositoryProvider =
    Provider<DemoRepository>((ref) => _elegir<DemoRepository>(ref, HttpNotificacionesRepository.new));

/// Abre la página de autorización del banco. Ahorra+ nunca ve las claves del banco.
abstract interface class AbridorEnlaces {
  Future<bool> abrir(Uri url);
}

class AbridorEnlacesNavegador implements AbridorEnlaces {
  @override
  Future<bool> abrir(Uri url) => launchUrl(url, mode: LaunchMode.externalApplication);
}

/// En modo demo "abrir" el enlace simula que el usuario autorizó en su banco.
class AbridorEnlacesFalso implements AbridorEnlaces {
  AbridorEnlacesFalso(this._fake);

  final FakeApi _fake;

  @override
  Future<bool> abrir(Uri url) async {
    _fake.simularAutorizacion(url);
    return true;
  }
}

final abridorEnlacesProvider = Provider<AbridorEnlaces>((ref) {
  if (ref.watch(appConfigProvider).fakeApi) return AbridorEnlacesFalso(ref.watch(fakeApiProvider));
  return AbridorEnlacesNavegador();
});

/// Datos del equipo: la huella es un UUID estable guardado en el almacén seguro.
final dispositivoProvider = FutureProvider<DispositivoInfo>((ref) async {
  final almacen = ref.watch(almacenSeguroProvider);
  var huella = await almacen.leer(ClavesAlmacen.huella);
  if (huella == null) {
    huella = const Uuid().v4();
    await almacen.escribir(ClavesAlmacen.huella, huella);
  }
  final plataforma = kIsWeb
      ? 'Web'
      : switch (defaultTargetPlatform) {
          TargetPlatform.android => 'Android',
          TargetPlatform.iOS => 'iOS',
          TargetPlatform.windows => 'Windows',
          TargetPlatform.macOS => 'macOS',
          TargetPlatform.linux => 'Linux',
          TargetPlatform.fuchsia => 'Fuchsia',
        };
  return DispositivoInfo(huella: huella, plataforma: plataforma, modelo: 'Ahorra+ $plataforma');
});
