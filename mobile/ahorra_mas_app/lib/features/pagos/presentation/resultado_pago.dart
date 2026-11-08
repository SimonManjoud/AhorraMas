import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/money/dinero.dart';
import '../../../core/theme/app_theme.dart';
import '../../../core/util/fechas.dart';
import '../../../core/widgets/comunes.dart';
import '../domain/pagos_models.dart';
import 'pagos_providers.dart';

/// Color e icono por estado del pago. `EN_VERIFICACION` se muestra como "en proceso", nunca fallido.
(Color, IconData) estiloEstado(BuildContext context, EstadoPago e) {
  final c = AhorraColores.of(context);
  if (e.exitoso) return (c.exito, Icons.check_circle);
  if (e.fallido) return (c.peligro, Icons.cancel);
  if (e.enProceso) return (c.enProceso, Icons.schedule);
  return (Theme.of(context).colorScheme.outline, Icons.help_outline);
}

class EtiquetaPago extends StatelessWidget {
  const EtiquetaPago({super.key, required this.estado});

  final EstadoPago estado;

  @override
  Widget build(BuildContext context) {
    final (color, icono) = estiloEstado(context, estado);
    return EtiquetaEstado(texto: estado.etiqueta, color: color, icono: icono);
  }
}

/// Pantalla de resultado / detalle de un pago con su línea de tiempo.
class ResultadoPagoScreen extends ConsumerWidget {
  const ResultadoPagoScreen({super.key, required this.idPago, this.pagoInicial});

  final int idPago;
  final Pago? pagoInicial;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(pagoProvider(idPago));
    final pago = async.value ?? pagoInicial;
    return Scaffold(
      appBar: AppBar(title: const Text('Pago de TDC')),
      body: pago == null
          ? VistaAsync<Pago>(
              valor: async,
              alReintentar: () => ref.invalidate(pagoProvider(idPago)),
              datos: (p) => const SizedBox.shrink(),
            )
          : ResultadoPagoView(
              pago: pago,
              actualizando: async.isLoading,
              alActualizar: () {
                ref.invalidate(pagoProvider(idPago));
                ref.invalidate(pagosProvider);
              },
              alTerminar: () => context.go('/pagar'),
            ),
    );
  }
}

class ResultadoPagoView extends StatelessWidget {
  const ResultadoPagoView({
    super.key,
    required this.pago,
    this.alActualizar,
    this.alTerminar,
    this.actualizando = false,
  });

  final Pago pago;
  final VoidCallback? alActualizar;
  final VoidCallback? alTerminar;
  final bool actualizando;

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context).textTheme;
    final (color, icono) = estiloEstado(context, pago.estado);
    final e = pago.estado;
    final titulo = e.exitoso
        ? '¡Pago exitoso!'
        : e == EstadoPago.rechazado
            ? 'Pago rechazado'
            : e == EstadoPago.reversado
                ? 'Pago reversado'
                : 'Pago en proceso';
    final explicacion = switch (e) {
      EstadoPago.capturado || EstadoPago.conciliado => pago.mensaje ?? 'Tu pago fue aplicado.',
      EstadoPago.rechazado =>
        'Motivo: ${pago.motivoRechazo ?? 'el banco no aprobó la operación'}. No se debitó dinero de tu cuenta.',
      EstadoPago.reversado => 'El pago fue reversado y el dinero devuelto a tu cuenta de origen.',
      EstadoPago.conDiferencia =>
        'Tu pago está en proceso de revisión por una diferencia en la conciliación. No lo repitas: te avisaremos.',
      _ => 'Tu pago está en proceso de verificación con el banco. No es un pago fallido y no '
          'necesitas repetirlo: te avisaremos cuando se confirme.',
    };
    return ListView(
      padding: const EdgeInsets.all(20),
      children: [
        const SizedBox(height: 8),
        Icon(icono, size: 84, color: color),
        const SizedBox(height: 8),
        Text(titulo, textAlign: TextAlign.center, style: t.headlineSmall?.copyWith(fontWeight: FontWeight.w800)),
        const SizedBox(height: 8),
        Text(explicacion, key: const ValueKey('resultado-explicacion'), textAlign: TextAlign.center),
        const SizedBox(height: 20),
        Card(
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              children: [
                _Fila('Estado', trailing: EtiquetaPago(estado: e)),
                _Fila('Monto', valor: Dinero.bs(pago.montoCts)),
                _Fila('Comisión', valor: Dinero.bs(pago.comisionCts)),
                _Fila('Total debitado', valor: Dinero.bs(pago.montoCts + pago.comisionCts)),
                if (pago.referenciaSuiche != null) _Fila('Referencia', valor: pago.referenciaSuiche!),
                if (pago.codigoAutorizacion != null) _Fila('Autorización', valor: pago.codigoAutorizacion!),
                if (pago.esPuntual == true && e.exitoso) const _Fila('Pago puntual', valor: '+ puntos'),
                _Fila('Fecha', valor: Fechas.conHora(pago.fechaHoraCreacion)),
              ],
            ),
          ),
        ),
        if (pago.historial.isNotEmpty) ...[
          const TituloSeccion('Línea de tiempo'),
          LineaTiempoPago(historial: pago.historial),
        ],
        const SizedBox(height: 20),
        if (e.enProceso && alActualizar != null)
          OutlinedButton.icon(
            onPressed: actualizando ? null : alActualizar,
            icon: actualizando
                ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2))
                : const Icon(Icons.refresh),
            label: const Text('Actualizar estado'),
          ),
        if (alTerminar != null) ...[
          const SizedBox(height: 8),
          FilledButton(onPressed: alTerminar, child: const Text('Listo')),
        ],
      ],
    );
  }
}

class LineaTiempoPago extends StatelessWidget {
  const LineaTiempoPago({super.key, required this.historial});

  final List<CambioEstadoPago> historial;

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        for (var i = 0; i < historial.length; i++)
          IntrinsicHeight(
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                SizedBox(
                  width: 28,
                  child: Column(
                    children: [
                      Icon(estiloEstado(context, historial[i].estadoNuevo).$2,
                          size: 20, color: estiloEstado(context, historial[i].estadoNuevo).$1),
                      if (i < historial.length - 1)
                        Expanded(child: VerticalDivider(width: 2, thickness: 2, color: Theme.of(context).dividerColor)),
                    ],
                  ),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: Padding(
                    padding: const EdgeInsets.only(bottom: 14),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(historial[i].estadoNuevo.etiqueta, style: const TextStyle(fontWeight: FontWeight.w600)),
                        Text(
                          '${Fechas.conHora(historial[i].fechaHora)}${historial[i].actor == null ? '' : ' - ${historial[i].actor}'}',
                          style: Theme.of(context).textTheme.bodySmall,
                        ),
                      ],
                    ),
                  ),
                ),
              ],
            ),
          ),
      ],
    );
  }
}

class _Fila extends StatelessWidget {
  const _Fila(this.etiqueta, {this.valor, this.trailing});

  final String etiqueta;
  final String? valor;
  final Widget? trailing;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        children: [
          Expanded(child: Text(etiqueta, style: Theme.of(context).textTheme.bodyMedium)),
          Flexible(
            child: trailing ??
                Text(valor ?? '', textAlign: TextAlign.end, style: const TextStyle(fontWeight: FontWeight.w600)),
          ),
        ],
      ),
    );
  }
}
