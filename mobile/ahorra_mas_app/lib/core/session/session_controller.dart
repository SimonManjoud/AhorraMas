import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../features/auth/domain/auth_models.dart';

/// Sesión en memoria (el access token no se persiste: cada arranque pide PIN).
class SessionController extends Notifier<Sesion?> {
  /// Se activa cuando el backend respondió `401 NO_AUTENTICADO` (sesión expirada).
  bool expiradaPorServidor = false;

  @override
  Sesion? build() => null;

  void iniciar(Sesion sesion) {
    expiradaPorServidor = false;
    state = sesion;
  }

  void actualizarUsuario(Usuario usuario) {
    final s = state;
    if (s != null) state = s.copyWith(usuario: usuario);
  }

  void cerrar() => state = null;

  void expirar() {
    if (state == null) return;
    expiradaPorServidor = true;
    state = null;
  }
}

final sessionProvider = NotifierProvider<SessionController, Sesion?>(SessionController.new);
