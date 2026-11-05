/// Modelos del Cofre Protector (M5): metas, Ruta del día, aportes, reglas, racha, sello y
/// Espejo de Impacto.
library;

enum CategoriaMeta {
  viaje('viaje', 'Viaje'),
  emergencia('emergencia', 'Emergencias'),
  estudio('estudio', 'Estudios'),
  salud('salud', 'Salud'),
  hogar('hogar', 'Hogar'),
  otro('otro', 'Otro');

  const CategoriaMeta(this.codigo, this.etiqueta);
  final String codigo;
  final String etiqueta;

  static CategoriaMeta desde(String? c) =>
      values.firstWhere((e) => e.codigo == c, orElse: () => otro);
}

enum FrecuenciaCuota {
  diaria('diaria', 'Diaria'),
  semanal('semanal', 'Semanal');

  const FrecuenciaCuota(this.codigo, this.etiqueta);
  final String codigo;
  final String etiqueta;

  static FrecuenciaCuota desde(String? c) => c == 'semanal' ? semanal : diaria;
}

class Sello {
  const Sello({
    required this.idSello,
    required this.montoSelladoUsdCts,
    required this.fechaLiberacion,
    required this.estatus,
  });

  final int idSello;
  final int montoSelladoUsdCts;
  final DateTime? fechaLiberacion;

  /// `activo|roto|liberado`
  final String estatus;

  bool get activo => estatus == 'activo';
}

class RutaDelDia {
  const RutaDelDia({
    required this.idMeta,
    required this.nombreMeta,
    required this.fecha,
    required this.montoSugeridoUsdCts,
    required this.montoSugeridoBsCts,
    required this.tasaValorBsX10000,
    required this.aportadoHoyUsdCts,
    required this.estatus,
    required this.periodosRestantes,
    required this.mensaje,
  });

  final int idMeta;
  final String nombreMeta;
  final DateTime? fecha;
  final int montoSugeridoUsdCts;
  final int montoSugeridoBsCts;
  final int tasaValorBsX10000;
  final int aportadoHoyUsdCts;

  /// `pendiente|cumplida|parcial|colchon|omitida`
  final String estatus;
  final int periodosRestantes;
  final String mensaje;

  bool get cumplida => estatus == 'cumplida';

  /// Lo que falta por apartar hoy para cumplir la cuota.
  int get faltanteHoyUsdCts {
    final f = montoSugeridoUsdCts - aportadoHoyUsdCts;
    return f < 0 ? 0 : f;
  }
}

class Meta {
  const Meta({
    required this.idMeta,
    required this.nombre,
    required this.categoria,
    required this.montoObjetivoUsdCts,
    required this.fechaObjetivo,
    required this.frecuenciaCuota,
    required this.estatus,
    required this.acumuladoUsdCts,
    required this.progresoPb,
    this.fechaCreacion,
    this.sello,
    this.cuotaHoy,
  });

  final int idMeta;
  final String nombre;
  final CategoriaMeta categoria;
  final int montoObjetivoUsdCts;
  final DateTime? fechaObjetivo;
  final FrecuenciaCuota frecuenciaCuota;

  /// `activa|pausada|cancelada|cumplida`
  final String estatus;
  final int acumuladoUsdCts;
  final int progresoPb;
  final DateTime? fechaCreacion;
  final Sello? sello;
  final RutaDelDia? cuotaHoy;

  bool get activa => estatus == 'activa';
  Sello? get selloActivo => (sello?.activo ?? false) ? sello : null;
}

class SolicitudMeta {
  const SolicitudMeta({
    required this.nombre,
    required this.categoria,
    required this.montoObjetivoUsdCts,
    required this.fechaObjetivo,
    required this.frecuenciaCuota,
  });

  final String nombre;
  final CategoriaMeta categoria;
  final int montoObjetivoUsdCts;
  final DateTime fechaObjetivo;
  final FrecuenciaCuota frecuenciaCuota;
}

class Aporte {
  const Aporte({
    required this.idAporte,
    required this.fechaHora,
    required this.tipo,
    required this.origen,
    required this.montoUsdCts,
    required this.montoBsCts,
  });

  final int idAporte;
  final DateTime? fechaHora;

  /// `aporte|retiro`
  final String tipo;

  /// `manual|cuota_del_dia|regla|...`
  final String? origen;
  final int montoUsdCts;
  final int? montoBsCts;

  bool get esRetiro => tipo == 'retiro';
}

class Racha {
  const Racha({
    required this.idRacha,
    required this.fechaInicio,
    required this.diasConsecutivos,
    required this.diasAporte,
    required this.diasColchonUsados,
    required this.colchonesDisponibles,
    required this.multiplicadorX100,
    required this.siguienteTramo,
    required this.aporteHoy,
    required this.ultimosDias,
  });

