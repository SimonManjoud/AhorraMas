import 'package:ahorra_mas_app/core/money/dinero.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('Dinero.formato', () {
    test('Bs con separadores es_VE', () {
      expect(Dinero.bs(123456), 'Bs 1.234,56');
      expect(Dinero.bs(152030044), 'Bs 1.520.300,44');
      expect(Dinero.bs(5), 'Bs 0,05');
      expect(Dinero.bs(0), 'Bs 0,00');
      expect(Dinero.bs(-1999), '-Bs 19,99');
    });

    test('USD', () {
      expect(Dinero.usd(1234), r'$12,34');
      expect(Dinero.usd(304831), r'$3.048,31');
      expect(Dinero.usd(240), r'$2,40');
      expect(Dinero.usd(100000000), r'$1.000.000,00');
    });

    test('enMoneda según código ISO', () {
      expect(Dinero.enMoneda(1543022, 'VES'), 'Bs 15.430,22');
      expect(Dinero.enMoneda(20050, 'USD'), r'$200,50');
      expect(Dinero.enMoneda(8540, 'USDT'), '85,40 USDT');
    });

    test('tasa, porcentaje y multiplicador', () {
      expect(Dinero.tasa(4987321), '498,73 Bs/USD');
      expect(Dinero.tasa(3013700), '301,37 Bs/USD');
      expect(Dinero.porcentajePb(814), '8,14 %');
      expect(Dinero.porcentajePb(10000), '100,00 %');
      expect(Dinero.multiplicador(125), '1,25x');
      expect(Dinero.multiplicador(150), '1,5x');
      expect(Dinero.multiplicador(100), '1x');
      expect(Dinero.multiplicador(200), '2x');
    });
  });

  group('Dinero.conversion (aritmética entera, half-up)', () {
    test('USD → Bs con tasa BCV × 10⁴', () {
      // $2,40 × 498,7321 = Bs 1.196,95704 → 119696 céntimos
      expect(Dinero.usdABs(240, 4987321), 119696);
      expect(Dinero.usdABs(100, 3013700), 30137);
      expect(Dinero.usdABs(0, 4987321), 0);
    });

    test('Bs → USD', () {
      // Bs 15.430,22 / 498,7321 = $30,9389 → 3094 (coincide con el ejemplo del contrato)
      expect(Dinero.bsAUsd(1543022, 4987321), 3094);
      expect(Dinero.bsAUsd(30137, 3013700), 100);
    });

    test('redondeo half-up con signo', () {
      expect(Dinero.dividirRedondeando(5, 10), 1);
      expect(Dinero.dividirRedondeando(4, 10), 0);
      expect(Dinero.dividirRedondeando(-5, 10), -1);
      expect(Dinero.dividirRedondeando(-4, 10), 0);
    });

    test('montos grandes no pierden precisión (int de 64 bits)', () {
      const usd = 9000000000; // $90 millones en céntimos
      expect(Dinero.usdABs(usd, 4987321), 4488588900000);
    });
  });

  group('Dinero.parsear (sin punto flotante)', () {
    test('decimal con coma o punto', () {
      expect(Dinero.parsear('2,40'), 240);
      expect(Dinero.parsear('2.40'), 240);
      expect(Dinero.parsear('2,4'), 240);
      expect(Dinero.parsear('2.4'), 240);
      expect(Dinero.parsear('0,05'), 5);
      expect(Dinero.parsear(',5'), 50);
    });

    test('enteros', () {
      expect(Dinero.parsear('15'), 1500);
      expect(Dinero.parsear('0'), 0);
      expect(Dinero.parsear('  300 '), 30000);
    });

    test('separadores de miles (es_VE y en_US)', () {
      expect(Dinero.parsear('1.234,56'), 123456);
      expect(Dinero.parsear('1,234.56'), 123456);
      expect(Dinero.parsear('1.234'), 123400);
      expect(Dinero.parsear('1.234.567'), 123456700);
      expect(Dinero.parsear('1.234.567,8'), 123456780);
    });

    test('tolera símbolos de moneda', () {
      expect(Dinero.parsear(r'$ 12,34'), 1234);
      expect(Dinero.parsear('Bs 1.000,77'), 100077);
      expect(Dinero.parsear('BS. 10'), 1000);
      expect(Dinero.parsear('25 USD'), 2500);
    });

    test('rechaza entradas inválidas', () {
      expect(Dinero.parsear(''), isNull);
      expect(Dinero.parsear('abc'), isNull);
      expect(Dinero.parsear('2,345'), 234500); // coma + 3 dígitos = miles
      expect(Dinero.parsear('2,3456'), isNull);
      expect(Dinero.parsear('2.'), isNull);
      expect(Dinero.parsear('1.23.4'), isNull);
      expect(Dinero.parsear('12.34,56,7'), isNull);
      expect(Dinero.parsear('-5'), isNull);
      expect(Dinero.parsear('1,2.3,4'), isNull);
    });

    test('ida y vuelta con paraEditar', () {
      for (final cts in [0, 5, 240, 123456, 99999999]) {
        expect(Dinero.parsear(Dinero.paraEditar(cts)), cts);
      }
    });
  });
}
