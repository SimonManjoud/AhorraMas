import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/di/providers.dart';
import '../../../../core/money/dinero.dart';
import '../../../../core/theme/app_theme.dart';
import '../../../../core/widgets/comunes.dart';
import '../../../puntos/presentation/puntos_providers.dart';
import '../../domain/cofre_models.dart';
import '../cofre_providers.dart';

/// Botón primario "Apartar $x · Bs y": aporta la cuota pendiente de hoy en un toque
/// (`origen = cuota_del_dia`) y refresca libro, racha y puntos.
class BotonApartarCuota extends ConsumerStatefulWidget {
  const BotonApartarCuota({super.key, required this.ruta});

  final RutaDelDia ruta;

  @override
  ConsumerState<BotonApartarCuota> createState() => _BotonApartarCuotaState();
}

class _BotonApartarCuotaState extends ConsumerState<BotonApartarCuota> {
  bool _ocupado = false;

  Future<void> _apartar() async {
    final ruta = widget.ruta;
    setState(() => _ocupado = true);
    try {
      final r = await ref.read(cofreRepositoryProvider).aportar(
            ruta.idMeta,
            montoUsdCts: ruta.faltanteHoyUsdCts,
            origen: 'cuota_del_dia',
          );
      refrescarCofre(ref, ruta.idMeta);
      refrescarPuntos(ref);
      if (!mounted) return;
      final racha = r.racha;
      mostrarMensaje(
        context,
        '¡Apartaste ${Dinero.usd(r.aporte.montoUsdCts)}! +${r.puntosGanados} puntos'
        '${racha == null ? '' : ' - racha de ${racha.diasConsecutivos} días'}',
      );
    } catch (e) {
      if (mounted) mostrarError(context, e);
    } finally {
      if (mounted) setState(() => _ocupado = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final ruta = widget.ruta;
    final c = AhorraColores.of(context);
    final faltaBs = Dinero.usdABs(ruta.faltanteHoyUsdCts, ruta.tasaValorBsX10000);
    return SizedBox(
      width: double.infinity,
      child: FilledButton(
        onPressed: _ocupado ? null : _apartar,
        child: _ocupado
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
                    if (faltaBs > 0) ...[
                      const SizedBox(width: 10),
                      Text(
                        Dinero.bs(faltaBs),
                        style: AppTheme.cifra(tamano: 13, color: c.sobreTapa.withValues(alpha: 0.8)),
                      ),
                    ],
                  ],
                ),
              ),
      ),
    );
  }
}
