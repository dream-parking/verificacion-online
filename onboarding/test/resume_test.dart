import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

import 'package:onboarding/api/onboarding_api.dart';
import 'package:onboarding/api/resume.dart';
import 'package:onboarding/onboarding/resume_view.dart';
import 'package:onboarding/onboarding/application_form.dart';

void main() {
  const resumable =
      '{"requestId":"r1","state":"RESUMABLE","currentStep":"INCOME","firstName":"Marta","number":null,'
      '"saved":{"basicData":{"firstNames":"Marta Alejandra","lastNames":"Rivas Cruz","dui":"04812377-5","mobilePhone":"7845-2310"},'
      '"identityDocument":{"confirmed":true,"ocrStatus":"READ"},'
      '"income":{"sourceCode":"OTRO","rangeCode":"500_1000","sourceDetail":"Venta de pupusas"},'
      '"expectedActivity":{"transactionTypeCode":"AHORRO","monthlyAmountRangeCode":"200_500","version":1}}}';

  group('ResumeInfo', () {
    test('a resumable request says where it was and what was saved', () {
      final info = ResumeInfo.fromJson(
        jsonDecode(resumable) as Map<String, dynamic>,
      );
      expect(info.resumable, isTrue);
      expect(info.step, Screen.income);
      expect(info.firstName, 'Marta');
      expect(
        [info.firstNames, info.lastNames, info.dui, info.mobilePhone],
        ['Marta Alejandra', 'Rivas Cruz', '04812377-5', '7845-2310'],
      );
      expect(
        [info.incomeSource, info.incomeRange, info.incomeDetail],
        ['OTRO', '500_1000', 'Venta de pupusas'],
      );
      expect([info.transactionType, info.amountRange], ['AHORRO', '200_500']);
    });

    test('it says whether the DUI data was confirmed', () {
      expect(ResumeInfo.fromJson(jsonDecode(resumable) as Map<String, dynamic>).identityConfirmed, isTrue);
      final unconfirmed = ResumeInfo.fromJson({
        'state': 'RESUMABLE',
        'currentStep': 'INCOME',
        'saved': {'identityDocument': {'confirmed': false, 'ocrStatus': 'READ'}},
      });
      expect(unconfirmed.identityConfirmed, isFalse);
      expect(ResumeInfo.fromJson({'state': 'RESUMABLE', 'currentStep': 'INCOME'}).identityConfirmed, isFalse,
          reason: 'without a DUI saved it is not confirmed');
    });

    test('it keeps the steps with their state and the date they were saved', () {
      final info = ResumeInfo.fromJson({
        'state': 'RESUMABLE',
        'currentStep': 'INCOME',
        'steps': [
          {'step': 'PRIVACY_NOTICE', 'state': 'DONE', 'savedAt': '2026-10-08T20:00:00Z'},
          {'step': 'BASIC_DATA', 'state': 'DONE', 'savedAt': '2026-10-08T21:42:00Z'},
          {'step': 'IDENTITY_DOCUMENT', 'state': 'DONE', 'savedAt': '2026-10-08T21:50:00Z'},
          {'step': 'INCOME', 'state': 'CURRENT', 'savedAt': null},
          {'step': 'EXPECTED_ACTIVITY', 'state': 'PENDING', 'savedAt': null},
          {'step': 'REVIEW', 'state': 'PENDING', 'savedAt': null},
        ],
      });
      expect(info.steps.map((s) => s.screen), [Screen.basicData, Screen.identityDocument, Screen.income, Screen.expectedActivity, Screen.review],
          reason: 'the privacy notice is not one of the 5 steps the person sees');
      expect(info.steps.map((s) => s.done), [true, true, false, false, false]);
      expect(info.steps.first.savedAt, DateTime.utc(2026, 10, 8, 21, 42));
      expect(info.steps[2].savedAt, isNull);
    });

    test('without steps the list is empty', () {
      expect(ResumeInfo.fromJson({'state': 'RESUMABLE', 'currentStep': 'INCOME'}).steps, isEmpty);
    });

    test('each API step has its screen; the privacy notice goes on with the basic data', () {
      expect(ResumeInfo.screenOf('PRIVACY_NOTICE'), Screen.basicData);
      expect(ResumeInfo.screenOf('BASIC_DATA'), Screen.basicData);
      expect(ResumeInfo.screenOf('IDENTITY_DOCUMENT'), Screen.identityDocument);
      expect(ResumeInfo.screenOf('INCOME'), Screen.income);
      expect(ResumeInfo.screenOf('EXPECTED_ACTIVITY'), Screen.expectedActivity);
      expect(ResumeInfo.screenOf('REVIEW'), Screen.review);
      expect(ResumeInfo.screenOf('SOMETHING_NEW'), isNull);
      expect(ResumeInfo.screenOf(null), isNull);
    });

    for (final state in [
      'EXPIRED',
      'COMPLETED',
      'ABANDONED',
      'SOMETHING_NEW',
    ]) {
      test('$state cannot be resumed and has no step', () {
        final info = ResumeInfo.fromJson({'requestId': 'r1', 'state': state});
        expect(info.resumable, isFalse);
        expect(info.step, isNull);
        expect(info.dui, isNull);
      });
    }

    test('a step that is not saved yet stays empty', () {
      final info = ResumeInfo.fromJson({
        'state': 'RESUMABLE',
        'currentStep': 'BASIC_DATA',
        'saved': {
          'basicData': {'firstNames': 'Marta'},
        },
      });
      expect(info.firstNames, 'Marta');
      expect(info.incomeSource, isNull);
      expect(info.transactionType, isNull);
    });
  });

  group('formatSavedAt', () {
    test('day, abbreviated month and 12-hour time, as the design shows it', () {
      expect(formatSavedAt(DateTime(2026, 10, 8, 21, 42)), '8 de oct. a las 9:42 p. m.');
      expect(formatSavedAt(DateTime(2026, 1, 15, 9, 5)), '15 de ene. a las 9:05 a. m.');
    });

    test('midnight and noon are 12', () {
      expect(formatSavedAt(DateTime(2026, 3, 1, 0, 7)), '1 de mar. a las 12:07 a. m.');
      expect(formatSavedAt(DateTime(2026, 3, 1, 12, 0)), '1 de mar. a las 12:00 p. m.');
    });
  });

  group('OnboardingApi', () {
    OnboardingApi apiAnswering(
      int status,
      String body,
      List<http.Request> calls,
    ) => OnboardingApi(
      baseUrl: 'https://api.test',
      client: MockClient((req) async {
        calls.add(req);
        return http.Response.bytes(utf8.encode(body), status);
      }),
    );

    test('resume reads the request left halfway', () async {
      final calls = <http.Request>[];
      final info = await apiAnswering(200, resumable, calls).resume('r1');
      expect(calls.single.method, 'GET');
      expect(calls.single.url.path, '/api/onboarding/requests/r1/resume');
      expect(info.step, Screen.income);
    });

    test('resume of a request that does not exist is a 404', () async {
      final api = apiAnswering(
        404,
        '{"status":404,"detail":"Request not found"}',
        [],
      );
      await expectLater(
        api.resume('r1'),
        throwsA(isA<ApiException>().having((e) => e.status, 'status', 404)),
      );
    });

    test('abandon posts to the request', () async {
      final calls = <http.Request>[];
      await apiAnswering(200, '{"status":"ABANDONED"}', calls).abandon('r1');
      expect(calls.single.method, 'POST');
      expect(calls.single.url.path, '/api/onboarding/requests/r1/abandon');
    });

    test(
      'abandon of a request that is no longer in progress is a 409',
      () async {
        final api = apiAnswering(409, '{"status":409,"detail":"closed"}', []);
        await expectLater(
          api.abandon('r1'),
          throwsA(isA<ApiException>().having((e) => e.status, 'status', 409)),
        );
      },
    );
  });
}
