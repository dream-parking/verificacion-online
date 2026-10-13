import 'package:flutter_test/flutter_test.dart';

import 'package:onboarding/identity/identity_document.dart';
import 'package:onboarding/onboarding/application_form.dart';

/// Data as the confirmation screen shows it once the person confirmed it.
ApplicationForm confirmed() => ApplicationForm()
  ..idDui = '01234567-8'
  ..idFirstNames = 'María José'
  ..idLastNames = 'Pérez López'
  ..idBirthDate = '12/04/1990'
  ..idExpiryDate = '01/06/2029';

void main() {
  group('date mask', () {
    test('adds the slashes while typing and keeps digits only', () {
      expect(formatDate('14'), '14');
      expect(formatDate('143'), '14/3');
      expect(formatDate('1403'), '14/03');
      expect(formatDate('14031'), '14/03/1');
      expect(formatDate('14031991'), '14/03/1991');
      expect(formatDate('14/03/1991'), '14/03/1991');
      expect(formatDate('1a4-0b3 1991'), '14/03/1991');
    });

    test('ignores digits past 8', () {
      expect(formatDate('140319912345'), '14/03/1991');
    });
  });

  group('parsing a date', () {
    test('reads day/month/year', () {
      expect(parseDate('14/03/1991'), DateTime(1991, 3, 14));
      expect(parseDate('29/02/2028'), DateTime(2028, 2, 29));
    });

    test('rejects what is not a real date, with no rollover', () {
      for (final text in ['', '14/03', '14/03/91', '1/3/1991', '31/02/2020', '29/02/2027', '00/01/2020', '10/13/2020', 'ab/cd/efgh']) {
        expect(parseDate(text), isNull, reason: text);
      }
    });

    test('writes the date for the API as year-month-day', () {
      expect(dateForApi(DateTime(1991, 3, 4)), '1991-03-04');
      expect(formatDateOf(DateTime(1991, 3, 4)), '04/03/1991');
    });
  });

  group('validating the confirmed data', () {
    test('valid data has no messages', () {
      final f = confirmed();
      expect(f.isScreenValid(Screen.identityConfirmation), isTrue);
    });

    test('the five fields are the ones of the screen', () {
      expect(ApplicationForm.fieldsOf(Screen.identityConfirmation),
          ['idDui', 'idFirstNames', 'idLastNames', 'idBirthDate', 'idExpiryDate']);
    });

    test('DUI needs the 00000000-0 format', () {
      for (final dui in ['', '1234567-8', '012345678', '0123456a-8', '01234567-88']) {
        expect((confirmed()..idDui = dui).messages()['idDui'], 'Escribe tu DUI con el formato 00000000-0.', reason: dui);
      }
    });

    test('names follow the same rule as the basic data', () {
      expect((confirmed()..idFirstNames = '').messages()['idFirstNames'], 'Escribe tus nombres.');
      expect((confirmed()..idFirstNames = 'Ana2').messages()['idFirstNames'],
          'Usa solo letras y espacios, sin números ni símbolos.');
      expect((confirmed()..idFirstNames = 'Ana 😀').messages()['idFirstNames'],
          'Usa solo letras y espacios, sin números ni símbolos.');
      expect((confirmed()..idFirstNames = 'A' * 101).messages()['idFirstNames'], 'Usa como máximo 100 caracteres.');
      expect((confirmed()..idLastNames = '').messages()['idLastNames'], 'Escribe tus apellidos.');
      expect((confirmed()..idLastNames = 'Pérez 2#').messages()['idLastNames'],
          'Usa solo letras y espacios, sin números ni símbolos.');
    });

    test('birth date must be a real date before today', () {
      const format = 'Escribe tu fecha de nacimiento como día/mes/año, por ejemplo 14/03/1991.';
      expect((confirmed()..idBirthDate = '').messages()['idBirthDate'], format);
      expect((confirmed()..idBirthDate = '31/02/1990').messages()['idBirthDate'], format);
      expect((confirmed()..idBirthDate = '14/03/90').messages()['idBirthDate'], format);
      expect((confirmed()..idBirthDate = '01/01/2999').messages()['idBirthDate'],
          'La fecha de nacimiento debe ser anterior a hoy.');
      final today = formatDateOf(DateTime.now());
      expect((confirmed()..idBirthDate = today).messages()['idBirthDate'],
          'La fecha de nacimiento debe ser anterior a hoy.', reason: 'today is not in the past');
    });

    test('expiry date must be a real date after the birth date', () {
      const format = 'Escribe la fecha de vencimiento como día/mes/año, por ejemplo 22/07/2031.';
      expect((confirmed()..idExpiryDate = '').messages()['idExpiryDate'], format);
      expect((confirmed()..idExpiryDate = '30/02/2029').messages()['idExpiryDate'], format);
      expect((confirmed()..idExpiryDate = '11/04/1990').messages()['idExpiryDate'],
          'La fecha de vencimiento debe ser posterior a la de nacimiento.');
      expect((confirmed()..idExpiryDate = '12/04/1990').messages()['idExpiryDate'],
          'La fecha de vencimiento debe ser posterior a la de nacimiento.');
    });

    test('an expired DUI is rejected, naming the date that was read', () {
      final f = confirmed()..idExpiryDate = '22/07/2020';
      expect(f.messages()['idExpiryDate'],
          'Tu DUI venció el 22/07/2020. Para abrir tu cuenta necesitamos un DUI vigente.');
      expect(f.isScreenValid(Screen.identityConfirmation), isFalse);
    });

    test('a DUI that expires today or later is valid; yesterday is expired', () {
      final now = DateTime.now();
      final today = DateTime(now.year, now.month, now.day);
      expect((confirmed()..idExpiryDate = formatDateOf(today)).messages()['idExpiryDate'], isEmpty);
      expect((confirmed()..idExpiryDate = formatDateOf(today.add(const Duration(days: 1)))).messages()['idExpiryDate'],
          isEmpty);
      final yesterday = formatDateOf(DateTime(today.year, today.month, today.day - 1));
      expect((confirmed()..idExpiryDate = yesterday).messages()['idExpiryDate'], contains('venció el $yesterday'));
    });

    test('a date before the birth date says so, not that the DUI expired', () {
      expect((confirmed()..idExpiryDate = '11/04/1990').messages()['idExpiryDate'],
          'La fecha de vencimiento debe ser posterior a la de nacimiento.');
    });

    test('an invalid birth date does not hide the expiry date message', () {
      final m = (confirmed()
            ..idBirthDate = ''
            ..idExpiryDate = '')
          .messages();
      expect(m['idBirthDate'], isNotEmpty);
      expect(m['idExpiryDate'], isNotEmpty);
    });
  });

  group('prefilling from what was read', () {
    const reading = IdentityReading(
      status: ReadingStatus.read,
      dui: '01234567-8',
      firstNames: 'María José',
      lastNames: 'Pérez López',
      birthDate: null,
      expiryDate: null,
    );

    test('puts every value that was read in its field', () {
      final f = ApplicationForm()
        ..applyReading(IdentityReading(
          status: ReadingStatus.read,
          dui: '01234567-8',
          firstNames: 'María José',
          lastNames: 'Pérez López',
          birthDate: DateTime(1990, 4, 12),
          expiryDate: DateTime(2029, 6, 1),
        ));
      expect(f.idDui, '01234567-8');
      expect(f.idFirstNames, 'María José');
      expect(f.idLastNames, 'Pérez López');
      expect(f.idBirthDate, '12/04/1990');
      expect(f.idExpiryDate, '01/06/2029');
      expect(f.idUnclear, isEmpty);
      expect(f.isScreenValid(Screen.identityConfirmation), isTrue);
    });

    test('a value that was not read stays empty and is marked to review', () {
      final f = ApplicationForm()..applyReading(reading);
      expect(f.idBirthDate, '');
      expect(f.idExpiryDate, '');
      expect(f.idUnclear, {'idBirthDate', 'idExpiryDate'});
      expect(f.isScreenValid(Screen.identityConfirmation), isFalse);
    });

    test('a value that was read badly is also marked to review, but kept so it can be corrected', () {
      final f = ApplicationForm()
        ..applyReading(const IdentityReading(
          status: ReadingStatus.read,
          dui: '0123456-8',
          firstNames: 'M4ria',
          lastNames: 'Pérez',
        ));
      expect(f.idDui, '0123456-8');
      expect(f.idFirstNames, 'M4ria');
      expect(f.idUnclear, containsAll(['idDui', 'idFirstNames', 'idBirthDate', 'idExpiryDate']));
      expect(f.idUnclear, isNot(contains('idLastNames')));
    });

    test('an expired date that was read is not marked as unclear: it was read, the DUI is just expired', () {
      final f = ApplicationForm()
        ..applyReading(IdentityReading(
          status: ReadingStatus.read,
          dui: '01234567-8',
          firstNames: 'María José',
          lastNames: 'Pérez López',
          birthDate: DateTime(1990, 4, 12),
          expiryDate: DateTime(2020, 6, 1),
        ));
      expect(f.idExpiryDate, '01/06/2020');
      expect(f.idUnclear, isEmpty);
      expect(f.isScreenValid(Screen.identityConfirmation), isFalse);
    });

    test('if the reader failed everything is empty and nothing is marked: the person types it all', () {
      final f = ApplicationForm()..applyReading(const IdentityReading(status: ReadingStatus.failed));
      expect(f.idDui, '');
      expect(f.idFirstNames, '');
      expect(f.idUnclear, isEmpty);
      expect(f.readerFailed, isTrue);
    });

    test('correcting a field removes its mark', () {
      final f = ApplicationForm()..applyReading(reading);
      f.markEdited('idBirthDate');
      expect(f.idUnclear, {'idExpiryDate'});
    });

    test('reading again keeps what the person already corrected and fills in the rest', () {
      final f = ApplicationForm()..applyReading(reading);
      f
        ..idFirstNames = 'Maria Jose'
        ..markEdited('idFirstNames');
      f.applyReading(IdentityReading(
        status: ReadingStatus.read,
        dui: '09999999-9',
        firstNames: 'María José',
        lastNames: 'Pérez López',
        birthDate: DateTime(1990, 4, 12),
        expiryDate: DateTime(2029, 6, 1),
      ));
      expect(f.idFirstNames, 'Maria Jose', reason: 'the correction is kept');
      expect(f.idDui, '09999999-9', reason: 'what was not touched takes the new reading');
      expect(f.idBirthDate, '12/04/1990');
      expect(f.idUnclear, isEmpty);
    });

    test('reading again, a value that is still missing keeps what the person had', () {
      final f = ApplicationForm()..applyReading(reading);
      f
        ..idBirthDate = '12/04/1990'
        ..markEdited('idBirthDate');
      f.applyReading(reading);
      expect(f.idBirthDate, '12/04/1990');
    });
  });
}
