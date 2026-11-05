import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/date_symbol_data_local.dart';

import 'app/app.dart';

/// Ahorra+ — app móvil (MVP).
///
/// * Backend real (por defecto): `flutter run --dart-define=API_BASE_URL=http://10.0.2.2:8080/api/v1`
/// * Demo sin backend: `flutter run --dart-define=FAKE_API=true`
Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await initializeDateFormatting('es');
  runApp(
    ProviderScope(
      // Los errores de negocio (403 KYC, 422...) no deben reintentarse solos.
      retry: (_, _) => null,
      child: const AhorraApp(),
    ),
  );
}
