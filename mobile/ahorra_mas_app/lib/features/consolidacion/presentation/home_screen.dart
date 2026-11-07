import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/di/providers.dart';
import '../../../core/money/dinero.dart';
import '../../../core/session/session_controller.dart';
import '../../../core/theme/app_theme.dart';
import '../../../core/util/fechas.dart';
import '../../../core/widgets/comunes.dart';
import '../../../core/widgets/libreta.dart';
import '../../auth/domain/recuerdo_equipo.dart';
import '../../auth/presentation/auth_providers.dart';
import '../../cofre/domain/cofre_models.dart';
import '../../cofre/presentation/cofre_providers.dart';
import '../../cofre/presentation/widgets/boton_apartar.dart';
import '../domain/consolidacion_models.dart';
import 'consolidacion_providers.dart';
import 'vincular_bancos.dart';
import 'widgets/grafico_posicion.dart';

/// "Mi dinero": tapa vino con la posición global en USD y Bs y la tasa BCV del día; debajo,
/// la dona de distribución por cuenta (toca una porción para saber qué cuenta es) y la Ruta del día.
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
    // El equipo recuerda los bancos (sin montos) para mostrarlos en el login la próxima vez.
    ref.listen(consolidadoProvider, (_, s) {
      final d = s.value;
      if (d == null) return;
      ref.read(recuerdoEquipoServiceProvider).recordarBancos([
        for (final i in d.instituciones) BancoRecordado(nombre: i.nombre, tipo: i.tipo),
      ]);
    });
    final c = AhorraColores.of(context);
    return Scaffold(
      appBar: AppBar(
        titleSpacing: 16,
        title: Row(
          children: [
            Container(
              width: 36,
              height: 36,
              decoration: BoxDecoration(color: c.tapa, borderRadius: BorderRadius.circular(12)),
              child: Icon(Icons.savings_rounded, size: 20, color: c.sobreTapa),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Text(
                'Hola, ${usuario?.primerNombre ?? ''}',
                overflow: TextOverflow.ellipsis,
              ),
            ),
          ],
        ),
        actions: [
          IconButton(
            tooltip: 'Notificaciones',
            onPressed: () => context.push('/mas/notificaciones'),
            icon: const Icon(Icons.notifications_none_rounded),
          ),
          const SizedBox(width: 8),
        ],
      ),
      body: RefreshIndicator(
        color: c.tapa,
        onRefresh: () => _refrescar(ref),
        child: ListView(
          physics: const AlwaysScrollableScrollPhysics(),
          padding: const EdgeInsets.fromLTRB(16, 4, 16, 28),
          children: [
            VistaAsync<Consolidado>(
              valor: consolidado,
              alReintentar: () => ref.invalidate(consolidadoProvider),
              datos: (datos) => Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  _Tapa(consolidado: datos),
                  const SizedBox(height: 4),
                  _Vinculos(instituciones: datos.instituciones.length),
                  const SizedBox(height: 4),
                  if (datos.instituciones.isEmpty)
                    const _SinBancos()
                  else
                    GraficoPosicion(consolidado: datos),
                  const _RutaDeHoy(),
                  const SizedBox(height: 20),
                  _Pie(consolidado: datos),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// Tapa vino: "Posición global", total en USD a escala de titular, Bs y la tasa BCV en una píldora.
class _Tapa extends StatelessWidget {
  const _Tapa({required this.consolidado});

  final Consolidado consolidado;

  @override
  Widget build(BuildContext context) {
    final c = AhorraColores.of(context);
    final t = Theme.of(context).textTheme;
    final cuentas = consolidado.cuentas.length;
    final tasa = consolidado.tasa;
    return Container(
      padding: const EdgeInsets.fromLTRB(20, 18, 20, 18),
      decoration: BoxDecoration(color: c.tapa, borderRadius: BorderRadius.circular(28)),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Text('Posición global',
                    style: t.titleSmall?.copyWith(color: c.sobreTapaTenue, fontWeight: FontWeight.w600)),
              ),
              if (cuentas > 0)
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                  decoration: BoxDecoration(
                    color: Colors.white.withValues(alpha: 0.12),
                    borderRadius: BorderRadius.circular(20),
                  ),
                  child: Text('$cuentas ${cuentas == 1 ? 'cuenta' : 'cuentas'}',
                      style: t.labelMedium?.copyWith(color: c.sobreTapa)),
                ),
            ],
          ),
          const SizedBox(height: 10),
          FittedBox(
            fit: BoxFit.scaleDown,
            alignment: Alignment.centerLeft,
            child: Text(
              Dinero.usd(consolidado.totalUsdCts),
              key: const ValueKey('total-usd'),
              style: t.displayLarge?.copyWith(color: c.sobreTapa),
            ),
          ),
          const SizedBox(height: 4),
          Text(
            Dinero.bs(consolidado.totalBsCts),
            key: const ValueKey('total-bs'),
            style: AppTheme.cifra(tamano: 16, peso: FontWeight.w600, color: c.sobreTapaTenue),
          ),
          if (tasa.valorBsX10000 > 0) ...[
            const SizedBox(height: 16),
            PildoraTasa(
              tasa: Dinero.numero(Dinero.dividirRedondeando(tasa.valorBsX10000, 100)),
              fecha: Fechas.libreta(tasa.fecha),
              sobreTapa: true,
            ),
          ],
        ],
      ),
    );
  }
}

