import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/di/providers.dart';
import '../../../core/money/dinero.dart';
import '../../../core/theme/app_theme.dart';
import '../../../core/util/fechas.dart';
import '../../../core/widgets/comunes.dart';
import '../../../core/widgets/dinero_widgets.dart';
import '../../../core/widgets/libreta.dart';
import '../../../core/widgets/step_up.dart';
import '../../consolidacion/domain/consolidacion_models.dart';
import '../../consolidacion/presentation/consolidacion_providers.dart';
import '../../puntos/presentation/puntos_providers.dart';
import '../domain/cofre_models.dart';
import 'cofre_providers.dart';
import 'widgets/cofre_widgets.dart';

/// Detalle de meta: Ruta del día, aportes, retiros, Sello de compromiso y reglas automáticas.
class MetaDetalleScreen extends ConsumerStatefulWidget {
  const MetaDetalleScreen({super.key, required this.idMeta});

  final int idMeta;

  @override
  ConsumerState<MetaDetalleScreen> createState() => _MetaDetalleScreenState();
}

class _MetaDetalleScreenState extends ConsumerState<MetaDetalleScreen> {
  bool _apartando = false;
  int? _idCuentaOrigen;

  int get _id => widget.idMeta;

  void _refrescar() {
    refrescarCofre(ref, _id);
    refrescarPuntos(ref);
  }

  Future<void> _aportar(int montoUsdCts, String origen) async {
    setState(() => _apartando = true);
    try {
      final r = await ref.read(cofreRepositoryProvider).aportar(
            _id,
            montoUsdCts: montoUsdCts,
            origen: origen,
            idCuentaOrigen: _idCuentaOrigen,
          );
      _refrescar();
      if (!mounted) return;
      final racha = r.racha;
      mostrarMensaje(
        context,
        '¡Apartaste ${Dinero.usd(r.aporte.montoUsdCts)}! +${r.puntosGanados} puntos'
        '${racha == null ? '' : ' · racha de ${racha.diasConsecutivos} días (${Dinero.multiplicador(racha.multiplicadorX100)})'}',
      );
    } catch (e) {
      if (mounted) mostrarError(context, e);
    } finally {
      if (mounted) setState(() => _apartando = false);
    }
  }

  Future<void> _aporteManual(Meta meta) async {
    final monto = await pedirMonto(context, titulo: 'Aporte manual', etiqueta: 'Monto en USD');
    if (monto != null) await _aportar(monto, 'manual');
  }

  Future<void> _retirar(Meta meta) async {
    final sello = meta.selloActivo;
    final monto = await pedirMonto(
      context,
      titulo: 'Retirar del cofre',
      etiqueta: 'Monto en USD',
      maximoCts: meta.acumuladoUsdCts,
      aviso: sello == null
          ? 'El dinero sigue en tu banco: el retiro solo libera el apartado lógico de esta meta.'
          : 'Tienes ${Dinero.usd(sello.montoSelladoUsdCts)} sellados hasta ${Fechas.media(sello.fechaLiberacion)}. '
              'Si el retiro deja tu cofre por debajo de ese monto, el sello se rompe y pierdes el '
              'multiplicador de racha. Nunca pierdes tu dinero.',
    );
    if (monto == null || !mounted) return;
    final stepUp = await solicitarStepUp(context, motivo: 'Retirar ${Dinero.usd(monto)} de «${meta.nombre}».');
    if (stepUp == null) return;
    try {
      final r = await ref.read(cofreRepositoryProvider).retirar(_id, montoUsdCts: monto, stepUp: stepUp.token);
      _refrescar();
      if (!mounted) return;
      mostrarMensaje(
        context,
        r.selloRoto
            ? 'Retiraste ${Dinero.usd(monto)}. Tu sello se rompió: perdiste el multiplicador, no tu dinero.'
            : 'Retiraste ${Dinero.usd(monto)} de tu cofre.',
      );
    } catch (e) {
      if (mounted) mostrarError(context, e);
    }
  }

