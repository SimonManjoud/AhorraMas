import 'dart:convert';
import 'dart:math' as math;

import 'package:ahorra_mas_app/core/money/dinero.dart';
import 'package:ahorra_mas_app/core/storage/almacen_seguro.dart';
import 'package:ahorra_mas_app/fake/fake_api.dart';
import 'package:ahorra_mas_app/features/auth/domain/auth_models.dart';
import 'package:ahorra_mas_app/features/pagos/domain/pagos_models.dart';
import 'package:ahorra_mas_app/features/pagos/presentation/widgets/tarjeta_visual.dart';
import 'package:fl_chart/fl_chart.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:intl/date_symbol_data_local.dart';

import 'helpers.dart';

/// Almacén de un equipo donde María ya ingresó antes (recuerda sus bancos, sin montos).
AlmacenEnMemoria _equipoConocido({bool saldoRapido = false}) => AlmacenEnMemoria({
      ClavesAlmacen.ultimaCedula: '${FakeApi.cedulaDemo}',
      ClavesAlmacen.ultimaNacionalidad: 'V',
      ClavesAlmacen.recuerdoEquipo: jsonEncode({
        'nacionalidad': 'V',
        'cedula': FakeApi.cedulaDemo,
        'primerNombre': 'María',
        'bancos': [
          {'nombre': 'Banco Ávila', 'tipo': 'banco'},
          {'nombre': 'Banco Orinoco', 'tipo': 'banco'},
          {'nombre': 'CriptoAndes Exchange', 'tipo': 'plataforma_cripto'},
        ],
      }),
      if (saldoRapido) ClavesAlmacen.tokenSaldo: 'saldo-demo-1',
    });

