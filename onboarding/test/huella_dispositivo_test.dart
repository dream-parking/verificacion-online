import 'package:flutter_test/flutter_test.dart';

import 'package:onboarding/senales/huella_dispositivo.dart';
import 'package:onboarding/senales/senales.dart';

void main() {
  group('calcularHuella', () {
    test('tiene el formato de la API y cabe en 64 caracteres', () {
      final h = calcularHuella(['android', 'abc', 'google', 'Pixel 9']);
      expect(h, matches(RegExp(r'^[0-9a-f]{4}·[0-9a-f]{4}·[0-9a-f]{4}$')));
      expect(h.length, lessThanOrEqualTo(64));
    });

    test('es estable para el mismo dispositivo', () {
      expect(calcularHuella(['android', 'abc', 'Pixel 9']), calcularHuella(['android', 'abc', 'Pixel 9']));
      expect(calcularHuella(['android', 'abc', 'Pixel 9']), calcularHuella([' ANDROID', 'abc ', 'pixel 9']));
    });

    test('cambia si cambia la instalación o el modelo', () {
      final base = calcularHuella(['android', 'abc', 'Pixel 9']);
      expect(calcularHuella(['android', 'xyz', 'Pixel 9']), isNot(base));
      expect(calcularHuella(['android', 'abc', 'Pixel 8']), isNot(base));
    });
  });

  test('recortar respeta los límites de la API', () {
    expect(recortar('Android 16', 30), 'Android 16');
    expect(recortar('x' * 100, 80), hasLength(80));
  });

  test('Senales sin dispositivo no manda campos; con dispositivo manda los cuatro', () {
    final s = Senales();
    expect(s.toJson(), isEmpty);
    s.dispositivo = const DatosDispositivo(
      huella: 'd4f1·9a3c·e7b2',
      modelo: 'iPhone 15',
      sistemaOperativo: 'iOS 18.1',
      versionApp: '1.0.0+1',
    );
    expect(s.toJson(), {
      'deviceFingerprint': 'd4f1·9a3c·e7b2',
      'deviceModel': 'iPhone 15',
      'operatingSystem': 'iOS 18.1',
      'appVersion': '1.0.0+1',
    });
  });
}
