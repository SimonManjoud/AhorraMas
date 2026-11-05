import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/money/dinero.dart';
import '../../../core/theme/app_theme.dart';
import '../../../core/util/fechas.dart';
import '../../../core/widgets/comunes.dart';
import '../domain/cofre_models.dart';
import 'cofre_providers.dart';
import 'widgets/cofre_widgets.dart';

/// Racha de ahorro con días colchón: la racha no se rompe por un mal día.
class RachaScreen extends ConsumerWidget {
  const RachaScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final racha = ref.watch(rachaProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('Tu racha')),
      body: RefreshIndicator(
        onRefresh: () async => ref.invalidate(rachaProvider),
        child: VistaAsync<Racha>(
          valor: racha,
          alReintentar: () => ref.invalidate(rachaProvider),
          datos: (r) => ListView(
            padding: const EdgeInsets.all(16),
            children: [
              _Llama(racha: r),
              const SizedBox(height: 16),
              Row(
                children: [
                  Expanded(child: _Dato(titulo: 'Días con aporte', valor: '${r.diasAporte}', icono: Icons.savings_outlined)),
                  const SizedBox(width: 12),
                  Expanded(
                    child: _Dato(
                      titulo: 'Colchones disponibles',
                      valor: '${r.colchonesDisponibles}',
                      icono: Icons.shield_outlined,
                      color: AhorraColores.of(context).colchon,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 12),
              Row(
                children: [
                  Expanded(child: _Dato(titulo: 'Colchones usados', valor: '${r.diasColchonUsados}', icono: Icons.shield)),
                  const SizedBox(width: 12),
                  Expanded(
                    child: _Dato(
                      titulo: 'Multiplicador',
                      valor: Dinero.multiplicador(r.multiplicadorX100),
                      icono: Icons.bolt,
                      color: AhorraColores.of(context).cofre,
                    ),
                  ),
                ],
              ),
              if (r.siguienteTramo case final s?) ...[
                const SizedBox(height: 16),
                Card(
                  child: Padding(
                    padding: const EdgeInsets.all(16),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          'Siguiente tramo: ${Dinero.multiplicador(s.multiplicadorX100)} a los ${s.diasMinimos} días',
                          style: const TextStyle(fontWeight: FontWeight.w700),
                        ),
                        const SizedBox(height: 8),
                        ClipRRect(
                          borderRadius: BorderRadius.circular(8),
                          child: LinearProgressIndicator(
                            minHeight: 10,
                            value: s.diasMinimos == 0 ? 1 : (r.diasConsecutivos / s.diasMinimos).clamp(0, 1).toDouble(),
                            color: Colors.deepOrange,
                          ),
                        ),
                        const SizedBox(height: 6),
                        Text('Te faltan ${(s.diasMinimos - r.diasConsecutivos).clamp(0, 9999)} días.'),
                      ],
                    ),
                  ),
                ),
              ],
              const TituloSeccion('Últimos días'),
              if (r.ultimosDias.isEmpty)
                const Text('Aún no hay días registrados.')
              else
                FranjaDias(dias: r.ultimosDias),
              const SizedBox(height: 8),
              const _Leyenda(),
              const SizedBox(height: 20),
              Card(
                color: AhorraColores.of(context).colchon.withValues(alpha: 0.12),
                child: const Padding(
                  padding: EdgeInsets.all(16),
                  child: Text(
                    '¿Qué es un día colchón? Si un día no puedes aportar, usamos un colchón y tu racha '
                    'sigue viva. Sabemos que los ingresos no siempre son regulares: aquí no se castiga '
                    'un mes malo. Ganas colchones manteniendo tu racha.',
                  ),
                ),
              ),
              if (r.fechaInicio != null) ...[
                const SizedBox(height: 12),
                Text('Racha iniciada el ${Fechas.larga(r.fechaInicio)}', textAlign: TextAlign.center),
              ],
            ],
          ),
        ),
      ),
    );
  }
}

class _Llama extends StatelessWidget {
  const _Llama({required this.racha});

  final Racha racha;

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context).textTheme;
    final activa = racha.diasConsecutivos > 0;
    return Card(
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 28, horizontal: 16),
        child: Column(
          children: [
            Icon(Icons.local_fire_department, size: 96, color: activa ? Colors.deepOrange : Colors.grey),
            Text('${racha.diasConsecutivos}', style: t.displayMedium?.copyWith(fontWeight: FontWeight.w800)),
            Text(racha.diasConsecutivos == 1 ? 'día de racha' : 'días de racha', style: t.titleMedium),
            const SizedBox(height: 8),
            Text(
              racha.aporteHoy
                  ? '¡Ya aportaste hoy! Vuelve mañana.'
                  : 'Aporta hoy (aunque sea el redondeo) para sumar un día.',
              textAlign: TextAlign.center,
            ),
          ],
        ),
      ),
    );
  }
}

class _Dato extends StatelessWidget {
  const _Dato({required this.titulo, required this.valor, required this.icono, this.color});

  final String titulo;
  final String valor;
  final IconData icono;
  final Color? color;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Row(
          children: [
            Icon(icono, color: color ?? Theme.of(context).colorScheme.primary),
            const SizedBox(width: 10),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(valor, style: Theme.of(context).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w800)),
                  Text(titulo, style: Theme.of(context).textTheme.bodySmall),
                ],
              ),
            ),
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
    final colores = AhorraColores.of(context);
    Widget item(Color c, String texto) => Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(width: 10, height: 10, decoration: BoxDecoration(color: c, shape: BoxShape.circle)),
            const SizedBox(width: 4),
            Text(texto, style: Theme.of(context).textTheme.bodySmall),
          ],
        );
    return Wrap(
      spacing: 16,
      children: [
        item(Colors.deepOrange, 'Aporte'),
        item(colores.colchon, 'Día colchón'),
        item(Theme.of(context).colorScheme.outline, 'Sin aporte'),
      ],
    );
  }
}
