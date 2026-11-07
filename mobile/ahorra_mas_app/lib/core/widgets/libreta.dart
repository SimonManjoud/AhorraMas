import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../theme/app_theme.dart';

/// Componentes del mundo "La Libreta Viva" en su versión moderna (ver DESIGN.md):
/// fondo liso, láminas redondeadas, filas de cifras, insignia de sello, marcas de racha
/// y barra de progreso.

/// Fondo de la app: verde pálido liso (sin tramas ni rayas).
class FondoHoja extends StatelessWidget {
  const FondoHoja({super.key, required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) => ColoredBox(color: AhorraColores.of(context).hoja, child: child);
}

/// Lámina: bloque claro con esquinas de 24 sobre el fondo. Reemplaza a la tarjeta genérica.
class Lamina extends StatelessWidget {
  const Lamina({
    super.key,
    required this.child,
    this.relleno = const EdgeInsets.all(18),
    this.color,
    this.onTap,
  });

  final Widget child;
  final EdgeInsets relleno;
  final Color? color;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final c = AhorraColores.of(context);
    return Material(
      color: color ?? c.superficie,
      borderRadius: BorderRadius.circular(24),
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: onTap,
        child: Padding(padding: relleno, child: child),
      ),
    );
  }
}

/// Separador fino. `doble` se conserva por compatibilidad: ahora es un filete algo más marcado.
class Filete extends StatelessWidget {
  const Filete({super.key, this.doble = false, this.color, this.suave = false});

  final bool doble;
  final bool suave;
  final Color? color;

  @override
  Widget build(BuildContext context) {
    final c = AhorraColores.of(context);
    final col = color ?? (doble ? c.regla : c.reglaSuave);
    return SizedBox(height: 1, width: double.infinity, child: ColoredBox(color: col));
  }
}

/// Etiqueta secundaria en gris (tipo de cuenta, encabezado de bloque). Respeta mayúsculas y minúsculas.
class Rubrica extends StatelessWidget {
  const Rubrica(this.texto, {super.key, this.color, this.alineacion});

  final String texto;
  final Color? color;
  final TextAlign? alineacion;

  @override
  Widget build(BuildContext context) => Text(
        texto,
        textAlign: alineacion,
        style: AppTheme.rubrica(color: color ?? AhorraColores.of(context).tintaGris),
      );
}

/// Cifra alineada a la derecha que se encoge antes de desbordar su columna.
class CeldaCifra extends StatelessWidget {
  const CeldaCifra(this.texto, {super.key, this.negrita = false, this.color});

  final String texto;
  final bool negrita;
  final Color? color;

  @override
  Widget build(BuildContext context) => Align(
        alignment: Alignment.topRight,
        child: FittedBox(
          fit: BoxFit.scaleDown,
          alignment: Alignment.topRight,
          child: Text(
            texto,
            maxLines: 1,
            style: AppTheme.cifra(
              tamano: 13.5,
              peso: negrita ? FontWeight.w700 : FontWeight.w600,
              color: color ?? AhorraColores.of(context).tinta,
            ),
          ),
        ),
      );
}

/// Proporción de columnas: concepto | Bs | USD.
const flexLibro = [14, 10, 7];

/// Fila de concepto y cifras (sin reglas verticales); separador fino opcional al pie.
class FilaLibro extends StatelessWidget {
  const FilaLibro({
    super.key,
    required this.concepto,
    required this.cifras,
    this.onTap,
    this.separador = true,
    this.sangria = 0,
    this.relleno = const EdgeInsets.symmetric(vertical: 10),
  });

  final Widget concepto;
  final List<Widget> cifras;
  final VoidCallback? onTap;
  final bool separador;
  final double sangria;
  final EdgeInsets relleno;

  @override
  Widget build(BuildContext context) {
    final c = AhorraColores.of(context);
    final fila = Row(
      crossAxisAlignment: CrossAxisAlignment.center,
      children: [
        Expanded(
          flex: flexLibro[0],
          child: Padding(
            padding: relleno.copyWith(left: sangria, right: 8),
            child: Align(alignment: Alignment.centerLeft, child: concepto),
          ),
        ),
        for (var i = 0; i < cifras.length; i++)
          Expanded(
            flex: flexLibro[math.min(i + 1, flexLibro.length - 1)],
            child: Padding(
              padding: relleno.copyWith(left: 6, right: 0),
              child: cifras[i],
            ),
          ),
      ],
    );
    return Material(
      type: MaterialType.transparency,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(12),
        child: DecoratedBox(
          decoration: BoxDecoration(
            border: separador ? Border(bottom: BorderSide(color: c.reglaSuave)) : null,
          ),
          child: fila,
        ),
      ),
    );
  }
}

