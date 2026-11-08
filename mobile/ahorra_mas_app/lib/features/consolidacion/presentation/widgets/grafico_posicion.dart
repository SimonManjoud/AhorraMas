import 'package:fl_chart/fl_chart.dart';
import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../../../core/money/dinero.dart';
import '../../../../core/theme/app_theme.dart';
import '../../../../core/widgets/libreta.dart';
import '../../domain/consolidacion_models.dart';

/// Una porción de la dona: una cuenta (o "Otras" cuando hay más de 8).
class _Porcion {
  _Porcion({required this.cuentas, required this.institucion, required this.color, required this.usdCts, required this.bsCts});

  final List<Cuenta> cuentas;

  /// `null` para "Otras".
  final InstitucionConsolidada? institucion;
  final Color color;
  final int usdCts;
  final int bsCts;

  bool get esOtras => institucion == null;
}

String tipoCuentaLegible(String tipo) => switch (tipo) {
      'corriente' => 'Cuenta corriente',
      'ahorro' => 'Cuenta de ahorro',
      'divisas' => 'Cuenta en divisas',
      'billetera' => 'Billetera',
      _ => tipo.isEmpty ? 'Cuenta' : '${tipo[0].toUpperCase()}${tipo.substring(1)}',
    };

String _porcentaje(double p) {
  final v = p < 10 ? (p * 10).round() / 10 : p.roundToDouble();
  final s = v == v.roundToDouble() ? v.toStringAsFixed(0) : v.toStringAsFixed(1).replaceAll('.', ',');
  return '$s %';
}

/// Lámina "¿Dónde está tu dinero?": dona interactiva de la posición global por cuenta (en USD a
/// tasa BCV) y, debajo, la lista de cuentas por institución, que hace de leyenda con su muestra de color.
class GraficoPosicion extends StatefulWidget {
  const GraficoPosicion({super.key, required this.consolidado});

  final Consolidado consolidado;

  @override
  State<GraficoPosicion> createState() => _GraficoPosicionState();
}

class _GraficoPosicionState extends State<GraficoPosicion> {
  int? _sel;

  /// Color por cuenta según su lugar estable en la lista (nunca por su tamaño).
  Map<int, Color> _colores(BuildContext context, List<Cuenta> cuentas) {
    final pliega = cuentas.length > AppTheme.maxSeries;
    return {
      for (var i = 0; i < cuentas.length; i++)
        cuentas[i].idCuenta:
            pliega && i >= AppTheme.maxSeries - 1 ? AppTheme.serieOtras(context) : AppTheme.serie(context, i),
    };
  }

  List<_Porcion> _porciones(BuildContext context, Map<int, Color> colores) {
    final todas = widget.consolidado.cuentas;
    final pliega = todas.length > AppTheme.maxSeries;
    final propias = <_Porcion>[];
    final otras = <Cuenta>[];
    var i = 0;
    for (final inst in widget.consolidado.instituciones) {
      for (final cu in inst.cuentas) {
        if (pliega && i >= AppTheme.maxSeries - 1) {
          if (cu.saldoUsdCts > 0) otras.add(cu);
        } else if (cu.saldoUsdCts > 0) {
          propias.add(_Porcion(
              cuentas: [cu], institucion: inst, color: colores[cu.idCuenta]!, usdCts: cu.saldoUsdCts, bsCts: cu.saldoBsCts));
        }
        i++;
      }
    }
    if (otras.isNotEmpty) {
      propias.add(_Porcion(
        cuentas: otras,
        institucion: null,
        color: AppTheme.serieOtras(context),
        usdCts: otras.fold(0, (a, c) => a + c.saldoUsdCts),
        bsCts: otras.fold(0, (a, c) => a + c.saldoBsCts),
      ));
    }
    return propias;
  }

  InstitucionConsolidada? _institucionDe(Cuenta c) {
    for (final i in widget.consolidado.instituciones) {
      if (i.cuentas.any((x) => x.idCuenta == c.idCuenta)) return i;
    }
    return null;
  }