  Future<void> _sellar(Meta meta) async {
    final monto = await pedirMonto(
      context,
      titulo: 'Sellar parte de tu cofre',
      etiqueta: 'Monto a sellar (USD)',
      maximoCts: meta.acumuladoUsdCts,
      mensajeMaximo: 'Solo puedes sellar hasta lo acumulado (${Dinero.usd(meta.acumuladoUsdCts)})',
      aviso: 'El sello es un compromiso contigo: el dinero sigue en tu banco y siempre puedes '
          'retirarlo. Si rompes el sello antes del ${Fechas.media(meta.fechaObjetivo)} pierdes el '
          'multiplicador de racha, nunca tu dinero.',
    );
    if (monto == null || !mounted) return;
    final stepUp = await solicitarStepUp(context, motivo: 'Sellar ${Dinero.usd(monto)} hasta la fecha de tu meta.');
    if (stepUp == null) return;
    try {
      await ref.read(cofreRepositoryProvider).sellar(_id, montoUsdCts: monto, stepUp: stepUp.token);
      _refrescar();
      if (mounted) mostrarMensaje(context, '¡Sellado! Tu yo del futuro te lo agradecerá.');
    } catch (e) {
      if (mounted) mostrarError(context, e);
    }
  }

  Future<void> _romperSello(Meta meta) async {
    final ok = await confirmar(
      context,
      titulo: '¿Romper el sello?',
      mensaje: 'Si rompes el sello pierdes el multiplicador de tu racha, nunca tu dinero. '
          'Tu dinero sigue en tu banco y en tu cofre.',
      aceptar: 'Romper sello',
      peligroso: true,
    );
    if (!ok || !mounted) return;
    final stepUp = await solicitarStepUp(context, motivo: 'Romper el sello de «${meta.nombre}».');
    if (stepUp == null) return;
    try {
      await ref.read(cofreRepositoryProvider).romperSello(_id, stepUp: stepUp.token);
      _refrescar();
      if (mounted) mostrarMensaje(context, 'Sello roto. Tu racha se reinicia; tu dinero sigue intacto.');
    } catch (e) {
      if (mounted) mostrarError(context, e);
    }
  }

  Future<void> _cambiarEstatus(String estatus) async {
    if (estatus == 'cancelada') {
      final ok = await confirmar(
        context,
        titulo: 'Cancelar meta',
        mensaje: 'La meta dejará de mostrarse y no tendrás Ruta del día para ella. Tu dinero sigue en tu banco.',
        aceptar: 'Cancelar meta',
        peligroso: true,
      );
      if (!ok) return;
    }
    try {
      await ref.read(cofreRepositoryProvider).cambiarEstatusMeta(_id, estatus);
      _refrescar();
      if (mounted && estatus == 'cancelada') Navigator.of(context).maybePop();
    } catch (e) {
      if (mounted) mostrarError(context, e);
    }
  }