/// Guía punteada suave que une un concepto con su monto.
class GuiaPunteada extends StatelessWidget {
  const GuiaPunteada({super.key});

  @override
  Widget build(BuildContext context) => SizedBox(
        height: 2,
        child: CustomPaint(painter: _PuntosPainter(AhorraColores.of(context).regla)),
      );
}

class _PuntosPainter extends CustomPainter {
  _PuntosPainter(this.color);

  final Color color;

  @override
  void paint(Canvas canvas, Size size) {
    final p = Paint()..color = color;
    for (var x = 0.0; x < size.width; x += 5) {
      canvas.drawCircle(Offset(x + 1, size.height / 2), 1, p);
    }
  }

  @override
  bool shouldRepaint(_PuntosPainter old) => old.color != color;
}

/// Aparición del sello: escala 0,92 → 1 con desaceleración (240 ms); sin animación si el sistema la quita.
class _Aparece extends StatelessWidget {
  const _Aparece({required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) {
    if (MediaQuery.maybeDisableAnimationsOf(context) ?? false) return child;
    return TweenAnimationBuilder<double>(
      tween: Tween(begin: 0.92, end: 1),
      duration: const Duration(milliseconds: 240),
      curve: Curves.easeOutBack,
      builder: (context, escala, hijo) => Transform.scale(scale: escala, child: hijo),
      child: child,
    );
  }
}

/// Insignia del Sello de compromiso: violeta, con candado. Una por vista.
class SelloRectangular extends StatelessWidget {
  const SelloRectangular({super.key, required this.titulo, required this.detalle});

  final String titulo;
  final String detalle;

