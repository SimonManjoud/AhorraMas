import 'dart:convert';

import 'package:ahorra_mas_app/core/api/api_client.dart';
import 'package:ahorra_mas_app/core/api/api_exception.dart';
import 'package:ahorra_mas_app/fake/fake_api.dart';
import 'package:ahorra_mas_app/features/auth/domain/auth_models.dart';
import 'package:ahorra_mas_app/features/pagos/data/http_pagos_repository.dart';
import 'package:ahorra_mas_app/features/pagos/domain/intento_pago.dart';
import 'package:ahorra_mas_app/features/pagos/domain/pagos_models.dart';
import 'package:ahorra_mas_app/features/pagos/domain/pagos_repository.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

const _pagoJson = {
  'idPago': 21,
  'estado': 'CAPTURADO',
  'montoCts': 1500000,
  'comisionCts': 3000,
  'referenciaSuiche': 'S7B-1',
  'codigoAutorizacion': 'AUT1',
  'esPuntual': true,
  'motivoRechazo': null,
  'fechaHoraCreacion': '2026-11-04T10:00:00-04:00',
  'mensaje': 'Pago aplicado a tu TDC terminada en 7781',
  'historial': [],
};

const _solicitud = SolicitudPagoTdc(idTarjeta: 4, idCuentaOrigen: 7, montoCts: 1500000);

/// Repositorio que falla por red la primera vez y registra las claves recibidas.
class _RepoConFalloDeRed implements PagosRepository {
  final claves = <String>[];
  int fallosRestantes = 1;