  @override
  void didUpdateWidget(GraficoPosicion old) {
    super.didUpdateWidget(old);
    if (old.consolidado != widget.consolidado) _sel = null;
  }

  @override
  Widget build(BuildContext context) {
    final c = AhorraColores.of(context);
    final t = Theme.of(context).textTheme;
    final colores = _colores(context, widget.consolidado.cuentas);
    final porciones = _porciones(context, colores);
    final total = porciones.fold<int>(0, (a, p) => a + p.usdCts);
    final sel = _sel != null && _sel! < porciones.length ? porciones[_sel!] : null;
    final reducir = MediaQuery.maybeDisableAnimationsOf(context) ?? false;

    return Lamina(
      relleno: const EdgeInsets.fromLTRB(18, 18, 18, 10),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text('¿Dónde está tu dinero?', style: t.titleMedium),
          const SizedBox(height: 2),
          Text('Toca una porción para ver de qué cuenta se trata.', style: t.bodySmall?.copyWith(color: c.tintaGris)),
          if (total > 0) ...[
            const SizedBox(height: 12),
            Center(
              child: SizedBox(
                width: 236,
                height: 236,
                child: _Dona(
                  porciones: porciones,
                  total: total,
                  seleccionada: _sel,
                  reducir: reducir,
                  alTocar: (i) => setState(() => _sel = (i == null || i == _sel) ? null : i),
                  centro: _Centro(porcion: sel, total: total, cuentas: widget.consolidado.cuentas.length),
                ),
              ),
            ),
            AnimatedSize(
              duration: reducir ? Duration.zero : const Duration(milliseconds: 260),
              curve: Curves.easeOutCubic,
              alignment: Alignment.topCenter,
              child: sel == null
                  ? const SizedBox(width: double.infinity, height: 8)
                  : Padding(
                      padding: const EdgeInsets.only(top: 8, bottom: 6),
                      child: _Detalle(
                        key: ValueKey(_sel),
                        porcion: sel,
                        total: total,
                        alCerrar: () => setState(() => _sel = null),
                      ),
                    ),
            ),
          ],
          const SizedBox(height: 4),
          for (final inst in widget.consolidado.instituciones) ...[
            _EncabezadoInstitucion(institucion: inst),
            for (final cu in inst.cuentas)
              _FilaCuenta(
                cuenta: cu,
                color: colores[cu.idCuenta]!,
                porcentaje: total > 0 && cu.saldoUsdCts > 0 ? cu.saldoUsdCts * 100 / total : null,
                destacada: sel != null && sel.cuentas.any((x) => x.idCuenta == cu.idCuenta),
              ),
          ],
          const SizedBox(height: 6),
          const Filete(),
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 12),
            child: Row(
              children: [
                Expanded(child: Text('Total', style: t.titleSmall?.copyWith(fontWeight: FontWeight.w700))),
                Column(
                  crossAxisAlignment: CrossAxisAlignment.end,
                  children: [
                    Text(Dinero.usd(widget.consolidado.totalUsdCts),
                        style: AppTheme.cifra(tamano: 15, peso: FontWeight.w800, color: c.tinta)),
                    Text(Dinero.bs(widget.consolidado.totalBsCts),
                        style: AppTheme.cifra(tamano: 12, peso: FontWeight.w500, color: c.tintaGris)),
                  ],
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  /// Usado por [_Detalle] para nombrar la institución de cada cuenta de "Otras".
  String nombreInstitucion(Cuenta c) => _institucionDe(c)?.nombre ?? '';
}

/// La dona: barrido de entrada (900 ms), porción tocada que crece y las demás que se atenúan.
class _Dona extends StatelessWidget {
  const _Dona({
    required this.porciones,
    required this.total,
    required this.seleccionada,
    required this.reducir,
    required this.alTocar,
    required this.centro,
  });

  final List<_Porcion> porciones;
  final int total;
  final int? seleccionada;
  final bool reducir;
  final ValueChanged<int?> alTocar;
  final Widget centro;

  @override
  Widget build(BuildContext context) {
    final c = AhorraColores.of(context);
    final resumen = porciones
        .map((p) => '${p.esOtras ? 'Otras cuentas' : p.cuentas.first.nombreVisible} ${_porcentaje(p.usdCts * 100 / total)}')
        .join(', ');
    return Semantics(
      label: 'Distribución de tu posición global por cuenta: $resumen',
      child: TweenAnimationBuilder<double>(
        tween: Tween(begin: reducir ? 1 : 0, end: 1),
        duration: reducir ? Duration.zero : const Duration(milliseconds: 900),
        curve: Curves.easeOutCubic,
        builder: (context, avance, _) {
          final secciones = <PieChartSectionData>[
            for (var i = 0; i < porciones.length; i++)
              PieChartSectionData(
                value: porciones[i].usdCts * avance,
                color: seleccionada == null || seleccionada == i
                    ? porciones[i].color
                    : porciones[i].color.withValues(alpha: 0.28),
                radius: seleccionada == i ? 46 : 36,
                showTitle: false,
                cornerRadius: 6,
                borderSide: BorderSide(color: c.superficie, width: 0),
              ),
            // Relleno transparente para el barrido de entrada; no se puede tocar.
            if (avance < 1)
              PieChartSectionData(value: total * (1 - avance), color: Colors.transparent, radius: 36, showTitle: false),
          ];
          return Stack(
            alignment: Alignment.center,
            children: [
              PieChart(
                PieChartData(
                  sections: secciones,
                  centerSpaceRadius: 66,
                  sectionsSpace: 3,
                  startDegreeOffset: -90,
                  pieTouchData: PieTouchData(
                    mouseCursorResolver: (e, r) =>
                        (r?.touchedSection?.touchedSectionIndex ?? -1) >= 0 ? SystemMouseCursors.click : MouseCursor.defer,
                    touchCallback: (e, r) {
                      if (e is! FlTapUpEvent) return;
                      final i = r?.touchedSection?.touchedSectionIndex ?? -1;
                      alTocar(i >= 0 && i < porciones.length ? i : null);
                    },
                  ),
                ),
                duration: reducir ? Duration.zero : const Duration(milliseconds: 280),
                curve: Curves.easeOutCubic,
              ),
              IgnorePointer(child: SizedBox(width: 118, child: centro)),
            ],
          );
        },
      ),
    );
  }
}

class _Centro extends StatelessWidget {
  const _Centro({required this.porcion, required this.total, required this.cuentas});

  final _Porcion? porcion;
  final int total;
  final int cuentas;

  @override
  Widget build(BuildContext context) {
    final c = AhorraColores.of(context);
    final t = Theme.of(context).textTheme;
    final p = porcion;
    final reducir = MediaQuery.maybeDisableAnimationsOf(context) ?? false;
    final Widget contenido = p == null
        ? Column(
            key: const ValueKey('centro-total'),
            mainAxisSize: MainAxisSize.min,
            children: [
              Text('En USD', style: t.bodySmall?.copyWith(color: c.tintaGris)),
              FittedBox(
                fit: BoxFit.scaleDown,
                child: Text(Dinero.usd(total), style: AppTheme.cifra(tamano: 20, peso: FontWeight.w800, color: c.tinta)),
              ),
              Text('$cuentas ${cuentas == 1 ? 'cuenta' : 'cuentas'}', style: t.bodySmall?.copyWith(color: c.tintaGris)),
            ],
          )
        : Column(
            key: ValueKey('centro-${p.cuentas.first.idCuenta}'),
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(_porcentaje(p.usdCts * 100 / total),
                  style: AppTheme.cifra(tamano: 26, peso: FontWeight.w800, color: c.tinta)),
              Text(
                p.esOtras ? 'Otras ${p.cuentas.length} cuentas' : p.cuentas.first.nombreVisible,
                textAlign: TextAlign.center,
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                style: t.bodySmall?.copyWith(color: c.tintaGris, fontWeight: FontWeight.w600),
              ),
            ],
          );
    return AnimatedSwitcher(
      duration: reducir ? Duration.zero : const Duration(milliseconds: 200),
      child: contenido,
    );
  }
}

/// Qué cuenta es la porción tocada: institución, banco o cripto, tipo de cuenta, moneda y montos.
class _Detalle extends StatelessWidget {
  const _Detalle({super.key, required this.porcion, required this.total, required this.alCerrar});

  final _Porcion porcion;
  final int total;
  final VoidCallback alCerrar;

  @override
  Widget build(BuildContext context) {
    final c = AhorraColores.of(context);
    final t = Theme.of(context).textTheme;
    final p = porcion;
    final cuenta = p.cuentas.first;
    final inst = p.institucion;
    final cripto = inst?.tipo == 'plataforma_cripto';
    final padre = context.findAncestorStateOfType<_GraficoPosicionState>();
    return Container(
      key: const ValueKey('detalle-porcion'),
      padding: const EdgeInsets.fromLTRB(14, 12, 6, 12),
      decoration: BoxDecoration(
        color: p.color.withValues(alpha: 0.10),
        borderRadius: BorderRadius.circular(18),
        border: Border.all(color: p.color.withValues(alpha: 0.45)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Padding(
                padding: const EdgeInsets.only(top: 4),
                child: Container(width: 12, height: 12, decoration: BoxDecoration(color: p.color, shape: BoxShape.circle)),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: Text(
                  p.esOtras ? 'Otras ${p.cuentas.length} cuentas' : cuenta.nombreVisible,
                  style: t.titleSmall?.copyWith(fontWeight: FontWeight.w700),
                ),
              ),
              IconButton(
                tooltip: 'Cerrar detalle',
                visualDensity: VisualDensity.compact,
                onPressed: alCerrar,
                icon: const Icon(Icons.close_rounded, size: 20),
              ),
            ],
          ),
          if (p.esOtras)
            for (final cu in p.cuentas)
              Padding(
                padding: const EdgeInsets.only(left: 22, top: 2),
                child: Text('${cu.nombreVisible} - ${padre?.nombreInstitucion(cu) ?? ''}',
                    style: t.bodySmall?.copyWith(color: c.tintaGris)),
              )
          else ...[
            const SizedBox(height: 6),
            Wrap(
              spacing: 6,
              runSpacing: 6,
              children: [
                _Etiqueta(icono: cripto ? Icons.currency_bitcoin_rounded : Icons.account_balance_rounded, texto: inst!.nombre),
                _Etiqueta(icono: cripto ? Icons.lock_outline_rounded : Icons.verified_outlined, texto: cripto ? 'Cripto - solo lectura' : 'Banco'),
                _Etiqueta(icono: Icons.wallet_rounded, texto: tipoCuentaLegible(cuenta.tipo)),
                _Etiqueta(icono: Icons.payments_outlined, texto: '${cuenta.moneda} - ${cuenta.ultimos4}'),
              ],
            ),
          ],
          const SizedBox(height: 10),
          Row(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(Dinero.usd(p.usdCts), style: AppTheme.cifra(tamano: 20, peso: FontWeight.w800, color: c.tinta)),
                    Text('${Dinero.bs(p.bsCts)} - ${_porcentaje(p.usdCts * 100 / total)} del total',
                        style: AppTheme.cifra(tamano: 12, peso: FontWeight.w500, color: c.tintaGris)),
                  ],
                ),
              ),
              if (!p.esOtras)
                TextButton(
                  onPressed: () => context.push('/inicio/cuenta/${cuenta.idCuenta}', extra: cuenta),
                  child: const Text('Movimientos'),
                ),
            ],
          ),
        ],
      ),
    );
  }
}

