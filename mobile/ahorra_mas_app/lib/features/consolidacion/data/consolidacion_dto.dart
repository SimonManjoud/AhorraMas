import '../../../core/api/api_client.dart';
import '../domain/consolidacion_models.dart';

class ConsolidacionDto {
  ConsolidacionDto._();

  static Tasa tasa(Map<String, dynamic> j) => Tasa(
        fecha: j.fecha('fecha'),
        valorBsX10000: j.entero('valorBsX10000'),
        fuente: j.textoONulo('fuente') ?? 'BCV',
      );

  static Institucion institucion(Map<String, dynamic> j) => Institucion(
        idInstitucion: j.entero('idInstitucion'),
        codigo: j.textoONulo('codigo') ?? '',
        nombre: j.texto('nombre'),
        tipo: j.textoONulo('tipo') ?? 'banco',
        estatusIntegracion: j.textoONulo('estatusIntegracion'),
      );

  static ConsentimientoCreado consentimientoCreado(Map<String, dynamic> j) => ConsentimientoCreado(
        idConsentimiento: j.entero('idConsentimiento'),
        idInstitucion: j.entero('idInstitucion'),
        institucion: j.textoONulo('institucion') ?? '',
        estatus: j.textoONulo('estatus') ?? 'pendiente',
        urlAutorizacion: j.texto('urlAutorizacion'),
      );

  static Consentimiento consentimiento(Map<String, dynamic> j) => Consentimiento(
        idConsentimiento: j.entero('idConsentimiento'),
        idInstitucion: j.entero('idInstitucion'),
        institucion: j.textoONulo('institucion') ?? '',
        estatus: j.textoONulo('estatus') ?? 'pendiente',
        permiteLectura: j.booleano('permiteLectura', true),
        permiteDebitoTdc: j.booleano('permiteDebitoTdc'),
        fechaOtorgamiento: j.fecha('fechaOtorgamiento'),
        fechaVencimiento: j.fecha('fechaVencimiento'),
      );

  static Cuenta cuenta(Map<String, dynamic> j) => Cuenta(
        idCuenta: j.entero('idCuenta'),
        alias: j.textoONulo('alias'),
        ultimos4: j.textoONulo('ultimos4') ?? '----',
        tipo: j.textoONulo('tipo') ?? 'corriente',
        moneda: j.textoONulo('moneda') ?? 'VES',
        saldoCts: j.enteroO('saldoCts', 0),
        saldoBsCts: j.enteroO('saldoBsCts', 0),
        saldoUsdCts: j.enteroO('saldoUsdCts', 0),
        soloLectura: j.booleano('soloLectura'),
      );

  static InstitucionConsolidada institucionConsolidada(Map<String, dynamic> j) =>
      InstitucionConsolidada(
        idInstitucion: j.entero('idInstitucion'),
        nombre: j.texto('nombre'),
        tipo: j.textoONulo('tipo') ?? 'banco',
        totalBsCts: j.enteroO('totalBsCts', 0),
        totalUsdCts: j.enteroO('totalUsdCts', 0),
        cuentas: j.lista('cuentas', cuenta),
      );

  static Consolidado consolidado(Map<String, dynamic> j) => Consolidado(
        tasa: tasa(j.mapa('tasa')!),
        totalBsCts: j.enteroO('totalBsCts', 0),
        totalUsdCts: j.enteroO('totalUsdCts', 0),
        actualizadoEn: j.fecha('actualizadoEn'),
        instituciones: j.lista('instituciones', institucionConsolidada),
      );

  static Movimiento movimiento(Map<String, dynamic> j) => Movimiento(
        idMovimiento: j.entero('idMovimiento'),
        fechaHora: j.fecha('fechaHora'),
        naturaleza: j.textoONulo('naturaleza') ?? '',
        clasificacion: j.textoONulo('clasificacion'),
        montoCts: j.enteroO('montoCts', 0),
        moneda: j.textoONulo('moneda') ?? 'VES',
        descripcion: j.textoONulo('descripcion'),
      );
}
