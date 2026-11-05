import 'package:flutter/material.dart';

import '../../../../core/money/dinero.dart';
import '../../../../core/theme/app_theme.dart';
import '../../../../core/util/fechas.dart';
import '../../domain/cofre_models.dart';

IconData iconoCategoria(CategoriaMeta c) => switch (c) {
      CategoriaMeta.viaje => Icons.flight_takeoff,
      CategoriaMeta.emergencia => Icons.health_and_safety_outlined,
      CategoriaMeta.estudio => Icons.school_outlined,
      CategoriaMeta.salud => Icons.favorite_outline,
      CategoriaMeta.hogar => Icons.home_outlined,
      CategoriaMeta.otro => Icons.star_outline,
    };

/// Tarjeta "Ruta del día": la meta convertida en la micro-acción de hoy.
class RutaDelDiaCard extends StatelessWidget {
  const RutaDelDiaCard({
    super.key,
    required this.ruta,
    this.alApartar,
    this.ocupado = false,
  });

  final RutaDelDia ruta;

  /// Aporte de un toque con `origen = cuota_del_dia`.
  final VoidCallback? alApartar;
  final bool ocupado;

  @override
  Widget build(BuildContext context) {
    final colores = AhorraColores.of(context);
    final t = Theme.of(context).textTheme;
    final cumplida = ruta.cumplida;
    final (etiqueta, color) = switch (ruta.estatus) {
      'cumplida' => ('Cumplida', colores.exito),
      'parcial' => ('Parcial', colores.enProceso),
      'colchon' => ('Día colchón', colores.colchon),
      'omitida' => ('Omitida', colores.peligro),
      _ => ('Pendiente', colores.alCofre),
    };
    return Card(
      key: const ValueKey('ruta-del-dia'),
      color: colores.cofreContenedor,
      child: Padding(
        padding: const EdgeInsets.all(18),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(Icons.route, color: colores.alCofre),
                const SizedBox(width: 8),
                Expanded(
                  child: Text('Ruta del día',
                      overflow: TextOverflow.ellipsis,
                      style: t.titleSmall?.copyWith(color: colores.alCofre, fontWeight: FontWeight.w700)),
                ),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 3),
                  decoration: BoxDecoration(
                    color: color.withValues(alpha: 0.15),
                    borderRadius: BorderRadius.circular(20),
                  ),
                  child: Text(etiqueta, style: TextStyle(color: color, fontWeight: FontWeight.w700, fontSize: 12)),
                ),
              ],
            ),
            const SizedBox(height: 12),
            Text(ruta.mensaje, style: t.titleLarge?.copyWith(color: colores.alCofre, fontWeight: FontWeight.w700)),
            const SizedBox(height: 8),
            Text(
              'Cuota: ${Dinero.usd(ruta.montoSugeridoUsdCts)} · ${Dinero.bs(ruta.montoSugeridoBsCts)} '
              'a tasa BCV ${Dinero.tasa(ruta.tasaValorBsX10000)}',
              style: t.bodySmall?.copyWith(color: colores.alCofre),
            ),
            Text(
              '${ruta.periodosRestantes} ${ruta.periodosRestantes == 1 ? 'periodo restante' : 'periodos restantes'}'
              '${ruta.aportadoHoyUsdCts > 0 ? ' · hoy llevas ${Dinero.usd(ruta.aportadoHoyUsdCts)}' : ''}',
              style: t.bodySmall?.copyWith(color: colores.alCofre),
            ),
            if (alApartar != null && !cumplida) ...[
              const SizedBox(height: 14),
              SizedBox(
                width: double.infinity,
                child: FilledButton.icon(
                  key: const ValueKey('btn-apartar-cuota'),
                  style: FilledButton.styleFrom(backgroundColor: colores.cofre, foregroundColor: Colors.black87),
                  onPressed: ocupado ? null : alApartar,
                  icon: ocupado
                      ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2))
                      : const Icon(Icons.savings),
                  label: Text('Apartar ${Dinero.usd(ruta.faltanteHoyUsdCts)}'),
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }
}

/// Tarjeta de meta con progreso.
class MetaCard extends StatelessWidget {
  const MetaCard({super.key, required this.meta, this.onTap});