class _Etiqueta extends StatelessWidget {
  const _Etiqueta({required this.icono, required this.texto});

  final IconData icono;
  final String texto;

  @override
  Widget build(BuildContext context) {
    final c = AhorraColores.of(context);
    return Container(
      padding: const EdgeInsets.fromLTRB(8, 5, 10, 5),
      decoration: BoxDecoration(color: c.superficie, borderRadius: BorderRadius.circular(20)),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icono, size: 14, color: c.tintaGris),
          const SizedBox(width: 5),
          Flexible(
            child: Text(texto,
                overflow: TextOverflow.ellipsis,
                style: TextStyle(fontFamily: AppTheme.familia, fontSize: 12, fontWeight: FontWeight.w600, color: c.tinta)),
          ),
        ],
      ),
    );
  }
}

class _EncabezadoInstitucion extends StatelessWidget {
  const _EncabezadoInstitucion({required this.institucion});

  final InstitucionConsolidada institucion;

  @override
  Widget build(BuildContext context) {
    final c = AhorraColores.of(context);
    final t = Theme.of(context).textTheme;
    final cripto = institucion.tipo == 'plataforma_cripto';
    return Padding(
      padding: const EdgeInsets.only(top: 14, bottom: 2),
      child: Row(
        children: [
          Icon(cripto ? Icons.currency_bitcoin_rounded : Icons.account_balance_rounded, size: 16, color: c.tintaGris),
          const SizedBox(width: 6),
          Flexible(
            child: Text(institucion.nombre,
                overflow: TextOverflow.ellipsis, style: t.labelLarge?.copyWith(color: c.tintaGris)),
          ),
          if (cripto) ...[
            const SizedBox(width: 8),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
              decoration: BoxDecoration(color: c.hojaHonda, borderRadius: BorderRadius.circular(10)),
              child: Text('Cripto', style: t.labelSmall?.copyWith(color: c.tintaGris)),
            ),
          ],
        ],
      ),
    );
  }
}

