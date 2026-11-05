import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../money/dinero.dart';

/// Monto en Bs y USD (uno principal y el otro como equivalencia).
class MontoDual extends StatelessWidget {
  const MontoDual({
    super.key,
    required this.bsCts,
    required this.usdCts,
    this.principalUsd = false,
    this.estilo,
    this.alineacion = CrossAxisAlignment.start,
  });

  final int bsCts;
  final int usdCts;
  final bool principalUsd;
  final TextStyle? estilo;
  final CrossAxisAlignment alineacion;

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context).textTheme;
    final principal = principalUsd ? Dinero.usd(usdCts) : Dinero.bs(bsCts);
    final secundario = principalUsd ? Dinero.bs(bsCts) : Dinero.usd(usdCts);
    return Column(
      crossAxisAlignment: alineacion,
      mainAxisSize: MainAxisSize.min,
      children: [
        Text(principal, style: estilo ?? t.titleMedium?.copyWith(fontWeight: FontWeight.w700)),
        Text('≈ $secundario', style: t.bodySmall?.copyWith(color: Theme.of(context).colorScheme.onSurfaceVariant)),
      ],
    );
  }
}

/// Campo para escribir montos ("2,40" o "2.40"); valida y convierte a céntimos sin `double`.
class CampoMonto extends StatelessWidget {
  const CampoMonto({
    super.key,
    required this.controller,
    required this.etiqueta,
    this.prefijo = r'$ ',
    this.maximoCts,
    this.minimoCts = 1,
    this.ayuda,
    this.alCambiar,
    this.autofocus = false,
    this.mensajeMaximo,
  });

  final TextEditingController controller;
  final String etiqueta;
  final String prefijo;
  final int? maximoCts;
  final int minimoCts;
  final String? ayuda;
  final ValueChanged<int?>? alCambiar;
  final bool autofocus;
  final String? mensajeMaximo;

  @override
  Widget build(BuildContext context) {
    return TextFormField(
      controller: controller,
      autofocus: autofocus,
      keyboardType: const TextInputType.numberWithOptions(decimal: true),
      inputFormatters: [FilteringTextInputFormatter.allow(RegExp(r'[0-9.,]'))],
      decoration: InputDecoration(labelText: etiqueta, prefixText: prefijo, helperText: ayuda),
      onChanged: (v) => alCambiar?.call(Dinero.parsear(v)),
      validator: (v) {
        final cts = Dinero.parsear(v ?? '');
        if (cts == null) return 'Escribe un monto válido (ej. 2,40)';
        if (cts < minimoCts) return 'El monto debe ser mayor a cero';
        if (maximoCts != null && cts > maximoCts!) {
          return mensajeMaximo ?? 'El máximo es ${prefijo.trim()} ${Dinero.numero(maximoCts!)}';
        }
        return null;
      },
    );
  }
}
