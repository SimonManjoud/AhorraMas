// CONTRATO DE DIRECCIÓN · "La Libreta Viva", versión moderna (DESIGN.md)
// THESIS: el dinero de la persona como su libreta de ahorros, hoy en una app cálida
//   y táctil: tapa vino, hojas claras con esquinas suaves, cifras grandes y legibles.
//   Rechaza el degradado neón de la fintech genérica y el verde dinero.
// OWN-WORLD: vino tinto (#5C1726) para la tapa (tarjeta principal, botón primario,
//   navegación); fondo verde pálido liso (#EEF2EE), sin tramas; láminas claras con
//   radio 24; botones píldora; violeta (#5A3E9E) solo para el Sello de compromiso.
//   Archivo para todo; Archivo Condensada solo en cifras de titular; cifras tabulares.
// STORY: la persona ve su posición global en USD y Bs a tasa BCV, toca una porción del
//   gráfico para saber qué cuenta es y aparta la cuota de hoy en un toque.
// FIRST VIEWPORT: tapa vino con el total en USD, Bs y la tasa BCV del día en una
//   píldora; debajo, la dona de distribución por cuenta con su leyenda-lista.
// FINISH: unreviewed and undocumented is unfinished; this build ends with the
//   finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance.

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

/// Colores semánticos propios de Ahorra+ (además del `ColorScheme`).
///
/// Los nombres heredados (`cofre`, `cofreContenedor`, `alCofre`…) se conservan para
/// las pantallas existentes; en el mundo Libreta apuntan a tinta y hoja.
@immutable
class AhorraColores extends ThemeExtension<AhorraColores> {
  const AhorraColores({
    required this.tapa,
    required this.tapaHonda,
    required this.sobreTapa,
    required this.sobreTapaTenue,
    required this.hoja,
    required this.hojaHonda,
    required this.superficie,
    required this.guilloche,
    required this.regla,
    required this.reglaSuave,
    required this.tinta,
    required this.tintaGris,
    required this.sello,
    required this.cofre,
    required this.cofreContenedor,
    required this.alCofre,
    required this.exito,
    required this.enProceso,
    required this.peligro,
    required this.colchon,
  });

  /// Tapa de vinilo: cabecera, botón primario, indicador de navegación. Solo regiones completas.
  final Color tapa;
  final Color tapaHonda;
  final Color sobreTapa;
  final Color sobreTapaTenue;

  /// Fondo liso de la app y su nivel inferior (pistas, rellenos).
  final Color hoja;
  final Color hojaHonda;

  /// Lámina clara sobre la hoja: tarjetas, campos, barra de navegación.
  final Color superficie;

  /// Heredado del fondo guilloché (ya no se pinta); queda como verde de apoyo.
  final Color guilloche;
  final Color regla;
  final Color reglaSuave;
  final Color tinta;
  final Color tintaGris;

  /// Tinta del sello de goma: un único sello por vista.
  final Color sello;

  /// Acento del Cofre (iconos, cifras destacadas): en la Libreta es tinta.
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
        tapa: tapa,
        tapaHonda: tapaHonda,
        sobreTapa: sobreTapa,
        sobreTapaTenue: sobreTapaTenue,
        hoja: hoja,
        hojaHonda: hojaHonda,
        superficie: superficie,
        guilloche: guilloche,
        regla: regla,
        reglaSuave: reglaSuave,
        tinta: tinta,
        tintaGris: tintaGris,
        sello: sello,
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
    Color l(Color a, Color b) => Color.lerp(a, b, t)!;
    return AhorraColores(
      tapa: l(tapa, other.tapa),
      tapaHonda: l(tapaHonda, other.tapaHonda),
      sobreTapa: l(sobreTapa, other.sobreTapa),
      sobreTapaTenue: l(sobreTapaTenue, other.sobreTapaTenue),
      hoja: l(hoja, other.hoja),
      hojaHonda: l(hojaHonda, other.hojaHonda),
      superficie: l(superficie, other.superficie),
      guilloche: l(guilloche, other.guilloche),
      regla: l(regla, other.regla),
      reglaSuave: l(reglaSuave, other.reglaSuave),
      tinta: l(tinta, other.tinta),
      tintaGris: l(tintaGris, other.tintaGris),
      sello: l(sello, other.sello),
      cofre: l(cofre, other.cofre),
      cofreContenedor: l(cofreContenedor, other.cofreContenedor),
      alCofre: l(alCofre, other.alCofre),
      exito: l(exito, other.exito),
      enProceso: l(enProceso, other.enProceso),
      peligro: l(peligro, other.peligro),
      colchon: l(colchon, other.colchon),
    );
  }
}

