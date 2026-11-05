import 'dart:math';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/di/providers.dart';
import '../../../core/money/dinero.dart';
import '../../../core/theme/app_theme.dart';
import '../../../core/util/fechas.dart';
import '../../../core/widgets/comunes.dart';
import '../../../core/widgets/dinero_widgets.dart';
import '../../consolidacion/presentation/consolidacion_providers.dart';
import '../domain/cofre_models.dart';
import 'cofre_providers.dart';
import 'widgets/cofre_widgets.dart';

/// Onboarding emocional de una meta: nombre, categoría, monto en USD, fecha y frecuencia.
class CrearMetaScreen extends ConsumerStatefulWidget {
  const CrearMetaScreen({super.key});

  @override
  ConsumerState<CrearMetaScreen> createState() => _CrearMetaScreenState();
}

class _CrearMetaScreenState extends ConsumerState<CrearMetaScreen> {
  final _form = GlobalKey<FormState>();
  final _nombre = TextEditingController();
  final _monto = TextEditingController();
  CategoriaMeta _categoria = CategoriaMeta.viaje;
  FrecuenciaCuota _frecuencia = FrecuenciaCuota.diaria;
  DateTime? _fecha;
  int? _montoCts;
  bool _ocupado = false;

  static const _sugerencias = {
    CategoriaMeta.viaje: 'Viaje en marzo',
    CategoriaMeta.emergencia: 'Fondo de emergencia',
    CategoriaMeta.estudio: 'Curso de inglés',
    CategoriaMeta.salud: 'Chequeo médico',
    CategoriaMeta.hogar: 'Nevera nueva',
    CategoriaMeta.otro: 'Mi meta',
  };

  @override
  void dispose() {
    _nombre.dispose();
    _monto.dispose();
    super.dispose();
  }

  /// Estimado local de la cuota (el backend calcula la oficial con la fórmula R-09).
  int? get _cuotaEstimada {
    final monto = _montoCts;
    final f = _fecha;
    if (monto == null || f == null) return null;
    final dias = max(1, Fechas.soloFecha(f).difference(Fechas.soloFecha(DateTime.now())).inDays);
    final periodos = _frecuencia == FrecuenciaCuota.diaria ? dias : max(1, (dias + 6) ~/ 7);
    return (monto + periodos - 1) ~/ periodos;
  }

  Future<void> _elegirFecha() async {
    final hoy = DateTime.now();
    final f = await showDatePicker(
      context: context,
      initialDate: _fecha ?? hoy.add(const Duration(days: 120)),
      firstDate: hoy.add(const Duration(days: 1)),
      lastDate: hoy.add(const Duration(days: 365 * 10)),
      helpText: '¿Para cuándo?',
    );
    if (f != null) setState(() => _fecha = f);
  }

