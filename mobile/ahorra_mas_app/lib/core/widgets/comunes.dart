import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../api/api_exception.dart';
import '../theme/app_theme.dart';
import 'texto_enfasis.dart';

/// Pinta un [AsyncValue] con estados de carga y error uniformes.
class VistaAsync<T> extends StatelessWidget {
  const VistaAsync({
    super.key,
    required this.valor,
    required this.datos,
    this.alReintentar,
    this.cargando,
  });

  final AsyncValue<T> valor;
  final Widget Function(T datos) datos;
  final VoidCallback? alReintentar;
  final Widget? cargando;

  @override
  Widget build(BuildContext context) {
    return valor.when(
      skipLoadingOnRefresh: true,
      skipLoadingOnReload: true,
      data: datos,
      loading: () =>
          cargando ?? const Padding(padding: EdgeInsets.all(32), child: Center(child: CircularProgressIndicator())),
      error: (e, _) => VistaError(error: e, alReintentar: alReintentar),
    );
  }
}

class VistaError extends StatelessWidget {
  const VistaError({super.key, required this.error, this.alReintentar});

  final Object error;
  final VoidCallback? alReintentar;

  @override
  Widget build(BuildContext context) {
    final kyc = error is ApiException && (error as ApiException).kycPendiente;
    return Padding(
      padding: const EdgeInsets.all(24),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(kyc ? Icons.badge_outlined : Icons.cloud_off_outlined,
              size: 44, color: Theme.of(context).colorScheme.outline),
          const SizedBox(height: 12),
          Text(mensajeDeError(error), textAlign: TextAlign.center),
          if (alReintentar != null) ...[
            const SizedBox(height: 12),
            OutlinedButton.icon(
              onPressed: alReintentar,
              icon: const Icon(Icons.refresh),
              label: const Text('Reintentar'),
            ),
          ],
        ],
      ),
    );
  }
}

/// Aviso legal de simulación (Espejo de Impacto y asistente IA).
class AvisoSimulacion extends StatelessWidget {
  const AvisoSimulacion({super.key, required this.texto});

  final String texto;

  static const textoBase = 'Simulación informativa, no asesoría.';

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return Container(
      padding: const EdgeInsets.symmetric(vertical: 10),
      decoration: BoxDecoration(
        border: Border(top: BorderSide(color: cs.outlineVariant), bottom: BorderSide(color: cs.outlineVariant)),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(Icons.info_outline, size: 18, color: cs.onSurfaceVariant),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              texto.toLowerCase().contains('no asesor') ? texto : '$textoBase $texto',
              style: Theme.of(context)
                  .textTheme
                  .bodySmall
                  ?.copyWith(color: cs.onSurfaceVariant, fontStyle: FontStyle.italic),
            ),
          ),
        ],
      ),
    );
  }
}

/// Recordatorio de la regla de oro: nunca pedimos claves del banco.
class AvisoSinClaves extends StatelessWidget {
  const AvisoSinClaves({super.key, this.compacto = false});

  final bool compacto;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return Row(
      children: [
        Icon(Icons.lock_outline, size: compacto ? 14 : 18, color: cs.onSurfaceVariant),
        const SizedBox(width: 6),
        Expanded(
          child: Text(
            'Ahorra+ nunca te pedirá la clave de tu banco.',
            style: (compacto ? Theme.of(context).textTheme.bodySmall : Theme.of(context).textTheme.bodyMedium)
                ?.copyWith(color: cs.onSurfaceVariant),
          ),
        ),
      ],
    );
  }
}

class TituloSeccion extends StatelessWidget {
  const TituloSeccion(this.texto, {super.key, this.accion});

  final String texto;
  final Widget? accion;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(0, 20, 0, 8),
      child: Row(
        children: [
          Expanded(child: Text(texto, style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700))),
          ?accion,
        ],
      ),
    );
  }
}

/// Chip de estado con color.
class EtiquetaEstado extends StatelessWidget {
  const EtiquetaEstado({super.key, required this.texto, required this.color, this.icono});

  final String texto;
  final Color color;
  final IconData? icono;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: BoxDecoration(color: color.withValues(alpha: 0.12), borderRadius: BorderRadius.circular(20)),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (icono != null) ...[Icon(icono, size: 14, color: color), const SizedBox(width: 4)],
          Flexible(
            child: Text(texto,
                overflow: TextOverflow.ellipsis,
                style: TextStyle(fontFamily: AppTheme.familia, color: color, fontWeight: FontWeight.w700, fontSize: 12)),
          ),
        ],
      ),
    );
  }
}

/// Muestra un error de operación en un SnackBar.
void mostrarError(BuildContext context, Object error) {
  if (!context.mounted) return;
  ScaffoldMessenger.of(context)
    ..hideCurrentSnackBar()
    ..showSnackBar(SnackBar(
      content: TextoEnfasis(mensajeDeError(error)),
      backgroundColor: AhorraColores.of(context).peligro,
      behavior: SnackBarBehavior.floating,
    ));
}

void mostrarMensaje(BuildContext context, String mensaje) {
  if (!context.mounted) return;
  ScaffoldMessenger.of(context)
    ..hideCurrentSnackBar()
    ..showSnackBar(SnackBar(content: TextoEnfasis(mensaje), behavior: SnackBarBehavior.floating));
}

Future<bool> confirmar(
  BuildContext context, {
  required String titulo,
  required String mensaje,
  String aceptar = 'Continuar',
  bool peligroso = false,
}) async {
  final r = await showDialog<bool>(
    context: context,
    builder: (ctx) => AlertDialog(
      title: Text(titulo),
      content: TextoEnfasis(mensaje),
      actions: [
        TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancelar')),
        FilledButton(
          style: peligroso
              ? FilledButton.styleFrom(backgroundColor: AhorraColores.of(ctx).peligro)
              : null,
          onPressed: () => Navigator.pop(ctx, true),
          child: Text(aceptar),
        ),
      ],
    ),
  );
  return r ?? false;
}
