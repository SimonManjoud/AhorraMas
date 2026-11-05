import '../../../core/api/api_client.dart';
import '../domain/pagos_models.dart';

class PagosDto {
  PagosDto._();

  static EstadoCuentaTdc? estadoCuenta(Map<String, dynamic>? j) => j == null
      ? null
      : EstadoCuentaTdc(
          fechaCorte: j.fecha('fechaCorte'),
          fechaLimitePago: j.fecha('fechaLimitePago'),
          saldoTotalCts: j.enteroO('saldoTotalCts', 0),
          pagoMinimoCts: j.enteroO('pagoMinimoCts', 0),
        );

  static Tarjeta tarjeta(Map<String, dynamic> j) => Tarjeta(
        idTarjeta: j.entero('idTarjeta'),
        idInstitucion: j.enteroO('idInstitucion', 0),
        banco: j.textoONulo('banco') ?? '',
        ultimos4: j.textoONulo('ultimos4') ?? '----',
        alias: j.textoONulo('alias'),
        titularConfirmado: j.textoONulo('titularConfirmado'),
        esPropia: j.booleano('esPropia', true),
        estadoCuenta: estadoCuenta(j.mapa('estadoCuenta')),
      );

  static Beneficiario beneficiario(Map<String, dynamic> j) =>
      Beneficiario(titular: j.texto('titular'), ultimos4: j.texto('ultimos4'));

  static CuentaOrigen cuentaOrigen(Map<String, dynamic> j) => CuentaOrigen(
        idCuenta: j.entero('idCuenta'),
        banco: j.textoONulo('banco') ?? '',
        ultimos4: j.textoONulo('ultimos4') ?? '----',
        alias: j.textoONulo('alias'),
        saldoCts: j.enteroO('saldoCts', 0),
      );

  static Cotizacion cotizacion(Map<String, dynamic> j) => Cotizacion(
        montoCts: j.entero('montoCts'),
        comisionCts: j.enteroO('comisionCts', 0),
        totalCts: j.entero('totalCts'),
      );

  static CambioEstadoPago cambio(Map<String, dynamic> j) => CambioEstadoPago(
        secuencia: j.enteroO('secuencia', 0),
        estadoAnterior:
            j.textoONulo('estadoAnterior') == null ? null : EstadoPago.desde(j.texto('estadoAnterior')),
        estadoNuevo: EstadoPago.desde(j.textoONulo('estadoNuevo')),
        fechaHora: j.fecha('fechaHora'),
        actor: j.textoONulo('actor'),
      );

  static Pago pago(Map<String, dynamic> j) => Pago(
        idPago: j.entero('idPago'),
        estado: EstadoPago.desde(j.textoONulo('estado')),
        montoCts: j.enteroO('montoCts', 0),
        comisionCts: j.enteroO('comisionCts', 0),
        referenciaSuiche: j.textoONulo('referenciaSuiche'),
        codigoAutorizacion: j.textoONulo('codigoAutorizacion'),
        esPuntual: j['esPuntual'] as bool?,
        motivoRechazo: j.textoONulo('motivoRechazo'),
        fechaHoraCreacion: j.fecha('fechaHoraCreacion'),
        mensaje: j.textoONulo('mensaje'),
        historial: j.lista('historial', cambio)..sort((a, b) => a.secuencia.compareTo(b.secuencia)),
      );

  static Map<String, dynamic> solicitud(SolicitudPagoTdc s) => {
        'idTarjeta': s.idTarjeta,
        'idCuentaOrigen': s.idCuentaOrigen,
        'montoCts': s.montoCts,
        'beneficiarioConfirmado': s.beneficiarioConfirmado,
      };

  static Limites limites(Map<String, dynamic> j) => Limites(
        montoMaxDiarioCts: j.enteroO('montoMaxDiarioCts', 0),
        maxOperacionesDiarias: j.enteroO('maxOperacionesDiarias', 0),
        usadoHoyCts: j.enteroO('usadoHoyCts', 0),
        operacionesHoy: j.enteroO('operacionesHoy', 0),
      );
}
