import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/di/providers.dart';
import '../../../core/money/dinero.dart';
import '../../../core/theme/app_theme.dart';
import '../../../core/util/fechas.dart';
import '../../../core/widgets/comunes.dart';
import '../../../core/widgets/step_up.dart';
import '../../cofre/domain/cofre_models.dart';
import '../../cofre/presentation/cofre_providers.dart';
import '../../cofre/presentation/meta_detalle_screen.dart' show describirRegla;
import '../domain/asistente.dart';
import '../../../core/widgets/texto_enfasis.dart';

final historialRecomendacionesProvider = FutureProvider.autoDispose<List<Recomendacion>>(
    (ref) => ref.watch(asistenteRepositoryProvider).historialRecomendaciones());

/// Asistente de perfil de riesgo con IA. La IA nunca actúa sola: el usuario acepta o rechaza
/// con su PIN.
class AsistenteScreen extends ConsumerStatefulWidget {
  const AsistenteScreen({super.key});

  @override
  ConsumerState<AsistenteScreen> createState() => _AsistenteScreenState();
}

class _AsistenteScreenState extends ConsumerState<AsistenteScreen> {
  String _objetivo = 'proteger';
  double _horizonte = 6;
  String _tolerancia = 'baja';
  Evaluacion? _evaluacion;
  bool _ocupado = false;
  bool _entendio = false;
  int? _idMeta;
  String? _decision;

  Future<void> _evaluar() async {
    setState(() {
      _ocupado = true;
      _decision = null;
      _entendio = false;
    });
    try {
      final e = await ref.read(asistenteRepositoryProvider).evaluar(Cuestionario(
            objetivoPrincipal: _objetivo,
            horizonteMeses: _horizonte.round(),
            toleranciaRiesgo: _tolerancia,
          ));
      setState(() => _evaluacion = e);
      ref.invalidate(historialRecomendacionesProvider);
    } catch (e) {
      if (mounted) mostrarError(context, e);
    } finally {
      if (mounted) setState(() => _ocupado = false);
    }
  }

  Future<void> _decidir(bool aceptar) async {
    final rec = _evaluacion!.recomendacion;
    final stepUp = await solicitarStepUp(
      context,
      motivo: aceptar
          ? 'Aceptar la recomendación${_idMeta == null ? '' : ' y crear la regla sugerida en tu meta'}.'
          : 'Rechazar la recomendación.',
    );
    if (stepUp == null) return;
    setState(() => _ocupado = true);
    try {
      final d = await ref.read(asistenteRepositoryProvider).decidir(
            rec.idRecomendacion,
            aceptar: aceptar,
            entendioExplicacion: _entendio,
            idMeta: aceptar ? _idMeta : null,
            stepUp: stepUp.token,
          );
      setState(() => _decision = d.estatus);
      ref.invalidate(historialRecomendacionesProvider);
      if (d.reglaCreada != null) {
        ref.invalidate(reglasProvider(d.reglaCreada!.idMeta));
      }
      if (mounted) {
        mostrarMensaje(
          context,
          aceptar
              ? (d.reglaCreada != null ? 'Aceptada. Creamos la regla en tu meta.' : 'Recomendación aceptada.')
              : 'Recomendación rechazada. No se hizo ningún cambio.',
        );
      }
    } catch (e) {
      if (mounted) mostrarError(context, e);
    } finally {
      if (mounted) setState(() => _ocupado = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context).textTheme;
    final e = _evaluacion;
    return Scaffold(
      appBar: AppBar(title: const Text('Asistente IA')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          const AvisoSimulacion(
            texto: 'El asistente usa reglas e IA con datos seudonimizados. Nunca mueve tu dinero ni '
                'crea reglas sin tu aprobación con PIN.',
          ),
          const SizedBox(height: 16),
          Text('¿Qué es lo más importante para ti?', style: t.titleSmall),
          const SizedBox(height: 8),
          Wrap(
            spacing: 8,
            children: [
              for (final o in const [
                ('proteger', 'Proteger mi dinero'),
                ('meta_corta', 'Una meta cercana'),
                ('crecer', 'Hacerlo crecer'),
              ])
                ChoiceChip(
                  label: Text(o.$2),
                  selected: _objetivo == o.$1,
                  onSelected: (_) => setState(() => _objetivo = o.$1),
                ),
            ],
          ),
          const SizedBox(height: 16),
          Text('¿En cuánto tiempo? ${_horizonte.round()} meses', style: t.titleSmall),
          Slider(
            value: _horizonte,
            min: 1,
            max: 36,
            divisions: 35,
            label: '${_horizonte.round()} meses',
            onChanged: (v) => setState(() => _horizonte = v),
          ),
          Text('Si tu ahorro baja un poco por un tiempo, ¿cómo te sentirías?', style: t.titleSmall),
          const SizedBox(height: 8),
          SegmentedButton<String>(
            segments: const [
              ButtonSegment(value: 'baja', label: Text('Muy incómodo')),
              ButtonSegment(value: 'media', label: Text('Lo tolero')),
              ButtonSegment(value: 'alta', label: Text('Tranquilo')),
            ],
            selected: {_tolerancia},
            showSelectedIcon: false,
            onSelectionChanged: (s) => setState(() => _tolerancia = s.first),
          ),
          const SizedBox(height: 16),
          FilledButton.icon(
            onPressed: _ocupado ? null : _evaluar,
            icon: const Icon(Icons.auto_awesome),
            label: Text(e == null ? 'Ver mi recomendación' : 'Volver a evaluar'),
          ),
          if (e != null) ...[
            const SizedBox(height: 20),
            _Recomendacion(evaluacion: e),
            const SizedBox(height: 12),
            if (_decision == null) ...[
              _SelectorMeta(
                seleccion: _idMeta,
                alCambiar: (id) => setState(() => _idMeta = id),
              ),
              CheckboxListTile(
                contentPadding: EdgeInsets.zero,
                value: _entendio,
                onChanged: (v) => setState(() => _entendio = v ?? false),
                title: const Text('Leí y entendí la explicación'),
                controlAffinity: ListTileControlAffinity.leading,
              ),
              Row(
                children: [
                  Expanded(
                    child: OutlinedButton(
                      onPressed: _ocupado ? null : () => _decidir(false),
                      child: const Text('Rechazar'),
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: FilledButton(
                      onPressed: _ocupado || !_entendio ? null : () => _decidir(true),
                      child: const Text('Aceptar con PIN'),
                    ),
                  ),
                ],
              ),
            ] else
              Card(
                child: ListTile(
                  leading: Icon(_decision == 'aceptada' ? Icons.check_circle : Icons.cancel_outlined),
                  title: Text('Recomendación $_decision'),
                ),
              ),
          ],
          const TituloSeccion('Historial'),
          const _Historial(),
        ],
      ),
    );
  }
}

class _Recomendacion extends StatelessWidget {
  const _Recomendacion({required this.evaluacion});

