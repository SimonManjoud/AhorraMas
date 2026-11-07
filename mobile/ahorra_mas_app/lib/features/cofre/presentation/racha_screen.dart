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

/// Racha de ahorro con días colchón: la racha no se rompe por un mal día.
class RachaScreen extends ConsumerWidget {
  const RachaScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final racha = ref.watch(rachaProvider);
    final c = AhorraColores.of(context);
    return Scaffold(
      appBar: AppBar(title: const Text('Tu racha')),
      body: FondoHoja(
        child: RefreshIndicator(
          color: c.tinta,
          onRefresh: () async => ref.invalidate(rachaProvider),
          child: VistaAsync<Racha>(
            valor: racha,
            alReintentar: () => ref.invalidate(rachaProvider),
            datos: (r) => ListView(
              padding: const EdgeInsets.fromLTRB(16, 20, 16, 32),
              children: [
                _Cifra(racha: r),
                const SizedBox(height: 16),
                const Filete(),
                _Dato(titulo: 'Días con aporte', valor: '${r.diasAporte}'),
                _Dato(titulo: 'Colchones disponibles', valor: '${r.colchonesDisponibles}'),
                _Dato(titulo: 'Colchones usados', valor: '${r.diasColchonUsados}'),
                _Dato(titulo: 'Multiplicador de puntos', valor: Dinero.multiplicador(r.multiplicadorX100)),
                if (r.siguienteTramo case final s?) ...[
                  const SizedBox(height: 18),
                  Text(
                    'Siguiente tramo: ${Dinero.multiplicador(s.multiplicadorX100)} a los ${s.diasMinimos} días',
                    style: Theme.of(context).textTheme.titleSmall,
                  ),
                  const SizedBox(height: 8),
                  TiraProgreso(
                    fraccion: s.diasMinimos == 0 ? 1 : (r.diasConsecutivos / s.diasMinimos).clamp(0, 1).toDouble(),
                  ),
                  const SizedBox(height: 6),
                  Text(
                    'Te faltan ${(s.diasMinimos - r.diasConsecutivos).clamp(0, 9999)} días.',
                    style: AppTheme.cifra(tamano: 12, color: c.tintaGris),
                  ),
                ],
                const SizedBox(height: 22),
                const Rubrica('Últimos días'),
                const SizedBox(height: 8),
                if (r.ultimosDias.isEmpty)
                  const Text('Aún no hay días registrados.')
                else
                  FranjaDias(dias: r.ultimosDias),
                const SizedBox(height: 10),
                const _Leyenda(),
                const SizedBox(height: 22),
                const Filete(doble: true),
                Padding(
                  padding: const EdgeInsets.symmetric(vertical: 14),
                  child: Text(
                    '¿Qué es un día colchón? Si un día no puedes aportar, usamos un colchón y tu racha '
                    'sigue viva. Sabemos que los ingresos no siempre son regulares: aquí no se castiga '
                    'un mes malo. Ganas colchones manteniendo tu racha.',
                    style: Theme.of(context).textTheme.bodyMedium,
                  ),
                ),
                const Filete(),
                if (r.fechaInicio != null) ...[
                  const SizedBox(height: 12),
                  Text(
                    'Racha iniciada el ${Fechas.larga(r.fechaInicio)}',
                    style: Theme.of(context).textTheme.bodySmall?.copyWith(color: c.tintaGris),
                  ),
                ],
              ],
            ),
          ),
        ),
      ),
    );
  }
}

/// Los días de racha como cifra de tapa, sin llamas ni celebración.
class _Cifra extends StatelessWidget {
  const _Cifra({required this.racha});

  final Racha racha;

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context).textTheme;
    final c = AhorraColores.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          crossAxisAlignment: CrossAxisAlignment.end,
          children: [
            Text('${racha.diasConsecutivos}', style: t.displayLarge),
            const SizedBox(width: 10),
            Flexible(
              child: Padding(
                padding: const EdgeInsets.only(bottom: 8),
                child: Text(racha.diasConsecutivos == 1 ? 'día de racha' : 'días de racha', style: t.titleMedium),
              ),
            ),
          ],
        ),
        const SizedBox(height: 6),
        Text(
          racha.aporteHoy
              ? '¡Ya aportaste hoy! Vuelve mañana.'
              : 'Aporta hoy (aunque sea el redondeo) para sumar un día.',
          style: t.bodyMedium?.copyWith(color: c.tintaGris),
        ),
      ],
    );
  }
}

class _Dato extends StatelessWidget {
  const _Dato({required this.titulo, required this.valor});

  final String titulo;
  final String valor;

  @override
  Widget build(BuildContext context) {
    final c = AhorraColores.of(context);
    return DecoratedBox(
      decoration: BoxDecoration(border: Border(bottom: BorderSide(color: c.reglaSuave))),
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 11),
        child: Row(
          children: [
            Expanded(child: Text(titulo, style: Theme.of(context).textTheme.bodyMedium)),
            Text(valor, style: AppTheme.cifra(tamano: 15, peso: FontWeight.w700, color: c.tinta)),
          ],
        ),
      ),
    );
  }
}

class _Leyenda extends StatelessWidget {
  const _Leyenda();

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context).textTheme;
    Widget item(TipoMarca tipo, String texto) => Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            SizedBox(width: 22, child: MarcaDia(etiqueta: '', tipo: tipo)),
            const SizedBox(width: 6),
            Text(texto, style: t.bodySmall),
          ],
        );
    return Wrap(
      spacing: 16,
      runSpacing: 6,
      children: [
        item(TipoMarca.aporte, 'Aporte'),
        item(TipoMarca.colchon, 'Día colchón'),
        item(TipoMarca.sinAporte, 'Sin aporte'),
      ],
    );
  }
}