  @override
  Widget build(BuildContext context) {
    final meta = ref.watch(metaProvider(_id));
    return Scaffold(
      appBar: AppBar(
        title: Text(meta.value?.nombre ?? 'Meta'),
        actions: [
          if (meta.value case final m?)
            PopupMenuButton<String>(
              onSelected: _cambiarEstatus,
              itemBuilder: (_) => [
                if (m.activa) const PopupMenuItem(value: 'pausada', child: Text('Pausar meta')),
                if (m.estatus == 'pausada') const PopupMenuItem(value: 'activa', child: Text('Reactivar meta')),
                const PopupMenuItem(value: 'cancelada', child: Text('Cancelar meta')),
              ],
            ),
        ],
      ),
      body: FondoHoja(
        child: RefreshIndicator(
        color: AhorraColores.of(context).tinta,
        onRefresh: () async => _refrescar(),
        child: VistaAsync<Meta>(
          valor: meta,
          alReintentar: () => ref.invalidate(metaProvider(_id)),
          datos: (m) => ListView(
            padding: const EdgeInsets.fromLTRB(16, 4, 16, 32),
            children: [
              _Encabezado(meta: m),
              const SizedBox(height: 16),
              if (m.activa && m.cuotaHoy != null) ...[
                RutaDelDiaCard(
                  ruta: m.cuotaHoy!,
                  ocupado: _apartando,
                  alApartar: () => _aportar(m.cuotaHoy!.faltanteHoyUsdCts, 'cuota_del_dia'),
                ),
                const SizedBox(height: 8),
                _SelectorCuenta(
                  seleccion: _idCuentaOrigen,
                  alCambiar: (id) => setState(() => _idCuentaOrigen = id),
                ),
              ] else if (!m.activa)
                Card(
                  child: ListTile(
                    leading: const Icon(Icons.pause_circle_outline),
                    title: Text('Meta ${m.estatus}'),
                    subtitle: const Text('Reactívala para volver a tener tu Ruta del día.'),
                  ),
                ),
              const SizedBox(height: 12),
              Row(
                children: [
                  Expanded(
                    child: OutlinedButton.icon(
                      onPressed: m.activa && !_apartando ? () => _aporteManual(m) : null,
                      icon: const Icon(Icons.add),
                      label: const Text('Aportar'),
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: OutlinedButton.icon(
                      onPressed: m.acumuladoUsdCts > 0 ? () => _retirar(m) : null,
                      icon: const Icon(Icons.remove),
                      label: const Text('Retirar'),
                    ),
                  ),
                ],
              ),
              const TituloSeccion('Sello de compromiso'),
              _SeccionSello(meta: m, alSellar: () => _sellar(m), alRomper: () => _romperSello(m)),
              TituloSeccion(
                'Reglas de ahorro automático',
                accion: TextButton.icon(
                  onPressed: () => crearRegla(context, ref, _id),
                  icon: const Icon(Icons.add, size: 18),
                  label: const Text('Nueva'),
                ),
              ),
              _SeccionReglas(idMeta: _id),
              const TituloSeccion('Historial del cofre'),
              _HistorialAportes(idMeta: _id),
            ],
          ),
        ),
      ),
      ),
    );
  }
}

class _Encabezado extends StatelessWidget {
  const _Encabezado({required this.meta});

  final Meta meta;

  @override
  Widget build(BuildContext context) {
    final c = AhorraColores.of(context);
    final t = Theme.of(context).textTheme;
    final tasa = meta.cuotaHoy?.tasaValorBsX10000;
    return Padding(
      padding: const EdgeInsets.only(top: 14),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              Text(Dinero.usd(meta.acumuladoUsdCts), style: t.displaySmall),
              const SizedBox(width: 8),
              Expanded(
                child: Padding(
                  padding: const EdgeInsets.only(bottom: 4),
                  child: Text(
                    'de ${Dinero.usd(meta.montoObjetivoUsdCts)} · ${Dinero.porcentajePb(meta.progresoPb)}',
                    style: AppTheme.cifra(tamano: 13, color: c.tintaGris),
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),
          TiraProgreso(fraccion: Dinero.fraccionPb(meta.progresoPb)),
          const SizedBox(height: 8),
          if (tasa != null)
            Text('≈ ${Dinero.bs(Dinero.usdABs(meta.acumuladoUsdCts, tasa))} a tasa BCV ${Dinero.tasa(tasa)}',
                style: t.bodySmall?.copyWith(color: c.tintaGris)),
          Text('Meta: ${Fechas.larga(meta.fechaObjetivo)}', style: t.bodySmall?.copyWith(color: c.tintaGris)),
        ],
      ),
    );
  }
}

class _SelectorCuenta extends ConsumerWidget {
  const _SelectorCuenta({required this.seleccion, required this.alCambiar});

  final int? seleccion;
  final ValueChanged<int?> alCambiar;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final cuentas = (ref.watch(consolidadoProvider).value?.cuentas ?? const <Cuenta>[])
        .where((c) => !c.soloLectura)
        .toList();
    if (cuentas.isEmpty) return const SizedBox.shrink();
    final valor = cuentas.any((c) => c.idCuenta == seleccion) ? seleccion : null;
    return DropdownButtonFormField<int?>(
      initialValue: valor,
      isExpanded: true,
      decoration: const InputDecoration(labelText: 'Cuenta de origen del apartado', isDense: true),
      items: [
        const DropdownMenuItem<int?>(value: null, child: Text('Sin especificar')),
        for (final c in cuentas)
          DropdownMenuItem<int?>(
            value: c.idCuenta,
            child: Text('${c.nombreVisible} •••• ${c.ultimos4} · ${Dinero.enMoneda(c.saldoCts, c.moneda)}',
                overflow: TextOverflow.ellipsis),
          ),
      ],
      onChanged: alCambiar,
    );
  }
}

