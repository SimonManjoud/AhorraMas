import 'dart:async';
import 'dart:convert';

import 'package:http/http.dart' as http;

import 'api_exception.dart';

typedef ProveedorToken = String? Function();

/// Cliente HTTP delgado del contrato REST del núcleo.
///
/// * Agrega `Authorization: Bearer <accessToken>` cuando `auth` es `true`.
/// * Convierte respuestas `4xx/5xx` en [ApiException] con `{codigo, mensaje, detalles}`.
/// * Reintenta **solo** las peticiones marcadas como `reintentable` cuando falla la red,
///   enviando exactamente los mismos headers (p. ej. la misma `Idempotency-Key`).
class ApiClient {
  ApiClient({
    required this.baseUrl,
    required http.Client httpClient,
    required ProveedorToken token,
    this.alNoAutenticado,
    this.reintentosRed = 1,
    this.timeout = const Duration(seconds: 20),
    this.esperaEntreReintentos = const Duration(milliseconds: 600),
  })  : _http = httpClient,
        _token = token; // ignore: prefer_initializing_formals

  final String baseUrl;
  final http.Client _http;
  final ProveedorToken _token;
  final void Function()? alNoAutenticado;
  final int reintentosRed;
  final Duration timeout;
  final Duration esperaEntreReintentos;

  Future<dynamic> get(
    String ruta, {
    Map<String, String>? query,
    Map<String, String>? headers,
    bool auth = true,
  }) =>
      _enviar('GET', ruta, query: query, headers: headers, auth: auth, reintentable: true);

  Future<dynamic> post(
    String ruta, {
    Object? cuerpo,
    Map<String, String>? headers,
    bool auth = true,
    bool reintentable = false,
  }) =>
      _enviar('POST', ruta,
          cuerpo: cuerpo, headers: headers, auth: auth, reintentable: reintentable);

  Future<dynamic> patch(String ruta, {Object? cuerpo, Map<String, String>? headers}) =>
      _enviar('PATCH', ruta, cuerpo: cuerpo, headers: headers);

  Future<dynamic> put(String ruta, {Object? cuerpo, Map<String, String>? headers}) =>
      _enviar('PUT', ruta, cuerpo: cuerpo, headers: headers);

  Future<dynamic> delete(String ruta, {Map<String, String>? headers}) =>
      _enviar('DELETE', ruta, headers: headers);

  Uri construirUri(String ruta, [Map<String, String>? query]) {
    final base = baseUrl.endsWith('/') ? baseUrl.substring(0, baseUrl.length - 1) : baseUrl;
    final r = ruta.startsWith('/') ? ruta : '/$ruta';
    final uri = Uri.parse('$base$r');
    return query == null || query.isEmpty ? uri : uri.replace(queryParameters: query);
  }

  Future<dynamic> _enviar(
    String metodo,
    String ruta, {
    Map<String, String>? query,
    Object? cuerpo,
    Map<String, String>? headers,
    bool auth = true,
    bool reintentable = false,
  }) async {
    final uri = construirUri(ruta, query);
    final todosHeaders = <String, String>{
      'Accept': 'application/json',
      if (cuerpo != null) 'Content-Type': 'application/json; charset=utf-8',
    };
    if (auth) {
      final t = _token();
      if (t != null) todosHeaders['Authorization'] = 'Bearer $t';
    }
    if (headers != null) todosHeaders.addAll(headers);
    final cuerpoJson = cuerpo == null ? null : jsonEncode(cuerpo);

    var intento = 0;
    while (true) {
      intento++;
      http.Response respuesta;
      try {
        final req = http.Request(metodo, uri)..headers.addAll(todosHeaders);
        if (cuerpoJson != null) req.body = cuerpoJson;
        final streamed = await _http.send(req).timeout(timeout);
        respuesta = await http.Response.fromStream(streamed).timeout(timeout);
      } on http.ClientException catch (_) {
        if (reintentable && intento <= reintentosRed) {
          await Future<void>.delayed(esperaEntreReintentos);
          continue;
        }
        throw const ApiException.red();
      } on TimeoutException catch (_) {
        if (reintentable && intento <= reintentosRed) {
          await Future<void>.delayed(esperaEntreReintentos);
          continue;
        }
        throw const ApiException.red('La operación tardó demasiado. Inténtalo de nuevo.');
      }
      // Un PIN errado en step-up o el token de saldo rápido no deben cerrar la sesión.
      return _procesar(respuesta, avisarSesion: auth && !ruta.startsWith('/auth/step-up'));
    }
  }

  dynamic _procesar(http.Response r, {required bool avisarSesion}) {
    final texto = r.bodyBytes.isEmpty ? '' : utf8.decode(r.bodyBytes, allowMalformed: true);
    Object? json;
    if (texto.isNotEmpty) {
      try {
        json = jsonDecode(texto);
      } on FormatException {
        json = null;
      }
    }
    if (r.statusCode >= 200 && r.statusCode < 300) return json;
    final error = ApiException.desdeCuerpo(r.statusCode, json);
    if (avisarSesion && r.statusCode == 401 && error.noAutenticado) alNoAutenticado?.call();
    throw error;
  }
}

/// Helpers de lectura de JSON tolerantes a `int`/`num`.
extension LecturaJson on Map<String, dynamic> {
  int entero(String k) => (this[k] as num).toInt();
  int? enteroONulo(String k) => (this[k] as num?)?.toInt();
  int enteroO(String k, int porDefecto) => (this[k] as num?)?.toInt() ?? porDefecto;
  String texto(String k) => this[k] as String;
  String? textoONulo(String k) => this[k] as String?;
  bool booleano(String k, [bool porDefecto = false]) => (this[k] as bool?) ?? porDefecto;
  DateTime? fecha(String k) {
    final v = this[k] as String?;
    return v == null ? null : DateTime.tryParse(v);
  }

  Map<String, dynamic>? mapa(String k) => this[k] as Map<String, dynamic>?;
  List<T> lista<T>(String k, T Function(Map<String, dynamic>) f) =>
      ((this[k] as List?) ?? const [])
          .map((e) => f(e as Map<String, dynamic>))
          .toList();
}

List<T> listaJson<T>(Object? json, T Function(Map<String, dynamic>) f) =>
    ((json as List?) ?? const []).map((e) => f(e as Map<String, dynamic>)).toList();
