import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

import 'package:onboarding/api/onboarding_api.dart';
import 'package:onboarding/api/resume.dart';
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
