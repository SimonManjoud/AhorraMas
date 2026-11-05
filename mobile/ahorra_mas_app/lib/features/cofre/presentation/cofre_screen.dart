import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/money/dinero.dart';
import '../../../core/theme/app_theme.dart';
import '../../../core/widgets/comunes.dart';
import '../domain/cofre_models.dart';
import 'cofre_providers.dart';
import 'widgets/cofre_widgets.dart';

/// Pestaña "Cofre Protector": racha, Espejo de Impacto y metas con su Ruta del día.
class CofreScreen extends ConsumerWidget {
  const CofreScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final metas = ref.watch(metasProvider);
    final racha = ref.watch(rachaProvider);
    final espejo = ref.watch(espejoProvider(null));
    final colores = AhorraColores.of(context);
    return Scaffold(
      appBar: AppBar(title: const Text('Cofre Protector')),
      floatingActionButton: FloatingActionButton.extended(
        heroTag: 'nueva-meta',
        backgroundColor: colores.cofre,
        foregroundColor: Colors.black87,
        onPressed: () => context.push('/cofre/nueva'),
        icon: const Icon(Icons.add),
        label: const Text('Nueva meta'),
      ),
      body: RefreshIndicator(
        onRefresh: () async {
          ref.invalidate(metasProvider);
          ref.invalidate(rachaProvider);
          ref.invalidate(espejoProvider);
        },
        child: ListView(
          physics: const AlwaysScrollableScrollPhysics(),
          padding: const EdgeInsets.fromLTRB(16, 4, 16, 96),
          children: [
            racha.when(
              data: (r) => RachaResumen(racha: r, onTap: () => context.push('/cofre/racha')),
              loading: () => const SizedBox(height: 80),
              error: (_, _) => const SizedBox.shrink(),
            ),
            const SizedBox(height: 12),
            if (espejo.value case final e?)
              Card(
                color: Theme.of(context).colorScheme.secondaryContainer,
                child: ListTile(
                  contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                  leading: const Icon(Icons.shield_moon_outlined, size: 32),
                  title: const Text('Espejo de Impacto', style: TextStyle(fontWeight: FontWeight.w700)),
                  subtitle: Text('${e.frase}\nTu ahorro evitó perder ${Dinero.usd(e.perdidaEvitadaUsdCts)}.'),
                  isThreeLine: true,
                  trailing: const Icon(Icons.chevron_right),
                  onTap: () => context.push('/cofre/espejo'),
                ),
              ),
            const TituloSeccion('Mis metas'),
            VistaAsync<List<Meta>>(
              valor: metas,
              alReintentar: () => ref.invalidate(metasProvider),
              datos: (lista) => lista.isEmpty
                  ? _SinMetas(alCrear: () => context.push('/cofre/nueva'))
                  : Column(
                      children: [
                        for (final m in lista) ...[
                          MetaCard(meta: m, onTap: () => context.push('/cofre/meta/${m.idMeta}')),
                          const SizedBox(height: 12),
                        ],
                      ],
                    ),
            ),
          ],
        ),
      ),
    );
  }
}

class _SinMetas extends StatelessWidget {
  const _SinMetas({required this.alCrear});

  final VoidCallback alCrear;

  @override
  Widget build(BuildContext context) {
    final colores = AhorraColores.of(context);
    return Card(
      color: colores.cofreContenedor,
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          children: [
            Icon(Icons.savings_outlined, size: 48, color: colores.alCofre),
            const SizedBox(height: 8),
            Text('¿Para qué quieres ahorrar?',
                style: Theme.of(context).textTheme.titleMedium?.copyWith(color: colores.alCofre)),
            const SizedBox(height: 4),
            Text(
              'Elige una meta con fecha y monto en dólares. Nosotros la convertimos en una pequeña '
              'cuota diaria a tasa BCV.',
              textAlign: TextAlign.center,
              style: TextStyle(color: colores.alCofre),
            ),
            const SizedBox(height: 12),
            FilledButton(onPressed: alCrear, child: const Text('Crear mi primera meta')),
          ],
        ),
      ),
    );
  }
}
