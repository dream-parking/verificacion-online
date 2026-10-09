import 'package:flutter_test/flutter_test.dart';

import 'package:onboarding/onboarding/application_form.dart';

void main() {
  String firstNamesMessage(String value) => (ApplicationForm()..firstNames = value).messages()['firstNames']!;
  String lastNamesMessage(String value) => (ApplicationForm()..lastNames = value).messages()['lastNames']!;

  group('first and last names (VDI-75)', () {
    test('accept letters with accents, ñ and spaces', () {
      expect(firstNamesMessage('María José'), isEmpty);
      expect(lastNamesMessage('Núñez Peña'), isEmpty);
    });

    test('reject digits and special characters', () {
      for (final value in ['Ana2', 'Ana+', 'Ana-María', 'Ana.', '<script>', r'Ana$%#', 'O\'Brien', 'Ana 😀']) {
        expect(firstNamesMessage(value), 'Usa solo letras y espacios, sin números ni símbolos.', reason: value);
      }
      expect(lastNamesMessage('Pérez 2#'), 'Usa solo letras y espacios, sin números ni símbolos.');
    });

    test('reject more than 100 characters', () {
      expect(firstNamesMessage('A' * 100), isEmpty);
      expect(firstNamesMessage('A' * 101), 'Usa como máximo 100 caracteres.');
    });

    test('ask for the name when it is empty or too short', () {
      expect(firstNamesMessage('  '), 'Escribe tus nombres.');
      expect(lastNamesMessage('A'), 'Escribe tus apellidos.');
    });
  });

  test('normalizeName trims and leaves one space between words', () {
    expect(normalizeName('  Marta   Alejandra '), 'Marta Alejandra');
    expect((ApplicationForm()
          ..firstNames = ' Marta  Alejandra'
          ..lastNames = 'Rivas   Cruz ')
        .fullName, 'Marta Alejandra Rivas Cruz');
  });
}
