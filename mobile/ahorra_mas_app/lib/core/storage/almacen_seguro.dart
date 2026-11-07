import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// Almacén clave–valor seguro (Keychain/Keystore). Abstraído para poder probar sin plugins.
abstract interface class AlmacenSeguro {
  Future<String?> leer(String clave);
  Future<void> escribir(String clave, String valor);
  Future<void> borrar(String clave);
}

class AlmacenSeguroPlataforma implements AlmacenSeguro {
  AlmacenSeguroPlataforma([FlutterSecureStorage? storage])
      : _s = storage ?? const FlutterSecureStorage();

  final FlutterSecureStorage _s;

  @override
  Future<String?> leer(String clave) async {
    try {
      return await _s.read(key: clave);
    } catch (_) {
      return null; // p. ej. llavero inaccesible: se trata como vacío.
    }
  }

  @override
  Future<void> escribir(String clave, String valor) => _s.write(key: clave, value: valor);

  @override
  Future<void> borrar(String clave) => _s.delete(key: clave);
}

/// Implementación en memoria (pruebas y entornos sin almacenamiento seguro).
class AlmacenEnMemoria implements AlmacenSeguro {
  AlmacenEnMemoria([Map<String, String>? inicial]) : datos = {...?inicial};

  final Map<String, String> datos;

  @override
  Future<String?> leer(String clave) async => datos[clave];

  @override
  Future<void> escribir(String clave, String valor) async => datos[clave] = valor;

  @override
  Future<void> borrar(String clave) async => datos.remove(clave);
}

/// Claves usadas en el almacén.
class ClavesAlmacen {
  static const huella = 'dispositivo.huella';
  static const ultimaNacionalidad = 'login.nacionalidad';
  static const ultimaCedula = 'login.cedula';
  static const tokenSaldo = 'saldoRapido.token';
  static const tokenSaldoExpira = 'saldoRapido.expira';
  static const saldoInstantanea = 'saldoRapido.instantanea';

  /// Nombre y bancos del último usuario que ingresó en este equipo (sin montos).
  static const recuerdoEquipo = 'login.recuerdoEquipo';
}
