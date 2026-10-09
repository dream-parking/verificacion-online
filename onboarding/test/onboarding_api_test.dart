import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

import 'package:onboarding/api/onboarding_api.dart';
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
    expect(await api.submitRequest('abc'), 'SOL-2026-00419');
  });

  test('if the request was already submitted, submitRequest returns its number', () async {
    final api = OnboardingApi(
      baseUrl: 'https://api.test',
      client: MockClient((req) async => req.method == 'POST'
          ? http.Response('{"status":409,"detail":"Onboarding request abc is COMPLETED"}', 409)
          : http.Response('{"number":"SOL-2026-00419","status":"COMPLETED"}', 200)),
    );
    expect(await api.submitRequest('abc'), 'SOL-2026-00419');
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
}
