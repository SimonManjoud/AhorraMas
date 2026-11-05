import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/money/dinero.dart';
import '../../../core/theme/app_theme.dart';
import '../../../core/util/fechas.dart';
import '../../../core/widgets/comunes.dart';
import '../domain/cofre_models.dart';
import 'cofre_providers.dart';

/// Espejo de Impacto: cuánto poder de compra salvó el cofre en divisas frente a la devaluación.
/// Solo lee del ledger y de la tasa BCV; es una simulación informativa, no asesoría.
class EspejoScreen extends ConsumerStatefulWidget {
  const EspejoScreen({super.key});

  @override
  ConsumerState<EspejoScreen> createState() => _EspejoScreenState();
}

class _EspejoScreenState extends ConsumerState<EspejoScreen> {
  late MesAnio _mes;

  @override
  void initState() {
    super.initState();
    final hoy = DateTime.now();
    _mes = (anio: hoy.year, mes: hoy.month);
  }

  bool get _esMesActual {
    final hoy = DateTime.now();
    return _mes.anio == hoy.year && _mes.mes == hoy.month;
  }

  void _mover(int delta) {
    final d = DateTime(_mes.anio, _mes.mes + delta);
    setState(() => _mes = (anio: d.year, mes: d.month));
  }

  @override
  Widget build(BuildContext context) {
    final clave = _esMesActual ? null : _mes;
    final espejo = ref.watch(espejoProvider(clave));
    return Scaffold(
      appBar: AppBar(title: const Text('Espejo de Impacto')),
      body: RefreshIndicator(
        onRefresh: () async => ref.invalidate(espejoProvider(clave)),
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            Row(
              children: [
                IconButton(onPressed: () => _mover(-1), icon: const Icon(Icons.chevron_left)),
                Expanded(
                  child: Text(
                    Fechas.mesAnio(_mes.anio, _mes.mes),
                    textAlign: TextAlign.center,
                    style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700),
                  ),
                ),
                IconButton(
                  onPressed: _esMesActual ? null : () => _mover(1),
                  icon: const Icon(Icons.chevron_right),
                ),
              ],
            ),
            const SizedBox(height: 8),
            VistaAsync<EspejoImpacto>(
              valor: espejo,
              alReintentar: () => ref.invalidate(espejoProvider(clave)),
              datos: (e) => _Contenido(espejo: e),
            ),
          ],
        ),
      ),
    );
  }
}

class _Contenido extends StatelessWidget {
  const _Contenido({required this.espejo});

  final EspejoImpacto espejo;

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context).textTheme;
    final cs = Theme.of(context).colorScheme;
    final colores = AhorraColores.of(context);
    final e = espejo;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Container(
          padding: const EdgeInsets.all(22),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(24),
            gradient: LinearGradient(
              colors: [cs.primary, Color.lerp(cs.primary, colores.cofre, 0.35)!],
              begin: Alignment.topLeft,
              end: Alignment.bottomRight,
            ),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Icon(Icons.shield, color: cs.onPrimary),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text('Tu cofre te protegió',
                        overflow: TextOverflow.ellipsis, style: t.labelLarge?.copyWith(color: cs.onPrimary)),
                  ),
                  if (e.provisional)
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                      decoration: BoxDecoration(
                        color: cs.onPrimary.withValues(alpha: 0.18),
                        borderRadius: BorderRadius.circular(10),
                      ),
                      child: Text('Provisional', style: t.labelSmall?.copyWith(color: cs.onPrimary)),
                    ),
                ],
              ),
              const SizedBox(height: 12),
              Text(e.frase,
                  key: const ValueKey('espejo-frase'),
                  style: t.headlineSmall?.copyWith(color: cs.onPrimary, fontWeight: FontWeight.w700)),
            ],
          ),
        ),
        const SizedBox(height: 16),
        Row(
          children: [
            Expanded(
              child: _Cifra(
                titulo: 'Pérdida evitada en Bs',
                valor: Dinero.bs(e.perdidaEvitadaBsCts),
                color: colores.exito,
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: _Cifra(
                titulo: 'Equivale en USD',
                valor: Dinero.usd(e.perdidaEvitadaUsdCts),
                color: colores.exito,
              ),
            ),
          ],
        ),
        const SizedBox(height: 12),
        Row(
          children: [
            Expanded(child: _Cifra(titulo: 'Saldo de tus cofres', valor: Dinero.usd(e.saldoCofresUsdCts))),
            const SizedBox(width: 12),
            Expanded(
              child: _Cifra(
                titulo: 'Devaluación del Bs en el mes',
                valor: Dinero.porcentajePb(e.devaluacionPb),
                color: colores.peligro,
              ),
            ),
          ],
        ),
        const SizedBox(height: 12),
        Card(
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('Tasa oficial BCV', style: t.titleSmall?.copyWith(fontWeight: FontWeight.w700)),
                const SizedBox(height: 8),
                if (e.tasaInicio != null)
                  _Fila('Inicio (${Fechas.corta(e.tasaInicio!.fecha)})', Dinero.tasa(e.tasaInicio!.valorBsX10000)),
                if (e.tasaFin != null)
                  _Fila('Cierre (${Fechas.corta(e.tasaFin!.fecha)})', Dinero.tasa(e.tasaFin!.valorBsX10000)),
                _Fila(
                  'Inflación mensual',
                  e.inflacionMensualPb == null ? 'Aún no publicada' : Dinero.porcentajePb(e.inflacionMensualPb!),
                ),
                const Divider(height: 24),
                _Fila('Tu dinero total en Bs', Dinero.bs(e.totalBsCts)),
                _Fila('Tu dinero total en USD', Dinero.usd(e.totalUsdCts)),
              ],
            ),
          ),
        ),
        const SizedBox(height: 12),
        Text(
          'Calculado con cada aporte vivo de tu cofre: dólares × (tasa de cierre − tasa del día del '
          'aporte o del 1.º del mes).',
          style: t.bodySmall?.copyWith(color: cs.onSurfaceVariant),
        ),
        const SizedBox(height: 12),
        AvisoSimulacion(texto: e.disclaimer),
      ],
    );
  }
}

class _Cifra extends StatelessWidget {
  const _Cifra({required this.titulo, required this.valor, this.color});

  final String titulo;
  final String valor;
  final Color? color;

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context).textTheme;
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(titulo, style: t.bodySmall),
            const SizedBox(height: 4),
            FittedBox(
              fit: BoxFit.scaleDown,
              alignment: Alignment.centerLeft,
              child: Text(valor, style: t.titleLarge?.copyWith(fontWeight: FontWeight.w800, color: color)),
            ),
          ],
        ),
      ),
    );
  }
}

class _Fila extends StatelessWidget {
  const _Fila(this.etiqueta, this.valor);

  final String etiqueta;
  final String valor;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 3),
      child: Row(
        children: [
          Expanded(child: Text(etiqueta)),
          Flexible(child: Text(valor, textAlign: TextAlign.end, style: const TextStyle(fontWeight: FontWeight.w600))),
        ],
      ),
    );
  }
}
