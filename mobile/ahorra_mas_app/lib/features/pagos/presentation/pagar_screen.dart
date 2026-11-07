import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/money/dinero.dart';
import '../../../core/theme/app_theme.dart';
import '../../../core/util/fechas.dart';
import '../../../core/widgets/comunes.dart';
import '../../../core/widgets/libreta.dart';
import '../domain/pagos_models.dart';
import 'pagos_providers.dart';
import 'resultado_pago.dart';
import 'widgets/tarjeta_visual.dart';

/// Pestaña "Pagar": tarjetas de crédito dibujadas (número enmascarado, banco y red), su estado
/// de cuenta, el límite diario y el historial de pagos.
class PagarScreen extends ConsumerWidget {
  const PagarScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final tarjetas = ref.watch(tarjetasProvider);
    final pagos = ref.watch(pagosProvider);
    final limites = ref.watch(limitesProvider).value;
    return Scaffold(
      appBar: AppBar(title: const Text('Pagar mis TDC')),
      body: RefreshIndicator(
        onRefresh: () async {
          ref.invalidate(tarjetasProvider);
          ref.invalidate(pagosProvider);
          ref.invalidate(limitesProvider);
        },
        child: ListView(
          physics: const AlwaysScrollableScrollPhysics(),
          padding: const EdgeInsets.fromLTRB(16, 4, 16, 32),
          children: [
            if (limites != null) _Limites(limites: limites),
            const TituloSeccion('Mis tarjetas'),
            VistaAsync<List<Tarjeta>>(
              valor: tarjetas,
              alReintentar: () => ref.invalidate(tarjetasProvider),
              datos: (lista) => lista.isEmpty
                  ? const Lamina(
                      child: ListTile(
                        contentPadding: EdgeInsets.zero,
                        leading: Icon(Icons.credit_card_off_outlined),
                        title: Text('No encontramos tarjetas'),
                        subtitle: Text('Vincula tu banco con permiso de débito para ver y pagar tus TDC.'),
                      ),
                    )
                  : Column(
                      children: [
                        for (final t in lista) ...[
                          _TarjetaTdc(
                            tarjeta: t,
                            alPagar: () => context.push('/pagar/nuevo', extra: t),
                          ),
                          const SizedBox(height: 16),
                        ],
                      ],
                    ),
            ),
            const TituloSeccion('Historial de pagos'),
            VistaAsync<List<Pago>>(
              valor: pagos,
              alReintentar: () => ref.invalidate(pagosProvider),
              datos: (lista) => lista.isEmpty
                  ? const Padding(padding: EdgeInsets.all(16), child: Text('Aún no has pagado tarjetas.'))
                  : Lamina(
                      relleno: const EdgeInsets.symmetric(vertical: 6),
                      child: Column(
                        children: [
                          for (final p in lista)
                            ListTile(
                              onTap: () => context.push('/pagar/pago/${p.idPago}', extra: p),
                              title: Text(Dinero.bs(p.montoCts), style: const TextStyle(fontWeight: FontWeight.w700)),
                              subtitle: Text(Fechas.conHora(p.fechaHoraCreacion)),
                              trailing: EtiquetaPago(estado: p.estado),
                            ),
                        ],
                      ),
                    ),
            ),
          ],
        ),
      ),
    );
  }
}

class _Limites extends StatelessWidget {
  const _Limites({required this.limites});

  final Limites limites;

  @override
  Widget build(BuildContext context) {
    final c = AhorraColores.of(context);
    final t = Theme.of(context).textTheme;
    final usado = limites.montoMaxDiarioCts == 0 ? 0.0 : limites.usadoHoyCts / limites.montoMaxDiarioCts;
    return Lamina(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(child: Text('Límite diario', style: t.titleSmall?.copyWith(fontWeight: FontWeight.w700))),
              Text('${limites.operacionesHoy}/${limites.maxOperacionesDiarias} operaciones',
                  style: t.bodySmall?.copyWith(color: c.tintaGris)),
            ],
          ),
          const SizedBox(height: 10),
          TiraProgreso(fraccion: usado, alto: 8),
          const SizedBox(height: 8),
          Text(
            'Usado ${Dinero.bs(limites.usadoHoyCts)} de ${Dinero.bs(limites.montoMaxDiarioCts)}',
            style: t.bodySmall?.copyWith(color: c.tintaGris),
          ),
        ],
      ),
    );
  }
}

class _TarjetaTdc extends StatelessWidget {
  const _TarjetaTdc({required this.tarjeta, required this.alPagar});

  final Tarjeta tarjeta;
  final VoidCallback alPagar;

  @override
  Widget build(BuildContext context) {
    final c = AhorraColores.of(context);
    final t = Theme.of(context).textTheme;
    final ec = tarjeta.estadoCuenta;
    final diasLimite = ec?.fechaLimitePago?.difference(Fechas.soloFecha(DateTime.now())).inDays;
    final urgente = diasLimite != null && diasLimite <= 3;
    return Lamina(
      relleno: const EdgeInsets.all(14),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Center(
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 360),
              child: TarjetaVisual(tarjeta: tarjeta),
            ),
          ),
          const SizedBox(height: 6),
          Center(
            child: Text('Toca la tarjeta para girarla', style: t.bodySmall?.copyWith(color: c.tintaGris)),
          ),
          if (tarjeta.alias != null) ...[
            const SizedBox(height: 10),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 4),
              child: Text(tarjeta.alias!, style: t.titleSmall?.copyWith(fontWeight: FontWeight.w700)),
            ),
          ],
          if (ec != null)
            Padding(
              padding: const EdgeInsets.fromLTRB(4, 10, 4, 0),
              child: Column(
                children: [
                  _Dato('Saldo total', Dinero.bs(ec.saldoTotalCts)),
                  _Dato('Pago mínimo', Dinero.bs(ec.pagoMinimoCts)),
                  _Dato('Fecha de corte', Fechas.corta(ec.fechaCorte)),
                  _Dato(
                    'Fecha límite',
                    '${Fechas.corta(ec.fechaLimitePago)}${diasLimite == null ? '' : ' · ${diasLimite < 0 ? 'vencida' : '$diasLimite días'}'}',
                    color: urgente ? c.peligro : null,
                  ),
                ],
              ),
            ),
          const SizedBox(height: 12),
          FilledButton.icon(
            key: ValueKey('pagar-${tarjeta.idTarjeta}'),
            onPressed: alPagar,
            icon: const Icon(Icons.payments_outlined),
            label: const Text('Pagar esta tarjeta'),
          ),
        ],
      ),
    );
  }
}

class _Dato extends StatelessWidget {
  const _Dato(this.etiqueta, this.valor, {this.color});

  final String etiqueta;
  final String valor;
  final Color? color;

  @override
  Widget build(BuildContext context) {
    final c = AhorraColores.of(context);
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        children: [
          Expanded(child: Text(etiqueta, style: TextStyle(color: c.tintaGris))),
          Flexible(
            child: Text(valor,
                textAlign: TextAlign.end,
                style: AppTheme.cifra(tamano: 14, peso: FontWeight.w700, color: color ?? c.tinta)),
          ),
        ],
      ),
    );
  }
}
