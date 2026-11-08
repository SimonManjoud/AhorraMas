import 'package:flutter/material.dart';

import '../../../../core/money/dinero.dart';
import '../../../../core/theme/app_theme.dart';
import '../../../../core/util/fechas.dart';
import '../../../../core/widgets/libreta.dart';
import '../../domain/cofre_models.dart';
import '../../../../core/widgets/texto_enfasis.dart';

IconData iconoCategoria(CategoriaMeta c) => switch (c) {
      CategoriaMeta.viaje => Icons.flight_takeoff,
      CategoriaMeta.emergencia => Icons.health_and_safety_outlined,
      CategoriaMeta.estudio => Icons.school_outlined,
      CategoriaMeta.salud => Icons.favorite_outline,
      CategoriaMeta.hogar => Icons.home_outlined,
      CategoriaMeta.otro => Icons.star_outline,
    };

/// Etiqueta de estado impresa (no es un sello: el sello violeta es uno por vista).
class EtiquetaLibro extends StatelessWidget {
  const EtiquetaLibro(this.texto, {super.key, this.color});

  final String texto;
  final Color? color;

  @override
  Widget build(BuildContext context) {
    final col = color ?? AhorraColores.of(context).tinta;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(border: Border.all(color: col)),
      child: Text(texto, style: AppTheme.rubrica(color: col, tamano: 11)),
    );
  }
}

/// "Ruta del día": la meta convertida en la micro-acción de hoy, como bloque del libro
/// entre regla doble y filete.
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
    final c = AhorraColores.of(context);
    final t = Theme.of(context).textTheme;
    final cumplida = ruta.cumplida;
    final (etiqueta, color) = switch (ruta.estatus) {
      'cumplida' => ('Cumplida', c.exito),
      'parcial' => ('Parcial', c.tinta),
      'colchon' => ('Día colchón', c.colchon),
      'omitida' => ('Omitida', c.tintaGris),
      _ => ('Pendiente', c.tinta),
    };
    final detalle = t.bodySmall?.copyWith(color: c.tintaGris);
    return Column(
      key: const ValueKey('ruta-del-dia'),
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        const Filete(doble: true),
        Padding(
          padding: const EdgeInsets.symmetric(vertical: 14),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Align(alignment: Alignment.centerRight, child: EtiquetaLibro(etiqueta, color: color)),
              const SizedBox(height: 10),
              TextoEnfasis(ruta.mensaje, style: t.headlineSmall?.copyWith(color: c.tinta)),
              const SizedBox(height: 8),
              Text(
                'Cuota: ${Dinero.usd(ruta.montoSugeridoUsdCts)} - ${Dinero.bs(ruta.montoSugeridoBsCts)} '
                'a tasa BCV ${Dinero.tasa(ruta.tasaValorBsX10000)}',
                style: detalle,
              ),
              Text(
                '${ruta.periodosRestantes} ${ruta.periodosRestantes == 1 ? 'periodo restante' : 'periodos restantes'}'
                '${ruta.aportadoHoyUsdCts > 0 ? ' - hoy llevas ${Dinero.usd(ruta.aportadoHoyUsdCts)}' : ''}',
                style: detalle,
              ),
              if (alApartar != null && !cumplida) ...[
                const SizedBox(height: 14),
                SizedBox(
                  width: double.infinity,
                  child: FilledButton(
                    key: const ValueKey('btn-apartar-cuota'),
                    onPressed: ocupado ? null : alApartar,
                    child: ocupado
                        ? SizedBox(
                            width: 20,
                            height: 20,
                            child: CircularProgressIndicator(strokeWidth: 2, color: c.tintaGris),
                          )
                        : FittedBox(
                            fit: BoxFit.scaleDown,
                            child: Row(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                Text('Apartar ${Dinero.usd(ruta.faltanteHoyUsdCts)}'),
                                const SizedBox(width: 10),
                                Text(
                                  Dinero.bs(Dinero.usdABs(ruta.faltanteHoyUsdCts, ruta.tasaValorBsX10000)),
                                  style: AppTheme.cifra(tamano: 13, color: c.sobreTapa.withValues(alpha: 0.8)),
                                ),
                              ],
                            ),
                          ),
                  ),
                ),
              ],
            ],
          ),
        ),
        const Filete(),
      ],
    );
  }
}

/// Meta como bloque del libro: nombre, tira reglada de progreso, cifras y la línea de hoy.
///
/// [conSello] pinta el Sello de compromiso violeta; solo una meta por vista debe llevarlo.
class MetaCard extends StatelessWidget {
  const MetaCard({super.key, required this.meta, this.onTap, this.conSello = true, this.mostrarCuota = true});

  final Meta meta;
  final VoidCallback? onTap;
  final bool conSello;

  /// Oculta la línea "Hoy toca" cuando la cuota ya se muestra a escala de titular.
  final bool mostrarCuota;

