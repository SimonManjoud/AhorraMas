import '../../../core/api/api_client.dart';
import '../../../core/util/fechas.dart';
import '../domain/cofre_models.dart';

class CofreDto {
  CofreDto._();

  static Sello sello(Map<String, dynamic> j) => Sello(
        idSello: j.entero('idSello'),
        montoSelladoUsdCts: j.enteroO('montoSelladoUsdCts', 0),
        fechaLiberacion: j.fecha('fechaLiberacion'),
        estatus: j.textoONulo('estatus') ?? 'activo',
      );

  static RutaDelDia ruta(Map<String, dynamic> j) => RutaDelDia(
        idMeta: j.entero('idMeta'),
        nombreMeta: j.textoONulo('nombreMeta') ?? '',
        fecha: j.fecha('fecha'),
        montoSugeridoUsdCts: j.enteroO('montoSugeridoUsdCts', 0),
        montoSugeridoBsCts: j.enteroO('montoSugeridoBsCts', 0),
        tasaValorBsX10000: j.enteroO('tasaValorBsX10000', 0),
        aportadoHoyUsdCts: j.enteroO('aportadoHoyUsdCts', 0),
        estatus: j.textoONulo('estatus') ?? 'pendiente',
        periodosRestantes: j.enteroO('periodosRestantes', 0),
        mensaje: j.textoONulo('mensaje') ?? '',
      );

  static Meta meta(Map<String, dynamic> j) => Meta(
        idMeta: j.entero('idMeta'),
        nombre: j.texto('nombre'),
        categoria: CategoriaMeta.desde(j.textoONulo('categoria')),
        montoObjetivoUsdCts: j.enteroO('montoObjetivoUsdCts', 0),
        fechaObjetivo: j.fecha('fechaObjetivo'),
        frecuenciaCuota: FrecuenciaCuota.desde(j.textoONulo('frecuenciaCuota')),
        estatus: j.textoONulo('estatus') ?? 'activa',
        acumuladoUsdCts: j.enteroO('acumuladoUsdCts', 0),
        progresoPb: j.enteroO('progresoPb', 0),
        fechaCreacion: j.fecha('fechaCreacion'),
        sello: j.mapa('sello') == null ? null : sello(j.mapa('sello')!),
        cuotaHoy: j.mapa('cuotaHoy') == null ? null : ruta(j.mapa('cuotaHoy')!),
      );

  static Map<String, dynamic> solicitudMeta(SolicitudMeta s) => {
        'nombre': s.nombre,
        'categoria': s.categoria.codigo,
        'montoObjetivoUsdCts': s.montoObjetivoUsdCts,
        'fechaObjetivo': Fechas.iso(s.fechaObjetivo),
        'frecuenciaCuota': s.frecuenciaCuota.codigo,
      };

  static Aporte aporte(Map<String, dynamic> j) => Aporte(
        idAporte: j.entero('idAporte'),
        fechaHora: j.fecha('fechaHora'),
        tipo: j.textoONulo('tipo') ?? 'aporte',
        origen: j.textoONulo('origen'),
        montoUsdCts: j.enteroO('montoUsdCts', 0),
        montoBsCts: j.enteroONulo('montoBsCts'),
      );

  static TipoDiaRacha _tipoDia(String? t) => switch (t) {
        'aporte' => TipoDiaRacha.aporte,
        'colchon' => TipoDiaRacha.colchon,
        _ => TipoDiaRacha.sinAporte,
      };

