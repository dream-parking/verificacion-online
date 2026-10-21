import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

import 'package:onboarding/api/onboarding_api.dart';
import 'package:onboarding/identity/identity_document.dart';
import 'package:onboarding/signals/device_fingerprint.dart';
import 'package:onboarding/signals/signals.dart';

void main() {
  test('startRequest returns the id', () async {
    final api = OnboardingApi(
      baseUrl: 'https://api.test',
      client: MockClient((req) async {
        expect(req.method, 'POST');
        expect(req.url.toString(), 'https://api.test/api/onboarding/requests');
        return http.Response('{"id":"abc","status":"IN_PROGRESS"}', 201);
      }),
    );
    expect(await api.startRequest(), 'abc');
  });

  test('an HTTP error becomes an ApiException with the detail', () async {
    final api = OnboardingApi(
      baseUrl: 'https://api.test',
      // The backend responds in UTF-8 (application/problem+json).
      client: MockClient(
        (_) async => http.Response.bytes(utf8.encode('{"status":409,"detail":"Ya no está en progreso"}'), 409),
      ),
    );
    expect(
      api.startRequest(),
      throwsA(isA<ApiException>()
          .having((e) => e.status, 'status', 409)
          .having((e) => e.message, 'message', 'Ya no está en progreso')),
    );
  });

  test('a 400 keeps the names of the invalid fields', () async {
    final api = OnboardingApi(
      baseUrl: 'https://api.test',
      client: MockClient((_) async => http.Response(
          '{"status":400,"detail":"Some fields are invalid: firstNames, dui","errors":{"firstNames":"x","dui":"y"}}',
          400)),
    );
    expect(
      api.startRequest(),
      throwsA(isA<ApiException>().having((e) => e.fields, 'fields', ['firstNames', 'dui'])),
    );
  });

  test('without a connection it throws ApiException', () async {
    final api = OnboardingApi(
      baseUrl: 'https://api.test',
      client: MockClient((_) async => throw http.ClientException('no network')),
    );
    expect(api.startRequest(), throwsA(isA<ApiException>()));
  });

  test('sendSignals PUTs the signals JSON', () async {
    late http.Request sent;
    final api = OnboardingApi(
      baseUrl: 'https://api.test',
      client: MockClient((req) async {
        sent = req;
        return http.Response('', 204);
      }),
    );
    final signals = Signals()
      ..device = const DeviceInfo(
        fingerprint: 'd4f1·9a3c·e7b2',
        model: 'iPhone 15',
        operatingSystem: 'iOS 18.1',
        appVersion: '1.0.0+1',
      );
    await api.sendSignals('abc', signals);
    expect(sent.method, 'PUT');
    expect(sent.url.toString(), 'https://api.test/api/onboarding/requests/abc/signals');
    expect(sent.headers['Content-Type'], startsWith('application/json'));
    expect(jsonDecode(sent.body)['deviceFingerprint'], 'd4f1·9a3c·e7b2');
  });

  test('catalogs reads the four lists with code and label', () async {
    final api = OnboardingApi(
      baseUrl: 'https://api.test',
      client: MockClient((req) async {
        expect(req.method, 'GET');
        expect(req.url.path, '/api/catalogs');
        return http.Response.bytes(
          utf8.encode(jsonEncode({
            'incomeSources': [
              {'code': 'PENSION', 'label': 'Pensión'},
            ],
            'incomeRanges': [
              {'code': 'HASTA_500', 'label': 'Hasta USD 500', 'minUsd': null, 'maxUsd': 500.0},
            ],
            'transactionTypes': [],
            'monthlyAmountRanges': [],
          })),
          200,
        );
      }),
    );
    final c = await api.catalogs();
    expect(c.incomeSources.single.value, 'PENSION');
    expect(c.incomeSources.single.label, 'Pensión');
    expect(c.incomeRanges.single.value, 'HASTA_500');
    expect(c.transactionTypes, isEmpty);
  });

  test('declareIncome sends sourceDetail only when there is a detail', () async {
    final bodies = <Object?>[];
    final api = OnboardingApi(
      baseUrl: 'https://api.test',
      client: MockClient((req) async {
        expect(req.method, 'PUT');
        expect(req.url.path, '/api/onboarding/requests/abc/income');
        bodies.add(jsonDecode(req.body));
        return http.Response('', 204);
      }),
    );
    await api.declareIncome('abc', source: 'SALARIO', range: 'HASTA_500');
    await api.declareIncome('abc', source: 'OTRO', range: 'MAS_2500', detail: 'Herencia');
    expect(bodies, [
      {'sourceCode': 'SALARIO', 'rangeCode': 'HASTA_500'},
      {'sourceCode': 'OTRO', 'rangeCode': 'MAS_2500', 'sourceDetail': 'Herencia'},
    ]);
  });

  test('declareExpectedActivity sends the transaction type and the amount range', () async {
    late http.Request sent;
    final api = OnboardingApi(
      baseUrl: 'https://api.test',
      client: MockClient((req) async {
        sent = req;
        return http.Response('{"level":"LOW"}', 200);
      }),
    );
    await api.declareExpectedActivity('abc', transactionType: 'AHORRO', amountRange: 'HASTA_200');
    expect(sent.method, 'PUT');
    expect(sent.url.path, '/api/onboarding/requests/abc/expected-activity');
    expect(jsonDecode(sent.body), {'transactionTypeCode': 'AHORRO', 'monthlyAmountRangeCode': 'HASTA_200'});
  });

  test('acceptPrivacyNotice records the acceptance including signal capture', () async {
    late http.Request sent;
    final api = OnboardingApi(
      baseUrl: 'https://api.test',
      client: MockClient((req) async {
        sent = req;
        return http.Response('{"completedSteps":1}', 200);
      }),
    );
    await api.acceptPrivacyNotice('abc');
    expect(sent.method, 'PUT');
    expect(sent.url.path, '/api/onboarding/requests/abc/privacy-consent');
    expect(jsonDecode(sent.body), {'signalsAccepted': true});
  });

  test('sendBasicData sends first names, last names, DUI and mobile number', () async {
    late http.Request sent;
    final api = OnboardingApi(
      baseUrl: 'https://api.test',
      client: MockClient((req) async {
        sent = req;
        return http.Response('{"completedSteps":2}', 200);
      }),
    );
    await api.sendBasicData('abc', firstNames: 'Ana', lastNames: 'Pérez', dui: '01234567-8', mobilePhone: '7123-4567');
    expect(sent.method, 'PUT');
    expect(sent.url.path, '/api/onboarding/requests/abc/basic-data');
    expect(jsonDecode(utf8.decode(sent.bodyBytes)),
        {'firstNames': 'Ana', 'lastNames': 'Pérez', 'dui': '01234567-8', 'mobilePhone': '7123-4567'});
  });

  test('submitRequest returns the number assigned by the server', () async {
    final api = OnboardingApi(
      baseUrl: 'https://api.test',
      client: MockClient((req) async {
        expect(req.method, 'POST');
        expect(req.url.path, '/api/onboarding/requests/abc/submit');
        return http.Response('{"number":"SOL-2026-00419","status":"COMPLETED"}', 200);
      }),
    );
    final submission = await api.submitRequest('abc');
    expect(submission.number, 'SOL-2026-00419');
    expect(submission.status, SubmissionStatus.completed);
  });

  test('submitRequest says when the request was held for enhanced review (VDI-118)', () async {
    final api = OnboardingApi(
      baseUrl: 'https://api.test',
      client: MockClient((req) async => http.Response('{"number":"SOL-2026-00417","status":"ENHANCED_REVIEW"}', 200)),
    );
    final submission = await api.submitRequest('abc');
    expect(submission.number, 'SOL-2026-00417');
    expect(submission.status, SubmissionStatus.enhancedReview);
  });

  test('a status the app does not know yet is told as received', () {
    expect(SubmissionStatus.fromApi('SOMETHING_NEW'), SubmissionStatus.completed);
    expect(SubmissionStatus.fromApi('IN_PROGRESS'), isNull);
    expect(SubmissionStatus.fromApi('ABANDONED'), isNull);
    expect(SubmissionStatus.fromApi(null), isNull);
  });

  for (final status in ['COMPLETED', 'ENHANCED_REVIEW']) {
    test('if the request was already submitted ($status), submitRequest returns its number and status', () async {
      final api = OnboardingApi(
        baseUrl: 'https://api.test',
        client: MockClient((req) async => req.method == 'POST'
            ? http.Response('{"status":409,"detail":"Onboarding request abc is $status"}', 409)
            : http.Response('{"number":"SOL-2026-00419","status":"$status"}', 200)),
      );
      final submission = await api.submitRequest('abc');
      expect(submission.number, 'SOL-2026-00419');
      expect(submission.status, SubmissionStatus.fromApi(status));
    });
  }

  test('if the request was abandoned, submitRequest fails', () async {
    final api = OnboardingApi(
      baseUrl: 'https://api.test',
      client: MockClient((req) async => req.method == 'POST'
          ? http.Response('{"status":409,"detail":"Onboarding request abc is ABANDONED"}', 409)
          : http.Response('{"number":null,"status":"ABANDONED"}', 200)),
    );
    expect(api.submitRequest('abc'), throwsA(isA<ApiException>()));
  });

  test('if steps are missing, submitRequest fails with the server detail', () async {
    final api = OnboardingApi(
      baseUrl: 'https://api.test',
      client: MockClient((req) async => req.method == 'POST'
          ? http.Response('{"status":409,"detail":"Complete the 4 previous steps before submitting"}', 409)
          : http.Response('{"number":null,"status":"IN_PROGRESS"}', 200)),
    );
    expect(
      api.submitRequest('abc'),
      throwsA(isA<ApiException>().having((e) => e.message, 'message', 'Complete the 4 previous steps before submitting')),
    );
  });

  test('confirmIdentityDocument sends the data the person confirmed, with the dates as year-month-day', () async {
    final api = OnboardingApi(
      baseUrl: 'https://api.test',
      client: MockClient((req) async {
        expect(req.method, 'PUT');
        expect(req.url.toString(), 'https://api.test/api/onboarding/requests/abc/identity-document/confirmation');
        expect(jsonDecode(req.body), {
          'dui': '01234567-8',
          'firstNames': 'María José',
          'lastNames': 'Pérez López',
          'birthDate': '1990-04-12',
          'expiryDate': '2029-06-01',
        });
        return http.Response('{"status":"IN_PROGRESS","completedSteps":3}', 200);
      }),
    );
    await api.confirmIdentityDocument(
      'abc',
      dui: '01234567-8',
      firstNames: 'María José',
      lastNames: 'Pérez López',
      birthDate: DateTime(1990, 4, 12),
      expiryDate: DateTime(2029, 6, 1),
    );
  });

  test('confirmIdentityDocument keeps the rejected fields of a 400', () async {
    final api = OnboardingApi(
      baseUrl: 'https://api.test',
      client: MockClient((_) async => http.Response(
          '{"status":400,"detail":"Some fields are invalid","errors":{"birthDate":"must be a past date"}}', 400)),
    );
    await expectLater(
      api.confirmIdentityDocument('abc',
          dui: '01234567-8',
          firstNames: 'A',
          lastNames: 'B',
          birthDate: DateTime(2999),
          expiryDate: DateTime(3000)),
      throwsA(isA<ApiException>().having((e) => e.fields, 'fields', ['birthDate'])),
    );
  });

  group('identity document (VDI-77)', () {
    final front = Uint8List.fromList([0xFF, 0xD8, 0x01]);
    final back = Uint8List.fromList([0xFF, 0xD8, 0x02]);

    test('sends both photos as multipart and returns what was read', () async {
      late http.BaseRequest sent;
      final api = OnboardingApi(
        baseUrl: 'https://api.test',
        client: MockClient.streaming((req, body) async {
          sent = req;
          final bytes = await body.toBytes();
          expect(
            utf8.decode(bytes, allowMalformed: true),
            allOf(contains('name="front"'), contains('name="back"')),
          );
          return http.StreamedResponse(
            Stream.value(
              utf8.encode(
                '{"status":"READ","attempts":1,"dui":"01234567-8","firstNames":"María José","lastNames":"Pérez López",'
                '"birthDate":"1990-04-12","issueDate":"2021-06-01","expiryDate":"2029-06-01","gender":"F"}',
              ),
            ),
            200,
          );
        }),
      );
      final reading = await api.uploadIdentityDocument(
        'abc',
        front: front,
        back: back,
      );
      expect(sent.method, 'PUT');
      expect(
        sent.url.toString(),
        'https://api.test/api/onboarding/requests/abc/identity-document',
      );
      expect(reading.status, ReadingStatus.read);
      expect(reading.dui, '01234567-8');
      expect(reading.firstNames, 'María José');
      expect(reading.birthDate, DateTime(1990, 4, 12));
      expect(reading.expiryDate, DateTime(2029, 6, 1));
    });

    test('a field that was not read clearly comes as null', () async {
      final api = OnboardingApi(
        baseUrl: 'https://api.test',
        client: MockClient.streaming((req, body) async {
          await body.drain<void>();
          return http.StreamedResponse(
            Stream.value(
              utf8.encode(
                '{"status":"READ","attempts":1,"dui":"01234567-8","firstNames":null}',
              ),
            ),
            200,
          );
        }),
      );
      final reading = await api.uploadIdentityDocument(
        'abc',
        front: front,
        back: back,
      );
      expect(reading.firstNames, isNull);
      expect(reading.lastNames, isNull);
      expect(reading.birthDate, isNull);
    });

    test(
      'if the reader is unavailable the status is FAILED and nothing was read',
      () async {
        final api = OnboardingApi(
          baseUrl: 'https://api.test',
          client: MockClient.streaming((req, body) async {
            await body.drain<void>();
            return http.StreamedResponse(
              Stream.value(utf8.encode('{"status":"FAILED","attempts":1}')),
              200,
            );
          }),
        );
        final reading = await api.uploadIdentityDocument(
          'abc',
          front: front,
          back: back,
        );
        expect(reading.status, ReadingStatus.failed);
        expect(reading.dui, isNull);
      },
    );

    test('a 422 keeps the reason and the side to take again', () async {
      final api = OnboardingApi(
        baseUrl: 'https://api.test',
        client: MockClient.streaming((req, body) async {
          await body.drain<void>();
          return http.StreamedResponse(
            Stream.value(
              utf8.encode(
                '{"status":422,"detail":"The identity document photos cannot be read: GLARE",'
                '"reason":"GLARE","side":"FRONT"}',
              ),
            ),
            422,
          );
        }),
      );
      await expectLater(
        api.uploadIdentityDocument('abc', front: front, back: back),
        throwsA(
          isA<ApiException>()
              .having((e) => e.status, 'status', 422)
              .having((e) => e.reason, 'reason', 'GLARE')
              .having((e) => e.side, 'side', 'FRONT'),
        ),
      );
    });

    test('a 422 without side leaves it null', () async {
      final api = OnboardingApi(
        baseUrl: 'https://api.test',
        client: MockClient.streaming((req, body) async {
          await body.drain<void>();
          return http.StreamedResponse(
            Stream.value(
              utf8.encode('{"status":422,"detail":"x","reason":"OTHER"}'),
            ),
            422,
          );
        }),
      );
      await expectLater(
        api.uploadIdentityDocument('abc', front: front, back: back),
        throwsA(isA<ApiException>().having((e) => e.side, 'side', isNull)),
      );
    });

    test('without a connection it throws ApiException', () async {
      final api = OnboardingApi(
        baseUrl: 'https://api.test',
        client: MockClient.streaming(
          (_, _) async => throw http.ClientException('no network'),
        ),
      );
      expect(
        api.uploadIdentityDocument('abc', front: front, back: back),
        throwsA(isA<ApiException>()),
      );
    });
  });
}
