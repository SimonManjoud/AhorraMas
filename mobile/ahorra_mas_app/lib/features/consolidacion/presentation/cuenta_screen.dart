import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/di/providers.dart';
import '../../../core/money/dinero.dart';
import '../../../core/theme/app_theme.dart';
import '../../../core/util/fechas.dart';
import '../../../core/widgets/comunes.dart';
import '../domain/consolidacion_models.dart';
import 'consolidacion_providers.dart';

/// Detalle de cuenta: movimientos y alias.
class CuentaScreen extends ConsumerWidget {
  const CuentaScreen({super.key, required this.idCuenta, this.cuenta});

  final int idCuenta;
  final Cuenta? cuenta;

  Future<void> _renombrar(BuildContext context, WidgetRef ref) async {
    final ctrl = TextEditingController(text: cuenta?.alias ?? '');
    final alias = await showDialog<String>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Alias de la cuenta'),
        content: TextField(
          controller: ctrl,
          autofocus: true,
          maxLength: 30,
          decoration: const InputDecoration(hintText: 'Ej. Nómina'),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('Cancelar')),
          FilledButton(onPressed: () => Navigator.pop(ctx, ctrl.text.trim()), child: const Text('Guardar')),
        ],
      ),
    );
    if (alias == null || alias.isEmpty) return;
    try {
      await ref.read(consolidacionRepositoryProvider).renombrarCuenta(idCuenta, alias);
      ref.invalidate(consolidadoProvider);
      if (context.mounted) mostrarMensaje(context, 'Alias actualizado.');
    } catch (e) {
      if (context.mounted) mostrarError(context, e);
    }
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final movs = ref.watch(movimientosCuentaProvider(idCuenta));
    final colores = AhorraColores.of(context);
    final c = cuenta;
    return Scaffold(
      appBar: AppBar(
        title: Text(c == null ? 'Cuenta' : '${c.nombreVisible} •••• ${c.ultimos4}'),
        actions: [
          IconButton(
            tooltip: 'Cambiar alias',
            onPressed: () => _renombrar(context, ref),
            icon: const Icon(Icons.edit_outlined),
          ),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: () async => ref.invalidate(movimientosCuentaProvider(idCuenta)),
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            if (c != null)
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text('Saldo', style: Theme.of(context).textTheme.labelLarge),
                      Text(Dinero.enMoneda(c.saldoCts, c.moneda),
                          style: Theme.of(context).textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.w700)),
                      Text('≈ ${Dinero.bs(c.saldoBsCts)} · ${Dinero.usd(c.saldoUsdCts)}'),
                    ],
                  ),
                ),
              ),
            const TituloSeccion('Movimientos'),
            VistaAsync<List<Movimiento>>(
              valor: movs,
              alReintentar: () => ref.invalidate(movimientosCuentaProvider(idCuenta)),
              datos: (lista) => Column(
                children: [
                  if (lista.isEmpty) const Padding(padding: EdgeInsets.all(24), child: Text('Sin movimientos.')),
                  for (final m in lista)
                    ListTile(
                      contentPadding: EdgeInsets.zero,
                      leading: Icon(
                        m.esEntrada ? Icons.south_west : Icons.north_east,
                        color: m.esEntrada ? colores.exito : colores.peligro,
                      ),
                      title: Text(m.descripcion ?? m.clasificacion ?? m.naturaleza),
                      subtitle: Text(Fechas.conHora(m.fechaHora)),
                      trailing: Text(
                        '${m.esEntrada ? '+' : '-'}${Dinero.enMoneda(m.montoCts.abs(), m.moneda)}',
                        style: TextStyle(
                          fontWeight: FontWeight.w600,
                          color: m.esEntrada ? colores.exito : null,
                        ),
                      ),
                    ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
