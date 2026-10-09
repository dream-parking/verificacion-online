import 'package:flutter_test/flutter_test.dart';

import 'package:flutter/services.dart';

import 'package:onboarding/onboarding/application_form.dart';
import 'package:onboarding/onboarding/widgets.dart';

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

  group('income detail of «Otro»', () {
    String detailMessage(String value) => (ApplicationForm()
          ..incomeSource = otherIncomeSource
          ..incomeSourceDetail = value)
        .messages()['incomeSourceDetail']!;

    test('accepts letters, numbers and basic punctuation', () {
      expect(detailMessage('Venta de artesanías (2 tiendas), años 2020-2026'), isEmpty);
    });

    test('rejects emojis and other symbols', () {
      for (final value in ['Venta 😀', 'Venta de usados 🩸', '<b>x</b>', r'Venta $$$', 'Venta #1']) {
        expect(detailMessage(value), 'Usa solo letras, números y los signos . , - / ( ), sin emojis ni otros símbolos.',
            reason: value);
      }
    });

    test('asks for the detail when it is empty or too short', () {
      expect(detailMessage(''), 'Cuéntanos de dónde vienen tus ingresos.');
      expect(detailMessage(' ab '), 'Cuéntanos de dónde vienen tus ingresos.');
    });
  });

  group('masks keep the cursor where the person is typing', () {
    TextEditingValue edit(String text, int cursor, String Function(String) format) => maskFormatter(format)
        .formatEditUpdate(TextEditingValue.empty, TextEditingValue(text: text, selection: TextSelection.collapsed(offset: cursor)));

    test('fixing the first digit of the phone leaves the cursor after it', () {
      // 4947-8497: the first digit was deleted and a 7 typed in its place.
      final v = edit('7947-8497', 1, formatPhone);
      expect(v.text, '7947-8497');
      expect(v.selection.extentOffset, 1);
    });

    test('a digit typed in the middle of the DUI keeps the cursor after it', () {
      final v = edit('1203456789', 3, formatDui);
      expect(v.text, '12034567-8');
      expect(v.selection.extentOffset, 3);
    });

    test('typing at the end skips the dash added by the mask', () {
      final v = edit('123456789', 9, formatDui);
      expect(v.text, '12345678-9');
      expect(v.selection.extentOffset, 10);
    });
  });
}
