import 'package:intl/intl.dart';

/// Formatos de fecha en español (es_VE). Requiere `initializeDateFormatting('es')` en `main`.
class Fechas {
  Fechas._();

  /// `yyyy-MM-dd` para el contrato.
  static String iso(DateTime d) =>
      '${d.year.toString().padLeft(4, '0')}-${d.month.toString().padLeft(2, '0')}-${d.day.toString().padLeft(2, '0')}';

  /// `04/11/2026`
  static String corta(DateTime? d) => d == null ? '—' : DateFormat('dd/MM/yyyy').format(d.toLocal());

  /// `4 nov 2026`
  static String media(DateTime? d) => d == null ? '—' : DateFormat('d MMM y', 'es').format(d.toLocal());

  /// `15 de marzo de 2027`
  static String larga(DateTime? d) =>
      d == null ? '—' : DateFormat("d 'de' MMMM 'de' y", 'es').format(d.toLocal());

  /// `04/11/2026 10:15`
  static String conHora(DateTime? d) =>
      d == null ? '—' : DateFormat('dd/MM/yyyy HH:mm').format(d.toLocal());

  /// `10:15`
  static String hora(DateTime? d) => d == null ? '—' : DateFormat('HH:mm').format(d.toLocal());

  /// `noviembre 2026`
  static String mesAnio(int anio, int mes) =>
      DateFormat('MMMM y', 'es').format(DateTime(anio, mes));

  /// Fecha corta: `07 nov`.
  static String libreta(DateTime? d) => d == null
      ? '—'
      : '${d.toLocal().day.toString().padLeft(2, '0')} ${_mesCorto(d.toLocal().month)}';

  /// Fecha corta con año: `15 mar 2027`.
  static String libretaAnio(DateTime? d) => d == null ? '—' : '${libreta(d)} ${d.toLocal().year}';

  static String _mesCorto(int mes) =>
      const ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'][mes - 1];

  /// Letra del día de la semana: L M M J V S D.
  static String inicialDia(DateTime d) => const ['L', 'M', 'M', 'J', 'V', 'S', 'D'][d.weekday - 1];

  static DateTime soloFecha(DateTime d) => DateTime(d.year, d.month, d.day);
}