class AppTheme {
  AppTheme._();

  static const familia = 'Archivo';
  static const familiaCondensada = 'ArchivoCondensado';

  static const _coloresClaro = AhorraColores(
    tapa: Color(0xFF5C1726),
    tapaHonda: Color(0xFF43101C),
    sobreTapa: Color(0xFFF7ECEE),
    sobreTapaTenue: Color(0xFFDDBBC3),
    hoja: Color(0xFFEEF2EE),
    hojaHonda: Color(0xFFE1E8E2),
    superficie: Color(0xFFFBFCFA),
    guilloche: Color(0xFF96B29D),
    regla: Color(0xFFB4C2B7),
    reglaSuave: Color(0xFFDCE4DD),
    tinta: Color(0xFF1B1F1C),
    tintaGris: Color(0xFF47524A),
    sello: Color(0xFF5A3E9E),
    cofre: Color(0xFF1B1F1C),
    cofreContenedor: Color(0xFFE1E8E2),
    alCofre: Color(0xFF1B1F1C),
    exito: Color(0xFF2F6B45),
    enProceso: Color(0xFF47524A),
    peligro: Color(0xFFA3271F),
    colchon: Color(0xFF47524A),
  );

  /// "Libreta de noche": esquema propio, no una inversión.
  static const _coloresOscuro = AhorraColores(
    tapa: Color(0xFF7A2236),
    tapaHonda: Color(0xFF5C1726),
    sobreTapa: Color(0xFFF7ECEE),
    sobreTapaTenue: Color(0xFFE3C3CB),
    hoja: Color(0xFF111613),
    hojaHonda: Color(0xFF232B25),
    superficie: Color(0xFF1A211C),
    guilloche: Color(0xFF5E7464),
    regla: Color(0xFF4A574D),
    reglaSuave: Color(0xFF2A322C),
    tinta: Color(0xFFE2EAE3),
    tintaGris: Color(0xFFA9B8AD),
    sello: Color(0xFFA794E8),
    cofre: Color(0xFFE2EAE3),
    cofreContenedor: Color(0xFF232B25),
    alCofre: Color(0xFFE2EAE3),
    exito: Color(0xFF8FD3A6),
    enProceso: Color(0xFFA9B8AD),
    peligro: Color(0xFFFFB4A8),
    colchon: Color(0xFFA9B8AD),
  );

  /// Colores categóricos de gráficos (orden fijo, nunca cíclico; validados para daltonismo
  /// con el validador de dataviz contra la lámina clara #FBFCFA y la oscura #1A211C).
  static const _serieClaro = [
    Color(0xFF2A78D6), Color(0xFFEB6834), Color(0xFF1BAF7A), Color(0xFFEDA100),
    Color(0xFFE87BA4), Color(0xFF008300), Color(0xFF6250D6), Color(0xFFE34948),
  ];
  static const _serieOscuro = [
    Color(0xFF3987E5), Color(0xFFD95926), Color(0xFF199E70), Color(0xFFC98500),
    Color(0xFFD55181), Color(0xFF008300), Color(0xFF9085E9), Color(0xFFE66767),
  ];

  /// Color de la serie [i] (0–7). Más allá de 8 series se agrupa en "Otras" con [serieOtras].
  static Color serie(BuildContext context, int i) =>
      (Theme.of(context).brightness == Brightness.dark ? _serieOscuro : _serieClaro)[i % 8];

  static const maxSeries = 8;

  static Color serieOtras(BuildContext context) =>
      Theme.of(context).brightness == Brightness.dark ? const Color(0xFF6B756E) : const Color(0xFF9AA59D);