class _FilaCuenta extends StatelessWidget {
  const _FilaCuenta({required this.cuenta, required this.color, required this.porcentaje, required this.destacada});

  final Cuenta cuenta;
  final Color color;
  final double? porcentaje;
  final bool destacada;

  @override
  Widget build(BuildContext context) {
    final c = AhorraColores.of(context);
    final t = Theme.of(context).textTheme;
    return Material(
      color: destacada ? color.withValues(alpha: 0.10) : Colors.transparent,
      borderRadius: BorderRadius.circular(14),
      child: InkWell(
        borderRadius: BorderRadius.circular(14),
        onTap: () => context.push('/inicio/cuenta/${cuenta.idCuenta}', extra: cuenta),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 9),
          child: Row(
            children: [
              Container(
                width: 12,
                height: 12,
                decoration: BoxDecoration(
                  color: porcentaje == null ? null : color,
                  shape: BoxShape.circle,
                  border: porcentaje == null ? Border.all(color: c.regla, width: 1.5) : null,
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(cuenta.nombreVisible, overflow: TextOverflow.ellipsis, style: t.bodyMedium?.copyWith(fontWeight: FontWeight.w600)),
                    Text(
                      '${tipoCuentaLegible(cuenta.tipo)} - ${cuenta.moneda} - ${cuenta.ultimos4}',
                      overflow: TextOverflow.ellipsis,
                      style: t.bodySmall?.copyWith(color: c.tintaGris),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 8),
              Column(
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  Text(Dinero.usd(cuenta.saldoUsdCts), style: AppTheme.cifra(tamano: 14, peso: FontWeight.w700, color: c.tinta)),
                  Text(porcentaje == null ? Dinero.bs(cuenta.saldoBsCts) : _porcentaje(porcentaje!),
                      style: AppTheme.cifra(tamano: 11.5, peso: FontWeight.w500, color: c.tintaGris)),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}