  Future<void> _crear() async {
    if (!_form.currentState!.validate()) return;
    if (_fecha == null) {
      mostrarMensaje(context, 'Elige la fecha de tu meta.');
      return;
    }
    setState(() => _ocupado = true);
    try {
      final meta = await ref.read(cofreRepositoryProvider).crearMeta(SolicitudMeta(
            nombre: _nombre.text.trim(),
            categoria: _categoria,
            montoObjetivoUsdCts: Dinero.parsear(_monto.text)!,
            fechaObjetivo: _fecha!,
            frecuenciaCuota: _frecuencia,
          ));
      ref.invalidate(metasProvider);
      ref.invalidate(rutasDelDiaProvider);
      if (mounted) context.pushReplacement('/cofre/meta/${meta.idMeta}');
    } catch (e) {
      if (mounted) mostrarError(context, e);
    } finally {
      if (mounted) setState(() => _ocupado = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context).textTheme;
    final colores = AhorraColores.of(context);
    final tasa = ref.watch(tasaHoyProvider).value;
    final cuota = _cuotaEstimada;
    return Scaffold(
      appBar: AppBar(title: const Text('Nueva meta')),
      body: Form(
        key: _form,
        child: ListView(
          padding: const EdgeInsets.all(20),
          children: [
            Text('¿Para qué estás ahorrando?', style: t.headlineSmall?.copyWith(fontWeight: FontWeight.w700)),
            const SizedBox(height: 4),
            Text('Ponle nombre a tu sueño. Lo convertiremos en una pequeña cuota.', style: t.bodyMedium),
            const SizedBox(height: 16),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                for (final c in CategoriaMeta.values)
                  ChoiceChip(
                    avatar: Icon(iconoCategoria(c), size: 18),
                    label: Text(c.etiqueta),
                    selected: _categoria == c,
                    onSelected: (_) => setState(() {
                      _categoria = c;
                      if (_nombre.text.isEmpty || _sugerencias.values.contains(_nombre.text)) {
                        _nombre.text = _sugerencias[c]!;
                      }
                    }),
                  ),
              ],
            ),
            const SizedBox(height: 16),
            TextFormField(
              controller: _nombre,
              maxLength: 60,
              textCapitalization: TextCapitalization.sentences,
              decoration: const InputDecoration(labelText: 'Nombre de la meta', hintText: 'Ej. Viaje en marzo'),
              validator: (v) => (v == null || v.trim().length < 3) ? 'Escribe un nombre (mín. 3 letras)' : null,
            ),
            const SizedBox(height: 8),
            CampoMonto(
              controller: _monto,
              etiqueta: '¿Cuánto necesitas? (USD)',
              ayuda: tasa == null || _montoCts == null
                  ? 'Monto en dólares'
                  : '≈ ${Dinero.bs(Dinero.usdABs(_montoCts!, tasa.valorBsX10000))} a tasa BCV de hoy',
              alCambiar: (v) => setState(() => _montoCts = v),
            ),
            const SizedBox(height: 16),
            ListTile(
              contentPadding: EdgeInsets.zero,
              leading: const Icon(Icons.event_outlined),
              title: const Text('¿Para cuándo?'),
              subtitle: Text(_fecha == null ? 'Elegir fecha' : Fechas.larga(_fecha)),
              trailing: const Icon(Icons.chevron_right),
              onTap: _elegirFecha,
            ),
            const SizedBox(height: 8),
            Text('¿Cada cuánto quieres apartar?', style: t.titleSmall),
            const SizedBox(height: 8),
            SegmentedButton<FrecuenciaCuota>(
              segments: const [
                ButtonSegment(value: FrecuenciaCuota.diaria, label: Text('Cada día'), icon: Icon(Icons.today)),
                ButtonSegment(value: FrecuenciaCuota.semanal, label: Text('Cada semana'), icon: Icon(Icons.date_range)),
              ],
              selected: {_frecuencia},
              onSelectionChanged: (s) => setState(() => _frecuencia = s.first),
            ),
            if (cuota != null) ...[
              const SizedBox(height: 20),
              Card(
                color: colores.cofreContenedor,
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Row(
                    children: [
                      Icon(Icons.route, color: colores.alCofre),
                      const SizedBox(width: 12),
                      Expanded(
                        child: Text(
                          '${_frecuencia == FrecuenciaCuota.diaria ? 'Cada día' : 'Cada semana'} apartarías '
                          'cerca de ${Dinero.usd(cuota)}'
                          '${tasa == null ? '' : ' (${Dinero.bs(Dinero.usdABs(cuota, tasa.valorBsX10000))} hoy)'}.',
                          style: t.titleSmall?.copyWith(color: colores.alCofre),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ],
            const SizedBox(height: 24),
            FilledButton(
              onPressed: _ocupado ? null : _crear,
              child: _ocupado
                  ? const SizedBox(width: 22, height: 22, child: CircularProgressIndicator(strokeWidth: 2.5))
                  : const Text('Crear meta'),
            ),
          ],
        ),
      ),
    );
  }
}