  static ThemeData claro() => _base(Brightness.light, _coloresClaro);

  static ThemeData oscuro() => _base(Brightness.dark, _coloresOscuro);

  static ColorScheme _esquema(Brightness brillo, AhorraColores c) {
    final claro = brillo == Brightness.light;
    return ColorScheme(
      brightness: brillo,
      primary: claro ? c.tapa : const Color(0xFFF2B7C3),
      onPrimary: claro ? c.sobreTapa : const Color(0xFF4A1020),
      primaryContainer: claro ? const Color(0xFFEBD3D8) : c.tapaHonda,
      onPrimaryContainer: claro ? const Color(0xFF3E0F19) : c.sobreTapa,
      secondary: c.tintaGris,
      onSecondary: c.hoja,
      secondaryContainer: c.hojaHonda,
      onSecondaryContainer: c.tinta,
      tertiary: c.sello,
      onTertiary: claro ? Colors.white : const Color(0xFF241446),
      tertiaryContainer: claro ? const Color(0xFFE6DEF6) : const Color(0xFF3B2A6E),
      onTertiaryContainer: claro ? const Color(0xFF2A1A55) : const Color(0xFFE6DEF6),
      error: c.peligro,
      onError: claro ? Colors.white : const Color(0xFF5C0F08),
      errorContainer: claro ? const Color(0xFFF6D9D5) : const Color(0xFF7A1F17),
      onErrorContainer: claro ? const Color(0xFF410E0A) : const Color(0xFFFFDAD4),
      surface: c.superficie,
      onSurface: c.tinta,
      onSurfaceVariant: c.tintaGris,
      surfaceContainerLowest: claro ? Colors.white : const Color(0xFF0F1411),
      surfaceContainerLow: c.superficie,
      surfaceContainer: c.hoja,
      surfaceContainerHigh: c.hojaHonda,
      surfaceContainerHighest: claro ? const Color(0xFFD5DED7) : const Color(0xFF2C352E),
      outline: claro ? const Color(0xFF6E7F72) : const Color(0xFF7D8D81),
      outlineVariant: c.reglaSuave,
      inverseSurface: claro ? const Color(0xFF2B302C) : c.tinta,
      onInverseSurface: c.hoja,
      inversePrimary: claro ? const Color(0xFFF2B7C3) : c.tapa,
      surfaceTint: Colors.transparent,
      shadow: Colors.black,
      scrim: Colors.black,
    );
  }

  static TextTheme _tipos() {
    const tabular = [FontFeature.tabularFigures()];
    TextStyle a(double size, FontWeight w, {double alto = 1.4, double espaciado = 0}) => TextStyle(
        fontFamily: familia, fontSize: size, fontWeight: w, height: alto, letterSpacing: espaciado);
    TextStyle c(double size, FontWeight w, {double alto = 1.05, double espaciado = 0}) => TextStyle(
        fontFamily: familiaCondensada,
        fontSize: size,
        fontWeight: w,
        height: alto,
        letterSpacing: espaciado,
        fontFeatures: tabular);
    return TextTheme(
      displayLarge: c(56, FontWeight.w700, alto: 1, espaciado: -1),
      displayMedium: c(48, FontWeight.w700, alto: 1, espaciado: -0.8),
      displaySmall: c(38, FontWeight.w700, alto: 1.05, espaciado: -0.5),
      headlineLarge: a(30, FontWeight.w800, alto: 1.15, espaciado: -0.6),
      headlineMedium: a(26, FontWeight.w800, alto: 1.15, espaciado: -0.5),
      headlineSmall: a(22, FontWeight.w700, alto: 1.2, espaciado: -0.3),
      titleLarge: a(20, FontWeight.w700, alto: 1.25),
      titleMedium: a(17, FontWeight.w700, alto: 1.25),
      titleSmall: a(14, FontWeight.w600, alto: 1.3),
      bodyLarge: a(16, FontWeight.w400),
      bodyMedium: a(14, FontWeight.w400),
      bodySmall: a(12, FontWeight.w400, alto: 1.35),
      labelLarge: a(15, FontWeight.w600, alto: 1.2),
      labelMedium: a(12, FontWeight.w600, alto: 1.2),
      labelSmall: a(11, FontWeight.w600, alto: 1.2, espaciado: 0.3),
    );
  }

