import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/di/providers.dart';
import '../../../core/money/dinero.dart';
import '../../../core/session/session_controller.dart';
import '../../../core/theme/app_theme.dart';
import '../../../core/util/fechas.dart';
import '../../../core/widgets/comunes.dart';
import '../../../core/widgets/dinero_widgets.dart';
import '../../cofre/presentation/cofre_providers.dart';
import '../domain/consolidacion_models.dart';
import 'consolidacion_providers.dart';
import 'vincular_bancos.dart';

/// "Mi dinero": posición global en Bs y USD a tasa BCV, por institución y cuenta.
class HomeScreen extends ConsumerWidget {
  const HomeScreen({super.key});

  Future<void> _refrescar(WidgetRef ref) async {
    try {
      await ref.read(consolidacionRepositoryProvider).sincronizar();
    } catch (_) {
      // Si la sincronización falla mostramos el último consolidado disponible.
    }
    ref.invalidate(rutasDelDiaProvider);
    ref.invalidate(consolidadoProvider);
    await ref.read(consolidadoProvider.future).catchError((_) => _vacio);
  }

  static final _vacio = Consolidado(
    tasa: const Tasa(fecha: null, valorBsX10000: 0),
    totalBsCts: 0,
    totalUsdCts: 0,
    actualizadoEn: null,
    instituciones: const [],
  );

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final usuario = ref.watch(sessionProvider)?.usuario;
    final consolidado = ref.watch(consolidadoProvider);
    return Scaffold(
      appBar: AppBar(
        title: Text('Hola, ${usuario?.primerNombre ?? ''}'),
        actions: [
          IconButton(
            tooltip: 'Notificaciones',
            onPressed: () => context.push('/mas/notificaciones'),
            icon: const Icon(Icons.notifications_outlined),
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton.extended(
        key: const ValueKey('btn-vincular'),
        onPressed: () => iniciarVinculacion(context, ref),
        icon: const Icon(Icons.add_link),
        label: const Text('Vincular bancos'),
      ),
      body: RefreshIndicator(
        onRefresh: () => _refrescar(ref),
        child: ListView(
          physics: const AlwaysScrollableScrollPhysics(),
          padding: const EdgeInsets.fromLTRB(16, 4, 16, 96),
          children: [
            VistaAsync<Consolidado>(
              valor: consolidado,
              alReintentar: () => ref.invalidate(consolidadoProvider),
              datos: (c) => Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  _PosicionGlobal(consolidado: c),
                  const _RutaDeHoy(),
                  TituloSeccion(
                    'Mis instituciones',
                    accion: TextButton(
                      onPressed: () => context.push('/inicio/consentimientos'),
                      child: const Text('Gestionar vínculos'),
                    ),
                  ),
                  if (c.instituciones.isEmpty)
                    const _SinBancos()
                  else
                    for (final i in c.instituciones) ...[
                      _TarjetaInstitucion(institucion: i),
                      const SizedBox(height: 12),
                    ],
                  const SizedBox(height: 8),
                  const AvisoSinClaves(compacto: true),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _PosicionGlobal extends StatelessWidget {
  const _PosicionGlobal({required this.consolidado});

  final Consolidado consolidado;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final t = Theme.of(context).textTheme;
    final sobre = cs.onPrimary;
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(24),
        gradient: LinearGradient(
          colors: [cs.primary, Color.lerp(cs.primary, Colors.black, 0.25)!],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('Posición global', style: t.labelLarge?.copyWith(color: sobre.withValues(alpha: 0.85))),
          const SizedBox(height: 6),
          Text(
            Dinero.bs(consolidado.totalBsCts),
            key: const ValueKey('total-bs'),
            style: t.headlineMedium?.copyWith(color: sobre, fontWeight: FontWeight.w800),
          ),
          Text(
            Dinero.usd(consolidado.totalUsdCts),
            key: const ValueKey('total-usd'),
            style: t.titleLarge?.copyWith(color: sobre.withValues(alpha: 0.95)),
          ),
          const SizedBox(height: 14),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            crossAxisAlignment: WrapCrossAlignment.center,
            children: [
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
                decoration: BoxDecoration(
                  color: sobre.withValues(alpha: 0.15),
                  borderRadius: BorderRadius.circular(20),
                ),
                child: Text(
                  'Tasa BCV ${Dinero.tasa(consolidado.tasa.valorBsX10000)} · ${Fechas.corta(consolidado.tasa.fecha)}',
                  style: t.bodySmall?.copyWith(color: sobre, fontWeight: FontWeight.w600),
                ),
              ),
              Text('Actualizado ${Fechas.conHora(consolidado.actualizadoEn)}',
                  style: t.bodySmall?.copyWith(color: sobre.withValues(alpha: 0.8))),
            ],
          ),
        ],
      ),
    );
  }
}

class _RutaDeHoy extends ConsumerWidget {
  const _RutaDeHoy();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final rutas = ref.watch(rutasDelDiaProvider).value ?? const [];
    final pendientes = rutas.where((r) => !r.cumplida).toList();
    if (rutas.isEmpty) return const SizedBox(height: 4);
    final colores = AhorraColores.of(context);
    return Padding(
      padding: const EdgeInsets.only(top: 16),
      child: Card(
        color: colores.cofreContenedor,
        child: ListTile(
          contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
          leading: Icon(Icons.route_outlined, color: colores.alCofre),
          title: Text(
            pendientes.isEmpty ? '¡Cumpliste tu Ruta de hoy!' : pendientes.first.mensaje,
            style: TextStyle(color: colores.alCofre, fontWeight: FontWeight.w600),
          ),
          subtitle: pendientes.length > 1
              ? Text('y ${pendientes.length - 1} meta(s) más', style: TextStyle(color: colores.alCofre))
              : null,
          trailing: Icon(Icons.chevron_right, color: colores.alCofre),
          onTap: () => pendientes.isEmpty
              ? context.go('/cofre')
              : context.go('/cofre/meta/${pendientes.first.idMeta}'),
        ),
      ),
    );
  }
}

class _SinBancos extends StatelessWidget {
  const _SinBancos();

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          children: [
            Icon(Icons.account_balance_outlined, size: 40, color: Theme.of(context).colorScheme.primary),
            const SizedBox(height: 8),
            const Text('Aún no vinculas bancos', style: TextStyle(fontWeight: FontWeight.w600)),
            const SizedBox(height: 4),
            const Text(
              'Vincula tus bancos y billeteras para ver todo tu dinero en un solo lugar, en Bs y USD.',
              textAlign: TextAlign.center,
            ),
          ],
        ),
      ),
    );
  }
}

