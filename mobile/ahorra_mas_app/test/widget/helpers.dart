import 'package:ahorra_mas_app/app/app.dart';
import 'package:ahorra_mas_app/core/config/app_config.dart';
import 'package:ahorra_mas_app/core/di/providers.dart';
import 'package:ahorra_mas_app/core/storage/almacen_seguro.dart';
import 'package:ahorra_mas_app/core/theme/app_theme.dart';
import 'package:ahorra_mas_app/fake/fake_api.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

/// App completa en modo API falsa, sin latencia y con almacén en memoria.
Widget appDePrueba({required FakeApi fake, AlmacenSeguro? almacen}) => ProviderScope(
      retry: (_, _) => null,
      overrides: [
        appConfigProvider.overrideWithValue(const AppConfig(apiBaseUrl: 'http://test/api/v1', fakeApi: true)),
        fakeApiProvider.overrideWithValue(fake),
        almacenSeguroProvider.overrideWithValue(almacen ?? AlmacenEnMemoria()),
      ],
      child: const AhorraApp(),
    );

/// Envuelve un widget suelto con el tema de la app.
Widget conTema(Widget hijo) => MaterialApp(
      theme: AppTheme.claro(),
      home: Scaffold(body: SingleChildScrollView(child: hijo)),
    );

/// Pantalla de teléfono (360×800 lógicos).
void usarPantallaTelefono(WidgetTester tester) {
  tester.view.physicalSize = const Size(1080, 2400);
  tester.view.devicePixelRatio = 3.0;
  addTearDown(tester.view.reset);
}
