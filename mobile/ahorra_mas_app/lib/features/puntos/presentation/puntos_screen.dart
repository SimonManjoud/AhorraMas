import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/di/providers.dart';
import '../../../core/theme/app_theme.dart';
import '../../../core/util/fechas.dart';
import '../../../core/widgets/comunes.dart';
import '../domain/puntos.dart';
import 'puntos_providers.dart';
import '../../../core/widgets/texto_enfasis.dart';

/// Puntos Ahorra: saldo, nivel (Base/Gold/Platinum), movimientos, recompensas y canjes.
class PuntosScreen extends ConsumerWidget {
  const PuntosScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final resumen = ref.watch(resumenPuntosProvider);
    return DefaultTabController(
      length: 3,
      child: Scaffold(
        appBar: AppBar(title: const Text('Puntos Ahorra')),
        body: NestedScrollView(
          headerSliverBuilder: (context, _) => [
            SliverToBoxAdapter(
              child: Padding(
                padding: const EdgeInsets.fromLTRB(16, 4, 16, 8),
                child: VistaAsync<ResumenPuntos>(
                  valor: resumen,
                  alReintentar: () => ref.invalidate(resumenPuntosProvider),
                  datos: (r) => _Resumen(resumen: r),
                ),
              ),
            ),
            const SliverToBoxAdapter(
              child: TabBar(tabs: [Tab(text: 'Movimientos'), Tab(text: 'Recompensas'), Tab(text: 'Mis canjes')]),
            ),
          ],
          body: const TabBarView(children: [_Movimientos(), _Recompensas(), _Canjes()]),
        ),
      ),
    );
  }
}

class _Resumen extends StatelessWidget {
  const _Resumen({required this.resumen});

  final ResumenPuntos resumen;

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context).textTheme;
    final colores = AhorraColores.of(context);
    final s = resumen.siguienteNivel;
    final colorNivel = switch (resumen.nivel.toLowerCase()) {
      'platinum' => const Color(0xFF8E9AAF),
      'gold' => colores.cofre,
      _ => Theme.of(context).colorScheme.primary,
    };
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(18),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(Icons.stars_rounded, size: 40, color: colorNivel),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text('${_miles(resumen.saldo)} pts', style: t.headlineSmall?.copyWith(fontWeight: FontWeight.w800)),
                      Text('Nivel ${resumen.nivel}', style: t.titleSmall?.copyWith(color: colorNivel)),
                    ],
                  ),
                ),
              ],
            ),
            const SizedBox(height: 14),
            if (s != null) ...[
              ClipRRect(
                borderRadius: BorderRadius.circular(8),
                child: LinearProgressIndicator(value: resumen.progresoSiguiente, minHeight: 10, color: colorNivel),
              ),
              const SizedBox(height: 6),
              Text('Te faltan ${_miles(s.puntosFaltantes)} pts para ${s.nombre} '
                  '(${_miles(resumen.puntosGanadosTotal)} / ${_miles(s.puntosMinimos)} ganados)'),
            ] else
              const Text('¡Estás en el nivel más alto!'),
            const SizedBox(height: 6),
            Text('Ganas puntos con aportes a tus metas (con el multiplicador de tu racha) y pagando tus TDC a tiempo.',
                style: t.bodySmall),
          ],
        ),
      ),
    );
  }
}

String _miles(int n) {
  final s = n.abs().toString();
  final b = StringBuffer(n < 0 ? '-' : '');
  for (var i = 0; i < s.length; i++) {
    if (i > 0 && (s.length - i) % 3 == 0) b.write('.');
    b.write(s[i]);
  }
  return b.toString();
}

class _Movimientos extends ConsumerWidget {
  const _Movimientos();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final colores = AhorraColores.of(context);
    return VistaAsync<List<MovimientoPuntos>>(
      valor: ref.watch(movimientosPuntosProvider),
      alReintentar: () => ref.invalidate(movimientosPuntosProvider),
      datos: (lista) => lista.isEmpty
          ? const Center(child: Text('Aún no tienes movimientos.'))
          : ListView.builder(
              itemCount: lista.length,
              itemBuilder: (_, i) {
                final m = lista[i];
                return ListTile(
                  leading: Icon(switch (m.concepto) {
                    'meta' => Icons.savings_outlined,
                    'pago_puntual' => Icons.event_available,
                    'racha' => Icons.local_fire_department,
                    'referido' => Icons.group_add_outlined,
                    'canje' => Icons.redeem,
                    _ => Icons.undo,
                  }),
                  title: TextoEnfasis(m.descripcion ?? m.concepto),
                  subtitle: Text(Fechas.conHora(m.fechaHora)),
                  trailing: Text(
                    '${m.puntos > 0 ? '+' : ''}${_miles(m.puntos)}',
                    style: TextStyle(
                      fontWeight: FontWeight.w700,
                      color: m.puntos >= 0 ? colores.exito : colores.peligro,
                    ),
                  ),
                );
              },
            ),
    );
  }
}

