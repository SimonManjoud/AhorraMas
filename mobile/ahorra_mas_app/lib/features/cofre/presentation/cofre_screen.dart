import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/money/dinero.dart';
import '../../../core/theme/app_theme.dart';
import '../../../core/widgets/comunes.dart';
import '../../../core/widgets/libreta.dart';
import '../domain/cofre_models.dart';
import 'cofre_providers.dart';
import 'widgets/boton_apartar.dart';
import 'widgets/cofre_widgets.dart';
import '../../../core/widgets/texto_enfasis.dart';

/// Pestaña "Cofre Protector" en la Libreta: la meta con la cuota de hoy primero (con su
/// sello y su botón de un toque), luego la racha con sus días colchón, el Espejo de Impacto
/// y el resto de las metas.
class CofreScreen extends ConsumerWidget {
  const CofreScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final metas = ref.watch(metasProvider);
    final racha = ref.watch(rachaProvider);
    final espejo = ref.watch(espejoProvider(null));
    final rutas = ref.watch(rutasDelDiaProvider).value ?? const <RutaDelDia>[];
    final c = AhorraColores.of(context);
    return Scaffold(
      appBar: AppBar(
        title: const Text('Cofre Protector'),
        actions: [
          TextButton.icon(
            onPressed: () => context.push('/cofre/nueva'),
            icon: const Icon(Icons.add, size: 20),
            label: const Text('Nueva meta'),
          ),
          const SizedBox(width: 8),
        ],
      ),
      body: FondoHoja(
        child: RefreshIndicator(
          color: c.tinta,
          onRefresh: () async {
            ref.invalidate(metasProvider);
            ref.invalidate(rachaProvider);
            ref.invalidate(espejoProvider);
            ref.invalidate(rutasDelDiaProvider);
          },
          child: ListView(
            physics: const AlwaysScrollableScrollPhysics(),
            padding: const EdgeInsets.fromLTRB(16, 16, 16, 32),
            children: [
              VistaAsync<List<Meta>>(
                valor: metas,
                alReintentar: () => ref.invalidate(metasProvider),
                datos: (lista) {
                  if (lista.isEmpty) return _SinMetas(alCrear: () => context.push('/cofre/nueva'));
                  // La meta destacada es la primera de la lista con cuota pendiente; si no hay, la primera.
                  final pendientes = rutas.where((r) => !r.cumplida).toList();
                  final destacada = lista.firstWhere(
                    (m) => pendientes.any((r) => r.idMeta == m.idMeta),
                    orElse: () => lista.first,
                  );
                  final pendiente = pendientes.where((r) => r.idMeta == destacada.idMeta).firstOrNull;
                  final resto = lista.where((m) => m.idMeta != destacada.idMeta).toList();
                  // Un solo sello violeta por vista: lo lleva la destacada o, si no tiene, la primera sellada.
                  final idSello = destacada.selloActivo != null
                      ? destacada.idMeta
                      : resto.where((m) => m.selloActivo != null).firstOrNull?.idMeta;
                  final apartado = lista.fold<int>(0, (a, m) => a + m.acumuladoUsdCts);
                  return Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      _Destacada(
                        meta: destacada,
                        ruta: pendiente?.idMeta == destacada.idMeta ? pendiente : null,
                        conSello: idSello == destacada.idMeta,
                      ),
                      racha.when(
                        data: (r) => _BloqueRacha(racha: r),
                        loading: () => const SizedBox(height: 80),
                        error: (_, _) => const SizedBox.shrink(),
                      ),
                      if (espejo.value case final e?) _BloqueEspejo(espejo: e),
                      const SizedBox(height: 22),
                      Padding(
                        padding: const EdgeInsets.only(left: 4, bottom: 8),
                        child: Text(
                          'Mis metas - ${Dinero.usd(apartado)} apartados',
                          style: Theme.of(context).textTheme.titleSmall?.copyWith(color: c.tintaGris),
                        ),
                      ),
                      if (resto.isEmpty)
                        Padding(
                          padding: const EdgeInsets.symmetric(vertical: 12),
                          child: TextoEnfasis(
                            'Solo tienes esta meta. Crea otra con «Nueva meta».',
                            style: Theme.of(context).textTheme.bodySmall?.copyWith(color: c.tintaGris),
                          ),
                        ),
                      for (final m in resto) ...[
                        Lamina(
                          relleno: const EdgeInsets.symmetric(horizontal: 16, vertical: 2),
                          child: MetaCard(
                            meta: m,
                            conSello: m.idMeta == idSello,
                            onTap: () => context.push('/cofre/meta/${m.idMeta}'),
                          ),
                        ),
                        const SizedBox(height: 12),
                      ],
                    ],
                  );
                },
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// La meta del día: bloque del libro con su sello y tira, y debajo la cuota de hoy a escala
/// de titular con su botón de un toque.
class _Destacada extends StatelessWidget {
  const _Destacada({required this.meta, required this.ruta, required this.conSello});

  final Meta meta;
  final RutaDelDia? ruta;
  final bool conSello;

  @override
  Widget build(BuildContext context) {
    final c = AhorraColores.of(context);
    final t = Theme.of(context).textTheme;
    final r = ruta;
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Lamina(
        relleno: const EdgeInsets.fromLTRB(16, 2, 16, 16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            MetaCard(
              meta: meta,
              conSello: conSello,
              mostrarCuota: ruta == null,
              onTap: () => context.push('/cofre/meta/${meta.idMeta}'),
            ),
            if (r != null) ...[
              const Filete(suave: true),
              const SizedBox(height: 12),
              Text(
                '${r.estatus == 'colchon' ? 'Hoy (día colchón)' : 'Hoy toca apartar'} ${Dinero.usd(r.faltanteHoyUsdCts)}',
                style: t.headlineSmall,
              ),
              const SizedBox(height: 4),
              Text(
                '${Dinero.bs(Dinero.usdABs(r.faltanteHoyUsdCts, r.tasaValorBsX10000))} a tasa BCV '
                '${Dinero.tasa(r.tasaValorBsX10000)} - ${r.periodosRestantes} '
                '${r.periodosRestantes == 1 ? 'periodo restante' : 'periodos restantes'}',
                style: t.bodySmall?.copyWith(color: c.tintaGris),
              ),
              const SizedBox(height: 12),
              BotonApartarCuota(ruta: r),
            ],
          ],
        ),
      ),
    );
  }
}

/// Racha en marcas de día: lleno = aporte, punteado = día colchón (cubierto, no fallo).
class _BloqueRacha extends StatelessWidget {
  const _BloqueRacha({required this.racha});

  final Racha racha;

  @override
  Widget build(BuildContext context) {
    final dias = [...racha.ultimosDias]..sort((a, b) => a.fecha.compareTo(b.fecha));
    final recientes = dias.length > 16 ? dias.sublist(dias.length - 16) : dias;
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Lamina(
        relleno: const EdgeInsets.fromLTRB(16, 4, 16, 16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            RachaResumen(racha: racha, onTap: () => context.push('/cofre/racha')),
            if (recientes.isNotEmpty) ...[const SizedBox(height: 4), FranjaDias(dias: recientes)],
          ],
        ),
      ),
    );
  }
}

/// Espejo de Impacto en una línea del libro; el detalle vive en su pantalla.
class _BloqueEspejo extends StatelessWidget {
  const _BloqueEspejo({required this.espejo});