  /// Cifras: montos, tasas, fechas y días (Archivo con números tabulares para que alineen).
  static TextStyle cifra({double tamano = 13, FontWeight peso = FontWeight.w600, Color? color}) => TextStyle(
        fontFamily: familia,
        fontSize: tamano,
        fontWeight: peso,
        height: 1.3,
        color: color,
        fontFeatures: const [FontFeature.tabularFigures()],
      );

  /// Etiqueta secundaria (tipo de cuenta, encabezado de bloque): pequeña y en tono gris.
  static TextStyle rubrica({Color? color, double tamano = 12}) => TextStyle(
        fontFamily: familia,
        fontSize: tamano,
        fontWeight: FontWeight.w500,
        height: 1.3,
        color: color,
      );

  static ThemeData _base(Brightness brillo, AhorraColores c) {
    final esquema = _esquema(brillo, c);
    final tipos = _tipos();
    const pildora = StadiumBorder();
    const tituloBoton = TextStyle(fontFamily: familia, fontWeight: FontWeight.w700, fontSize: 15, height: 1.2);
    final campo = OutlineInputBorder(borderRadius: BorderRadius.circular(16), borderSide: BorderSide(color: c.reglaSuave));
    return ThemeData(
      useMaterial3: true,
      brightness: brillo,
      colorScheme: esquema,
      extensions: [c],
      textTheme: tipos,
      scaffoldBackgroundColor: c.hoja,
      canvasColor: c.hoja,
      dividerColor: c.reglaSuave,
      appBarTheme: AppBarTheme(
        centerTitle: false,
        backgroundColor: c.hoja,
        foregroundColor: c.tinta,
        surfaceTintColor: Colors.transparent,
        elevation: 0,
        scrolledUnderElevation: 0,
        systemOverlayStyle: (brillo == Brightness.light ? SystemUiOverlayStyle.dark : SystemUiOverlayStyle.light)
            .copyWith(statusBarColor: Colors.transparent),
        titleTextStyle: TextStyle(
          fontFamily: familia,
          fontWeight: FontWeight.w800,
          fontSize: 22,
          height: 1.15,
          letterSpacing: -0.3,
          color: c.tinta,
        ),
        iconTheme: IconThemeData(color: c.tinta),
        actionsIconTheme: IconThemeData(color: c.tinta),
      ),
      cardTheme: CardThemeData(
        elevation: 0,
        color: c.superficie,
        surfaceTintColor: Colors.transparent,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(24)),
        margin: EdgeInsets.zero,
        clipBehavior: Clip.antiAlias,
      ),
      dividerTheme: DividerThemeData(color: c.reglaSuave, thickness: 1, space: 1),
      listTileTheme: ListTileThemeData(
        iconColor: c.tintaGris,
        textColor: c.tinta,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: c.superficie,
        contentPadding: const EdgeInsets.symmetric(horizontal: 18, vertical: 16),
        labelStyle: TextStyle(fontFamily: familia, color: c.tintaGris),
        floatingLabelStyle: TextStyle(fontFamily: familia, color: c.tinta, fontWeight: FontWeight.w600),
        border: campo,
        enabledBorder: campo,
        focusedBorder: campo.copyWith(borderSide: BorderSide(color: esquema.primary, width: 2)),
        errorBorder: campo.copyWith(borderSide: BorderSide(color: c.peligro)),
        focusedErrorBorder: campo.copyWith(borderSide: BorderSide(color: c.peligro, width: 2)),
        errorStyle: TextStyle(fontFamily: familia, color: c.peligro),
      ),
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(
          backgroundColor: c.tapa,
          foregroundColor: c.sobreTapa,
          disabledBackgroundColor: c.hojaHonda,
          disabledForegroundColor: c.tintaGris,
          minimumSize: const Size(64, 52),
          padding: const EdgeInsets.symmetric(horizontal: 24),
          shape: pildora,
          textStyle: tituloBoton,
          elevation: 0,
        ),
      ),
      outlinedButtonTheme: OutlinedButtonThemeData(
        style: OutlinedButton.styleFrom(
          foregroundColor: c.tinta,
          backgroundColor: c.superficie,
          side: BorderSide(color: c.reglaSuave, width: 1.5),
          minimumSize: const Size(64, 48),
          padding: const EdgeInsets.symmetric(horizontal: 20),
          shape: pildora,
          textStyle: tituloBoton.copyWith(fontSize: 14),
        ),
      ),
      textButtonTheme: TextButtonThemeData(
        style: TextButton.styleFrom(
          foregroundColor: esquema.primary,
          minimumSize: const Size(48, 44),
          padding: const EdgeInsets.symmetric(horizontal: 14),
          shape: pildora,
          textStyle: tituloBoton.copyWith(fontSize: 14),
        ),
      ),
      segmentedButtonTheme: SegmentedButtonThemeData(
        style: SegmentedButton.styleFrom(
          backgroundColor: c.superficie,
          selectedBackgroundColor: esquema.primaryContainer,
          selectedForegroundColor: esquema.onPrimaryContainer,
          foregroundColor: c.tintaGris,
          side: BorderSide(color: c.reglaSuave, width: 1.5),
          textStyle: tituloBoton.copyWith(fontSize: 15),
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 17),
        ),
      ),
      floatingActionButtonTheme: FloatingActionButtonThemeData(
        backgroundColor: c.tapa,
        foregroundColor: c.sobreTapa,
        elevation: 0,
        focusElevation: 0,
        hoverElevation: 0,
        highlightElevation: 0,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
        extendedTextStyle: tituloBoton,
      ),
      chipTheme: ChipThemeData(
        backgroundColor: c.hoja,
        selectedColor: esquema.primaryContainer,
        side: BorderSide.none,
        shape: pildora,
        labelStyle: TextStyle(fontFamily: familia, color: c.tinta, fontWeight: FontWeight.w600, fontSize: 13),
      ),
      progressIndicatorTheme: ProgressIndicatorThemeData(
        color: c.tapa,
        linearTrackColor: c.hojaHonda,
        circularTrackColor: Colors.transparent,
      ),
      snackBarTheme: SnackBarThemeData(
        backgroundColor: c.tinta,
        contentTextStyle: TextStyle(fontFamily: familia, color: c.hoja, fontSize: 14),
        actionTextColor: c.hoja,
        behavior: SnackBarBehavior.floating,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      ),
      dialogTheme: DialogThemeData(
        backgroundColor: c.superficie,
        surfaceTintColor: Colors.transparent,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(28)),
      ),
      bottomSheetTheme: BottomSheetThemeData(
        backgroundColor: c.superficie,
        surfaceTintColor: Colors.transparent,
        shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(28))),
      ),
      expansionTileTheme: ExpansionTileThemeData(
        shape: const RoundedRectangleBorder(side: BorderSide.none),
        collapsedShape: const RoundedRectangleBorder(side: BorderSide.none),
        iconColor: c.tinta,
        collapsedIconColor: c.tintaGris,
      ),
      navigationBarTheme: NavigationBarThemeData(
        height: 72,
        elevation: 0,
        backgroundColor: c.superficie,
        surfaceTintColor: Colors.transparent,
        indicatorColor: esquema.primaryContainer,
        indicatorShape: const StadiumBorder(),
        labelBehavior: NavigationDestinationLabelBehavior.alwaysShow,
        iconTheme: WidgetStateProperty.resolveWith(
          (s) => IconThemeData(size: 24, color: s.contains(WidgetState.selected) ? esquema.onPrimaryContainer : c.tintaGris),
        ),
        labelTextStyle: WidgetStateProperty.resolveWith(
          (s) => TextStyle(
            fontFamily: familia,
            fontSize: 12,
            fontWeight: s.contains(WidgetState.selected) ? FontWeight.w700 : FontWeight.w500,
            color: s.contains(WidgetState.selected) ? c.tinta : c.tintaGris,
          ),
        ),
      ),
    );
  }
}
