import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../../../../core/theme/app_theme.dart';
import '../../domain/pagos_models.dart';

/// Tarjeta de crédito dibujada: número enmascarado salvo los últimos 4, banco y red
/// (VISA / Mastercard). Al tocarla gira y muestra el reverso con el CVV oculto.
/// Nunca recibe ni muestra el número completo: el backend solo conoce los últimos 4.
class TarjetaVisual extends StatefulWidget {
  const TarjetaVisual({super.key, required this.tarjeta});

  final Tarjeta tarjeta;

  @override
  State<TarjetaVisual> createState() => _TarjetaVisualState();
}

class _TarjetaVisualState extends State<TarjetaVisual> with SingleTickerProviderStateMixin {
  late final AnimationController _giro =
      AnimationController(vsync: this, duration: const Duration(milliseconds: 700));
  late final Animation<double> _curva = CurvedAnimation(parent: _giro, curve: Curves.easeInOutCubic);

  @override
  void dispose() {
    _giro.dispose();
    super.dispose();
  }

  void _girar() {
    final reducir = MediaQuery.maybeDisableAnimationsOf(context) ?? false;
    final alReves = _giro.value > 0.5;
    if (reducir) {
      _giro.value = alReves ? 0 : 1;
    } else if (alReves) {
      _giro.reverse();
    } else {
      _giro.forward();
    }
  }

  @override
  Widget build(BuildContext context) {
    final t = widget.tarjeta;
    final red = switch (t.marca) {
      'visa' => 'VISA',
      'mastercard' => 'Mastercard',
      _ => 'tarjeta de crédito',
    };
    return Semantics(
      button: true,
      label: '$red de ${t.banco} terminada en ${t.ultimos4}. Toca para ver el reverso.',
      excludeSemantics: true,
      child: GestureDetector(
        key: ValueKey('tarjeta-visual-${t.idTarjeta}'),
        onTap: _girar,
        child: MouseRegion(
          cursor: SystemMouseCursors.click,
          child: AspectRatio(
            aspectRatio: 1.586,
            child: AnimatedBuilder(
              animation: _curva,
              builder: (context, _) {
                final v = _curva.value;
                final reverso = v > 0.5;
                return Transform(
                  alignment: Alignment.center,
                  transform: Matrix4.identity()
                    ..setEntry(3, 2, 0.0012)
                    ..rotateY(math.pi * v),
                  child: reverso
                      ? Transform(
                          alignment: Alignment.center,
                          transform: Matrix4.identity()..rotateY(math.pi),
                          child: _Reverso(tarjeta: t),
                        )
                      : _Frente(tarjeta: t),
                );
              },
            ),
          ),
        ),
      ),
    );
  }
}

/// Tono de cada banco (de su color a casi negro), para distinguir las tarjetas de un vistazo.
List<Color> _tono(int idInstitucion) => switch (idInstitucion % 4) {
      1 => const [Color(0xFF6B1B2D), Color(0xFF1E0A10)],
      2 => const [Color(0xFF16406B), Color(0xFF0A1424)],
      3 => const [Color(0xFF1F5A47), Color(0xFF0A1A14)],
      _ => const [Color(0xFF2B2B2B), Color(0xFF111111)],
    };

class _Base extends StatelessWidget {
  const _Base({required this.tarjeta, required this.child});

  final Tarjeta tarjeta;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    final tono = _tono(tarjeta.idInstitucion);
    return DecoratedBox(
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(20),
        gradient: LinearGradient(begin: Alignment.topLeft, end: Alignment.bottomRight, colors: tono),
        boxShadow: const [
          BoxShadow(color: Color(0x33000000), blurRadius: 18, offset: Offset(0, 10)),
          BoxShadow(color: Color(0x22000000), blurRadius: 4, offset: Offset(0, 2)),
        ],
      ),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(20),
        child: Stack(
          children: [
            // Brillo suave en curva: da volumen sin texto ni patrón.
            Positioned(
              right: -60,
              top: -80,
              child: Container(
                width: 220,
                height: 220,
                decoration: BoxDecoration(shape: BoxShape.circle, color: Colors.white.withValues(alpha: 0.06)),
              ),
            ),
            Positioned(
              left: -40,
              bottom: -110,
              child: Container(
                width: 240,
                height: 240,
                decoration: BoxDecoration(shape: BoxShape.circle, color: Colors.white.withValues(alpha: 0.04)),
              ),
            ),
            Positioned.fill(child: child),
          ],
        ),
      ),
    );
  }
}