void main() {
  setUpAll(() => initializeDateFormatting('es'));

  testWidgets('login: la cédula va primero; luego "Mis bancos" despliega los bancos sin montos', (tester) async {
    usarPantallaTelefono(tester);
    await tester.pumpWidget(appDePrueba(fake: FakeApi(latencia: Duration.zero), almacen: _equipoConocido()));
    await tester.pumpAndSettle();

    // Paso 1: solo cédula, sin teclado de PIN.
    expect(find.byKey(const ValueKey('login-cedula')), findsOneWidget);
    expect(find.byKey(const ValueKey('pin-1')), findsNothing);
    await tester.tap(find.byKey(const ValueKey('login-continuar')));
    await tester.pumpAndSettle();

    // Paso 2: saludo, desplegable de bancos y PIN.
    expect(find.text('Hola de nuevo, María'), findsOneWidget);
    expect(find.text('Mis bancos (3)'), findsOneWidget);
    expect(find.byKey(const ValueKey('pin-1')), findsOneWidget);
    expect(find.text('Banco Ávila'), findsNothing);

    await tester.tap(find.byKey(const ValueKey('mis-bancos')));
    await tester.pumpAndSettle();
    expect(find.text('Banco Ávila'), findsOneWidget);
    expect(find.text('CriptoAndes Exchange'), findsOneWidget);
    expect(find.text('••••'), findsNWidgets(3));
    expect(find.textContaining('activa Saldo rápido'), findsOneWidget);

    // Volver permite cambiar la cédula.
    await tester.tap(find.byKey(const ValueKey('login-volver')));
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('login-cedula')), findsOneWidget);
  });

  testWidgets('login con saldo rápido activo muestra el monto de cada banco antes del PIN', (tester) async {
    usarPantallaTelefono(tester);
    final fake = FakeApi(latencia: Duration.zero);
    // Montos esperados: los mismos que ve María al entrar.
    await fake.login(
      nacionalidadDoc: 'V',
      cedula: FakeApi.cedulaDemo,
      pin: FakeApi.pinDemo,
      dispositivo: const DispositivoInfo(huella: 'prueba', plataforma: 'Android', modelo: 'X'),
    );
    final consolidado = await fake.consolidado();
    await tester.pumpWidget(appDePrueba(fake: fake, almacen: _equipoConocido(saldoRapido: true)));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('login-continuar')));
    await tester.pumpAndSettle();

    expect(find.textContaining('Saldo rápido activo'), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('mis-bancos')));
    await tester.pumpAndSettle();

    for (final i in consolidado.instituciones) {
      expect(find.text(i.nombre), findsOneWidget);
      expect(find.text(Dinero.usd(i.totalUsdCts)), findsOneWidget);
    }
    expect(find.text(Dinero.usd(consolidado.totalUsdCts)), findsOneWidget);
  });

  testWidgets('otra cédula no ve los bancos recordados de este equipo', (tester) async {
    usarPantallaTelefono(tester);
    await tester.pumpWidget(appDePrueba(fake: FakeApi(latencia: Duration.zero), almacen: _equipoConocido(saldoRapido: true)));
    await tester.pumpAndSettle();
    await tester.enterText(find.byKey(const ValueKey('login-cedula')), '20111222');
    await tester.tap(find.byKey(const ValueKey('login-continuar')));
    await tester.pumpAndSettle();

    expect(find.text('Ingresa tu PIN'), findsOneWidget);
    expect(find.text('Mis bancos'), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('mis-bancos')));
    await tester.pumpAndSettle();
    expect(find.text('Banco Ávila'), findsNothing);
    expect(find.textContaining('Aún no conocemos tus bancos'), findsOneWidget);
  });

  testWidgets('Inicio: tocar una porción de la dona dice qué cuenta es (banco, tipo y moneda)', (tester) async {
    usarPantallaTelefono(tester);
    final fake = FakeApi(latencia: Duration.zero);
    await tester.pumpWidget(appDePrueba(fake: fake));
    await tester.pumpAndSettle();
    await tester.enterText(find.byKey(const ValueKey('login-cedula')), '${FakeApi.cedulaDemo}');
    await tester.tap(find.byKey(const ValueKey('login-continuar')));
    await tester.pumpAndSettle();
    for (final d in FakeApi.pinDemo.split('')) {
      await tester.tap(find.byKey(ValueKey('pin-$d')));
    }
    await tester.ensureVisible(find.byKey(const ValueKey('login-entrar')));
    await tester.tap(find.byKey(const ValueKey('login-entrar')));
    await tester.pumpAndSettle();

    expect(find.text('Posición global'), findsOneWidget);
    expect(find.textContaining('Tasa BCV'), findsOneWidget);
    final dona = find.byType(PieChart);
    await tester.ensureVisible(dona);
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('detalle-porcion')), findsNothing);

    // La primera porción empieza arriba (−90°) y avanza en sentido horario: tocamos justo después.
    final centro = tester.getCenter(dona);
    const radio = 66 + 18.0;
    const a = -math.pi / 2 + 0.12;
    await tester.tapAt(centro + Offset(math.cos(a) * radio, math.sin(a) * radio));
    await tester.pumpAndSettle();

    final primera = (await fake.consolidado()).instituciones.first;
    final cuenta = primera.cuentas.first;
    expect(find.byKey(const ValueKey('detalle-porcion')), findsOneWidget);
    expect(find.descendant(of: find.byKey(const ValueKey('detalle-porcion')), matching: find.text(primera.nombre)),
        findsOneWidget);
    expect(find.descendant(of: find.byKey(const ValueKey('detalle-porcion')), matching: find.text('Banco')),
        findsOneWidget);
    expect(find.descendant(of: find.byKey(const ValueKey('detalle-porcion')), matching: find.text('Cuenta corriente')),
        findsOneWidget);
    expect(find.text('${cuenta.moneda} · ${cuenta.ultimos4}'), findsOneWidget);

    await tester.tap(find.byTooltip('Cerrar detalle'));
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('detalle-porcion')), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('la tarjeta dibujada enmascara el número, muestra banco y red, y gira al tocarla', (tester) async {
    const tarjeta = Tarjeta(
      idTarjeta: 4,
      idInstitucion: 2,
      banco: 'Banco Orinoco',
      ultimos4: '7781',
      titularConfirmado: 'MARIA PEREZ',
      esPropia: true,
      marca: 'mastercard',
    );
    await tester.pumpWidget(conTema(const SizedBox(width: 340, child: TarjetaVisual(tarjeta: tarjeta))));

    expect(find.text('••••  ••••  ••••  7781'), findsOneWidget);
    expect(find.text('Banco Orinoco'), findsOneWidget);
    expect(find.text('MASTERCARD'), findsOneWidget);
    expect(find.text('CVV ***'), findsNothing);

    await tester.tap(find.byKey(const ValueKey('tarjeta-visual-4')));
    await tester.pumpAndSettle();
    expect(find.text('CVV ***'), findsOneWidget);
    expect(find.textContaining('7781'), findsNothing);
    expect(tester.takeException(), isNull);
  });
}
