/// Modelos de Consolidación multibanco (M2).
library;

class Tasa {
  const Tasa({required this.fecha, required this.valorBsX10000, this.fuente = 'BCV'});

  final DateTime? fecha;
  final int valorBsX10000;
  final String fuente;
}

class Institucion {
  const Institucion({
    required this.idInstitucion,
    required this.codigo,
    required this.nombre,
    required this.tipo,
    required this.estatusIntegracion,
  });

  final int idInstitucion;
  final String codigo;
  final String nombre;

  /// `banco|plataforma_cripto`
  final String tipo;
  final String? estatusIntegracion;

  bool get esCripto => tipo == 'plataforma_cripto';
}

/// Respuesta de `POST /consentimientos`.
class ConsentimientoCreado {
  const ConsentimientoCreado({
    required this.idConsentimiento,
    required this.idInstitucion,
    required this.institucion,
    required this.estatus,
    required this.urlAutorizacion,
  });

  final int idConsentimiento;
  final int idInstitucion;
  final String institucion;
  final String estatus;
  final String urlAutorizacion;
}

class Consentimiento {
  const Consentimiento({
    required this.idConsentimiento,
    required this.idInstitucion,
    required this.institucion,
    required this.estatus,
    required this.permiteLectura,
    required this.permiteDebitoTdc,
    this.fechaOtorgamiento,
    this.fechaVencimiento,
  });

  final int idConsentimiento;
  final int idInstitucion;
  final String institucion;

  /// `pendiente|activo|revocado|vencido`
  final String estatus;
  final bool permiteLectura;
  final bool permiteDebitoTdc;
  final DateTime? fechaOtorgamiento;
  final DateTime? fechaVencimiento;

  bool get activo => estatus == 'activo';
}

class Cuenta {
  const Cuenta({
    required this.idCuenta,
    required this.alias,
    required this.ultimos4,
    required this.tipo,
    required this.moneda,
    required this.saldoCts,
    required this.saldoBsCts,
    required this.saldoUsdCts,
    required this.soloLectura,
  });

  final int idCuenta;
  final String? alias;
  final String ultimos4;
  final String tipo;

  /// `VES|USD|USDT`
  final String moneda;
  final int saldoCts;
  final int saldoBsCts;
  final int saldoUsdCts;
  final bool soloLectura;

  String get nombreVisible => (alias == null || alias!.isEmpty) ? 'Cuenta $tipo' : alias!;
}

class InstitucionConsolidada {
  const InstitucionConsolidada({
    required this.idInstitucion,
    required this.nombre,
    required this.tipo,
    required this.totalBsCts,
    required this.totalUsdCts,
    required this.cuentas,
  });

  final int idInstitucion;
  final String nombre;
  final String tipo;
  final int totalBsCts;
  final int totalUsdCts;
  final List<Cuenta> cuentas;
}

class Consolidado {
  const Consolidado({
    required this.tasa,
    required this.totalBsCts,
    required this.totalUsdCts,
    required this.actualizadoEn,
    required this.instituciones,
  });

  final Tasa tasa;
  final int totalBsCts;
  final int totalUsdCts;
  final DateTime? actualizadoEn;
  final List<InstitucionConsolidada> instituciones;

  List<Cuenta> get cuentas => [for (final i in instituciones) ...i.cuentas];
}

class Movimiento {
  const Movimiento({
    required this.idMovimiento,
    required this.fechaHora,
    required this.naturaleza,
    required this.clasificacion,
    required this.montoCts,
    required this.moneda,
    required this.descripcion,
  });

  final int idMovimiento;
  final DateTime? fechaHora;

  /// p. ej. `credito|debito`
  final String naturaleza;

  /// p. ej. `ingreso|gasto|transferencia`
  final String? clasificacion;
  final int montoCts;
  final String moneda;
  final String? descripcion;

  bool get esEntrada =>
      naturaleza == 'credito' || naturaleza == 'abono' || clasificacion == 'ingreso';
}
