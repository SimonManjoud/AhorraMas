import 'package:flutter/material.dart';

/// Colores semánticos propios de Ahorra+ (además del `ColorScheme`).
@immutable
class AhorraColores extends ThemeExtension<AhorraColores> {
  const AhorraColores({
    required this.cofre,
    required this.cofreContenedor,
    required this.alCofre,
    required this.exito,
    required this.enProceso,
    required this.peligro,
    required this.colchon,
  });

  /// Ámbar del Cofre Protector.
  final Color cofre;
  final Color cofreContenedor;
  final Color alCofre;
  final Color exito;
  final Color enProceso;
  final Color peligro;
  final Color colchon;

  static AhorraColores of(BuildContext context) =>
      Theme.of(context).extension<AhorraColores>() ?? AppTheme._coloresClaro;

  @override
  AhorraColores copyWith({Color? cofre}) => AhorraColores(
        cofre: cofre ?? this.cofre,
        cofreContenedor: cofreContenedor,
        alCofre: alCofre,
        exito: exito,
        enProceso: enProceso,
        peligro: peligro,
        colchon: colchon,
      );

  @override
  AhorraColores lerp(ThemeExtension<AhorraColores>? other, double t) {
    if (other is! AhorraColores) return this;
    return AhorraColores(
      cofre: Color.lerp(cofre, other.cofre, t)!,
      cofreContenedor: Color.lerp(cofreContenedor, other.cofreContenedor, t)!,
      alCofre: Color.lerp(alCofre, other.alCofre, t)!,
      exito: Color.lerp(exito, other.exito, t)!,
      enProceso: Color.lerp(enProceso, other.enProceso, t)!,
      peligro: Color.lerp(peligro, other.peligro, t)!,
      colchon: Color.lerp(colchon, other.colchon, t)!,
    );
  }
}

class AppTheme {
  AppTheme._();

  /// Verde azulado profundo, sereno, de finanzas.
  static const semilla = Color(0xFF0E6B5C);

  static const _coloresClaro = AhorraColores(
    cofre: Color(0xFFE8A200),
    cofreContenedor: Color(0xFFFFF1CC),
    alCofre: Color(0xFF3D2A00),
    exito: Color(0xFF1B7F4B),
    enProceso: Color(0xFF2C6CB0),
    peligro: Color(0xFFB3261E),
    colchon: Color(0xFF5B8DEF),
  );

  static const _coloresOscuro = AhorraColores(
    cofre: Color(0xFFFFC23D),
    cofreContenedor: Color(0xFF4A3600),
    alCofre: Color(0xFFFFE6A6),
    exito: Color(0xFF6FD69C),
    enProceso: Color(0xFF8EC1FF),
    peligro: Color(0xFFFFB4AB),
    colchon: Color(0xFF9DBBFF),
  );

  static ThemeData claro() => _base(Brightness.light, _coloresClaro);

  static ThemeData oscuro() => _base(Brightness.dark, _coloresOscuro);

  static ThemeData _base(Brightness brillo, AhorraColores extra) {
    final esquema = ColorScheme.fromSeed(
      seedColor: semilla,
      brightness: brillo,
      tertiary: extra.cofre,
    );
    return ThemeData(
      useMaterial3: true,
      colorScheme: esquema,
      extensions: [extra],
      scaffoldBackgroundColor: esquema.surface,
      appBarTheme: AppBarTheme(
        centerTitle: false,
        backgroundColor: esquema.surface,
        foregroundColor: esquema.onSurface,
        elevation: 0,
        scrolledUnderElevation: 1,
      ),
      cardTheme: CardThemeData(
        elevation: 0,
        color: esquema.surfaceContainerLow,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
        margin: EdgeInsets.zero,
      ),
      inputDecorationTheme: InputDecorationTheme(
        border: OutlineInputBorder(borderRadius: BorderRadius.circular(14)),
        filled: true,
        fillColor: esquema.surfaceContainerHighest.withValues(alpha: 0.4),
      ),
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(
          minimumSize: const Size(64, 52),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
          textStyle: const TextStyle(fontWeight: FontWeight.w600, fontSize: 16),
        ),
      ),
      outlinedButtonTheme: OutlinedButtonThemeData(
        style: OutlinedButton.styleFrom(
          minimumSize: const Size(64, 48),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
        ),
      ),
      chipTheme: ChipThemeData(shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12))),
      navigationBarTheme: NavigationBarThemeData(
        indicatorColor: esquema.primaryContainer,
        labelBehavior: NavigationDestinationLabelBehavior.alwaysShow,
      ),
    );
  }
}
