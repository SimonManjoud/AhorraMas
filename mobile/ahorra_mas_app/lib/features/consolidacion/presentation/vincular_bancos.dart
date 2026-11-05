import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/di/providers.dart';
import '../../../core/widgets/comunes.dart';
import '../domain/consolidacion_models.dart';
import 'consolidacion_providers.dart';

/// Flujo "Vincular bancos": selección múltiple → consentimientos → se abre la página de cada
/// banco (Ahorra+ nunca ve claves) → se consulta hasta que los consentimientos estén `activo`.
Future<void> iniciarVinculacion(BuildContext context, WidgetRef ref) async {
  final seleccion = await showModalBottomSheet<_Seleccion>(
    context: context,
    isScrollControlled: true,
    useSafeArea: true,
    showDragHandle: true,
    builder: (_) => const _HojaSeleccion(),
  );
  if (seleccion == null || seleccion.ids.isEmpty || !context.mounted) return;

  List<ConsentimientoCreado> creados;
  try {
    creados = await ref
        .read(consolidacionRepositoryProvider)
        .crearConsentimientos(seleccion.ids.toList(), permiteDebitoTdc: seleccion.debito);
  } catch (e) {
    if (context.mounted) mostrarError(context, e);
    return;
  }
  if (!context.mounted) return;
  final listo = await showDialog<bool>(
    context: context,
    barrierDismissible: false,
    builder: (_) => _DialogoAutorizacion(creados: creados),
  );
  ref.invalidate(consentimientosProvider);
  if (listo == true) {
    try {
      await ref.read(consolidacionRepositoryProvider).sincronizar();
    } catch (_) {
      // La sincronización es best-effort; el consolidado se vuelve a pedir igual.
    }
    ref.invalidate(consolidadoProvider);
    ref.invalidate(consentimientosProvider);
    if (context.mounted) mostrarMensaje(context, '¡Listo! Tus cuentas ya aparecen en Mi dinero.');
  }
}

class _Seleccion {
  const _Seleccion(this.ids, this.debito);
  final Set<int> ids;
  final bool debito;
}

class _HojaSeleccion extends ConsumerStatefulWidget {
  const _HojaSeleccion();

  @override
  ConsumerState<_HojaSeleccion> createState() => _HojaSeleccionState();
}

