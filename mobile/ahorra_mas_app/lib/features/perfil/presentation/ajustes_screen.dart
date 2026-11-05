import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/di/providers.dart';
import '../../../core/session/session_controller.dart';
import '../../../core/util/fechas.dart';
import '../../../core/widgets/comunes.dart';
import '../../../core/widgets/step_up.dart';
import '../../auth/domain/auth_models.dart';
import '../../auth/presentation/auth_providers.dart';
import '../../cofre/presentation/cofre_providers.dart';
import '../../consolidacion/presentation/consolidacion_providers.dart';

/// Perfil y seguridad: datos, saldo rápido (F3), revocar dispositivo, cerrar sesión y reloj demo.
class AjustesScreen extends ConsumerStatefulWidget {
  const AjustesScreen({super.key});

  @override
  ConsumerState<AjustesScreen> createState() => _AjustesScreenState();
}

class _AjustesScreenState extends ConsumerState<AjustesScreen> {
  bool _ocupado = false;

  Future<void> _alternarSaldoRapido(bool activar) async {
    final servicio = ref.read(saldoRapidoServiceProvider);
    if (activar) {
      final stepUp = await solicitarStepUp(
        context,
        motivo: 'Activar el saldo rápido: verás tu total (sin detalle) en la pantalla de inicio sin '
            'entrar a la app. Solo en este dispositivo.',
      );
      if (stepUp == null) return;
      setState(() => _ocupado = true);
      try {
        await servicio.activar(stepUp.token);
        if (mounted) mostrarMensaje(context, 'Saldo rápido activado en este dispositivo.');
      } catch (e) {
        if (mounted) mostrarError(context, e);
      }
    } else {
      final ok = await confirmar(
        context,
        titulo: 'Desactivar saldo rápido',
        mensaje: 'Se revocará el acceso de solo lectura de este dispositivo.',
        aceptar: 'Desactivar',
      );
      if (!ok) return;
      setState(() => _ocupado = true);
      try {
        await servicio.desactivar();
        if (mounted) mostrarMensaje(context, 'Saldo rápido desactivado.');
      } catch (e) {
        if (mounted) mostrarError(context, e);
      }
    }
    ref.invalidate(saldoRapidoActivoProvider);
    if (mounted) setState(() => _ocupado = false);
  }

  Future<void> _revocarDispositivo() async {
    final motivo = await showDialog<String>(
      context: context,
      builder: (ctx) => SimpleDialog(
        title: const Text('¿Por qué revocas este dispositivo?'),
        children: [
          for (final m in const [
            ('usuario', 'Ya no lo usaré'),
            ('cambio_sim', 'Cambié mi SIM / número'),
            ('robo', 'Me lo robaron o lo perdí'),
            ('root', 'El equipo fue modificado (root/jailbreak)'),
          ])
            SimpleDialogOption(onPressed: () => Navigator.pop(ctx, m.$1), child: Text(m.$2)),
        ],
      ),
    );
    if (motivo == null || !mounted) return;
    final ok = await confirmar(
      context,
      titulo: 'Revocar dispositivo',
      mensaje: 'Se cerrará tu sesión y se anulará el saldo rápido en este equipo. Para volver a '
          'entrar necesitarás tu cédula y PIN.',
      aceptar: 'Revocar',
      peligroso: true,
    );
    if (!ok) return;
    setState(() => _ocupado = true);
    try {
      await ref.read(authRepositoryProvider).revocarDispositivoActual(motivo);
    } catch (e) {
      if (mounted) mostrarError(context, e);
      setState(() => _ocupado = false);
      return;
    }
    await ref.read(saldoRapidoServiceProvider).limpiarLocal();
    ref.read(sessionProvider.notifier).cerrar();
  }

  Future<void> _avanzarReloj() async {
    try {
      final f = await ref.read(demoRepositoryProvider).avanzarReloj(1);
      ref.invalidate(rachaProvider);
      ref.invalidate(metasProvider);
      ref.invalidate(rutasDelDiaProvider);
      ref.invalidate(consolidadoProvider);
      if (mounted) mostrarMensaje(context, 'Reloj demo: ahora es ${Fechas.corta(f)}.');
    } catch (e) {
      if (mounted) mostrarError(context, e);
    }
  }

  @override
  Widget build(BuildContext context) {
    final perfil = ref.watch(perfilProvider);
    final saldoActivo = ref.watch(saldoRapidoActivoProvider).value ?? false;
    return Scaffold(
      appBar: AppBar(title: const Text('Perfil y seguridad')),
      body: ListView(
        children: [
          VistaAsync<Perfil>(
            valor: perfil,
            alReintentar: () => ref.invalidate(perfilProvider),
            datos: (p) => Column(
              children: [
                ListTile(
                  leading: const Icon(Icons.person_outline),
                  title: Text('${p.primerNombre} ${p.primerApellido}'),
                  subtitle: Text('${p.nacionalidadDoc}-${p.cedula}'),
                ),
                if (p.correo != null)
                  ListTile(leading: const Icon(Icons.mail_outline), title: Text(p.correo!)),
                if (p.telefonoMovil != null)
                  ListTile(leading: const Icon(Icons.phone_iphone), title: Text(p.telefonoMovil!)),
                ListTile(
                  leading: Icon(p.kycAprobado ? Icons.verified : Icons.hourglass_top),
                  title: Text(p.kycAprobado ? 'Identidad verificada (KYC)' : 'Verificación pendiente'),
                  subtitle: Text('Cliente desde ${Fechas.media(p.fechaRegistro)}'),
                ),
              ],
            ),
          ),
          const Divider(),
          SwitchListTile(
            secondary: const Icon(Icons.bolt),
            value: saldoActivo,
            onChanged: _ocupado ? null : _alternarSaldoRapido,
            title: const Text('Saldo rápido'),
            subtitle: const Text('Ver tu total en Bs y USD desde el inicio de sesión, sin detalle por banco. Requiere PIN.'),
          ),
          ListTile(
            leading: const Icon(Icons.phonelink_erase),
            title: const Text('Revocar este dispositivo'),
            subtitle: const Text('Cierra la sesión y anula el saldo rápido aquí.'),
            onTap: _ocupado ? null : _revocarDispositivo,
          ),
          ListTile(
            leading: const Icon(Icons.logout),
            title: const Text('Cerrar sesión'),
            onTap: () => ref.read(sessionProvider.notifier).cerrar(),
          ),
          const Divider(),
          ListTile(
            leading: const Icon(Icons.fast_forward_outlined),
            title: const Text('Modo demo: avanzar el reloj 1 día'),
            subtitle: const Text('Ejecuta el cierre de día (racha, colchón, cuotas). Solo con backend en perfil demo.'),
            onTap: _avanzarReloj,
          ),
          const Padding(padding: EdgeInsets.all(16), child: AvisoSinClaves(compacto: true)),
        ],
      ),
    );
  }
}
