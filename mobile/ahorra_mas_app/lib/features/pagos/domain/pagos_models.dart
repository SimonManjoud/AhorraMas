/// Modelos de Pago de TDC interbancario (M3).
library;

class EstadoCuentaTdc {
  const EstadoCuentaTdc({
    required this.fechaCorte,
    required this.fechaLimitePago,
    required this.saldoTotalCts,
    required this.pagoMinimoCts,
  });

  final DateTime? fechaCorte;
  final DateTime? fechaLimitePago;
  final int saldoTotalCts;
  final int pagoMinimoCts;
}

class Tarjeta {
  const Tarjeta({
    required this.idTarjeta,
    required this.idInstitucion,
    required this.banco,
    required this.ultimos4,
    this.alias,
    this.titularConfirmado,
    required this.esPropia,
    this.estadoCuenta,
  });

  final int idTarjeta;
  final int idInstitucion;
  final String banco;
  final String ultimos4;
  final String? alias;
  final String? titularConfirmado;
  final bool esPropia;
  final EstadoCuentaTdc? estadoCuenta;

  String get nombreVisible => alias ?? '$banco •••• $ultimos4';
}

class Beneficiario {
  const Beneficiario({required this.titular, required this.ultimos4});

  final String titular;
  final String ultimos4;
}

class CuentaOrigen {
  const CuentaOrigen({
    required this.idCuenta,
    required this.banco,
    required this.ultimos4,
    this.alias,
    required this.saldoCts,
  });

  final int idCuenta;
  final String banco;
  final String ultimos4;
  final String? alias;
  final int saldoCts;

  String get nombreVisible => '${alias ?? banco} •••• $ultimos4';
}

class Cotizacion {
  const Cotizacion({required this.montoCts, required this.comisionCts, required this.totalCts});

  final int montoCts;
  final int comisionCts;
  final int totalCts;
}

/// Estados del pago (R-14).
enum EstadoPago {
  pendiente('PENDIENTE', 'Pendiente'),
  autorizado('AUTORIZADO', 'Autorizado'),
  capturado('CAPTURADO', 'Aplicado'),
  conciliado('CONCILIADO', 'Conciliado'),
  rechazado('RECHAZADO', 'Rechazado'),
  enVerificacion('EN_VERIFICACION', 'En proceso'),
  reversado('REVERSADO', 'Reversado'),
  conDiferencia('CON_DIFERENCIA', 'En revisión'),
  desconocido('DESCONOCIDO', 'Desconocido');

  const EstadoPago(this.codigo, this.etiqueta);
  final String codigo;
  final String etiqueta;

  static EstadoPago desde(String? c) =>
      values.firstWhere((e) => e.codigo == c, orElse: () => desconocido);

  bool get exitoso => this == capturado || this == conciliado;
  bool get fallido => this == rechazado || this == reversado;

  /// Estados intermedios: la app los muestra como "en proceso", nunca como fallidos.
  bool get enProceso =>
      this == pendiente || this == autorizado || this == enVerificacion || this == conDiferencia;
}

class CambioEstadoPago {
  const CambioEstadoPago({
    required this.secuencia,
    required this.estadoAnterior,
    required this.estadoNuevo,
    required this.fechaHora,
    required this.actor,
  });

  final int secuencia;
  final EstadoPago? estadoAnterior;
  final EstadoPago estadoNuevo;
  final DateTime? fechaHora;
  final String? actor;
}

class Pago {
  const Pago({
    required this.idPago,
    required this.estado,
    required this.montoCts,
    required this.comisionCts,
    this.referenciaSuiche,
    this.codigoAutorizacion,
    this.esPuntual,
    this.motivoRechazo,
    this.fechaHoraCreacion,
    this.mensaje,
    this.historial = const [],
  });

  final int idPago;
  final EstadoPago estado;
  final int montoCts;
  final int comisionCts;
  final String? referenciaSuiche;
  final String? codigoAutorizacion;
  final bool? esPuntual;
  final String? motivoRechazo;
  final DateTime? fechaHoraCreacion;
  final String? mensaje;
  final List<CambioEstadoPago> historial;
}

class SolicitudPagoTdc {
  const SolicitudPagoTdc({
    required this.idTarjeta,
    required this.idCuentaOrigen,
    required this.montoCts,
    this.beneficiarioConfirmado = true,
  });

  final int idTarjeta;
  final int idCuentaOrigen;
  final int montoCts;
  final bool beneficiarioConfirmado;

  @override
  bool operator ==(Object other) =>
      other is SolicitudPagoTdc &&
      other.idTarjeta == idTarjeta &&
      other.idCuentaOrigen == idCuentaOrigen &&
      other.montoCts == montoCts &&
      other.beneficiarioConfirmado == beneficiarioConfirmado;

  @override
  int get hashCode => Object.hash(idTarjeta, idCuentaOrigen, montoCts, beneficiarioConfirmado);
}

class Limites {
  const Limites({
    required this.montoMaxDiarioCts,
    required this.maxOperacionesDiarias,
    required this.usadoHoyCts,
    required this.operacionesHoy,
  });

  final int montoMaxDiarioCts;
  final int maxOperacionesDiarias;
  final int usadoHoyCts;
  final int operacionesHoy;

  int get disponibleHoyCts {
    final d = montoMaxDiarioCts - usadoHoyCts;
    return d < 0 ? 0 : d;
  }
}
