import 'package:ahorra_mas_app/fake/fake_api.dart';
import 'package:ahorra_mas_app/features/auth/domain/auth_models.dart';
import 'package:ahorra_mas_app/features/pagos/domain/pagos_models.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:intl/date_symbol_data_local.dart';

import 'helpers.dart';

Future<void> _login(WidgetTester tester) async {
  await tester.enterText(find.byKey(const ValueKey('login-cedula')), '${FakeApi.cedulaDemo}');
  await tester.tap(find.byKey(const ValueKey('login-continuar')));
  await tester.pumpAndSettle();
  for (final d in FakeApi.pinDemo.split('')) {
    await tester.tap(find.byKey(ValueKey('pin-$d')));
  }
  await tester.pump();
  await tester.ensureVisible(find.byKey(const ValueKey('login-entrar')));
  await tester.tap(find.byKey(const ValueKey('login-entrar')));
  await tester.pumpAndSettle();
}

Future<void> _irA(WidgetTester tester, String pestana) async {
  await tester.tap(find.descendant(of: find.byType(NavigationBar), matching: find.text(pestana)));
  await tester.pumpAndSettle();
  _sinErrores(tester, pestana);
}

/// Falla con el nombre del paso si hubo una excepción (p. ej. overflow) en esa pantalla.
void _sinErrores(WidgetTester tester, String paso) {
  final e = tester.takeException();
  if (e != null) fail('Error en "$paso": $e');
}

Future<void> _volver(WidgetTester tester, String paso) async {
  _sinErrores(tester, paso);
  await tester.tap(find.byTooltip('Atrás').last);
  await tester.pumpAndSettle();
}