  final Evaluacion evaluacion;

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context).textTheme;
    final r = evaluacion.recomendacion;
    final colores = AhorraColores.of(context);
    final paleta = [Theme.of(context).colorScheme.primary, colores.cofre, colores.enProceso, colores.colchon];
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                const Icon(Icons.auto_awesome),
                const SizedBox(width: 8),
                Expanded(
                  child: Text('Perfil ${evaluacion.perfilResultante}',
                      style: t.titleMedium?.copyWith(fontWeight: FontWeight.w700)),
                ),
              ],
            ),
            const SizedBox(height: 12),
            ClipRRect(
              borderRadius: BorderRadius.circular(8),
              child: SizedBox(
                height: 16,
                child: Row(
                  children: [
                    for (var i = 0; i < r.distribucion.length; i++)
                      Expanded(
                        flex: r.distribucion[i].porcentajePb.clamp(1, 10000),
                        child: Container(color: paleta[i % paleta.length]),
                      ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 10),
            for (var i = 0; i < r.distribucion.length; i++)
              Padding(
                padding: const EdgeInsets.symmetric(vertical: 3),
                child: Row(
                  children: [
                    Container(width: 12, height: 12, decoration: BoxDecoration(color: paleta[i % paleta.length], shape: BoxShape.circle)),
                    const SizedBox(width: 8),
                    Expanded(child: Text('${r.distribucion[i].instrumento} - riesgo ${r.distribucion[i].nivelRiesgo}')),
                    Text(Dinero.porcentajePb(r.distribucion[i].porcentajePb),
                        style: const TextStyle(fontWeight: FontWeight.w700)),
                  ],
                ),
              ),
            const SizedBox(height: 12),
            TextoEnfasis(r.explicacion),
            if (r.reglaSugerida case final s?) ...[
              const SizedBox(height: 12),
              Text('Regla sugerida', style: t.titleSmall),
              Text(describirRegla(s.tipo,
                  porcentajePb: s.porcentajePb,
                  multiploRedondeoCts: s.multiploRedondeoCts,
                  montoFijoUsdCts: s.montoFijoUsdCts)),
            ],
            const SizedBox(height: 12),
            if (r.versionModelo != null) Text('Versión: ${r.versionModelo}', style: t.labelSmall),
            const SizedBox(height: 8),
            AvisoSimulacion(texto: r.disclaimer),
          ],
        ),
      ),
    );
  }
}

class _SelectorMeta extends ConsumerWidget {
  const _SelectorMeta({required this.seleccion, required this.alCambiar});

  final int? seleccion;
  final ValueChanged<int?> alCambiar;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final metas = (ref.watch(metasProvider).value ?? const <Meta>[]).where((m) => m.activa).toList();
    if (metas.isEmpty) return const SizedBox.shrink();
    return DropdownButtonFormField<int?>(
      initialValue: seleccion,
      isExpanded: true,
      decoration: const InputDecoration(labelText: 'Aplicar la regla sugerida a la meta (opcional)'),
      items: [
        const DropdownMenuItem<int?>(value: null, child: Text('No aplicar a ninguna meta')),
        for (final m in metas) DropdownMenuItem<int?>(value: m.idMeta, child: Text(m.nombre)),
      ],
      onChanged: alCambiar,
    );
  }
}

class _Historial extends ConsumerWidget {
  const _Historial();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return VistaAsync<List<Recomendacion>>(
      valor: ref.watch(historialRecomendacionesProvider),
      alReintentar: () => ref.invalidate(historialRecomendacionesProvider),
      datos: (lista) => lista.isEmpty
          ? const Text('Aún no has pedido recomendaciones.')
          : Column(
              children: [
                for (final r in lista)
                  ListTile(
                    contentPadding: EdgeInsets.zero,
                    leading: const Icon(Icons.history),
                    title: Text('Perfil ${r.perfilResultante ?? '—'} - ${r.estatus}'),
                    subtitle: Text('${Fechas.conHora(r.fecha)}${r.versionModelo == null ? '' : ' - ${r.versionModelo}'}'),
                  ),
              ],
            ),
    );
  }
}