/// La cuota de hoy de la meta pendiente, en una lámina con su botón de un toque.
class _RutaDeHoy extends ConsumerWidget {
  const _RutaDeHoy();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final rutas = ref.watch(rutasDelDiaProvider).value ?? const <RutaDelDia>[];
    if (rutas.isEmpty) return const SizedBox.shrink();
    final pendientes = rutas.where((r) => !r.cumplida).toList();
    final c = AhorraColores.of(context);
    final cs = Theme.of(context).colorScheme;
    final t = Theme.of(context).textTheme;
    final ruta = pendientes.isEmpty ? rutas.first : pendientes.first;
    return Padding(
      padding: const EdgeInsets.only(top: 16),
      child: Lamina(
        onTap: () => context.go('/cofre/meta/${ruta.idMeta}'),
        child: pendientes.isEmpty
            ? Row(
                children: [
                  CircleAvatar(
                    radius: 18,
                    backgroundColor: c.exito.withValues(alpha: 0.14),
                    child: Icon(Icons.check_rounded, size: 20, color: c.exito),
                  ),
                  const SizedBox(width: 12),
                  Expanded(child: Text('¡Cumpliste tu Ruta de hoy!', style: t.titleSmall)),
                ],
              )
            : Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Row(
                    children: [
                      CircleAvatar(
                        radius: 18,
                        backgroundColor: cs.primaryContainer,
                        child: Icon(Icons.flag_rounded, size: 18, color: cs.onPrimaryContainer),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text('Ruta de hoy · ${Fechas.libreta(ruta.fecha ?? DateTime.now())}',
                                style: t.bodySmall?.copyWith(color: c.tintaGris)),
                            Text('${ruta.nombreMeta}, hoy toca', overflow: TextOverflow.ellipsis, style: t.titleSmall),
                          ],
                        ),
                      ),
                      const SizedBox(width: 8),
                      Text(Dinero.usd(ruta.faltanteHoyUsdCts),
                          style: AppTheme.cifra(tamano: 18, peso: FontWeight.w800, color: c.tinta)),
                    ],
                  ),
                  const SizedBox(height: 14),
                  BotonApartarCuota(ruta: ruta),
                  if (pendientes.length > 1)
                    Align(
                      alignment: Alignment.center,
                      child: TextButton(
                        onPressed: () => context.go('/cofre'),
                        child: Text('y ${pendientes.length - 1} más en tu Cofre'),
                      ),
                    ),
                ],
              ),
      ),
    );
  }
}

/// Línea de vínculos bajo la tapa: cuántas instituciones y acceso a sus consentimientos.
class _Vinculos extends StatelessWidget {
  const _Vinculos({required this.instituciones});

  final int instituciones;

  @override
  Widget build(BuildContext context) {
    final c = AhorraColores.of(context);
    final texto = switch (instituciones) {
      0 => 'Sin instituciones vinculadas',
      1 => '1 institución vinculada',
      _ => '$instituciones instituciones vinculadas',
    };
    return Row(
      children: [
        const SizedBox(width: 4),
        Icon(Icons.link_rounded, size: 18, color: c.tintaGris),
        const SizedBox(width: 6),
        Expanded(
          child: Text(texto, style: Theme.of(context).textTheme.bodySmall?.copyWith(color: c.tintaGris)),
        ),
        TextButton(
          onPressed: () => context.push('/inicio/consentimientos'),
          child: const Text('Gestionar vínculos'),
        ),
      ],
    );
  }
}

/// Pie: botón para vincular más bancos, regla de oro y hora de sincronización.
class _Pie extends ConsumerWidget {
  const _Pie({required this.consolidado});

  final Consolidado consolidado;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final c = AhorraColores.of(context);
    final t = Theme.of(context).textTheme;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        OutlinedButton.icon(
          key: const ValueKey('btn-vincular'),
          onPressed: () => iniciarVinculacion(context, ref),
          icon: const Icon(Icons.add_rounded, size: 20),
          label: const Text('Vincular bancos'),
        ),
        const SizedBox(height: 16),
        const AvisoSinClaves(compacto: true),
        const SizedBox(height: 4),
        Padding(
          padding: const EdgeInsets.only(left: 20),
          child: Text('Sincronizado ${Fechas.conHora(consolidado.actualizadoEn)}',
              style: t.bodySmall?.copyWith(color: c.tintaGris)),
        ),
      ],
    );
  }
}

class _SinBancos extends StatelessWidget {
  const _SinBancos();

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context).textTheme;
    final c = AhorraColores.of(context);
    return Lamina(
      relleno: const EdgeInsets.all(20),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('Tu libreta está en blanco', style: t.headlineSmall),
          const SizedBox(height: 6),
          Text(
            'Vincula tus bancos y billeteras desde la página oficial de cada banco. Aquí verás '
            'todo tu dinero en un solo lugar, en Bs y USD a tasa BCV.',
            style: t.bodyMedium?.copyWith(color: c.tintaGris),
          ),
        ],
      ),
    );
  }
}