  static const vacia = Racha(
    idRacha: null,
    fechaInicio: null,
    diasConsecutivos: 0,
    diasAporte: 0,
    diasColchonUsados: 0,
    colchonesDisponibles: 0,
    multiplicadorX100: 100,
    siguienteTramo: null,
    aporteHoy: false,
    ultimosDias: [],
  );

  final int? idRacha;
  final DateTime? fechaInicio;
  final int diasConsecutivos;
  final int diasAporte;
  final int diasColchonUsados;
  final int colchonesDisponibles;
  final int multiplicadorX100;
  final TramoRacha? siguienteTramo;
  final bool aporteHoy;
  final List<DiaRacha> ultimosDias;
}

class TramoRacha {
  const TramoRacha({required this.diasMinimos, required this.multiplicadorX100});

  final int diasMinimos;
  final int multiplicadorX100;
}

enum TipoDiaRacha { aporte, colchon, sinAporte }

class DiaRacha {
  const DiaRacha({required this.fecha, required this.tipo});

  final DateTime fecha;
  final TipoDiaRacha tipo;
}

class ResultadoAporte {
  const ResultadoAporte({
    required this.aporte,
    required this.meta,
    this.racha,
    this.puntosGanados = 0,
    this.selloRoto = false,
  });

  final Aporte aporte;
  final Meta meta;
  final Racha? racha;
  final int puntosGanados;
  final bool selloRoto;
}

enum TipoRegla {
  pctIngreso('pct_ingreso', '% de cada ingreso'),
  redondeo('redondeo', 'Redondeo de gastos'),
  fijoQuincenal('fijo_quincenal', 'Monto fijo quincenal');

  const TipoRegla(this.codigo, this.etiqueta);
  final String codigo;
  final String etiqueta;

  static TipoRegla desde(String c) =>
      values.firstWhere((e) => e.codigo == c, orElse: () => pctIngreso);
}

class Regla {
  const Regla({
    required this.idRegla,
    required this.idMeta,
    required this.tipo,
    this.porcentajePb,
    this.multiploRedondeoCts,
    this.montoFijoUsdCts,
    required this.estatus,
    this.fechaAprobacion,
  });

  final int idRegla;
  final int idMeta;
  final TipoRegla tipo;
  final int? porcentajePb;
  final int? multiploRedondeoCts;
  final int? montoFijoUsdCts;

  /// `activa|pausada|finalizada`
  final String estatus;
  final DateTime? fechaAprobacion;

  bool get activa => estatus == 'activa';
}

/// Cuerpo para crear una regla (`POST /metas/{id}/reglas`). También la usa la IA
/// como `reglaSugerida`.
class SolicitudRegla {
  const SolicitudRegla.pctIngreso(int pb)
      : tipo = TipoRegla.pctIngreso,
        porcentajePb = pb,
        multiploRedondeoCts = null,
        montoFijoUsdCts = null;

  const SolicitudRegla.redondeo(int multiploCts)
      : tipo = TipoRegla.redondeo,
        porcentajePb = null,
        multiploRedondeoCts = multiploCts,
        montoFijoUsdCts = null;

  const SolicitudRegla.fijoQuincenal(int usdCts)
      : tipo = TipoRegla.fijoQuincenal,
        porcentajePb = null,
        multiploRedondeoCts = null,
        montoFijoUsdCts = usdCts;

  const SolicitudRegla({
    required this.tipo,
    this.porcentajePb,
    this.multiploRedondeoCts,
    this.montoFijoUsdCts,
  });

  final TipoRegla tipo;
  final int? porcentajePb;
  final int? multiploRedondeoCts;
  final int? montoFijoUsdCts;
}

class TasaFechada {
  const TasaFechada({required this.fecha, required this.valorBsX10000});

  final DateTime? fecha;
  final int valorBsX10000;
}

class EspejoImpacto {
  const EspejoImpacto({
    required this.anio,
    required this.mes,
    required this.provisional,
    required this.saldoCofresUsdCts,
    required this.perdidaEvitadaBsCts,
    required this.perdidaEvitadaUsdCts,
    required this.tasaInicio,
    required this.tasaFin,
    required this.devaluacionPb,
    required this.inflacionMensualPb,
    required this.totalBsCts,
    required this.totalUsdCts,
    required this.frase,
    required this.disclaimer,
  });

  final int anio;
  final int mes;
  final bool provisional;
  final int saldoCofresUsdCts;
  final int perdidaEvitadaBsCts;
  final int perdidaEvitadaUsdCts;
  final TasaFechada? tasaInicio;
  final TasaFechada? tasaFin;
  final int devaluacionPb;
  final int? inflacionMensualPb;
  final int totalBsCts;
  final int totalUsdCts;
  final String frase;
  final String disclaimer;
}
