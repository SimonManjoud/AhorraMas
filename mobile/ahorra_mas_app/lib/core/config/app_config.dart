import 'package:flutter/foundation.dart';

/// Configuración de arranque de la app.
///
/// * `--dart-define=API_BASE_URL=http://192.168.1.10:8080/api/v1` cambia el backend.
/// * `--dart-define=FAKE_API=true` usa la API falsa en memoria (demo sin backend).
class AppConfig {
  const AppConfig({required this.apiBaseUrl, required this.fakeApi});

  final String apiBaseUrl;
  final bool fakeApi;

  static const bool _fakeApiDefine = bool.fromEnvironment('FAKE_API');
  static const String _baseUrlDefine = String.fromEnvironment('API_BASE_URL');

  /// Lee los `--dart-define` y aplica los valores por defecto por plataforma.
  factory AppConfig.desdeEntorno() {
    return AppConfig(apiBaseUrl: _baseUrlPorDefecto(), fakeApi: _fakeApiDefine);
  }

  static String _baseUrlPorDefecto() {
    if (_baseUrlDefine.isNotEmpty) return _baseUrlDefine;
    // El emulador de Android ve la máquina anfitriona en 10.0.2.2.
    if (!kIsWeb && defaultTargetPlatform == TargetPlatform.android) {
      return 'http://10.0.2.2:8080/api/v1';
    }
    return 'http://localhost:8080/api/v1';
  }
}