class _SeccionSello extends StatelessWidget {
  const _SeccionSello({required this.meta, required this.alSellar, required this.alRomper});

  final Meta meta;
  final VoidCallback alSellar;
  final VoidCallback alRomper;

  @override
  Widget build(BuildContext context) {
    final c = AhorraColores.of(context);
    final t = Theme.of(context).textTheme;
    final s = meta.selloActivo;
    final roto = meta.sello?.estatus == 'roto';
    if (s != null) {
      return Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          const Filete(),
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 14),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        '${Dinero.usd(s.montoSelladoUsdCts)} sellados hasta ${Fechas.media(s.fechaLiberacion)}',
                        style: t.titleSmall,
                      ),
                      const SizedBox(height: 6),
                      Text(
                        'Compromiso suave: el dinero sigue en tu banco. Mantener el sello protege tu multiplicador.',
                        style: t.bodySmall?.copyWith(color: c.tintaGris),
                      ),
                    ],
                  ),
                ),
                const SizedBox(width: 12),
                SelloRectangular(
                  titulo: 'Sellado',
                  detalle: '${Dinero.usd(s.montoSelladoUsdCts)} · ${Fechas.libreta(s.fechaLiberacion)}',
                ),
              ],
            ),
          ),
          Align(
            alignment: Alignment.centerLeft,
            child: TextButton.icon(
              onPressed: alRomper,
              icon: const Icon(Icons.lock_open),
              label: const Text('Romper sello'),
            ),
          ),
          const Filete(),
        ],
      );
    }
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        const Filete(),
        Padding(
          padding: const EdgeInsets.symmetric(vertical: 12),
          child: Row(
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(roto ? 'Tu sello anterior se rompió' : 'Sella una parte hasta tu meta', style: t.titleSmall),
                    const SizedBox(height: 2),
                    Text('Si lo rompes antes, pierdes el multiplicador, nunca tu dinero.',
                        style: t.bodySmall?.copyWith(color: c.tintaGris)),
                  ],
                ),
              ),
              const SizedBox(width: 12),
              OutlinedButton(
                onPressed: meta.acumuladoUsdCts > 0 ? alSellar : null,
                child: const Text('Sellar'),
              ),
            ],
          ),
        ),
        const Filete(),
      ],
    );
  }
}

String describirRegla(TipoRegla tipo, {int? porcentajePb, int? multiploRedondeoCts, int? montoFijoUsdCts}) =>
    switch (tipo) {
      TipoRegla.pctIngreso => 'Aparta el ${Dinero.porcentajePb(porcentajePb ?? 0)} de cada ingreso',
      TipoRegla.redondeo =>
        'Redondea cada gasto en Bs al múltiplo de ${Dinero.bs(multiploRedondeoCts ?? 0)} y aparta la diferencia',
      TipoRegla.fijoQuincenal => 'Aparta ${Dinero.usd(montoFijoUsdCts ?? 0)} los días 15 y último de cada mes',
    };

class _SeccionReglas extends ConsumerWidget {
  const _SeccionReglas({required this.idMeta});

  final int idMeta;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final reglas = ref.watch(reglasProvider(idMeta));
    return VistaAsync<List<Regla>>(
      valor: reglas,
      alReintentar: () => ref.invalidate(reglasProvider(idMeta)),
      datos: (lista) => lista.isEmpty
          ? const Card(
              child: ListTile(
                leading: Icon(Icons.auto_mode),
                title: Text('Sin reglas todavía'),
                subtitle: Text('Automatiza tu ahorro: % de ingresos, redondeo o monto fijo quincenal.'),
              ),
            )
          : Card(
              child: Column(
                children: [
                  for (final r in lista.where((r) => r.estatus != 'finalizada'))
                    SwitchListTile(
                      value: r.activa,
                      title: Text(r.tipo.etiqueta),
                      subtitle: Text(describirRegla(r.tipo,
                          porcentajePb: r.porcentajePb,
                          multiploRedondeoCts: r.multiploRedondeoCts,
                          montoFijoUsdCts: r.montoFijoUsdCts)),
                      onChanged: (v) async {
                        try {
                          await ref.read(cofreRepositoryProvider).cambiarEstatusRegla(r.idRegla, v ? 'activa' : 'pausada');
                          ref.invalidate(reglasProvider(idMeta));
                        } catch (e) {
                          if (context.mounted) mostrarError(context, e);
                        }
                      },
                    ),
                ],
              ),
            ),
    );
  }
}

