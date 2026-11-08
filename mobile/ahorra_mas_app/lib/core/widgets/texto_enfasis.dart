import 'package:flutter/material.dart';

/// Énfasis tipográfico para nombres propios dentro de un texto (metas, objetivos…).
///
/// Los textos — propios o del backend — marcan el nombre entre comillas angulares
/// («Viaje en marzo»). En pantalla las comillas no se muestran: el contenido va en cursiva.
final RegExp _marcado = RegExp('«([^«»]*)»|<<(.*?)>>');

/// Separa [texto] en tramos; lo marcado sale en cursiva sobre [estilo].
List<InlineSpan> tramosConEnfasis(String texto, {TextStyle? estilo}) {
  final tramos = <InlineSpan>[];
  var desde = 0;
  for (final m in _marcado.allMatches(texto)) {
    if (m.start > desde) tramos.add(TextSpan(text: texto.substring(desde, m.start), style: estilo));
    tramos.add(TextSpan(
      text: m.group(1) ?? m.group(2) ?? '',
      style: (estilo ?? const TextStyle()).copyWith(fontStyle: FontStyle.italic),
    ));
    desde = m.end;
  }
  if (desde < texto.length) tramos.add(TextSpan(text: texto.substring(desde), style: estilo));
  return tramos;
}

/// El mismo texto, sin marcas (para semántica o lugares que solo aceptan `String`).
String sinMarcasDeEnfasis(String texto) =>
    texto.replaceAllMapped(_marcado, (m) => m.group(1) ?? m.group(2) ?? '');

/// `Text` que respeta el énfasis «…» → cursiva.
class TextoEnfasis extends StatelessWidget {
  const TextoEnfasis(
    this.texto, {
    super.key,
    this.style,
    this.textAlign,
    this.maxLines,
    this.overflow,
  });

  final String texto;
  final TextStyle? style;
  final TextAlign? textAlign;
  final int? maxLines;
  final TextOverflow? overflow;

  @override
  Widget build(BuildContext context) => Text.rich(
        TextSpan(children: tramosConEnfasis(texto), style: style),
        textAlign: textAlign,
        maxLines: maxLines,
        overflow: overflow,
      );
}