const _blanco = Color(0xFFF7F4F1);
const _blancoTenue = Color(0xB3F7F4F1);

class _Frente extends StatelessWidget {
  const _Frente({required this.tarjeta});

  final Tarjeta tarjeta;

  @override
  Widget build(BuildContext context) {
    return _Base(
      tarjeta: tarjeta,
      child: LayoutBuilder(builder: (context, caja) {
        final e = caja.maxWidth / 320; // escala respecto a una tarjeta de 320 de ancho
        TextStyle st(double tam, {FontWeight peso = FontWeight.w600, Color color = _blanco, double esp = 0}) => TextStyle(
              fontFamily: AppTheme.familia,
              fontSize: tam * e,
              fontWeight: peso,
              color: color,
              letterSpacing: esp * e,
              fontFeatures: const [FontFeature.tabularFigures()],
              height: 1.1,
            );
        return Padding(
          padding: EdgeInsets.fromLTRB(20 * e, 18 * e, 20 * e, 16 * e),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Expanded(
                    child: Text(tarjeta.banco, maxLines: 1, overflow: TextOverflow.ellipsis, style: st(15, peso: FontWeight.w800)),
                  ),
                  Text(
                    switch (tarjeta.marca) {
                      'visa' => 'VISA',
                      'mastercard' => 'MASTERCARD',
                      _ => 'CRÉDITO',
                    },
                    style: st(9, peso: FontWeight.w700, color: _blancoTenue, esp: 2),
                  ),
                ],
              ),
              const Spacer(),
              Row(
                children: [
                  SizedBox(width: 40 * e, height: 30 * e, child: const CustomPaint(painter: _ChipPainter())),
                  SizedBox(width: 10 * e),
                  Icon(Icons.contactless_outlined, color: _blancoTenue, size: 24 * e),
                ],
              ),
              SizedBox(height: 14 * e),
              FittedBox(
                fit: BoxFit.scaleDown,
                alignment: Alignment.centerLeft,
                child: Text('••••  ••••  ••••  ${tarjeta.ultimos4}', style: st(19, peso: FontWeight.w700, esp: 1.5)),
              ),
              const Spacer(),
              Row(
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Text('VENCE ••/••', style: st(8.5, color: _blancoTenue, esp: 1)),
                        SizedBox(height: 4 * e),
                        Text(
                          (tarjeta.titularConfirmado ?? 'TITULAR').toUpperCase(),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: st(12, peso: FontWeight.w700, esp: 1),
                        ),
                      ],
                    ),
                  ),
                  SizedBox(width: 8 * e),
                  _LogoRed(marca: tarjeta.marca, escala: e),
                ],
              ),
            ],
          ),
        );
      }),
    );
  }
}

class _Reverso extends StatelessWidget {
  const _Reverso({required this.tarjeta});

  final Tarjeta tarjeta;

  @override
  Widget build(BuildContext context) {
    return _Base(
      tarjeta: tarjeta,
      child: LayoutBuilder(builder: (context, caja) {
        final e = caja.maxWidth / 320;
        return Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            SizedBox(height: 22 * e),
            SizedBox(height: 40 * e, child: const CustomPaint(painter: _BandaPainter())),
            SizedBox(height: 18 * e),
            Padding(
              padding: EdgeInsets.symmetric(horizontal: 18 * e),
              child: Row(
                children: [
                  Expanded(
                    flex: 3,
                    child: Container(
                      height: 30 * e,
                      decoration: BoxDecoration(color: _blanco, borderRadius: BorderRadius.circular(4 * e)),
                    ),
                  ),
                  SizedBox(width: 8 * e),
                  Container(
                    height: 30 * e,
                    width: 64 * e,
                    alignment: Alignment.center,
                    decoration: BoxDecoration(color: _blanco, borderRadius: BorderRadius.circular(4 * e)),
                    child: Text('CVV ***',
                        style: TextStyle(
                            fontFamily: AppTheme.familia,
                            fontSize: 11 * e,
                            fontWeight: FontWeight.w800,
                            color: const Color(0xFF111111))),
                  ),
                ],
              ),
            ),
            const Spacer(),
            Padding(
              padding: EdgeInsets.fromLTRB(18 * e, 0, 18 * e, 16 * e),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  Expanded(
                    child: Text(
                      'Ahorra+ solo conoce los últimos 4 dígitos de tu tarjeta.',
                      style: TextStyle(fontFamily: AppTheme.familia, fontSize: 9.5 * e, color: _blancoTenue, height: 1.3),
                    ),
                  ),
                  SizedBox(width: 8 * e),
                  _LogoRed(marca: tarjeta.marca, escala: e * 0.8),
                ],
              ),
            ),
          ],
        );
      }),
    );
  }
}

