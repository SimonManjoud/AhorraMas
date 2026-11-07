import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../core/theme/app_theme.dart';

/// Navegación inferior: Inicio · Cofre · Pagar · Puntos · Más.
/// Barra Material 3 sobre la lámina clara, con indicador píldora en rosa vino y sombra suave.
class ShellPrincipal extends StatelessWidget {
  const ShellPrincipal({super.key, required this.navigationShell});

  final StatefulNavigationShell navigationShell;

  @override
  Widget build(BuildContext context) {
    final c = AhorraColores.of(context);
    return Scaffold(
      body: navigationShell,
      bottomNavigationBar: DecoratedBox(
        decoration: BoxDecoration(
          color: c.superficie,
          boxShadow: const [BoxShadow(color: Color(0x14000000), blurRadius: 16, offset: Offset(0, -2))],
        ),
        child: NavigationBar(
          selectedIndex: navigationShell.currentIndex,
          onDestinationSelected: (i) => navigationShell.goBranch(i, initialLocation: i == navigationShell.currentIndex),
          destinations: const [
            NavigationDestination(
              icon: Icon(Icons.home_outlined),
              selectedIcon: Icon(Icons.home_rounded),
              label: 'Inicio',
            ),
            NavigationDestination(
              icon: Icon(Icons.savings_outlined),
              selectedIcon: Icon(Icons.savings_rounded),
              label: 'Cofre',
            ),
            NavigationDestination(
              icon: Icon(Icons.credit_card_outlined),
              selectedIcon: Icon(Icons.credit_card_rounded),
              label: 'Pagar',
            ),
            NavigationDestination(
              icon: Icon(Icons.stars_outlined),
              selectedIcon: Icon(Icons.stars_rounded),
              label: 'Puntos',
            ),
            NavigationDestination(
              icon: Icon(Icons.grid_view_outlined),
              selectedIcon: Icon(Icons.grid_view_rounded),
              label: 'Más',
            ),
          ],
        ),
      ),
    );
  }
}
