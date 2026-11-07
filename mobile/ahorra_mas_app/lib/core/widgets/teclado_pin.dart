import 'package:flutter/material.dart';

/// Indicador de dígitos del PIN (puntos).
class PuntosPin extends StatelessWidget {
  const PuntosPin({super.key, required this.longitud, this.maximo = 6, this.error = false});

  final int longitud;
  final int maximo;
  final bool error;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return Row(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        for (var i = 0; i < maximo; i++)
          AnimatedContainer(
            duration: const Duration(milliseconds: 150),
            margin: const EdgeInsets.symmetric(horizontal: 7),
            width: 14,
            height: 14,
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              color: i < longitud ? (error ? cs.error : cs.primary) : Colors.transparent,
              border: Border.all(color: error ? cs.error : cs.outline, width: 1.5),
            ),
          ),
      ],
    );
  }
}

/// Teclado numérico para PIN (4–6 dígitos).
class TecladoPin extends StatelessWidget {
  const TecladoPin({super.key, required this.alDigito, required this.alBorrar, this.habilitado = true});

  final ValueChanged<String> alDigito;
  final VoidCallback alBorrar;
  final bool habilitado;

  @override
  Widget build(BuildContext context) {
    Widget tecla(String d) => _Tecla(
          key: ValueKey('pin-$d'),
          onTap: habilitado ? () => alDigito(d) : null,
          child: Text(d, style: Theme.of(context).textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.w600)),
        );
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        for (final fila in const [
          ['1', '2', '3'],
          ['4', '5', '6'],
          ['7', '8', '9'],
        ])
          Row(mainAxisAlignment: MainAxisAlignment.spaceEvenly, children: fila.map(tecla).toList()),
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceEvenly,
          children: [
            const SizedBox(width: 72, height: 64),
            tecla('0'),
            _Tecla(
              key: const ValueKey('pin-borrar'),
              onTap: habilitado ? alBorrar : null,
              child: const Icon(Icons.backspace_outlined, size: 22),
            ),
          ],
        ),
      ],
    );
  }
}

class _Tecla extends StatelessWidget {
  const _Tecla({super.key, required this.child, this.onTap});

  final Widget child;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return SizedBox(
      width: 72,
      height: 64,
      child: Center(
        child: Material(
          color: cs.surface,
          shape: const CircleBorder(),
          clipBehavior: Clip.antiAlias,
          child: InkWell(
            onTap: onTap,
            child: SizedBox(width: 58, height: 58, child: Center(child: child)),
          ),
        ),
      ),
    );
  }
}
