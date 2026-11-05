import 'package:ahorra_mas_app/core/api/api_exception.dart';
import 'package:ahorra_mas_app/core/money/dinero.dart';
import 'package:ahorra_mas_app/fake/fake_api.dart';
import 'package:ahorra_mas_app/features/auth/domain/auth_models.dart';
import 'package:ahorra_mas_app/features/pagos/domain/pagos_models.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:intl/date_symbol_data_local.dart';

const _disp = DispositivoInfo(huella: 'h', plataforma: 'Android', modelo: 'Pixel 8');

Future<FakeApi> _conSesion() async {
  final f = FakeApi(latencia: Duration.zero, ahora: DateTime(2026, 11, 4, 10));
  await f.login(nacionalidadDoc: 'V', cedula: FakeApi.cedulaDemo, pin: FakeApi.pinDemo, dispositivo: _disp);
  return f;
}

void main() {
  setUpAll(() => initializeDateFormatting('es'));

  test('login con PIN incorrecto informa intentos restantes', () async {
    final f = FakeApi(latencia: Duration.zero);
    await expectLater(
      f.login(nacionalidadDoc: 'V', cedula: FakeApi.cedulaDemo, pin: '9999', dispositivo: _disp),
      throwsA(isA<ApiException>().having((e) => e.mensaje, 'mensaje', contains('2 intentos'))),
    );
  });

  test('Ruta del día: (objetivo − acumulado) / periodos, redondeo hacia arriba (R-09)', () async {
    final f = await _conSesion();
    final meta = (await f.metas()).first;
    final r = meta.cuotaHoy!;
    final faltante = meta.montoObjetivoUsdCts - meta.acumuladoUsdCts + r.aportadoHoyUsdCts;
    expect(r.montoSugeridoUsdCts, (faltante + r.periodosRestantes - 1) ~/ r.periodosRestantes);
    expect(r.montoSugeridoBsCts, Dinero.usdABs(r.montoSugeridoUsdCts, r.tasaValorBsX10000));
    expect(r.mensaje, startsWith('Hoy toca apartar'));
  });

  test('aportar la cuota del día la cumple y suma racha y puntos', () async {
    final f = await _conSesion();
    final meta = (await f.metas()).first;
    final antes = await f.racha();
    final r = await f.aportar(meta.idMeta, montoUsdCts: meta.cuotaHoy!.montoSugeridoUsdCts, origen: 'cuota_del_dia');
    expect(r.meta.cuotaHoy!.estatus, 'cumplida');
    expect(r.racha!.diasConsecutivos, antes.diasConsecutivos + 1);
    expect(r.racha!.aporteHoy, isTrue);
    expect(r.puntosGanados, greaterThan(0));
  });

  test('sello ≤ acumulado y requiere step-up', () async {
    final f = await _conSesion();
    final meta = (await f.metas()).last;
    final pin = await f.stepUp(pin: FakeApi.pinDemo);
    await expectLater(
      f.sellar(meta.idMeta, montoUsdCts: meta.acumuladoUsdCts + 1, stepUp: pin.token),
      throwsA(isA<ApiException>().having((e) => e.codigo, 'codigo', 'REGLA_NEGOCIO')),
    );
    await expectLater(
      f.sellar(meta.idMeta, montoUsdCts: 100, stepUp: 'token-invalido'),
      throwsA(isA<ApiException>().having((e) => e.stepUpRequerido, 'stepUp', isTrue)),
    );
    final s = await f.sellar(meta.idMeta, montoUsdCts: meta.acumuladoUsdCts, stepUp: pin.token);
    expect(s.activo, isTrue);
  });

  test('retirar por debajo del sello lo rompe: se pierde el multiplicador, no el dinero', () async {
    final f = await _conSesion();
    final meta = (await f.metas()).first; // "Viaje en marzo" con sello de $50
    final pin = await f.stepUp(pin: FakeApi.pinDemo);
    final r = await f.retirar(meta.idMeta, montoUsdCts: meta.acumuladoUsdCts - 100, stepUp: pin.token);
    expect(r.selloRoto, isTrue);
    expect(r.meta.acumuladoUsdCts, 100);
    expect((await f.racha()).diasConsecutivos, 0);
    expect((await f.racha()).multiplicadorX100, 100);
  });

  test('pago con céntimos 77 queda EN_VERIFICACION y luego se captura', () async {
    final f = await _conSesion();
    final d = await f.solicitarOtp('pago');
    final s = await f.stepUp(pin: FakeApi.pinDemo, idDesafio: d.idDesafio, otp: d.otpDemo);
    final cuenta = (await f.cuentasOrigen()).first;
    final p = await f.pagar(
      SolicitudPagoTdc(idTarjeta: 4, idCuentaOrigen: cuenta.idCuenta, montoCts: 100077),
      idempotencyKey: 'k-77',
      stepUp: s.token,
    );
    expect(p.estado, EstadoPago.enVerificacion);
    await f.pago(p.idPago);
    expect((await f.pago(p.idPago)).estado, EstadoPago.capturado);
  });

  test('pago sin step-up pin_otp es rechazado', () async {
    final f = await _conSesion();
    final soloPin = await f.stepUp(pin: FakeApi.pinDemo);
    await expectLater(
      f.pagar(const SolicitudPagoTdc(idTarjeta: 4, idCuentaOrigen: 1, montoCts: 100),
          idempotencyKey: 'x', stepUp: soloPin.token),
      throwsA(isA<ApiException>().having((e) => e.codigo, 'codigo', 'STEP_UP_REQUERIDO')),
    );
  });

  test('día colchón: avanzar el reloj sin aportar mantiene la racha', () async {
    final f = await _conSesion();
    final antes = await f.racha();
    await f.avanzarReloj(1);
    final despues = await f.racha();
    expect(despues.diasConsecutivos, antes.diasConsecutivos + 1);
    expect(despues.colchonesDisponibles, antes.colchonesDisponibles - 1);
  });

  test('Espejo de Impacto: frase y pérdida evitada coherentes', () async {
    final f = await _conSesion();
    final e = await f.espejoImpacto();
    expect(e.provisional, isTrue);
    expect(e.perdidaEvitadaBsCts, greaterThan(0));
    expect(e.perdidaEvitadaUsdCts, Dinero.bsAUsd(e.perdidaEvitadaBsCts, e.tasaFin!.valorBsX10000));
    expect(e.frase, contains(Dinero.bs(e.perdidaEvitadaBsCts)));
    expect(e.disclaimer, contains('no constituye asesoría'));
  });
}