  @override
  Future<Pago> pagar(SolicitudPagoTdc s, {required String idempotencyKey, required String stepUp}) async {
    claves.add(idempotencyKey);
    if (fallosRestantes-- > 0) throw const ApiException.red();
    return Pago(idPago: 21, estado: EstadoPago.capturado, montoCts: s.montoCts, comisionCts: 3000);
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

void main() {
  group('ApiClient + HttpPagosRepository', () {
    test('reintenta por red con la MISMA Idempotency-Key y headers de step-up', () async {
      final recibidas = <http.Request>[];
      final cliente = MockClient((req) async {
        recibidas.add(req);
        if (recibidas.length == 1) throw http.ClientException('conexión reiniciada');
        return http.Response(jsonEncode(_pagoJson), 201, headers: {'content-type': 'application/json'});
      });
      final api = ApiClient(
        baseUrl: 'http://localhost:8080/api/v1',
        httpClient: cliente,
        token: () => 'acceso',
        esperaEntreReintentos: Duration.zero,
      );

      final pago = await HttpPagosRepository(api)
          .pagar(_solicitud, idempotencyKey: 'clave-123', stepUp: 'stepup-otp');

      expect(pago.idPago, 21);
      expect(recibidas, hasLength(2));
      for (final r in recibidas) {
        expect(r.url.toString(), 'http://localhost:8080/api/v1/pagos-tdc');
        expect(r.headers['Idempotency-Key'], 'clave-123');
        expect(r.headers['X-Step-Up'], 'stepup-otp');
        expect(r.headers['Authorization'], 'Bearer acceso');
        expect(jsonDecode(r.body), {
          'idTarjeta': 4,
          'idCuentaOrigen': 7,
          'montoCts': 1500000,
          'beneficiarioConfirmado': true,
        });
      }
    });

    test('POST no idempotente no se reintenta solo', () async {
      var llamadas = 0;
      final api = ApiClient(
        baseUrl: 'http://x/api/v1',
        httpClient: MockClient((_) async {
          llamadas++;
          throw http.ClientException('sin red');
        }),
        token: () => null,
        esperaEntreReintentos: Duration.zero,
      );
      await expectLater(api.post('/metas', cuerpo: {}), throwsA(isA<ApiException>().having((e) => e.esDeRed, 'esDeRed', true)));
      expect(llamadas, 1);
    });

    test('convierte el cuerpo de error del contrato y avisa 401', () async {
      var expirada = false;
      final api = ApiClient(
        baseUrl: 'http://x/api/v1',
        httpClient: MockClient((_) async => http.Response(
            jsonEncode({'codigo': 'NO_AUTENTICADO', 'mensaje': 'Sesión vencida', 'detalles': {}}), 401,
            headers: {'content-type': 'application/json; charset=utf-8'})),
        token: () => 't',
        alNoAutenticado: () => expirada = true,
      );
      await expectLater(api.get('/me'), throwsA(isA<ApiException>().having((e) => e.mensaje, 'mensaje', 'Sesión vencida')));
      expect(expirada, isTrue);
    });
  });

  group('ServicioPagoTdc (una clave por intento)', () {
    test('el reintento manual tras un error de red reutiliza la clave', () async {
      final repo = _RepoConFalloDeRed();
      var n = 0;
      final servicio = ServicioPagoTdc(repo, generador: () => 'clave-${++n}');

      await expectLater(servicio.pagar(_solicitud, stepUp: 's'), throwsA(isA<ApiException>()));
      expect(ServicioPagoTdc.esReintentable(const ApiException.red()), isTrue);
      final pago = await servicio.pagar(_solicitud, stepUp: 's');

      expect(pago.estado, EstadoPago.capturado);
      expect(repo.claves, ['clave-1', 'clave-1']);
      expect(servicio.intentoActual!.envios, 2);
    });

    test('cambiar la solicitud o iniciar un nuevo intento genera otra clave', () async {
      final repo = _RepoConFalloDeRed()..fallosRestantes = 0;
      var n = 0;
      final servicio = ServicioPagoTdc(repo, generador: () => 'clave-${++n}');

      await servicio.pagar(_solicitud, stepUp: 's');
      await servicio.pagar(const SolicitudPagoTdc(idTarjeta: 4, idCuentaOrigen: 7, montoCts: 999), stepUp: 's');
      servicio.nuevoIntento();
      await servicio.pagar(const SolicitudPagoTdc(idTarjeta: 4, idCuentaOrigen: 7, montoCts: 999), stepUp: 's');

      expect(repo.claves, ['clave-1', 'clave-2', 'clave-3']);
    });

    test('la clave por defecto es un UUID v4 distinto por intento', () {
      final a = IntentoPago(_solicitud);
      final b = IntentoPago(_solicitud);
      expect(a.idempotencyKey, matches(RegExp(r'^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$')));
      expect(a.idempotencyKey, isNot(b.idempotencyKey));
    });
  });

  group('FakeApi respeta la idempotencia', () {
    test('misma clave ⇒ mismo pago, nunca duplica', () async {
      final fake = FakeApi(latencia: Duration.zero);
      await fake.login(
          nacionalidadDoc: 'V', cedula: FakeApi.cedulaDemo, pin: FakeApi.pinDemo, dispositivo: _dispositivo);
      final desafio = await fake.solicitarOtp('pago');
      final stepUp = await fake.stepUp(pin: FakeApi.pinDemo, idDesafio: desafio.idDesafio, otp: desafio.otpDemo);
      final cuenta = (await fake.cuentasOrigen()).first;
      final s = SolicitudPagoTdc(idTarjeta: 4, idCuentaOrigen: cuenta.idCuenta, montoCts: 100000);
      final antes = (await fake.pagos()).length;

      final p1 = await fake.pagar(s, idempotencyKey: 'k', stepUp: stepUp.token);
      final p2 = await fake.pagar(s, idempotencyKey: 'k', stepUp: stepUp.token);

      expect(p1.idPago, p2.idPago);
      expect((await fake.pagos()).length, antes + 1);
      final cuentaDespues = (await fake.cuentasOrigen()).firstWhere((c) => c.idCuenta == cuenta.idCuenta);
      expect(cuentaDespues.saldoCts, cuenta.saldoCts - 100000 - 200, reason: 'débito único con 0,2 % de comisión');
    });
  });
}

const _dispositivo = DispositivoInfo(huella: 'h', plataforma: 'Android', modelo: 'Pixel 8');
