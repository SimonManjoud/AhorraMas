import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/money/dinero.dart';
import '../../../core/theme/app_theme.dart';
import '../../../core/util/fechas.dart';
import '../../../core/widgets/comunes.dart';
import '../domain/pagos_models.dart';
import 'pagos_providers.dart';
import 'resultado_pago.dart';

/// Pestaña "Pagar": tarjetas de crédito con estado de cuenta, límites e historial de pagos.
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
                  ? const Card(
                      child: ListTile(
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
                          const SizedBox(height: 12),
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
                  : Card(
                      child: Column(
                        children: [
                          for (final p in lista)
                            ListTile(
                              onTap: () => context.push('/pagar/pago/${p.idPago}', extra: p),
                              title: Text(Dinero.bs(p.montoCts), style: const TextStyle(fontWeight: FontWeight.w600)),
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
    final usado = limites.montoMaxDiarioCts == 0 ? 0.0 : limites.usadoHoyCts / limites.montoMaxDiarioCts;
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Límite diario', style: Theme.of(context).textTheme.labelLarge),
            const SizedBox(height: 6),
            LinearProgressIndicator(value: usado.clamp(0, 1), minHeight: 6, borderRadius: BorderRadius.circular(6)),
            const SizedBox(height: 6),
            Text(
              'Usado ${Dinero.bs(limites.usadoHoyCts)} de ${Dinero.bs(limites.montoMaxDiarioCts)} · '
              '${limites.operacionesHoy}/${limites.maxOperacionesDiarias} operaciones',
              style: Theme.of(context).textTheme.bodySmall,
            ),
          ],
        ),
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
    final cs = Theme.of(context).colorScheme;
    final t = Theme.of(context).textTheme;
    final ec = tarjeta.estadoCuenta;
    final diasLimite = ec?.fechaLimitePago?.difference(Fechas.soloFecha(DateTime.now())).inDays;
    final urgente = diasLimite != null && diasLimite <= 3;
    return Card(
      clipBehavior: Clip.antiAlias,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              gradient: LinearGradient(colors: [cs.secondary, cs.primary]),
            ),
            child: Row(
              children: [
                Icon(Icons.credit_card, color: cs.onPrimary),
                const SizedBox(width: 10),
                Expanded(
                  child: Text(tarjeta.nombreVisible,
                      style: t.titleMedium?.copyWith(color: cs.onPrimary, fontWeight: FontWeight.w700)),
                ),
                Text('•••• ${tarjeta.ultimos4}', style: t.titleMedium?.copyWith(color: cs.onPrimary)),
              ],
            ),
          ),
          if (ec != null)
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 12, 16, 0),
              child: Column(
                children: [
                  _Dato('Saldo total', Dinero.bs(ec.saldoTotalCts)),
                  _Dato('Pago mínimo', Dinero.bs(ec.pagoMinimoCts)),
                  _Dato('Fecha de corte', Fechas.corta(ec.fechaCorte)),
                  _Dato(
                    'Fecha límite',
                    '${Fechas.corta(ec.fechaLimitePago)}${diasLimite == null ? '' : ' (${diasLimite < 0 ? 'vencida' : 'en $diasLimite días'})'}',
                    color: urgente ? AhorraColores.of(context).peligro : null,
                  ),
                ],
              ),
            ),
          Padding(
            padding: const EdgeInsets.all(12),
            child: FilledButton.icon(
              key: ValueKey('pagar-${tarjeta.idTarjeta}'),
              onPressed: alPagar,
              icon: const Icon(Icons.payments_outlined),
              label: const Text('Pagar esta tarjeta'),
            ),
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
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 2),
      child: Row(
        children: [
          Expanded(child: Text(etiqueta)),
          Flexible(
            child: Text(valor, textAlign: TextAlign.end, style: TextStyle(fontWeight: FontWeight.w600, color: color)),
          ),
        ],
      ),
    );
  }
}