  final EspejoImpacto espejo;

  @override
  Widget build(BuildContext context) {
    final c = AhorraColores.of(context);
    final t = Theme.of(context).textTheme;
    return Lamina(
      onTap: () => context.push('/cofre/espejo'),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            children: [
              Expanded(child: Text('Espejo de Impacto', style: t.titleMedium)),
              Icon(Icons.chevron_right, color: c.tintaGris),
            ],
          ),
          const SizedBox(height: 6),
          Text(espejo.frase, style: t.bodyMedium),
          const SizedBox(height: 4),
          Text(
            AvisoSimulacion.textoBase,
            style: t.bodySmall?.copyWith(color: c.tintaGris, fontStyle: FontStyle.italic),
          ),
        ],
      ),
    );
  }
}

class _SinMetas extends StatelessWidget {
  const _SinMetas({required this.alCrear});

  final VoidCallback alCrear;

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context).textTheme;
    final c = AhorraColores.of(context);
    return Lamina(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 4),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('¿Para qué quieres ahorrar?', style: t.headlineSmall),
                const SizedBox(height: 6),
                Text(
                  'Elige una meta con fecha y monto en dólares. Nosotros la convertimos en una pequeña '
                  'cuota diaria a tasa BCV.',
                  style: t.bodyMedium?.copyWith(color: c.tintaGris),
                ),
                const SizedBox(height: 14),
                FilledButton(onPressed: alCrear, child: const Text('Crear mi primera meta')),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
