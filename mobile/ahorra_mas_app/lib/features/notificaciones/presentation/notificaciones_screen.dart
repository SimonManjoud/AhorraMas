import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/di/providers.dart';
import '../../../core/util/fechas.dart';
import '../../../core/widgets/comunes.dart';
import '../domain/notificaciones.dart';

final notificacionesProvider = FutureProvider.autoDispose<List<Notificacion>>(
    (ref) => ref.watch(notificacionesRepositoryProvider).notificaciones());

class NotificacionesScreen extends ConsumerWidget {
  const NotificacionesScreen({super.key});

  IconData _icono(String? tipo) => switch (tipo) {
        'cofre' || 'meta' => Icons.savings_outlined,
        'racha' => Icons.local_fire_department_outlined,
        'seguridad' => Icons.security,
        'pago' => Icons.credit_card,
        'puntos' => Icons.stars_outlined,
        _ => Icons.notifications_outlined,
      };

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final cs = Theme.of(context).colorScheme;
    return Scaffold(
      appBar: AppBar(title: const Text('Notificaciones')),
      body: RefreshIndicator(
        onRefresh: () async => ref.invalidate(notificacionesProvider),
        child: VistaAsync<List<Notificacion>>(
          valor: ref.watch(notificacionesProvider),
          alReintentar: () => ref.invalidate(notificacionesProvider),
          datos: (lista) => lista.isEmpty
              ? ListView(children: const [Padding(padding: EdgeInsets.all(32), child: Center(child: Text('No tienes notificaciones.')))])
              : ListView.separated(
                  itemCount: lista.length,
                  separatorBuilder: (_, _) => const Divider(height: 1),
                  itemBuilder: (_, i) {
                    final n = lista[i];
                    return ListTile(
                      tileColor: n.leida ? null : cs.primaryContainer.withValues(alpha: 0.35),
                      leading: Icon(_icono(n.tipo), color: n.leida ? cs.outline : cs.primary),
                      title: Text(n.titulo, style: TextStyle(fontWeight: n.leida ? FontWeight.w500 : FontWeight.w700)),
                      subtitle: Text('${n.mensaje}\n${Fechas.conHora(n.fechaHoraEnvio)}${n.canal == null ? '' : ' · ${n.canal}'}'),
                      isThreeLine: true,
                      onTap: n.leida
                          ? null
                          : () async {
                              try {
                                await ref.read(notificacionesRepositoryProvider).marcarLeida(n.idNotificacion);
                                ref.invalidate(notificacionesProvider);
                              } catch (e) {
                                if (context.mounted) mostrarError(context, e);
                              }
                            },
                    );
                  },
                ),
        ),
      ),
    );
  }
}