class _HojaSeleccionState extends ConsumerState<_HojaSeleccion> {
  final Set<int> _ids = {};
  bool _debito = true;

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context).textTheme;
    final instituciones = ref.watch(institucionesProvider);
    final consentimientos = ref.watch(consentimientosProvider).value ?? const [];
    final vinculadas = {for (final c in consentimientos.where((c) => c.activo)) c.idInstitucion};

    return DraggableScrollableSheet(
      expand: false,
      initialChildSize: 0.8,
      maxChildSize: 0.95,
      builder: (context, scroll) => Padding(
        padding: const EdgeInsets.symmetric(horizontal: 20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text('Vincular bancos y billeteras', style: t.titleLarge),
            const SizedBox(height: 4),
            Text('Elige una o varias. Te llevaremos a la página oficial de cada una para autorizar.',
                style: t.bodyMedium),
            const SizedBox(height: 8),
            const AvisoSinClaves(compacto: true),
            const SizedBox(height: 8),
            Expanded(
              child: VistaAsync(
                valor: instituciones,
                alReintentar: () => ref.invalidate(institucionesProvider),
                datos: (lista) => ListView(
                  controller: scroll,
                  children: [
                    for (final i in lista)
                      CheckboxListTile(
                        key: ValueKey('inst-${i.idInstitucion}'),
                        value: vinculadas.contains(i.idInstitucion) || _ids.contains(i.idInstitucion),
                        onChanged: vinculadas.contains(i.idInstitucion)
                            ? null
                            : (v) => setState(() => v == true ? _ids.add(i.idInstitucion) : _ids.remove(i.idInstitucion)),
                        secondary: CircleAvatar(
                          child: Icon(i.esCripto ? Icons.currency_bitcoin : Icons.account_balance_outlined),
                        ),
                        title: Text(i.nombre),
                        subtitle: Text(vinculadas.contains(i.idInstitucion)
                            ? 'Ya vinculada'
                            : (i.esCripto ? 'Plataforma cripto · solo lectura' : 'Banco')),
                      ),
                  ],
                ),
              ),
            ),
            SwitchListTile(
              contentPadding: EdgeInsets.zero,
              value: _debito,
              onChanged: (v) => setState(() => _debito = v),
              title: const Text('Permitir débito para pagar mis TDC'),
              subtitle: const Text('Solo aplica a bancos. Siempre te pediremos PIN + código SMS.'),
            ),
            Padding(
              padding: const EdgeInsets.only(bottom: 16, top: 8),
              child: FilledButton(
                key: const ValueKey('vincular-continuar'),
                onPressed: _ids.isEmpty ? null : () => Navigator.pop(context, _Seleccion(_ids, _debito)),
                child: Text(_ids.isEmpty ? 'Elige al menos una' : 'Continuar (${_ids.length})'),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _DialogoAutorizacion extends ConsumerStatefulWidget {
  const _DialogoAutorizacion({required this.creados});

  final List<ConsentimientoCreado> creados;

  @override
  ConsumerState<_DialogoAutorizacion> createState() => _DialogoAutorizacionState();
}

class _DialogoAutorizacionState extends ConsumerState<_DialogoAutorizacion> {
  final Map<int, String> _estatus = {};
  final Set<int> _abiertos = {};
  Timer? _timer;
  int _consultas = 0;
  static const _maxConsultas = 60; // ~3 minutos

  @override
  void initState() {
    super.initState();
    for (final c in widget.creados) {
      _estatus[c.idConsentimiento] = c.estatus;
    }
    _timer = Timer.periodic(const Duration(seconds: 3), (_) => _consultar());
    // Abre la primera institución automáticamente.
    WidgetsBinding.instance.addPostFrameCallback((_) => _abrir(widget.creados.first));
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  bool get _todosActivos => _estatus.values.every((e) => e == 'activo');

  Future<void> _abrir(ConsentimientoCreado c) async {
    final ok = await ref.read(abridorEnlacesProvider).abrir(Uri.parse(c.urlAutorizacion));
    if (!mounted) return;
    setState(() => _abiertos.add(c.idConsentimiento));
    if (!ok) mostrarMensaje(context, 'No pudimos abrir la página de ${c.institucion}.');
  }

  Future<void> _consultar() async {
    if (++_consultas > _maxConsultas) {
      _timer?.cancel();
      return;
    }
    try {
      final lista = await ref.read(consolidacionRepositoryProvider).consentimientos();
      if (!mounted) return;
      setState(() {
        for (final c in lista) {
          if (_estatus.containsKey(c.idConsentimiento)) _estatus[c.idConsentimiento] = c.estatus;
        }
      });
      if (_todosActivos) {
        _timer?.cancel();
      } else {
        // Abre automáticamente la siguiente institución pendiente que no se ha abierto.
        final siguiente = widget.creados.where((c) =>
            _estatus[c.idConsentimiento] == 'pendiente' && !_abiertos.contains(c.idConsentimiento));
        final anteriorListo = widget.creados
            .where((c) => _abiertos.contains(c.idConsentimiento))
            .every((c) => _estatus[c.idConsentimiento] == 'activo');
        if (siguiente.isNotEmpty && anteriorListo) _abrir(siguiente.first);
      }
    } catch (_) {
      // Se reintenta en el siguiente ciclo.
    }
  }

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return AlertDialog(
      title: Text(_todosActivos ? '¡Autorización completa!' : 'Autoriza en tu banco'),
      content: SizedBox(
        width: 400,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            if (!_todosActivos)
              const Text('Se abrió la página oficial de cada institución. Inicia sesión allí y '
                  'acepta el permiso; luego vuelve a Ahorra+.'),
            const SizedBox(height: 12),
            for (final c in widget.creados)
              ListTile(
                dense: true,
                contentPadding: EdgeInsets.zero,
                leading: switch (_estatus[c.idConsentimiento]) {
                  'activo' => Icon(Icons.check_circle, color: cs.primary),
                  'pendiente' => const SizedBox(
                      width: 20, height: 20, child: CircularProgressIndicator(strokeWidth: 2)),
                  _ => Icon(Icons.error_outline, color: cs.error),
                },
                title: Text(c.institucion),
                subtitle: Text(_estatus[c.idConsentimiento] ?? ''),
                trailing: _estatus[c.idConsentimiento] == 'pendiente'
                    ? TextButton(onPressed: () => _abrir(c), child: const Text('Abrir'))
                    : null,
              ),
            if (_consultas > _maxConsultas && !_todosActivos)
              Text('Seguimos esperando. Puedes cerrar y revisar luego en "Gestionar vínculos".',
                  style: TextStyle(color: cs.error)),
          ],
        ),
      ),
      actions: [
        if (!_todosActivos)
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cerrar')),
        if (!_todosActivos)
          TextButton(onPressed: _consultar, child: const Text('Ya autoricé')),
        if (_todosActivos)
          FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('Ver mis cuentas')),
      ],
    );
  }
}
