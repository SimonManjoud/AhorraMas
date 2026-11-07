import 'dart:convert';

import 'package:ahorra_mas_app/core/api/api_exception.dart';
import 'package:ahorra_mas_app/features/asistente/data/http_asistente_repository.dart';
import 'package:ahorra_mas_app/features/auth/data/auth_dto.dart';
import 'package:ahorra_mas_app/features/auth/domain/auth_models.dart';
import 'package:ahorra_mas_app/features/cofre/data/cofre_dto.dart';
import 'package:ahorra_mas_app/features/cofre/domain/cofre_models.dart';
import 'package:ahorra_mas_app/features/consolidacion/data/consolidacion_dto.dart';
import 'package:ahorra_mas_app/features/pagos/data/pagos_dto.dart';
import 'package:ahorra_mas_app/features/pagos/domain/pagos_models.dart';
import 'package:ahorra_mas_app/features/puntos/data/http_puntos_repository.dart';
import 'package:flutter_test/flutter_test.dart';

/// Ejemplos JSON copiados de `docs/contrato-api.md` (los "..." se reemplazaron por fechas ISO).
Map<String, dynamic> j(String s) => jsonDecode(s) as Map<String, dynamic>;

void main() {
  test('§1.2 Sesion', () {
    final s = AuthDto.sesion(j('''
      {"accessToken": "eyJ...", "expiraEn": "2026-11-04T11:15:00-04:00",
       "usuario": {"idUsuario": 1, "primerNombre": "María", "primerApellido": "Pérez",
                   "kycAprobado": false, "estatus": "activo"},
       "idDispositivo": 3}'''));
    expect(s.accessToken, 'eyJ...');
    expect(s.usuario.primerNombre, 'María');
    expect(s.usuario.kycAprobado, isFalse);
    expect(s.idDispositivo, 3);
    expect(s.expiraEn!.toUtc(), DateTime.utc(2026, 11, 4, 15, 15));
  });

  test('§1.4 OTP y step-up', () {
    final d = AuthDto.desafio(j('{"idDesafio": "uuid", "expiraEn": "2026-11-04T11:15:00-04:00", "otpDemo": "482913"}'));
    expect(d.otpDemo, '482913');
    final s = AuthDto.stepUp(j('{"stepUpToken": "eyJ...", "nivel": "pin_otp", "expiraEn": "2026-11-04T11:15:00-04:00"}'));
    expect(s.nivel, NivelStepUp.pinOtp);
  });

  test('§1.5 KYC y §1.6 saldo rápido', () {
    final k = AuthDto.kyc(j('''{"idVerificacion": 1, "resultado": "aprobado", "pruebaVidaSuperada": true,
        "coincideListaSanciones": false, "motivoRechazo": null}'''));
    expect(k.aprobado, isTrue);
    final sr = AuthDto.saldoRapido(
        j('{"totalBsCts": 152030044, "totalUsdCts": 304512, "actualizadoEn": "2026-11-04T10:00:00-04:00"}'));
    expect(sr.totalBsCts, 152030044);
    expect(sr.totalUsdCts, 304512);
    expect(sr.bancos, isEmpty, reason: 'bancos es opcional (servidores anteriores)');
    final conBancos = AuthDto.saldoRapido(j('''{"totalBsCts": 100, "totalUsdCts": 2, "actualizadoEn": null,
        "bancos": [{"nombre": "Banco Ávila", "tipo": "banco", "totalBsCts": 60, "totalUsdCts": 1},
                   {"nombre": "CriptoAndes Exchange", "tipo": "plataforma_cripto", "totalBsCts": 40, "totalUsdCts": 1}]}'''));
    expect(conBancos.bancos.map((b) => b.nombre), ['Banco Ávila', 'CriptoAndes Exchange']);
    expect(conBancos.bancos.last.esCripto, isTrue);
  });

  test('§1.1 registro serializa al contrato', () {
    final m = AuthDto.registroAJson(SolicitudRegistro(
      nacionalidadDoc: 'V',
      cedula: 12345678,
      primerNombre: 'María',
      primerApellido: 'Pérez',
      fechaNacimiento: DateTime(1995, 4, 12),
      correo: 'maria@correo.com',
      telefonoMovil: '04141234567',
      pin: '1234',
      dispositivo: const DispositivoInfo(huella: 'h', plataforma: 'Android', modelo: 'Pixel 8'),
    ));
    expect(m['fechaNacimiento'], '1995-04-12');
    expect(m['segundoNombre'], isNull);
    expect((m['dispositivo'] as Map)['huella'], 'h');
  });

  test('§2 Consolidado', () {
    final c = ConsolidacionDto.consolidado(j('''
      {"tasa": {"fecha": "2026-11-04", "valorBsX10000": 4987321},
       "totalBsCts": 152030044, "totalUsdCts": 304831, "actualizadoEn": "2026-11-04T10:00:00-04:00",
       "instituciones": [{"idInstitucion": 1, "nombre": "Banco Ávila", "tipo": "banco",
          "totalBsCts": 100000000, "totalUsdCts": 200500,
          "cuentas": [{"idCuenta": 7, "alias": "Nómina", "ultimos4": "4821", "tipo": "corriente",
                       "moneda": "VES", "saldoCts": 1543022, "saldoBsCts": 1543022, "saldoUsdCts": 3094,
                       "soloLectura": false}]}]}'''));
    expect(c.tasa.valorBsX10000, 4987321);
    expect(c.totalBsCts, 152030044);
    expect(c.instituciones.single.cuentas.single.saldoUsdCts, 3094);
    expect(c.cuentas.single.alias, 'Nómina');
  });

  test('§2 consentimientos', () {
    final creado = ConsolidacionDto.consentimientoCreado(j('''{"idConsentimiento": 10, "idInstitucion": 1,
        "institucion": "Banco Ávila", "estatus": "pendiente",
        "urlAutorizacion": "http://localhost:8090/ob/0901/oauth/authorize?x=1"}'''));
    expect(creado.urlAutorizacion, startsWith('http://localhost:8090'));
    final c = ConsolidacionDto.consentimiento(j('''{"idConsentimiento": 10, "idInstitucion": 1,
        "institucion": "Banco Ávila", "estatus": "activo", "permiteLectura": true, "permiteDebitoTdc": true,
        "fechaOtorgamiento": "2026-11-04T10:00:00-04:00", "fechaVencimiento": "2027-05-03"}'''));
    expect(c.activo, isTrue);
  });

  const rutaJson = '''{"idMeta": 1, "nombreMeta": "Viaje en marzo", "fecha": "2026-11-04",
     "montoSugeridoUsdCts": 240, "montoSugeridoBsCts": 11969, "tasaValorBsX10000": 4987321,
     "aportadoHoyUsdCts": 0, "estatus": "pendiente",
     "periodosRestantes": 131, "mensaje": "Hoy toca apartar \$2.40 (Bs 119,69) para «Viaje en marzo»"}''';

  const metaJson = '''{"idMeta": 1, "nombre": "Viaje en marzo", "categoria": "viaje", "montoObjetivoUsdCts": 30000,
     "fechaObjetivo": "2027-03-15", "frecuenciaCuota": "diaria", "estatus": "activa",
     "acumuladoUsdCts": 1200, "progresoPb": 400, "fechaCreacion": "2026-10-20T09:00:00-04:00",
     "sello": {"idSello": 2, "montoSelladoUsdCts": 1000, "fechaLiberacion": "2027-03-15", "estatus": "activo"},
     "cuotaHoy": $rutaJson}''';

  test('§3.1 Meta con sello y cuotaHoy', () {
    final m = CofreDto.meta(j(metaJson));
    expect(m.categoria, CategoriaMeta.viaje);
    expect(m.frecuenciaCuota, FrecuenciaCuota.diaria);
    expect(m.progresoPb, 400);
    expect(m.selloActivo!.montoSelladoUsdCts, 1000);
    expect(m.cuotaHoy!.montoSugeridoUsdCts, 240);
    expect(m.cuotaHoy!.faltanteHoyUsdCts, 240);
  });

  test('§3.1 Meta sin sello (null)', () {
    final m = CofreDto.meta(j(metaJson.replaceFirst(RegExp(r'"sello": \{[^}]*\}'), '"sello": null')));
    expect(m.sello, isNull);
  });

  test('§3.3 aporte', () {
    final r = CofreDto.resultadoAporte(j('''{"aporte": {"idAporte": 15, "fechaHora": "2026-11-04T10:00:00-04:00",
        "tipo": "aporte", "origen": "cuota_del_dia", "montoUsdCts": 240, "montoBsCts": 11969},
        "meta": $metaJson,
        "racha": {"idRacha": 3, "fechaInicio": "2026-10-20", "diasConsecutivos": 16, "diasAporte": 14,
          "diasColchonUsados": 2, "colchonesDisponibles": 1, "multiplicadorX100": 125,
          "siguienteTramo": {"diasMinimos": 30, "multiplicadorX100": 150}, "aporteHoy": true,
          "ultimosDias": [{"fecha": "2026-11-04", "tipo": "aporte"}]},
        "puntosGanados": 36}'''));
    expect(r.aporte.origen, 'cuota_del_dia');
    expect(r.puntosGanados, 36);
    expect(r.racha!.multiplicadorX100, 125);
    expect(r.racha!.siguienteTramo!.diasMinimos, 30);
    expect(r.racha!.ultimosDias.single.tipo, TipoDiaRacha.aporte);
    expect(r.selloRoto, isFalse);
  });

  test('§3.4 regla y serialización de los 3 tipos', () {
    final r = CofreDto.regla(j('''{"idRegla": 4, "idMeta": 1, "tipo": "redondeo", "porcentajePb": null,
        "multiploRedondeoCts": 10000, "montoFijoUsdCts": null, "estatus": "activa",
        "fechaAprobacion": "2026-11-04T10:00:00-04:00"}'''));
    expect(r.tipo, TipoRegla.redondeo);
    expect(r.multiploRedondeoCts, 10000);
    expect(CofreDto.solicitudReglaAJson(const SolicitudRegla.pctIngreso(1000)),
        {'tipo': 'pct_ingreso', 'porcentajePb': 1000});
    expect(CofreDto.solicitudReglaAJson(const SolicitudRegla.redondeo(10000)),
        {'tipo': 'redondeo', 'multiploRedondeoCts': 10000});
    expect(CofreDto.solicitudReglaAJson(const SolicitudRegla.fijoQuincenal(2500)),
        {'tipo': 'fijo_quincenal', 'montoFijoUsdCts': 2500});
  });

  test('§3.5 racha nula ⇒ vacía', () {
    expect(CofreDto.racha(null).diasConsecutivos, 0);
  });

  test('§3.7 Espejo de Impacto', () {
    final e = CofreDto.espejo(j('''{"anio": 2026, "mes": 11, "provisional": true,
       "saldoCofresUsdCts": 12000, "perdidaEvitadaBsCts": 4528800, "perdidaEvitadaUsdCts": 908,
       "tasaInicio": {"fecha": "2026-11-01", "valorBsX10000": 4612000},
       "tasaFin": {"fecha": "2026-11-04", "valorBsX10000": 4987321},
       "devaluacionPb": 814, "inflacionMensualPb": 1240,
       "totalBsCts": 152030044, "totalUsdCts": 304831,
       "frase": "Tu cofre de \$120,00 te protegió de perder Bs 45.288,00 este mes.",
       "disclaimer": "Simulación informativa basada en la tasa oficial BCV; no constituye asesoría financiera."}'''));
    expect(e.provisional, isTrue);
    expect(e.devaluacionPb, 814);
    expect(e.tasaFin!.valorBsX10000, 4987321);
    expect(e.frase, contains('te protegió'));
  });

  test('§4 tarjeta, cotización y pago con historial', () {
    final t = PagosDto.tarjeta(j('''{"idTarjeta": 4, "idInstitucion": 2, "banco": "Banco Orinoco", "ultimos4": "7781",
        "alias": null, "titularConfirmado": "MARIA PEREZ", "esPropia": true,
        "estadoCuenta": {"fechaCorte": "2026-10-28", "fechaLimitePago": "2026-11-13",
                         "saldoTotalCts": 8754320, "pagoMinimoCts": 1312000}}'''));
    expect(t.estadoCuenta!.pagoMinimoCts, 1312000);
    expect(t.marca, isNull, reason: 'sin marca el emisor no la informó');
    expect(PagosDto.tarjeta(j('{"idTarjeta": 5, "ultimos4": "3310", "marca": "VISA"}')).marca, 'visa');
    final c = PagosDto.cotizacion(j('{"montoCts":1500000,"comisionCts":3000,"totalCts":1503000}'));
    expect(c.totalCts, 1503000);
    final p = PagosDto.pago(j('''{"idPago": 21, "estado": "CAPTURADO", "montoCts": 1500000, "comisionCts": 3000,
       "referenciaSuiche": "S7B-1", "codigoAutorizacion": "AUT1", "esPuntual": true,
       "motivoRechazo": null, "fechaHoraCreacion": "2026-11-04T10:00:00-04:00",
       "mensaje": "Pago aplicado a tu TDC terminada en 7781",
       "historial": [{"secuencia": 2, "estadoAnterior": "PENDIENTE", "estadoNuevo": "CAPTURADO", "fechaHora": "2026-11-04T10:00:02-04:00", "actor": "suiche"},
                     {"secuencia": 1, "estadoAnterior": null, "estadoNuevo": "PENDIENTE", "fechaHora": "2026-11-04T10:00:00-04:00", "actor": "usuario"}]}'''));
    expect(p.estado, EstadoPago.capturado);
    expect(p.estado.exitoso, isTrue);
    expect(p.historial.first.estadoNuevo, EstadoPago.pendiente, reason: 'ordenado por secuencia');
    expect(p.historial.first.estadoAnterior, isNull);
    expect(EstadoPago.desde('EN_VERIFICACION').enProceso, isTrue);
    expect(EstadoPago.desde('EN_VERIFICACION').fallido, isFalse);
    final l = PagosDto.limites(
        j('{"montoMaxDiarioCts": 50000000, "maxOperacionesDiarias": 5, "usadoHoyCts": 1500000, "operacionesHoy": 1}'));
    expect(l.disponibleHoyCts, 48500000);
  });

  test('§5 puntos', () {
    final r = PuntosDto.resumen(j('''{"saldo": 1240, "puntosGanadosTotal": 1500,
        "nivel": {"nombre": "Base", "desde": "2026-09-01"},
        "siguienteNivel": {"nombre": "Gold", "puntosMinimos": 5000, "puntosFaltantes": 3500}}'''));
    expect(r.nivel, 'Base');
    expect(r.progresoSiguiente, closeTo(0.3, 1e-9));
  });

  test('§6 evaluación IA', () {
    final e = AsistenteDto.evaluacion(j('''{"idEvaluacion": 2, "perfilResultante": "conservador",
       "recomendacion": {"idRecomendacion": 5, "estatus": "propuesta",
          "explicacion": "Texto redactado por el LLM con datos seudonimizados...",
          "distribucion": [{"instrumento": "USD", "porcentajePb": 6000, "nivelRiesgo": "bajo"}],
          "reglaSugerida": {"tipo": "pct_ingreso", "porcentajePb": 1000},
          "versionModelo": "reglas-1.2 / modelo-0.3",
          "disclaimer": "Recomendación orientativa..."}}'''));
    expect(e.perfilResultante, 'conservador');
    expect(e.recomendacion.distribucion.single.porcentajePb, 6000);
    expect(e.recomendacion.reglaSugerida!.tipo, TipoRegla.pctIngreso);
    expect(e.recomendacion.versionModelo, 'reglas-1.2 / modelo-0.3');
  });

  test('error {codigo, mensaje, detalles}', () {
    final e = ApiException.desdeCuerpo(
        422, j('{"codigo": "LIMITE_EXCEDIDO", "mensaje": "Texto para mostrar al usuario", "detalles": {"x": 1}}'));
    expect(e.codigo, 'LIMITE_EXCEDIDO');
    expect(e.mensaje, 'Texto para mostrar al usuario');
    expect(e.detalles['x'], 1);
    expect(ApiException.desdeCuerpo(502, null).mensaje, contains('no está disponible'));
  });
}
