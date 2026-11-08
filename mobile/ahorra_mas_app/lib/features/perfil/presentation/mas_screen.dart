import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/di/providers.dart';
import '../../../core/session/session_controller.dart';
import '../../../core/widgets/comunes.dart';

/// Pestaña "Más": asistente IA, notificaciones, vínculos, perfil y ajustes.
class MasScreen extends ConsumerWidget {
  const MasScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final usuario = ref.watch(sessionProvider)?.usuario;
    final config = ref.watch(appConfigProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('Más')),
      body: ListView(
        padding: const EdgeInsets.symmetric(vertical: 8),
        children: [
          if (usuario != null)
            ListTile(
              leading: CircleAvatar(child: Text(usuario.primerNombre.characters.first)),
              title: Text('${usuario.primerNombre} ${usuario.primerApellido}'),
              subtitle: Text(usuario.kycAprobado ? 'Identidad verificada' : 'Verificación pendiente'),
            ),
          const Divider(),
          _Item(icono: Icons.auto_awesome, titulo: 'Asistente IA', subtitulo: 'Tu perfil de ahorro y una recomendación', ruta: '/mas/asistente'),
          _Item(icono: Icons.notifications_outlined, titulo: 'Notificaciones', ruta: '/mas/notificaciones'),
          _Item(icono: Icons.link, titulo: 'Vínculos con bancos', subtitulo: 'Consentimientos Open Banking', ruta: '/inicio/consentimientos'),
          _Item(icono: Icons.local_fire_department_outlined, titulo: 'Mi racha', ruta: '/cofre/racha'),
          _Item(icono: Icons.shield_moon_outlined, titulo: 'Espejo de Impacto', ruta: '/cofre/espejo'),
          _Item(icono: Icons.manage_accounts_outlined, titulo: 'Perfil y seguridad', subtitulo: 'Saldo rápido, dispositivo, sesión', ruta: '/mas/ajustes'),
          const Divider(),
          ListTile(
            leading: const Icon(Icons.logout),
            title: const Text('Cerrar sesión'),
            onTap: () => ref.read(sessionProvider.notifier).cerrar(),
          ),
          const SizedBox(height: 16),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: Column(
              children: [
                const AvisoSinClaves(compacto: true),
                const SizedBox(height: 8),
                Text(
                  'Ahorra+ - Impulsa Tech, C.A. - MVP académico\n'
                  '${config.fakeApi ? 'Modo demo (API falsa en memoria)' : 'API: ${config.apiBaseUrl}'}',
                  textAlign: TextAlign.center,
                  style: Theme.of(context).textTheme.bodySmall,
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _Item extends StatelessWidget {
  const _Item({required this.icono, required this.titulo, required this.ruta, this.subtitulo});

  final IconData icono;
  final String titulo;
  final String? subtitulo;
  final String ruta;

  @override
  Widget build(BuildContext context) {
    return ListTile(
      leading: Icon(icono),
      title: Text(titulo),
      subtitle: subtitulo == null ? null : Text(subtitulo!),
      trailing: const Icon(Icons.chevron_right),
      onTap: () => context.push(ruta),
    );
  }
}
