import 'package:ahorra_mas_app/core/money/dinero.dart';
import 'package:ahorra_mas_app/core/storage/almacen_seguro.dart';
import 'package:ahorra_mas_app/fake/fake_api.dart';
import 'package:ahorra_mas_app/features/cofre/domain/cofre_models.dart';
import 'package:ahorra_mas_app/features/cofre/presentation/widgets/cofre_widgets.dart';
import 'package:ahorra_mas_app/features/pagos/domain/pagos_models.dart';
import 'package:ahorra_mas_app/features/pagos/presentation/resultado_pago.dart';
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

void main() {
  setUpAll(() => initializeDateFormatting('es'));

  testWidgets('login con PIN → Home muestra la posición global en Bs y USD', (tester) async {
    usarPantallaTelefono(tester);
    final fake = FakeApi(latencia: Duration.zero);
    final almacen = AlmacenEnMemoria();
    await tester.pumpWidget(appDePrueba(fake: fake, almacen: almacen));
    await tester.pumpAndSettle();

    expect(find.text('Hola de nuevo'), findsOneWidget);
    expect(find.textContaining('nunca te pedirá la clave'), findsOneWidget);

    await _login(tester);

    final consolidado = await fake.consolidado();
    expect(find.text('Hola, María'), findsOneWidget);
    expect(find.text('Posición global'), findsOneWidget);
    expect(tester.widget<Text>(find.byKey(const ValueKey('total-bs'))).data, Dinero.bs(consolidado.totalBsCts));
    expect(tester.widget<Text>(find.byKey(const ValueKey('total-usd'))).data, Dinero.usd(consolidado.totalUsdCts));
    expect(find.textContaining('Tasa BCV'), findsOneWidget);
    expect(find.text('Banco Ávila'), findsOneWidget);
    expect(almacen.datos[ClavesAlmacen.ultimaCedula], '${FakeApi.cedulaDemo}');
  });

  testWidgets('Cofre → meta muestra la Ruta del día y "Apartar cuota" aporta', (tester) async {
    usarPantallaTelefono(tester);
    final fake = FakeApi(latencia: Duration.zero);
    await tester.pumpWidget(appDePrueba(fake: fake));
    await tester.pumpAndSettle();
    await _login(tester);

    await tester.tap(find.text('Cofre'));
    await tester.pumpAndSettle();
    expect(find.text('Cofre Protector'), findsOneWidget);

    await tester.tap(find.text('Viaje en marzo').first);
    await tester.pumpAndSettle();

    expect(find.byKey(const ValueKey('ruta-del-dia')), findsOneWidget);
    expect(find.textContaining('Hoy toca apartar'), findsWidgets);

    await tester.ensureVisible(find.byKey(const ValueKey('btn-apartar-cuota')));
    await tester.tap(find.byKey(const ValueKey('btn-apartar-cuota')));
    await tester.pumpAndSettle();

    expect(find.textContaining('¡Apartaste'), findsOneWidget);
    expect(find.text('Cumplida'), findsOneWidget);
    // Deja expirar el SnackBar para no dejar temporizadores pendientes.
    await tester.pump(const Duration(seconds: 5));
    await tester.pumpAndSettle();
  });

  testWidgets('RutaDelDiaCard muestra el mensaje y el botón de un toque', (tester) async {
    var tocado = false;
    const ruta = RutaDelDia(
      idMeta: 1,
      nombreMeta: 'Viaje en marzo',
      fecha: null,
      montoSugeridoUsdCts: 240,
      montoSugeridoBsCts: 119696,
      tasaValorBsX10000: 4987321,
      aportadoHoyUsdCts: 0,
      estatus: 'pendiente',
      periodosRestantes: 131,
      mensaje: 'Hoy toca apartar \$2,40 (Bs 1.196,96) para «Viaje en marzo»',
    );
    await tester.pumpWidget(conTema(RutaDelDiaCard(ruta: ruta, alApartar: () => tocado = true)));

    expect(find.text(ruta.mensaje), findsOneWidget);
    expect(find.text('Pendiente'), findsOneWidget);
    expect(find.textContaining('131 periodos restantes'), findsOneWidget);
    expect(find.text('Apartar \$2,40'), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('btn-apartar-cuota')));
    expect(tocado, isTrue);
  });

  testWidgets('RutaDelDiaCard cumplida no ofrece apartar', (tester) async {
    const ruta = RutaDelDia(
      idMeta: 1,
      nombreMeta: 'Viaje',
      fecha: null,
      montoSugeridoUsdCts: 240,
      montoSugeridoBsCts: 119696,
      tasaValorBsX10000: 4987321,
      aportadoHoyUsdCts: 240,
      estatus: 'cumplida',
      periodosRestantes: 1,
      mensaje: '¡Listo!',
    );
    await tester.pumpWidget(conTema(RutaDelDiaCard(ruta: ruta, alApartar: () {})));
    expect(find.text('Cumplida'), findsOneWidget);
    expect(find.byKey(const ValueKey('btn-apartar-cuota')), findsNothing);
  });

  testWidgets('resultado EN_VERIFICACION se muestra "en proceso", no como fallido', (tester) async {
    usarPantallaTelefono(tester);
    var actualizado = false;
    final pago = Pago(
      idPago: 30,
      estado: EstadoPago.enVerificacion,
      montoCts: 100077,
      comisionCts: 200,
      referenciaSuiche: 'S7B-30',
      fechaHoraCreacion: DateTime(2026, 11, 4, 10, 15),
      historial: [
        CambioEstadoPago(
            secuencia: 1, estadoAnterior: null, estadoNuevo: EstadoPago.pendiente, fechaHora: DateTime(2026, 11, 4), actor: 'usuario'),
        CambioEstadoPago(
            secuencia: 2,
            estadoAnterior: EstadoPago.pendiente,
            estadoNuevo: EstadoPago.enVerificacion,
            fechaHora: DateTime(2026, 11, 4),
            actor: 'suiche'),
      ],
    );
    await tester.pumpWidget(MaterialApp(
      home: Scaffold(body: ResultadoPagoView(pago: pago, alActualizar: () => actualizado = true)),
    ));

    expect(find.text('Pago en proceso'), findsOneWidget);
    expect(find.textContaining('en proceso de verificación'), findsOneWidget);
    expect(find.text('Pago rechazado'), findsNothing);
    expect(find.byIcon(Icons.cancel), findsNothing);
    expect(find.text('En proceso'), findsWidgets); // etiqueta de estado + línea de tiempo
    expect(find.text('Línea de tiempo'), findsOneWidget);

    await tester.scrollUntilVisible(find.text('Actualizar estado'), 200);
    await tester.tap(find.text('Actualizar estado'));
    expect(actualizado, isTrue);
  });

  testWidgets('resultado RECHAZADO muestra el motivo', (tester) async {
    usarPantallaTelefono(tester);
    const pago = Pago(
      idPago: 31,
      estado: EstadoPago.rechazado,
      montoCts: 100,
      comisionCts: 0,
      motivoRechazo: 'Fondos insuficientes en la cuenta de origen.',
    );
    await tester.pumpWidget(const MaterialApp(home: Scaffold(body: ResultadoPagoView(pago: pago))));
    expect(find.text('Pago rechazado'), findsOneWidget);
    expect(find.textContaining('Fondos insuficientes'), findsOneWidget);
    expect(find.text('Actualizar estado'), findsNothing);
  });
}