class _TarjetaInstitucion extends StatelessWidget {
  const _TarjetaInstitucion({required this.institucion});

  final InstitucionConsolidada institucion;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final cripto = institucion.tipo == 'plataforma_cripto';
    return Card(
      clipBehavior: Clip.antiAlias,
      child: Column(
        children: [
          ListTile(
            leading: CircleAvatar(
              backgroundColor: cs.secondaryContainer,
              child: Icon(cripto ? Icons.currency_bitcoin : Icons.account_balance, color: cs.onSecondaryContainer),
            ),
            title: Text(institucion.nombre, style: const TextStyle(fontWeight: FontWeight.w700)),
            subtitle: Text(cripto ? 'Plataforma cripto' : 'Banco'),
            trailing: MontoDual(
              bsCts: institucion.totalBsCts,
              usdCts: institucion.totalUsdCts,
              alineacion: CrossAxisAlignment.end,
            ),
          ),
          const Divider(height: 1),
          for (final c in institucion.cuentas)
            ListTile(
              dense: true,
              onTap: () => context.push('/inicio/cuenta/${c.idCuenta}', extra: c),
              title: Text('${c.nombreVisible} •••• ${c.ultimos4}'),
              subtitle: Text('${c.tipo} · ${c.moneda}${c.soloLectura ? ' · solo lectura' : ''}'),
              trailing: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  Text(Dinero.enMoneda(c.saldoCts, c.moneda), style: const TextStyle(fontWeight: FontWeight.w600)),
                  Text(
                    c.moneda == 'VES' ? '≈ ${Dinero.usd(c.saldoUsdCts)}' : '≈ ${Dinero.bs(c.saldoBsCts)}',
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
