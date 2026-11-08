import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/di/providers.dart';
import '../../../core/theme/app_theme.dart';
import '../../../core/util/fechas.dart';
import '../../../core/widgets/comunes.dart';
import '../domain/consolidacion_models.dart';
import 'consolidacion_providers.dart';
import 'vincular_bancos.dart';

/// Gestión de consentimientos Open Banking: ver estado y revocar.
class ConsentimientosScreen extends ConsumerWidget {
  const ConsentimientosScreen({super.key});

  Future<void> _revocar(BuildContext context, WidgetRef ref, Consentimiento c) async {
    final ok = await confirmar(
      context,
      titulo: 'Revocar ${c.institucion}',
      mensaje: 'Ahorra+ dejará de leer tus cuentas de ${c.institucion} y no podrá debitar para '
          'pagar tus TDC. Puedes volver a vincularla cuando quieras.',
      aceptar: 'Revocar',
      peligroso: true,
    );
    if (!ok) return;
    try {
      await ref.read(consolidacionRepositoryProvider).revocarConsentimiento(c.idConsentimiento);
      ref.invalidate(consentimientosProvider);
      ref.invalidate(consolidadoProvider);
      if (context.mounted) mostrarMensaje(context, 'Vínculo con ${c.institucion} revocado.');
    } catch (e) {
      if (context.mounted) mostrarError(context, e);
    }
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final colores = AhorraColores.of(context);
    final cs = Theme.of(context).colorScheme;
    final lista = ref.watch(consentimientosProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('Vínculos con bancos')),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => iniciarVinculacion(context, ref),
        icon: const Icon(Icons.add_link),
        label: const Text('Vincular'),
      ),
      body: RefreshIndicator(
        onRefresh: () async => ref.invalidate(consentimientosProvider),
        child: VistaAsync<List<Consentimiento>>(
          valor: lista,
          alReintentar: () => ref.invalidate(consentimientosProvider),
          datos: (items) => ListView(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 96),
            children: [
              const AvisoSinClaves(),
              const SizedBox(height: 12),
              if (items.isEmpty) const Center(child: Text('No tienes vínculos todavía.')),
              for (final c in items) ...[
                Card(
                  child: ListTile(
                    title: Text(c.institucion, style: const TextStyle(fontWeight: FontWeight.w600)),
                    subtitle: Text([
                      'Lectura de saldos${c.permiteDebitoTdc ? ' - débito para TDC' : ''}',
                      if (c.fechaVencimiento != null) 'Vence ${Fechas.corta(c.fechaVencimiento)}',
                    ].join('\n')),
                    isThreeLine: c.fechaVencimiento != null,
                    leading: EtiquetaEstado(
                      texto: c.estatus,
                      color: switch (c.estatus) {
                        'activo' => colores.exito,
                        'pendiente' => colores.enProceso,
                        _ => cs.outline,
                      },
                    ),
                    trailing: c.estatus == 'activo' || c.estatus == 'pendiente'
                        ? IconButton(
                            tooltip: 'Revocar',
                            icon: const Icon(Icons.link_off),
                            onPressed: () => _revocar(context, ref, c),
                          )
                        : null,
                  ),
                ),
                const SizedBox(height: 8),
              ],
            ],
          ),
        ),
      ),
    );
  }
}
