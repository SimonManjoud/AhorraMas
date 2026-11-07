import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/money/dinero.dart';
import '../../../core/theme/app_theme.dart';
import '../../../core/util/fechas.dart';
import '../../../core/widgets/comunes.dart';
import '../../../core/widgets/libreta.dart';
import '../domain/cofre_models.dart';
import 'cofre_providers.dart';
import 'widgets/cofre_widgets.dart';

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
      body: FondoHoja(
        child: RefreshIndicator(
        color: AhorraColores.of(context).tinta,
        onRefresh: () async => ref.invalidate(espejoProvider(clave)),
        child: ListView(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 32),
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
    final c = AhorraColores.of(context);
    final e = espejo;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (e.provisional) ...[
          Align(alignment: Alignment.centerRight, child: EtiquetaLibro('Provisional', color: c.tintaGris)),
          const SizedBox(height: 8),
        ],
        Text(e.frase, key: const ValueKey('espejo-frase'), style: t.headlineMedium),
        const SizedBox(height: 16),
        const Filete(doble: true),
        _Fila('Pérdida evitada en Bs', Dinero.bs(e.perdidaEvitadaBsCts), destacado: true),
        _Fila('Equivale en USD', Dinero.usd(e.perdidaEvitadaUsdCts), destacado: true),
        _Fila('Saldo de tus cofres', Dinero.usd(e.saldoCofresUsdCts)),
        _Fila('Devaluación del Bs en el mes', '▼ ${Dinero.porcentajePb(e.devaluacionPb)}'),
        const SizedBox(height: 22),
        const Rubrica('Tasa oficial BCV'),
        const SizedBox(height: 6),
        const Filete(),
        if (e.tasaInicio != null)
          _Fila('Inicio (${Fechas.libretaAnio(e.tasaInicio!.fecha)})', Dinero.tasa(e.tasaInicio!.valorBsX10000)),
        if (e.tasaFin != null)
          _Fila('Cierre (${Fechas.libretaAnio(e.tasaFin!.fecha)})', Dinero.tasa(e.tasaFin!.valorBsX10000)),
        _Fila(
          'Inflación mensual',
          e.inflacionMensualPb == null ? 'Aún no publicada' : Dinero.porcentajePb(e.inflacionMensualPb!),
        ),
        const SizedBox(height: 2),
        const Filete(doble: true),
        _Fila('Tu dinero total en Bs', Dinero.bs(e.totalBsCts)),
        _Fila('Tu dinero total en USD', Dinero.usd(e.totalUsdCts)),
        const SizedBox(height: 14),
        Text(
          'Calculado con cada aporte vivo de tu cofre: dólares × (tasa de cierre − tasa del día del '
          'aporte o del 1.º del mes).',
          style: t.bodySmall?.copyWith(color: c.tintaGris),
        ),
        const SizedBox(height: 12),
        AvisoSimulacion(texto: e.disclaimer),
      ],
    );
  }
}

/// Fila del libro: concepto a la izquierda, cifra tabular a la derecha.
class _Fila extends StatelessWidget {
  const _Fila(this.etiqueta, this.valor, {this.destacado = false});

  final String etiqueta;
  final String valor;
  final bool destacado;

  @override
  Widget build(BuildContext context) {
    final c = AhorraColores.of(context);
    return DecoratedBox(
      decoration: BoxDecoration(border: Border(bottom: BorderSide(color: c.reglaSuave))),
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 10),
        child: Row(
          children: [
            Expanded(child: Text(etiqueta, style: Theme.of(context).textTheme.bodyMedium)),
            const SizedBox(width: 12),
            Flexible(
              child: Text(
                valor,
                textAlign: TextAlign.end,
                style: AppTheme.cifra(
                  tamano: destacado ? 15 : 13,
                  peso: destacado ? FontWeight.w700 : FontWeight.w500,
                  color: c.tinta,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