class _Recompensas extends ConsumerWidget {
  const _Recompensas();

  Future<void> _canjear(BuildContext context, WidgetRef ref, Recompensa r) async {
    final ok = await confirmar(
      context,
      titulo: 'Canjear ${r.descripcion}',
      mensaje: 'Usarás ${_miles(r.costoPuntos)} puntos${r.comercio == null ? '' : ' en ${r.comercio}'}.',
      aceptar: 'Canjear',
    );
    if (!ok) return;
    try {
      final c = await ref.read(puntosRepositoryProvider).canjear(r.idRecompensa);
      refrescarPuntos(ref);
      ref.invalidate(recompensasProvider);
      if (!context.mounted) return;
      await showDialog<void>(
        context: context,
        builder: (ctx) => AlertDialog(
          icon: const Icon(Icons.redeem, size: 40),
          title: const Text('¡Canje emitido!'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Text('Presenta este código en el comercio:'),
              const SizedBox(height: 12),
              SelectableText(c.codigoCanje,
                  style: Theme.of(ctx).textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.w800, letterSpacing: 2)),
            ],
          ),
          actions: [
            TextButton(
              onPressed: () => Clipboard.setData(ClipboardData(text: c.codigoCanje)),
              child: const Text('Copiar'),
            ),
            FilledButton(onPressed: () => Navigator.pop(ctx), child: const Text('Listo')),
          ],
        ),
      );
    } catch (e) {
      if (context.mounted) mostrarError(context, e);
    }
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final resumen = ref.watch(resumenPuntosProvider).value;
    return VistaAsync<List<Recompensa>>(
      valor: ref.watch(recompensasProvider),
      alReintentar: () => ref.invalidate(recompensasProvider),
      datos: (lista) => ListView.builder(
        padding: const EdgeInsets.all(12),
        itemCount: lista.length,
        itemBuilder: (_, i) {
          final r = lista[i];
          final nivelOk = resumen == null || ordenNivel(resumen.nivel) >= ordenNivel(r.nivelMinimo);
          final alcanza = resumen == null || resumen.saldo >= r.costoPuntos;
          final agotada = (r.stockDisponible ?? 1) <= 0;
          return Card(
            margin: const EdgeInsets.only(bottom: 10),
            child: ListTile(
              leading: const CircleAvatar(child: Icon(Icons.card_giftcard)),
              title: Text(r.descripcion),
              subtitle: Text([
                if (r.comercio != null) r.comercio!,
                '${_miles(r.costoPuntos)} pts',
                if (r.nivelMinimo != 'Base') 'Nivel ${r.nivelMinimo}',
              ].join(' - ')),
              trailing: FilledButton.tonal(
                onPressed: nivelOk && alcanza && !agotada ? () => _canjear(context, ref, r) : null,
                child: Text(agotada ? 'Agotada' : (!nivelOk ? 'Bloqueada' : 'Canjear')),
              ),
            ),
          );
        },
      ),
    );
  }
}

class _Canjes extends ConsumerWidget {
  const _Canjes();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return VistaAsync<List<Canje>>(
      valor: ref.watch(canjesProvider),
      alReintentar: () => ref.invalidate(canjesProvider),
      datos: (lista) => lista.isEmpty
          ? const Center(child: Text('Aún no has canjeado recompensas.'))
          : ListView(
              children: [
                for (final c in lista)
                  ListTile(
                    leading: const Icon(Icons.confirmation_number_outlined),
                    title: SelectableText(c.codigoCanje, style: const TextStyle(fontWeight: FontWeight.w700)),
                    subtitle: TextoEnfasis('${c.descripcion ?? ''} ${Fechas.conHora(c.fechaHora)}'.trim()),
                    trailing: Text('-${_miles(c.puntosCanjeados)} pts'),
                  ),
              ],
            ),
    );
  }
}