  static Racha racha(Map<String, dynamic>? j) {
    if (j == null) return Racha.vacia;
    final tramo = j.mapa('siguienteTramo');
    return Racha(
      idRacha: j.enteroONulo('idRacha'),
      fechaInicio: j.fecha('fechaInicio'),
      diasConsecutivos: j.enteroO('diasConsecutivos', 0),
      diasAporte: j.enteroO('diasAporte', 0),
      diasColchonUsados: j.enteroO('diasColchonUsados', 0),
      colchonesDisponibles: j.enteroO('colchonesDisponibles', 0),
      multiplicadorX100: j.enteroO('multiplicadorX100', 100),
      siguienteTramo: tramo == null
          ? null
          : TramoRacha(
              diasMinimos: tramo.entero('diasMinimos'),
              multiplicadorX100: tramo.entero('multiplicadorX100'),
            ),
      aporteHoy: j.booleano('aporteHoy'),
      ultimosDias: j.lista(
        'ultimosDias',
        (d) => DiaRacha(fecha: d.fecha('fecha')!, tipo: _tipoDia(d.textoONulo('tipo'))),
      ),
    );
  }

  static ResultadoAporte resultadoAporte(Map<String, dynamic> j) => ResultadoAporte(
        aporte: aporte(j.mapa('aporte')!),
        meta: meta(j.mapa('meta')!),
        racha: j.mapa('racha') == null ? null : racha(j.mapa('racha')),
        puntosGanados: j.enteroO('puntosGanados', 0),
        selloRoto: j.booleano('selloRoto'),
      );

  static Regla regla(Map<String, dynamic> j) => Regla(
        idRegla: j.entero('idRegla'),
        idMeta: j.enteroO('idMeta', 0),
        tipo: TipoRegla.desde(j.texto('tipo')),
        porcentajePb: j.enteroONulo('porcentajePb'),
        multiploRedondeoCts: j.enteroONulo('multiploRedondeoCts'),
        montoFijoUsdCts: j.enteroONulo('montoFijoUsdCts'),
        estatus: j.textoONulo('estatus') ?? 'activa',
        fechaAprobacion: j.fecha('fechaAprobacion'),
      );

  static SolicitudRegla solicitudRegla(Map<String, dynamic> j) => SolicitudRegla(
        tipo: TipoRegla.desde(j.texto('tipo')),
        porcentajePb: j.enteroONulo('porcentajePb'),
        multiploRedondeoCts: j.enteroONulo('multiploRedondeoCts'),
        montoFijoUsdCts: j.enteroONulo('montoFijoUsdCts'),
      );

  static Map<String, dynamic> solicitudReglaAJson(SolicitudRegla r) => {
        'tipo': r.tipo.codigo,
        if (r.porcentajePb != null) 'porcentajePb': r.porcentajePb,
        if (r.multiploRedondeoCts != null) 'multiploRedondeoCts': r.multiploRedondeoCts,
        if (r.montoFijoUsdCts != null) 'montoFijoUsdCts': r.montoFijoUsdCts,
      };

  static TasaFechada? _tasa(Map<String, dynamic>? j) => j == null
      ? null
      : TasaFechada(fecha: j.fecha('fecha'), valorBsX10000: j.entero('valorBsX10000'));

  static EspejoImpacto espejo(Map<String, dynamic> j) => EspejoImpacto(
        anio: j.entero('anio'),
        mes: j.entero('mes'),
        provisional: j.booleano('provisional'),
        saldoCofresUsdCts: j.enteroO('saldoCofresUsdCts', 0),
        perdidaEvitadaBsCts: j.enteroO('perdidaEvitadaBsCts', 0),
        perdidaEvitadaUsdCts: j.enteroO('perdidaEvitadaUsdCts', 0),
        tasaInicio: _tasa(j.mapa('tasaInicio')),
        tasaFin: _tasa(j.mapa('tasaFin')),
        devaluacionPb: j.enteroO('devaluacionPb', 0),
        inflacionMensualPb: j.enteroONulo('inflacionMensualPb'),
        totalBsCts: j.enteroO('totalBsCts', 0),
        totalUsdCts: j.enteroO('totalUsdCts', 0),
        frase: j.textoONulo('frase') ?? '',
        disclaimer: j.textoONulo('disclaimer') ??
            'Simulación informativa basada en la tasa oficial BCV; no constituye asesoría financiera.',
      );
}