class _HistorialAportes extends ConsumerWidget {
  const _HistorialAportes({required this.idMeta});

  final int idMeta;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final colores = AhorraColores.of(context);
    return VistaAsync<List<Aporte>>(
      valor: ref.watch(aportesProvider(idMeta)),
      alReintentar: () => ref.invalidate(aportesProvider(idMeta)),
      datos: (lista) => lista.isEmpty
          ? const Padding(padding: EdgeInsets.all(16), child: Text('Aún no hay aportes. ¡Empieza hoy!'))
          : Column(
              children: [
                for (final a in lista.take(30))
                  ListTile(
                    dense: true,
                    contentPadding: EdgeInsets.zero,
                    leading: Icon(a.esRetiro ? Icons.north_east : Icons.savings_outlined,
                        color: a.esRetiro ? colores.peligro : colores.cofre),
                    title: Text(switch (a.origen) {
                      _ when a.esRetiro => 'Retiro',
                      'cuota_del_dia' => 'Cuota del día',
                      'regla' => 'Regla automática',
                      _ => 'Aporte manual',
                    }),
                    subtitle: Text(Fechas.conHora(a.fechaHora)),
                    trailing: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      crossAxisAlignment: CrossAxisAlignment.end,
                      children: [
                        Text('${a.esRetiro ? '-' : '+'}${Dinero.usd(a.montoUsdCts)}',
                            style: const TextStyle(fontWeight: FontWeight.w600)),
                        if (a.montoBsCts != null)
                          Text(Dinero.bs(a.montoBsCts!), style: Theme.of(context).textTheme.bodySmall),
                      ],
                    ),
                  ),
              ],
            ),
    );
  }
}

/// Diálogo genérico para pedir un monto en USD (céntimos), con aviso opcional.
Future<int?> pedirMonto(
  BuildContext context, {
  required String titulo,
  required String etiqueta,
  int? maximoCts,
  String? mensajeMaximo,
  String? aviso,
}) {
  final ctrl = TextEditingController();
  final form = GlobalKey<FormState>();
  return showDialog<int>(
    context: context,
    builder: (ctx) => AlertDialog(
      title: Text(titulo),
      content: Form(
        key: form,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            CampoMonto(
              controller: ctrl,
              etiqueta: etiqueta,
              autofocus: true,
              maximoCts: maximoCts,
              mensajeMaximo: mensajeMaximo,
              ayuda: maximoCts == null ? null : 'Máximo ${Dinero.usd(maximoCts)}',
            ),
            if (aviso != null) ...[
              const SizedBox(height: 12),
              Text(aviso, style: Theme.of(ctx).textTheme.bodySmall),
            ],
          ],
        ),
      ),
      actions: [
        TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('Cancelar')),
        FilledButton(
          onPressed: () {
            if (form.currentState!.validate()) Navigator.pop(ctx, Dinero.parsear(ctrl.text));
          },
          child: const Text('Continuar'),
        ),
      ],
    ),
  );
}

/// Crea una regla de ahorro (3 tipos) y la aprueba con PIN.
Future<void> crearRegla(BuildContext context, WidgetRef ref, int idMeta) async {
  final solicitud = await showModalBottomSheet<SolicitudRegla>(
    context: context,
    isScrollControlled: true,
    useSafeArea: true,
    showDragHandle: true,
    builder: (_) => const _HojaRegla(),
  );
  if (solicitud == null || !context.mounted) return;
  final stepUp = await solicitarStepUp(
    context,
    motivo: 'Aprobar regla: ${describirRegla(solicitud.tipo, porcentajePb: solicitud.porcentajePb, multiploRedondeoCts: solicitud.multiploRedondeoCts, montoFijoUsdCts: solicitud.montoFijoUsdCts)}.',
  );
  if (stepUp == null) return;
  try {
    await ref.read(cofreRepositoryProvider).crearRegla(idMeta, solicitud, stepUp: stepUp.token);
    ref.invalidate(reglasProvider(idMeta));
    if (context.mounted) mostrarMensaje(context, 'Regla aprobada. Ahorrarás sin pensarlo.');
  } catch (e) {
    if (context.mounted) mostrarError(context, e);
  }
}

