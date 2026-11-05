/// Utilidades de dinero de Ahorra+.
///
/// Regla de oro: todos los montos son **enteros en céntimos** (`int`, 64 bits en móvil).
/// Nunca se usa `double` para dinero: solo se formatea y se convierte con aritmética entera.
///
/// * Tasa BCV: `valorBsX10000` = Bs por 1 USD × 10⁴ (498,7321 Bs/USD → 4987321).
/// * Porcentajes: puntos básicos (`pb`, 10 000 = 100 %).
/// * Multiplicadores: `x100` (150 = 1,5x).
class Dinero {
  Dinero._();

  /// `Bs 1.234,56`
  static String bs(int cts) => '${cts < 0 ? '-' : ''}Bs ${_numero(cts.abs())}';

  /// `$1.234,56`
  static String usd(int cts) => '${cts < 0 ? '-' : ''}\$${_numero(cts.abs())}';

  /// Formatea según la moneda ISO del contrato (`VES`, `USD`, `USDT`).
  static String enMoneda(int cts, String moneda) {
    switch (moneda.toUpperCase()) {
      case 'VES':
      case 'BS':
        return bs(cts);
      case 'USDT':
        return '${_numero(cts.abs())} USDT${cts < 0 ? ' (-)' : ''}';
      default:
        return usd(cts);
    }
  }

  /// Número sin símbolo en formato es_VE: `1.234,56`.
  static String numero(int cts) => '${cts < 0 ? '-' : ''}${_numero(cts.abs())}';

  static String _numero(int ctsPositivos) {
    final entero = ctsPositivos ~/ 100;
    final decimales = (ctsPositivos % 100).toString().padLeft(2, '0');
    return '${_agrupar(entero)},$decimales';
  }

  static String _agrupar(int entero) {
    final s = entero.toString();
    final buf = StringBuffer();
    for (var i = 0; i < s.length; i++) {
      if (i > 0 && (s.length - i) % 3 == 0) buf.write('.');
      buf.write(s[i]);
    }
    return buf.toString();
  }

  /// División entera con redondeo "half-up" (alejándose de cero en el .5).
  static int dividirRedondeando(int numerador, int denominador) {
    if (denominador == 0) throw ArgumentError('denominador 0');
    final negativo = (numerador < 0) != (denominador < 0);
    final n = numerador.abs();
    final d = denominador.abs();
    final q = (n + d ~/ 2) ~/ d;
    return negativo ? -q : q;
  }

  /// Convierte céntimos de USD a céntimos de Bs con la tasa BCV (× 10⁴).
  static int usdABs(int usdCts, int tasaX10000) =>
      dividirRedondeando(usdCts * tasaX10000, 10000);

  /// Convierte céntimos de Bs a céntimos de USD con la tasa BCV (× 10⁴).
  static int bsAUsd(int bsCts, int tasaX10000) =>
      dividirRedondeando(bsCts * 10000, tasaX10000);

  /// `498,73 Bs/USD` (la tasa se muestra con 2 decimales, redondeada).
  static String tasa(int tasaX10000) =>
      '${_numero(dividirRedondeando(tasaX10000, 100))} Bs/USD';

  /// `8,14 %` a partir de puntos básicos.
  static String porcentajePb(int pb) => '${numero(pb)} %';

  /// `1,25x` (sin ceros sobrantes: 150 → `1,5x`, 100 → `1x`).
  static String multiplicador(int x100) {
    final entero = x100 ~/ 100;
    final resto = x100 % 100;
    if (resto == 0) return '${entero}x';
    var dec = resto.toString().padLeft(2, '0');
    if (dec.endsWith('0')) dec = dec.substring(0, 1);
    return '$entero,${dec}x';
  }

  /// Progreso en fracción 0..1 a partir de puntos básicos (solo para pintar barras).
  static double fraccionPb(int pb) => (pb.clamp(0, 10000)) / 10000;

  /// Interpreta lo que escribe el usuario ("2,40", "2.40", "1.234,56", "1,234.56", "\$ 15")
  /// y devuelve céntimos, o `null` si no es un monto válido. Sin punto flotante.
  ///
  /// Reglas:
  /// * Si aparecen `.` y `,`, el último que aparece es el separador decimal.
  /// * Si aparece un solo tipo de separador una única vez seguido de 1–2 dígitos, es decimal
  ///   ("2.40", "2,4"); seguido de exactamente 3 dígitos es separador de miles ("1.234").
  /// * Si un mismo separador aparece varias veces, son separadores de miles ("1.234.567").
  /// * Máximo 2 decimales. No se aceptan negativos.
  static int? parsear(String entrada) {
    var s = entrada.trim().replaceAll(RegExp(r'bs\.?|usd|\$|\s', caseSensitive: false), '');
    if (s.isEmpty) return null;
    if (!RegExp(r'^[0-9.,]+$').hasMatch(s)) return null;

    final ultimoPunto = s.lastIndexOf('.');
    final ultimaComa = s.lastIndexOf(',');
    String? separadorDecimal;

    if (ultimoPunto >= 0 && ultimaComa >= 0) {
      separadorDecimal = ultimoPunto > ultimaComa ? '.' : ',';
    } else if (ultimoPunto >= 0 || ultimaComa >= 0) {
      final sep = ultimoPunto >= 0 ? '.' : ',';
      final ocurrencias = sep.allMatches(s).length;
      final digitosDespues = s.length - s.lastIndexOf(sep) - 1;
      if (ocurrencias == 1 && digitosDespues != 3) {
        separadorDecimal = sep;
      }
    }

    String parteEntera;
    String parteDecimal = '';
    if (separadorDecimal != null) {
      final idx = s.lastIndexOf(separadorDecimal);
      parteEntera = s.substring(0, idx);
      parteDecimal = s.substring(idx + 1);
      if (parteDecimal.contains('.') || parteDecimal.contains(',')) return null;
    } else {
      parteEntera = s;
    }

    final separadorMiles = separadorDecimal == null
        ? null
        : (separadorDecimal == '.' ? ',' : '.');
    if (separadorDecimal != null && parteEntera.contains(separadorDecimal)) return null;
    if (separadorMiles != null || separadorDecimal == null) {
      // Valida grupos de miles: 1.234.567 (grupos de 3 tras el primero).
      final grupos = parteEntera.split(RegExp(r'[.,]'));
      if (grupos.length > 1) {
        if (grupos.first.isEmpty || grupos.first.length > 3) return null;
        if (grupos.skip(1).any((g) => g.length != 3)) return null;
      }
    }
    parteEntera = parteEntera.replaceAll(RegExp(r'[.,]'), '');
    if (parteEntera.isEmpty) parteEntera = '0';
    if (parteDecimal.length > 2) return null;
    if (separadorDecimal != null && parteDecimal.isEmpty) return null;
    if (parteEntera.length > 13) return null; // evita desbordes absurdos

    final entero = int.parse(parteEntera);
    final dec = int.parse(parteDecimal.padRight(2, '0'));
    return entero * 100 + dec;
  }

  /// Representación editable para un campo de texto: 240 → "2,40".
  static String paraEditar(int cts) =>
      '${cts ~/ 100},${(cts % 100).toString().padLeft(2, '0')}';
}
