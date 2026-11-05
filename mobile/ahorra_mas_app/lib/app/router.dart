import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../core/session/session_controller.dart';
import '../features/asistente/presentation/asistente_screen.dart';
import '../features/auth/presentation/kyc_screen.dart';
import '../features/auth/presentation/login_screen.dart';
import '../features/auth/presentation/registro_screen.dart';
import '../features/cofre/presentation/cofre_screen.dart';
import '../features/cofre/presentation/crear_meta_screen.dart';
import '../features/cofre/presentation/espejo_screen.dart';
import '../features/cofre/presentation/meta_detalle_screen.dart';
import '../features/cofre/presentation/racha_screen.dart';
import '../features/consolidacion/domain/consolidacion_models.dart';
import '../features/consolidacion/presentation/consentimientos_screen.dart';
import '../features/consolidacion/presentation/cuenta_screen.dart';
import '../features/consolidacion/presentation/home_screen.dart';
import '../features/notificaciones/presentation/notificaciones_screen.dart';
import '../features/pagos/domain/pagos_models.dart';
import '../features/pagos/presentation/pagar_screen.dart';
import '../features/pagos/presentation/pago_tdc_flow_screen.dart';
import '../features/pagos/presentation/resultado_pago.dart';
import '../features/perfil/presentation/ajustes_screen.dart';
import '../features/perfil/presentation/mas_screen.dart';
import '../features/puntos/presentation/puntos_screen.dart';
import 'shell.dart';

/// Rutas de la app. La sesión controla las redirecciones:
/// sin sesión → `/login`; sesión sin KYC → `/kyc`; con KYC → pestañas.
final routerProvider = Provider<GoRouter>((ref) {
  final refresco = ValueNotifier<int>(0);
  ref.listen(sessionProvider, (_, _) => refresco.value++);
  ref.onDispose(refresco.dispose);

  return GoRouter(
    initialLocation: '/login',
    refreshListenable: refresco,
    redirect: (context, state) {
      final sesion = ref.read(sessionProvider);
      final ubicacion = state.matchedLocation;
      final publica = ubicacion == '/login' || ubicacion == '/registro';
      if (sesion == null) return publica ? null : '/login';
      if (!sesion.usuario.kycAprobado) return ubicacion == '/kyc' ? null : '/kyc';
      if (publica || ubicacion == '/kyc') return '/inicio';
      return null;
    },
    routes: [
      GoRoute(path: '/login', builder: (_, _) => const LoginScreen()),
      GoRoute(path: '/registro', builder: (_, _) => const RegistroScreen()),
      GoRoute(path: '/kyc', builder: (_, _) => const KycScreen()),
      StatefulShellRoute.indexedStack(
        builder: (context, state, shell) => ShellPrincipal(navigationShell: shell),
        branches: [
          StatefulShellBranch(routes: [
            GoRoute(
              path: '/inicio',
              builder: (_, _) => const HomeScreen(),
              routes: [
                GoRoute(path: 'consentimientos', builder: (_, _) => const ConsentimientosScreen()),
                GoRoute(
                  path: 'cuenta/:id',
                  builder: (_, s) => CuentaScreen(
                    idCuenta: int.parse(s.pathParameters['id']!),
                    cuenta: s.extra is Cuenta ? s.extra as Cuenta : null,
                  ),
                ),
              ],
            ),
          ]),
          StatefulShellBranch(routes: [
            GoRoute(
              path: '/cofre',
              builder: (_, _) => const CofreScreen(),
              routes: [
                GoRoute(path: 'nueva', builder: (_, _) => const CrearMetaScreen()),
                GoRoute(
                  path: 'meta/:id',
                  builder: (_, s) => MetaDetalleScreen(idMeta: int.parse(s.pathParameters['id']!)),
                ),
                GoRoute(path: 'racha', builder: (_, _) => const RachaScreen()),
                GoRoute(path: 'espejo', builder: (_, _) => const EspejoScreen()),
              ],
            ),
          ]),
          StatefulShellBranch(routes: [
            GoRoute(
              path: '/pagar',
              builder: (_, _) => const PagarScreen(),
              routes: [
                GoRoute(
                  path: 'nuevo',
                  builder: (_, s) => PagoTdcFlowScreen(tarjeta: s.extra is Tarjeta ? s.extra as Tarjeta : null),
                ),
                GoRoute(
                  path: 'pago/:id',
                  builder: (_, s) => ResultadoPagoScreen(
                    idPago: int.parse(s.pathParameters['id']!),
                    pagoInicial: s.extra is Pago ? s.extra as Pago : null,
                  ),
                ),
              ],
            ),
          ]),
          StatefulShellBranch(routes: [
            GoRoute(path: '/puntos', builder: (_, _) => const PuntosScreen()),
          ]),
          StatefulShellBranch(routes: [
            GoRoute(
              path: '/mas',
              builder: (_, _) => const MasScreen(),
              routes: [
                GoRoute(path: 'asistente', builder: (_, _) => const AsistenteScreen()),
                GoRoute(path: 'notificaciones', builder: (_, _) => const NotificacionesScreen()),
                GoRoute(path: 'ajustes', builder: (_, _) => const AjustesScreen()),
              ],
            ),
          ]),
        ],
      ),
    ],
  );
});