class _HojaRegla extends StatefulWidget {
  const _HojaRegla();

  @override
  State<_HojaRegla> createState() => _HojaReglaState();
}

class _HojaReglaState extends State<_HojaRegla> {
  TipoRegla _tipo = TipoRegla.pctIngreso;
  final _porcentaje = TextEditingController(text: '10');
  int _multiploCts = 10000;
  final _fijo = TextEditingController(text: '25');
  final _form = GlobalKey<FormState>();

  @override
  void dispose() {
    _porcentaje.dispose();
    _fijo.dispose();
    super.dispose();
  }

  void _guardar() {
    if (!_form.currentState!.validate()) return;
    final r = switch (_tipo) {
      TipoRegla.pctIngreso => SolicitudRegla.pctIngreso(Dinero.parsear(_porcentaje.text)!),
      TipoRegla.redondeo => SolicitudRegla.redondeo(_multiploCts),
      TipoRegla.fijoQuincenal => SolicitudRegla.fijoQuincenal(Dinero.parsear(_fijo.text)!),
    };
    Navigator.pop(context, r);
  }

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context).textTheme;
    return Padding(
      padding: EdgeInsets.fromLTRB(20, 0, 20, 20 + MediaQuery.viewInsetsOf(context).bottom),
      child: Form(
        key: _form,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text('Nueva regla de ahorro', style: t.titleLarge),
            const SizedBox(height: 12),
            SegmentedButton<TipoRegla>(
              segments: const [
                ButtonSegment(value: TipoRegla.pctIngreso, label: Text('% ingreso')),
                ButtonSegment(value: TipoRegla.redondeo, label: Text('Redondeo')),
                ButtonSegment(value: TipoRegla.fijoQuincenal, label: Text('Quincenal')),
              ],
              selected: {_tipo},
              showSelectedIcon: false,
              onSelectionChanged: (s) => setState(() => _tipo = s.first),
            ),
            const SizedBox(height: 16),
            switch (_tipo) {
              TipoRegla.pctIngreso => TextFormField(
                  controller: _porcentaje,
                  keyboardType: const TextInputType.numberWithOptions(decimal: true),
                  inputFormatters: [FilteringTextInputFormatter.allow(RegExp(r'[0-9.,]'))],
                  decoration: const InputDecoration(labelText: 'Porcentaje de cada ingreso', suffixText: '%'),
                  validator: (v) {
                    final pb = Dinero.parsear(v ?? '');
                    if (pb == null || pb <= 0 || pb > 5000) return 'Entre 0,01 % y 50 %';
                    return null;
                  },
                ),
              TipoRegla.redondeo => Wrap(
                  spacing: 8,
                  children: [
                    for (final m in const [100, 1000, 10000, 50000])
                      ChoiceChip(
                        label: Text('Múltiplo de ${Dinero.bs(m)}'),
                        selected: _multiploCts == m,
                        onSelected: (_) => setState(() => _multiploCts = m),
                      ),
                  ],
                ),
              TipoRegla.fijoQuincenal => CampoMonto(controller: _fijo, etiqueta: 'Monto fijo (USD)'),
            },
            const SizedBox(height: 12),
            Text(
              switch (_tipo) {
                TipoRegla.pctIngreso => 'Cada vez que recibas un ingreso en tus cuentas vinculadas apartamos ese porcentaje.',
                TipoRegla.redondeo => 'Cada gasto en Bs se redondea al múltiplo elegido y la diferencia va a tu cofre.',
                TipoRegla.fijoQuincenal => 'Los días 15 y último de cada mes apartamos este monto.',
              },
              style: t.bodySmall,
            ),
            const SizedBox(height: 16),
            FilledButton(onPressed: _guardar, child: const Text('Aprobar con PIN')),
          ],
        ),
      ),
    );
  }
}
