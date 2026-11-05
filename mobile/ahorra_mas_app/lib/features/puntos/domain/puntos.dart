/// Puntos Ahorro y niveles (M6): modelos y puerto.
library;

class ResumenPuntos {
  const ResumenPuntos({
    required this.saldo,
    required this.puntosGanadosTotal,
    required this.nivel,
    this.nivelDesde,
    this.siguienteNivel,
  });

  final int saldo;
  final int puntosGanadosTotal;

  /// `Base|Gold|Platinum`
  final String nivel;
  final DateTime? nivelDesde;
  final SiguienteNivel? siguienteNivel;

  /// Fracción 0..1 hacia el siguiente nivel (1 si ya está en el máximo).
  double get progresoSiguiente {
    final s = siguienteNivel;
    if (s == null || s.puntosMinimos <= 0) return 1;
    final logrado = s.puntosMinimos - s.puntosFaltantes;
    return (logrado / s.puntosMinimos).clamp(0, 1).toDouble();
  }
}

class SiguienteNivel {
  const SiguienteNivel({
    required this.nombre,
    required this.puntosMinimos,
    required this.puntosFaltantes,
  });

  final String nombre;
  final int puntosMinimos;
  final int puntosFaltantes;
}

class MovimientoPuntos {
  const MovimientoPuntos({
    required this.idTransaccion,
    required this.fechaHora,
    required this.concepto,
    required this.descripcion,
    required this.puntos,
  });

  final int idTransaccion;
  final DateTime? fechaHora;

  /// `meta|pago_puntual|racha|referido|canje|reverso`
  final String concepto;
  final String? descripcion;

  /// Con signo para el usuario.
  final int puntos;
}

class Recompensa {
  const Recompensa({
    required this.idRecompensa,
    required this.descripcion,
    required this.costoPuntos,
    required this.comercio,
    required this.nivelMinimo,
    required this.stockDisponible,
  });

  final int idRecompensa;
  final String descripcion;
  final int costoPuntos;
  final String? comercio;
  final String nivelMinimo;
  final int? stockDisponible;
}

class Canje {
  const Canje({
    required this.idCanje,
    required this.codigoCanje,
    required this.puntosCanjeados,
    required this.estatus,
    required this.fechaHora,
    this.descripcion,
  });

  final int idCanje;
  final String codigoCanje;
  final int puntosCanjeados;
  final String estatus;
  final DateTime? fechaHora;
  final String? descripcion;
}

/// Orden de niveles para comparar `nivelMinimo`.
int ordenNivel(String nivel) => switch (nivel.toLowerCase()) {
      'platinum' => 2,
      'gold' => 1,
      _ => 0,
    };

abstract interface class PuntosRepository {
  Future<ResumenPuntos> resumenPuntos();
  Future<List<MovimientoPuntos>> movimientosPuntos();
  Future<List<Recompensa>> recompensas();
  Future<Canje> canjear(int idRecompensa);
  Future<List<Canje>> canjes();
}
