import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../core/theme/app_theme.dart';

/// Navegación inferior: Inicio · Cofre · Pagar · Puntos · Más.
class ShellPrincipal extends StatelessWidget {
  const ShellPrincipal({super.key, required this.navigationShell});

  final StatefulNavigationShell navigationShell;

  @override
  Widget build(BuildContext context) {
    final cofre = AhorraColores.of(context).cofre;
    return Scaffold(
      body: navigationShell,
      bottomNavigationBar: NavigationBar(
        selectedIndex: navigationShell.currentIndex,
        onDestinationSelected: (i) => navigationShell.goBranch(i, initialLocation: i == navigationShell.currentIndex),
        destinations: [
          const NavigationDestination(
            icon: Icon(Icons.account_balance_wallet_outlined),
            selectedIcon: Icon(Icons.account_balance_wallet),
            label: 'Inicio',
          ),
          NavigationDestination(
            icon: Icon(Icons.savings_outlined, color: cofre),
            selectedIcon: Icon(Icons.savings, color: cofre),
            label: 'Cofre',
          ),
          const NavigationDestination(
            icon: Icon(Icons.credit_card_outlined),
            selectedIcon: Icon(Icons.credit_card),
            label: 'Pagar',
          ),
          const NavigationDestination(
            icon: Icon(Icons.stars_outlined),
            selectedIcon: Icon(Icons.stars),
            label: 'Puntos',
          ),
          const NavigationDestination(
            icon: Icon(Icons.menu),
            selectedIcon: Icon(Icons.menu_open),
            label: 'Más',
          ),
        ],
      ),
    );
  }
}