void main() {
  setUpAll(() => initializeDateFormatting('es'));

  testWidgets('recorrido por todas las pantallas en modo demo (sin errores de layout)', (tester) async {
    usarPantallaTelefono(tester);
    await tester.pumpWidget(appDePrueba(fake: FakeApi(latencia: Duration.zero)));
    await tester.pumpAndSettle();
    await _login(tester);

    // Inicio → vínculos.
    await tester.tap(find.text('Gestionar vínculos'));
    await tester.pumpAndSettle();
    expect(find.text('Vínculos con bancos'), findsOneWidget);
    await _volver(tester, 'vínculos');

    // Inicio → cuenta → movimientos.
    await tester.scrollUntilVisible(find.textContaining('Nómina').first, 300);
    await tester.pumpAndSettle();
    await tester.tap(find.textContaining('Nómina').first);
    await tester.pumpAndSettle();
    expect(find.text('Movimientos'), findsOneWidget);
    await _volver(tester, 'cuenta');

    // Cofre → racha, espejo, nueva meta.
    await _irA(tester, 'Cofre');
    await tester.tap(find.textContaining('días de racha'));
    await tester.pumpAndSettle();
    expect(find.text('Tu racha'), findsOneWidget);
    await tester.scrollUntilVisible(find.textContaining('Qué es un día colchón'), 300);
    expect(find.textContaining('Qué es un día colchón'), findsOneWidget);
    await _volver(tester, 'racha');

    await tester.tap(find.text('Espejo de Impacto'));
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('espejo-frase')), findsOneWidget);
    await tester.scrollUntilVisible(find.textContaining('no constituye asesoría'), 300);
    expect(find.textContaining('no constituye asesoría'), findsOneWidget);
    await _volver(tester, 'espejo');

    await tester.tap(find.text('Nueva meta'));
    await tester.pumpAndSettle();
    expect(find.text('¿Para qué estás ahorrando?'), findsOneWidget);
    await _volver(tester, 'nueva meta');

    // Puntos (3 pestañas).
    await _irA(tester, 'Puntos');
    expect(find.textContaining('Nivel Base'), findsOneWidget);
    await tester.tap(find.text('Recompensas'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Mis canjes'));
    await tester.pumpAndSettle();

    // Más → asistente, notificaciones, ajustes.
    await _irA(tester, 'Más');
    await tester.tap(find.text('Asistente IA'));
    await tester.pumpAndSettle();
    expect(find.textContaining('no asesor'), findsWidgets);
    await tester.tap(find.text('Ver mi recomendación'));
    await tester.pumpAndSettle();
    await tester.scrollUntilVisible(find.text('Perfil conservador'), 300);
    expect(find.text('Perfil conservador'), findsOneWidget);
    await _volver(tester, 'asistente');

    await tester.tap(find.text('Notificaciones'));
    await tester.pumpAndSettle();
    expect(find.text('Tu Ruta del día'), findsOneWidget);
    await _volver(tester, 'notificaciones');

    await tester.tap(find.text('Perfil y seguridad'));
    await tester.pumpAndSettle();
    expect(find.text('Saldo rápido'), findsOneWidget);
    await _volver(tester, 'ajustes');
  });

  testWidgets('pago TDC completo: cotización → beneficiario → PIN + OTP → resultado', (tester) async {
    usarPantallaTelefono(tester);
    final fake = FakeApi(latencia: Duration.zero);
    await tester.pumpWidget(appDePrueba(fake: fake));
    await tester.pumpAndSettle();
    await _login(tester);

    await _irA(tester, 'Pagar');
    expect(find.text('Pagar mis TDC'), findsOneWidget);
    await tester.ensureVisible(find.byKey(const ValueKey('pagar-4')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('pagar-4')));
    await tester.pumpAndSettle();

    // Paso 2: cuenta de origen y monto.
    await tester.tap(find.text('Cuenta de origen (Bs)'));
    await tester.pumpAndSettle();
    await tester.tap(find.textContaining('Nómina').last);
    await tester.pumpAndSettle();
    await tester.enterText(find.widgetWithText(TextFormField, 'Monto a pagar'), '1.000,77');
    await tester.tap(find.text('Ver comisión'));
    await tester.pumpAndSettle();
    expect(find.text('Total a debitar'), findsOneWidget);
    await tester.tap(find.text('Continuar'));
    await tester.pumpAndSettle();

    // Paso 3: beneficiario.
    expect(find.text('MARIA PEREZ'), findsOneWidget);
    await tester.tap(find.byType(CheckboxListTile));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Es correcto'));
    await tester.pumpAndSettle();

    // Paso 4: PIN + OTP (el OTP demo se muestra en un banner de desarrollo).
    await tester.tap(find.byKey(const ValueKey('btn-pagar')));
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('otp-demo')), findsOneWidget);
    await tester.tap(find.text('Usar'));
    await tester.enterText(find.byKey(const ValueKey('stepup-pin')), FakeApi.pinDemo);
    await tester.tap(find.byKey(const ValueKey('stepup-confirmar')));
    await tester.pumpAndSettle();

    // Céntimos 77 ⇒ EN_VERIFICACION en la API falsa ⇒ "en proceso", nunca "fallido".
    expect(find.text('Pago en proceso'), findsOneWidget);
    final pagos = await fake.pagos();
    expect(pagos.first.estado, EstadoPago.enVerificacion);
    expect(pagos.first.montoCts, 100077);
  });

  testWidgets('usuario nuevo sin KYC es llevado a verificación y luego al Inicio', (tester) async {
    usarPantallaTelefono(tester);
    final fake = FakeApi(latencia: Duration.zero);
    await fake.registrar(SolicitudRegistro(
      nacionalidadDoc: 'V',
      cedula: 20111222,
      primerNombre: 'Ana',
      primerApellido: 'Rojas',
      fechaNacimiento: DateTime(1999, 5, 2),
      correo: 'ana@correo.com',
      telefonoMovil: '04241234567',
      pin: '2580',
      dispositivo: const DispositivoInfo(huella: 'h', plataforma: 'Android', modelo: 'X'),
    ));
    await tester.pumpWidget(appDePrueba(fake: fake));
    await tester.pumpAndSettle();

    await tester.enterText(find.byKey(const ValueKey('login-cedula')), '20111222');
    await tester.tap(find.byKey(const ValueKey('login-continuar')));
    await tester.pumpAndSettle();
    for (final d in '2580'.split('')) {
      await tester.tap(find.byKey(ValueKey('pin-$d')));
    }
    await tester.ensureVisible(find.byKey(const ValueKey('login-entrar')));
    await tester.tap(find.byKey(const ValueKey('login-entrar')));
    await tester.pumpAndSettle();

    expect(find.text('Verifica tu identidad'), findsOneWidget);
    await tester.tap(find.text('Imagen de demostración').first);
    await tester.tap(find.text('Imagen de demostración').last);
    await tester.pumpAndSettle();
    await tester.scrollUntilVisible(find.text('Enviar verificación'), 300);
    await tester.tap(find.text('Enviar verificación'));
    await tester.pumpAndSettle();

    expect(find.text('Hola, Ana'), findsOneWidget);
    expect(find.text('Posición global'), findsOneWidget);
    _sinErrores(tester, 'kyc');
  });
}