  @override
  Widget build(BuildContext context) {
    final c = AhorraColores.of(context);
    final cs = Theme.of(context).colorScheme;
    return Semantics(
      label: '$titulo: $detalle',
      excludeSemantics: true,
      child: _Aparece(
        child: Container(
          padding: const EdgeInsets.fromLTRB(10, 7, 14, 7),
          decoration: BoxDecoration(color: cs.tertiaryContainer, borderRadius: BorderRadius.circular(14)),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(Icons.lock_rounded, size: 18, color: c.sello),
              const SizedBox(width: 8),
              Flexible(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(titulo,
                        style: TextStyle(
                            fontFamily: AppTheme.familia,
                            fontSize: 13,
                            fontWeight: FontWeight.w700,
                            color: cs.onTertiaryContainer)),
                    Text(detalle, style: AppTheme.cifra(tamano: 12, peso: FontWeight.w500, color: cs.onTertiaryContainer)),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

enum TipoMarca { aporte, colchon, sinAporte }

/// Marca de un día de racha: llena = aporte; contorno punteado = día colchón (cubierto, no fallo).
class MarcaDia extends StatelessWidget {
  const MarcaDia({super.key, required this.etiqueta, required this.tipo, this.descripcion});

  final String etiqueta;
  final TipoMarca tipo;
  final String? descripcion;

  @override
  Widget build(BuildContext context) {
    final c = AhorraColores.of(context);
    final texto = Text(
      etiqueta,
      style: AppTheme.cifra(
        tamano: 10.5,
        peso: FontWeight.w700,
        color: tipo == TipoMarca.aporte ? c.sobreTapa : (tipo == TipoMarca.colchon ? c.tinta : c.tintaGris),
      ),
    );
    final radio = BorderRadius.circular(9);
    final Widget marca = switch (tipo) {
      TipoMarca.aporte => Container(
          height: 28,
          alignment: Alignment.center,
          decoration: BoxDecoration(color: c.tapa, borderRadius: radio),
          child: texto,
        ),
      TipoMarca.colchon => CustomPaint(
          painter: _BordePunteadoPainter(c.tinta),
          child: Container(height: 28, alignment: Alignment.center, child: texto),
        ),
      TipoMarca.sinAporte => Container(
          height: 28,
          alignment: Alignment.center,
          decoration: BoxDecoration(color: c.hojaHonda, borderRadius: radio),
          child: texto,
        ),
    };
    return descripcion == null ? marca : Tooltip(message: descripcion!, child: marca);
  }
}

class _BordePunteadoPainter extends CustomPainter {
  _BordePunteadoPainter(this.color);

  final Color color;

  @override
  void paint(Canvas canvas, Size size) {
    final p = Paint()
      ..color = color
      ..style = PaintingStyle.stroke
      ..strokeWidth = 1.4;
    final ruta = Path()
      ..addRRect(RRect.fromRectAndRadius(
          Rect.fromLTWH(0.7, 0.7, size.width - 1.4, size.height - 1.4), const Radius.circular(9)));
    for (final m in ruta.computeMetrics()) {
      for (var d = 0.0; d < m.length; d += 6) {
        canvas.drawPath(m.extractPath(d, math.min(d + 3.5, m.length)), p);
      }
    }
  }

  @override
  bool shouldRepaint(_BordePunteadoPainter old) => old.color != color;
}

/// Barra de progreso redondeada en vino sobre una pista suave.
class TiraProgreso extends StatelessWidget {
  const TiraProgreso({super.key, required this.fraccion, this.alto = 10});

  final double fraccion;
  final double alto;

  @override
  Widget build(BuildContext context) {
    final c = AhorraColores.of(context);
    final f = fraccion.clamp(0.0, 1.0);
    return Semantics(
      label: 'Progreso ${(f * 100).round()} por ciento',
      excludeSemantics: true,
      child: ClipRRect(
        borderRadius: BorderRadius.circular(alto),
        child: SizedBox(
          height: alto,
          child: Stack(
            children: [
              Positioned.fill(child: ColoredBox(color: c.hojaHonda)),
              FractionallySizedBox(
                widthFactor: f,
                heightFactor: 1,
                child: DecoratedBox(
                  decoration: BoxDecoration(color: c.tapa, borderRadius: BorderRadius.circular(alto)),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// Tasa BCV del día como píldora: "1 US$ = Bs 36,52 · BCV 07 nov".
class PildoraTasa extends StatelessWidget {
  const PildoraTasa({super.key, required this.tasa, required this.fecha, this.sobreTapa = false});

  /// Tasa ya formateada (p. ej. "36,52").
  final String tasa;

  /// Fecha ya formateada (p. ej. "07 nov").
  final String fecha;

  /// `true` cuando va sobre la tapa vino.
  final bool sobreTapa;

  @override
  Widget build(BuildContext context) {
    final c = AhorraColores.of(context);
    final fondo = sobreTapa ? Colors.white.withValues(alpha: 0.12) : c.hojaHonda;
    final tinta = sobreTapa ? c.sobreTapa : c.tinta;
    final tenue = sobreTapa ? c.sobreTapaTenue : c.tintaGris;
    return Semantics(
      label: 'Tasa BCV del $fecha: un dólar equivale a $tasa bolívares',
      excludeSemantics: true,
      child: Container(
        padding: const EdgeInsets.fromLTRB(6, 6, 14, 6),
        decoration: BoxDecoration(color: fondo, borderRadius: BorderRadius.circular(40)),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              width: 26,
              height: 26,
              alignment: Alignment.center,
              decoration: BoxDecoration(color: tinta.withValues(alpha: 0.14), shape: BoxShape.circle),
              child: Icon(Icons.currency_exchange_rounded, size: 15, color: tinta),
            ),
            const SizedBox(width: 8),
            Flexible(
              child: Text.rich(
                TextSpan(children: [
                  TextSpan(text: '1 US\$ = ', style: TextStyle(color: tenue)),
                  TextSpan(text: 'Bs $tasa', style: AppTheme.cifra(tamano: 14, peso: FontWeight.w700, color: tinta)),
                  TextSpan(text: '  ·  Tasa BCV $fecha', style: TextStyle(color: tenue)),
                ]),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: TextStyle(fontFamily: AppTheme.familia, fontSize: 12.5, fontWeight: FontWeight.w500, color: tinta),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