  @override
  Widget build(BuildContext context) {
    final c = AhorraColores.of(context);
    final t = Theme.of(context).textTheme;
    final cuota = meta.cuotaHoy;
    final sello = meta.selloActivo;
    return Material(
      type: MaterialType.transparency,
      child: InkWell(
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 14),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Row(
                crossAxisAlignment: CrossAxisAlignment.center,
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(meta.nombre, style: t.titleMedium),
                        const SizedBox(height: 2),
                        Rubrica('${meta.categoria.etiqueta} - ${meta.frecuenciaCuota.etiqueta}'),
                      ],
                    ),
                  ),
                  if (sello != null && conSello)
                    Padding(
                      padding: const EdgeInsets.only(left: 8),
                      child: SelloRectangular(
                        titulo: 'Sellado',
                        detalle: '${Dinero.usd(sello.montoSelladoUsdCts)} - ${Fechas.libreta(sello.fechaLiberacion)}',
                      ),
                    )
                  else if (sello != null)
                    Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Icon(Icons.lock_outline, size: 14, color: c.tintaGris),
                        const SizedBox(width: 4),
                        Text('${Dinero.usd(sello.montoSelladoUsdCts)} sellado',
                            style: AppTheme.cifra(tamano: 11.5, color: c.tintaGris)),
                      ],
                    ),
                  if (!meta.activa)
                    Padding(
                      padding: const EdgeInsets.only(left: 8),
                      child: EtiquetaLibro(meta.estatus, color: c.tintaGris),
                    ),
                ],
              ),
              const SizedBox(height: 12),
              TiraProgreso(fraccion: Dinero.fraccionPb(meta.progresoPb)),
              const SizedBox(height: 6),
              Row(
                children: [
                  Expanded(
                    child: FittedBox(
                      fit: BoxFit.scaleDown,
                      alignment: Alignment.centerLeft,
                      child: Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Text(Dinero.usd(meta.acumuladoUsdCts),
                              style: AppTheme.cifra(tamano: 12.5, peso: FontWeight.w700, color: c.tinta)),
                          Text(' de ', style: t.bodySmall?.copyWith(color: c.tintaGris)),
                          Text(Dinero.usd(meta.montoObjetivoUsdCts),
                              style: AppTheme.cifra(tamano: 12.5, color: c.tinta)),
                        ],
                      ),
                    ),
                  ),
                  const SizedBox(width: 8),
                  Text(Fechas.libretaAnio(meta.fechaObjetivo), style: AppTheme.cifra(tamano: 12, color: c.tintaGris)),
                ],
              ),
              if (cuota != null && meta.activa && mostrarCuota) ...[
                const SizedBox(height: 10),
                if (cuota.cumplida)
                  Row(
                    children: [
                      Icon(Icons.check, size: 16, color: c.exito),
                      const SizedBox(width: 6),
                      Expanded(child: Text('Cuota de hoy cumplida', style: t.bodySmall)),
                    ],
                  )
                else
                  Row(
                    children: [
                      Text(cuota.estatus == 'colchon' ? 'Hoy (día colchón)' : 'Hoy toca', style: t.bodySmall),
                      const SizedBox(width: 8),
                      const Expanded(child: GuiaPunteada()),
                      const SizedBox(width: 8),
                      Text(Dinero.usd(cuota.faltanteHoyUsdCts),
                          style: AppTheme.cifra(tamano: 13, peso: FontWeight.w700, color: c.tinta)),
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

/// Resumen compacto de la racha: días, multiplicador y colchones, en tinta.
class RachaResumen extends StatelessWidget {
  const RachaResumen({super.key, required this.racha, this.onTap});

  final Racha racha;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final c = AhorraColores.of(context);
    final t = Theme.of(context).textTheme;
    final activa = racha.diasConsecutivos > 0;
    return Material(
      type: MaterialType.transparency,
      child: InkWell(
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 10),
          child: Row(
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      activa ? '${racha.diasConsecutivos} días de racha' : 'Empieza tu racha hoy',
                      style: t.titleMedium,
                    ),
                    const SizedBox(height: 2),
                    Text(
                      '${Dinero.multiplicador(racha.multiplicadorX100)} - '
                      '${racha.colchonesDisponibles} '
                      '${racha.colchonesDisponibles == 1 ? 'colchón libre' : 'colchones libres'}'
                      '${racha.aporteHoy ? ' - ya aportaste hoy' : ''}',
                      style: AppTheme.cifra(tamano: 11.5, color: c.tintaGris),
                    ),
                  ],
                ),
              ),
              Icon(Icons.chevron_right, color: c.tintaGris),
            ],
          ),
        ),
      ),
    );
  }
}

/// Marcas de los últimos días de racha: aporte lleno, día colchón punteado, sin aporte vacío.
class FranjaDias extends StatelessWidget {
  const FranjaDias({super.key, required this.dias, this.columnas = 8});

  final List<DiaRacha> dias;
  final int columnas;

  @override
  Widget build(BuildContext context) {
    final ordenados = [...dias]..sort((a, b) => a.fecha.compareTo(b.fecha));
    return LayoutBuilder(
      builder: (context, restricciones) {
        const hueco = 5.0;
        final ancho = (restricciones.maxWidth - hueco * (columnas - 1)) / columnas;
        return Wrap(
          spacing: hueco,
          runSpacing: hueco,
          children: [
            for (final d in ordenados)
              SizedBox(
                width: ancho,
                child: MarcaDia(
                  etiqueta: d.fecha.day.toString().padLeft(2, '0'),
                  tipo: switch (d.tipo) {
                    TipoDiaRacha.aporte => TipoMarca.aporte,
                    TipoDiaRacha.colchon => TipoMarca.colchon,
                    TipoDiaRacha.sinAporte => TipoMarca.sinAporte,
                  },
                  descripcion: '${Fechas.media(d.fecha)} - ${switch (d.tipo) {
                    TipoDiaRacha.aporte => 'aporte',
                    TipoDiaRacha.colchon => 'día colchón',
                    TipoDiaRacha.sinAporte => 'sin aporte',
                  }}',
                ),
              ),
          ],
        );
      },
    );
  }
}