  final Meta meta;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final colores = AhorraColores.of(context);
    final cs = Theme.of(context).colorScheme;
    final t = Theme.of(context).textTheme;
    final cuota = meta.cuotaHoy;
    return Card(
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  CircleAvatar(
                    backgroundColor: colores.cofreContenedor,
                    child: Icon(iconoCategoria(meta.categoria), color: colores.alCofre),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(meta.nombre, style: t.titleMedium?.copyWith(fontWeight: FontWeight.w700)),
                        Text(
                          '${meta.categoria.etiqueta} · ${meta.frecuenciaCuota.etiqueta.toLowerCase()} · '
                          'hasta ${Fechas.media(meta.fechaObjetivo)}',
                          style: t.bodySmall,
                        ),
                      ],
                    ),
                  ),
                  if (meta.selloActivo != null) Icon(Icons.lock, color: colores.cofre, size: 20),
                  if (!meta.activa)
                    Padding(
                      padding: const EdgeInsets.only(left: 6),
                      child: Text(meta.estatus, style: t.labelSmall?.copyWith(color: cs.outline)),
                    ),
                ],
              ),
              const SizedBox(height: 14),
              ClipRRect(
                borderRadius: BorderRadius.circular(8),
                child: LinearProgressIndicator(
                  value: Dinero.fraccionPb(meta.progresoPb),
                  minHeight: 10,
                  color: colores.cofre,
                  backgroundColor: colores.cofreContenedor,
                ),
              ),
              const SizedBox(height: 8),
              Row(
                children: [
                  Expanded(
                    child: Text('${Dinero.usd(meta.acumuladoUsdCts)} de ${Dinero.usd(meta.montoObjetivoUsdCts)}',
                        overflow: TextOverflow.ellipsis,
                        style: t.bodyMedium?.copyWith(fontWeight: FontWeight.w600)),
                  ),
                  Text(Dinero.porcentajePb(meta.progresoPb), style: t.bodySmall),
                ],
              ),
              if (cuota != null && meta.activa) ...[
                const SizedBox(height: 8),
                Row(
                  children: [
                    Icon(cuota.cumplida ? Icons.check_circle : Icons.route_outlined,
                        size: 16, color: cuota.cumplida ? colores.exito : colores.cofre),
                    const SizedBox(width: 6),
                    Expanded(child: Text(cuota.mensaje, style: t.bodySmall)),
                  ],
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }
}

/// Resumen compacto de la racha (llama + días + multiplicador + colchones).
class RachaResumen extends StatelessWidget {
  const RachaResumen({super.key, required this.racha, this.onTap});

  final Racha racha;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final colores = AhorraColores.of(context);
    final t = Theme.of(context).textTheme;
    final activa = racha.diasConsecutivos > 0;
    return Card(
      child: InkWell(
        borderRadius: BorderRadius.circular(20),
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Row(
            children: [
              Icon(Icons.local_fire_department, size: 40, color: activa ? Colors.deepOrange : Colors.grey),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      activa ? '${racha.diasConsecutivos} días de racha' : 'Empieza tu racha hoy',
                      style: t.titleMedium?.copyWith(fontWeight: FontWeight.w700),
                    ),
                    Text(
                      racha.aporteHoy ? 'Ya aportaste hoy 🙌' : 'Cualquier aporte, aunque sea pequeño, la mantiene',
                      style: t.bodySmall,
                    ),
                  ],
                ),
              ),
              Column(
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  Text(Dinero.multiplicador(racha.multiplicadorX100),
                      style: t.titleLarge?.copyWith(color: colores.cofre, fontWeight: FontWeight.w800)),
                  Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Icon(Icons.shield_outlined, size: 14, color: colores.colchon),
                      const SizedBox(width: 2),
                      Text('${racha.colchonesDisponibles}', style: t.bodySmall),
                    ],
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// Franja de los últimos días de racha (aporte / colchón / sin aporte).
class FranjaDias extends StatelessWidget {
  const FranjaDias({super.key, required this.dias});

  final List<DiaRacha> dias;

  @override
  Widget build(BuildContext context) {
    final colores = AhorraColores.of(context);
    final cs = Theme.of(context).colorScheme;
    final ordenados = [...dias]..sort((a, b) => a.fecha.compareTo(b.fecha));
    return Wrap(
      spacing: 6,
      runSpacing: 8,
      children: [
        for (final d in ordenados)
          Tooltip(
            message: '${Fechas.corta(d.fecha)} · ${switch (d.tipo) {
              TipoDiaRacha.aporte => 'aporte',
              TipoDiaRacha.colchon => 'día colchón',
              TipoDiaRacha.sinAporte => 'sin aporte',
            }}',
            child: Column(
              children: [
                Container(
                  width: 30,
                  height: 30,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    color: switch (d.tipo) {
                      TipoDiaRacha.aporte => Colors.deepOrange,
                      TipoDiaRacha.colchon => colores.colchon,
                      TipoDiaRacha.sinAporte => cs.surfaceContainerHighest,
                    },
                  ),
                  child: Icon(
                    switch (d.tipo) {
                      TipoDiaRacha.aporte => Icons.local_fire_department,
                      TipoDiaRacha.colchon => Icons.shield,
                      TipoDiaRacha.sinAporte => Icons.remove,
                    },
                    size: 16,
                    color: d.tipo == TipoDiaRacha.sinAporte ? cs.outline : Colors.white,
                  ),
                ),
                const SizedBox(height: 2),
                Text(Fechas.inicialDia(d.fecha), style: Theme.of(context).textTheme.labelSmall),
              ],
            ),
          ),
      ],
    );
  }
}