/// Logo de la red: dos círculos de Mastercard o la palabra VISA. Sin red conocida, nada.
class _LogoRed extends StatelessWidget {
  const _LogoRed({required this.marca, required this.escala});

  final String? marca;
  final double escala;

  @override
  Widget build(BuildContext context) {
    final e = escala;
    return switch (marca) {
      'mastercard' => SizedBox(width: 48 * e, height: 30 * e, child: const CustomPaint(painter: _MastercardPainter())),
      'visa' => Text(
          'VISA',
          style: TextStyle(
            fontFamily: AppTheme.familia,
            fontSize: 24 * e,
            fontWeight: FontWeight.w800,
            fontStyle: FontStyle.italic,
            letterSpacing: 1 * e,
            color: _blanco,
            height: 1,
          ),
        ),
      _ => const SizedBox.shrink(),
    };
  }
}

class _MastercardPainter extends CustomPainter {
  const _MastercardPainter();

  @override
  void paint(Canvas canvas, Size size) {
    final r = size.height / 2;
    final izq = Offset(r, r);
    final der = Offset(size.width - r, r);
    canvas.drawCircle(izq, r, Paint()..color = const Color(0xFFEB001B));
    canvas.drawCircle(der, r, Paint()..color = const Color(0xFFF79E1B));
    final a = Path()..addOval(Rect.fromCircle(center: izq, radius: r));
    final b = Path()..addOval(Rect.fromCircle(center: der, radius: r));
    canvas.drawPath(Path.combine(PathOperation.intersect, a, b), Paint()..color = const Color(0xFFFF5F00));
  }

  @override
  bool shouldRepaint(_MastercardPainter old) => false;
}

class _ChipPainter extends CustomPainter {
  const _ChipPainter();

  @override
  void paint(Canvas canvas, Size size) {
    final rect = RRect.fromRectAndRadius(Offset.zero & size, Radius.circular(size.height * 0.2));
    canvas.drawRRect(
      rect,
      Paint()
        ..shader = const LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [Color(0xFFF3D27A), Color(0xFFC9A04A), Color(0xFFE9C46A)],
        ).createShader(Offset.zero & size),
    );
    final linea = Paint()
      ..color = const Color(0x66523A0E)
      ..strokeWidth = 1
      ..style = PaintingStyle.stroke;
    final w = size.width, h = size.height;
    canvas.drawLine(Offset(0, h / 3), Offset(w * 0.32, h / 3), linea);
    canvas.drawLine(Offset(0, h * 2 / 3), Offset(w * 0.32, h * 2 / 3), linea);
    canvas.drawLine(Offset(w * 0.68, h / 3), Offset(w, h / 3), linea);
    canvas.drawLine(Offset(w * 0.68, h * 2 / 3), Offset(w, h * 2 / 3), linea);
    canvas.drawRRect(
      RRect.fromRectAndRadius(Rect.fromLTWH(w * 0.32, h * 0.18, w * 0.36, h * 0.64), Radius.circular(h * 0.12)),
      linea,
    );
  }

  @override
  bool shouldRepaint(_ChipPainter old) => false;
}

/// Banda magnética con trama diagonal oscura.
class _BandaPainter extends CustomPainter {
  const _BandaPainter();

  @override
  void paint(Canvas canvas, Size size) {
    canvas.drawRect(Offset.zero & size, Paint()..color = const Color(0xFF1C1C1C));
    final p = Paint()
      ..color = const Color(0xFF2E2E2E)
      ..strokeWidth = 7;
    canvas.save();
    canvas.clipRect(Offset.zero & size);
    for (var x = -size.height; x < size.width + size.height; x += 14) {
      canvas.drawLine(Offset(x, size.height), Offset(x + size.height, 0), p);
    }
    canvas.restore();
  }

  @override
  bool shouldRepaint(_BandaPainter old) => false;
}
