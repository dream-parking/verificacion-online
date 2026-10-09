import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

import 'package:onboarding/api/onboarding_api.dart';
import 'package:onboarding/main.dart';
import 'package:onboarding/onboarding/application_form.dart';
import 'package:onboarding/onboarding/onboarding_flow.dart';
import 'package:onboarding/onboarding/widgets.dart';
import 'package:onboarding/signals/device_fingerprint.dart';

/// Catalog like the one from the Dev API (VDI-46 and VDI-50).
const devCatalog = {
  'incomeSources': [
    {'code': 'SALARIO', 'label': 'Salario'},
    {'code': 'NEGOCIO_PROPIO', 'label': 'Negocio propio'},
    {'code': 'REMESAS', 'label': 'Remesas'},
    {'code': 'PENSION', 'label': 'Pensión'},
    {'code': 'OTRO', 'label': 'Otro'},
  ],
  'incomeRanges': [
    {'code': 'HASTA_500', 'label': 'Hasta USD 500', 'minUsd': null, 'maxUsd': 500.0},
    {'code': '500_1000', 'label': 'USD 500.01 a 1,000', 'minUsd': 500.01, 'maxUsd': 1000.0},
    {'code': '1000_2500', 'label': 'USD 1,000.01 a 2,500', 'minUsd': 1000.01, 'maxUsd': 2500.0},
    {'code': 'MAS_2500', 'label': 'Más de USD 2,500', 'minUsd': 2500.01, 'maxUsd': null},
  ],
  'transactionTypes': [
    {'code': 'PAGO_SALARIO', 'label': 'Pago de salario'},
    {'code': 'COBROS_NEGOCIO', 'label': 'Cobros de su negocio'},
    {'code': 'REMESAS', 'label': 'Remesas familiares'},
    {'code': 'AHORRO', 'label': 'Ahorro'},
  ],
  'monthlyAmountRanges': [
    {'code': 'HASTA_200', 'label': 'Hasta USD 200', 'minUsd': null, 'maxUsd': 200.0},
    {'code': '200_500', 'label': 'USD 200.01 a 500', 'minUsd': 200.01, 'maxUsd': 500.0},
    {'code': '500_1000', 'label': 'USD 500.01 a 1,000', 'minUsd': 500.01, 'maxUsd': 1000.0},
    {'code': 'MAS_1000', 'label': 'Más de USD 1,000', 'minUsd': 1000.01, 'maxUsd': null},
  ],
};

/// Fake API: answers the catalog and every request step, and records the calls.
class FakeApi {
  FakeApi({
    this.failStartTimes = 0,
    this.failSignalsTimes = 0,
    this.failCatalogsTimes = 0,
    this.privacyErrors = const [],
    this.basicDataErrors = const [],
    this.incomeErrors = const [],
    this.activityErrors = const [],
    this.submitErrors = const [],
    this.number = 'SOL-2026-00419',
  });

  int failStartTimes;
  int failSignalsTimes;
  int failCatalogsTimes;

  /// HTTP codes returned by the first `PUT /privacy-consent` calls (then 200).
  List<int> privacyErrors;

  /// HTTP codes returned by the first `PUT /basic-data` calls (then 200).
  List<int> basicDataErrors;

  /// HTTP codes returned by the first `PUT /income` calls (then 204).
  List<int> incomeErrors;

  /// HTTP codes returned by the first `PUT /expected-activity` calls (then 200 with the score).
  List<int> activityErrors;

  /// HTTP codes returned by the first `POST /submit` calls (then 200 with [number]).
  List<int> submitErrors;

  /// Number the server assigns on submit.
  final String number;

  /// Whether it was already submitted: `GET` of the request answers COMPLETED with [number].
  var submitted = false;
  final calls = <http.Request>[];

  List<http.Request> get starts =>
      calls.where((r) => r.method == 'POST' && r.url.path == '/api/onboarding/requests').toList();
  List<http.Request> get privacyConsents => calls.where((r) => r.url.path.endsWith('/privacy-consent')).toList();
  List<http.Request> get basicData => calls.where((r) => r.url.path.endsWith('/basic-data')).toList();
  List<http.Request> get submits => calls.where((r) => r.url.path.endsWith('/submit')).toList();
  List<http.Request> get incomes => calls.where((r) => r.url.path.endsWith('/income')).toList();
  List<http.Request> get activities => calls.where((r) => r.url.path.endsWith('/expected-activity')).toList();
  List<http.Request> get catalogs => calls.where((r) => r.url.path == '/api/catalogs').toList();
  List<http.Request> get signals => calls.where((r) => r.url.path.endsWith('/signals')).toList();

  OnboardingApi get api => OnboardingApi(
        baseUrl: 'https://api.test',
        client: MockClient((req) async {
          calls.add(req);
          if (req.url.path == '/api/catalogs') {
            if (failCatalogsTimes > 0) {
              failCatalogsTimes--;
              return http.Response('', 503);
            }
            return http.Response.bytes(utf8.encode(jsonEncode(devCatalog)), 200);
          }
          http.Response? error(List<int> errors, void Function(List<int>) remaining) {
            if (errors.isEmpty) return null;
            remaining(errors.sublist(1));
            return http.Response('{"status":${errors.first},"detail":"error"}', errors.first);
          }

          if (req.url.path.endsWith('/privacy-consent')) {
            return error(privacyErrors, (r) => privacyErrors = r) ??
                http.Response('{"status":"IN_PROGRESS","completedSteps":1}', 200);
          }
          if (req.url.path.endsWith('/basic-data')) {
            return error(basicDataErrors, (r) => basicDataErrors = r) ??
                http.Response('{"status":"IN_PROGRESS","completedSteps":2}', 200);
          }
          if (req.url.path.endsWith('/submit')) {
            final failure = error(submitErrors, (r) => submitErrors = r);
            if (failure != null) return failure;
            submitted = true;
            return http.Response('{"number":"$number","status":"COMPLETED","completedSteps":5}', 200);
          }
          if (req.method == 'GET' && req.url.path.startsWith('/api/onboarding/requests/')) {
            return submitted
                ? http.Response('{"number":"$number","status":"COMPLETED"}', 200)
                : http.Response('{"number":null,"status":"IN_PROGRESS"}', 200);
          }
          if (req.url.path.endsWith('/income')) {
            return error(incomeErrors, (r) => incomeErrors = r) ?? http.Response('', 204);
          }
          if (req.url.path.endsWith('/expected-activity')) {
            return error(activityErrors, (r) => activityErrors = r) ??
                http.Response('{"level":"LOW","ruleCode":"R-01"}', 200);
          }
          if (req.url.path.endsWith('/signals')) {
            if (failSignalsTimes > 0) {
              failSignalsTimes--;
              return http.Response('', 503);
            }
            return http.Response('', 204);
          }
          if (failStartTimes > 0) {
            failStartTimes--;
            return http.Response('{"status":503,"detail":"Service unavailable"}', 503);
          }
          return http.Response('{"id":"11111111-2222-3333-4444-555555555555","status":"IN_PROGRESS"}', 201);
        }),
      );
}

/// Fake device: counts how many times it was read.
class FakeDeviceInfo implements DeviceInfoSource {
  var reads = 0;

  @override
  Future<DeviceInfo> read() async {
    reads++;
    return const DeviceInfo(
      fingerprint: 'd4f1·9a3c·e7b2',
      model: 'Google Pixel 9',
      operatingSystem: 'Android 16',
      appVersion: '1.0.0+1',
    );
  }
}

void main() {
  group('validation and formatting', () {
    test('DUI and phone masks', () {
      expect(formatDui('048123775'), '04812377-5');
      expect(formatDui('04a8-12377599'), '04812377-5');
      expect(formatPhone('78452310'), '7845-2310');
      expect(formatPhone('784'), '784');
    });

    test('basic data', () {
      final f = ApplicationForm();
      expect(f.isScreenValid(Screen.basicData), isFalse);
      f
        ..firstNames = 'Marta'
        ..lastNames = 'Rivas'
        ..dui = '04812377-5'
        ..phone = '1845-2310';
      expect(f.messages()['phone'], isNotEmpty, reason: 'the mobile number must start with 6 or 7');
      f.phone = '2245-6789';
      expect(f.messages()['phone'], isNotEmpty, reason: 'numbers starting with 2 are landlines and the API rejects them');
      f.phone = '7845-2310';
      expect(f.isScreenValid(Screen.basicData), isTrue);
    });

    test('expected activity requires a transaction type and an amount range', () {
      final f = ApplicationForm()..transactionType = 'AHORRO';
      expect(f.isScreenValid(Screen.expectedActivity), isFalse);
      expect(f.messages()['amountRange'], 'Elige cuánto dinero moverás al mes.');
      f.amountRange = '200_500';
      expect(f.isScreenValid(Screen.expectedActivity), isTrue);
    });
  });

  group('flow', () {
    setUp(() {
      final binding = TestWidgetsFlutterBinding.ensureInitialized();
      binding.platformDispatcher.views.first
        ..physicalSize = const Size(390, 844)
        ..devicePixelRatio = 1;
    });
    tearDown(() => TestWidgetsFlutterBinding.instance.platformDispatcher.views.first
      ..resetPhysicalSize()
      ..resetDevicePixelRatio());

    Future<void> tap(WidgetTester tester, Finder f) async {
      await tester.ensureVisible(f);
      await tester.pumpAndSettle();
      await tester.tap(f);
      await tester.pumpAndSettle();
    }

    Future<void> tapContinue(WidgetTester tester) => tap(tester, find.text('CONTINUAR'));

    /// Checks the privacy notice and the terms and conditions.
    Future<void> acceptNotice(WidgetTester tester) async {
      await tap(tester, find.byType(Checkbox).at(0));
      await tap(tester, find.byType(Checkbox).at(1));
    }

    /// Types valid basic data in the four fields.
    Future<void> fillBasicData(WidgetTester tester) async {
      final fields = find.byType(TextField);
      await tester.enterText(fields.at(0), 'Marta Alejandra');
      await tester.enterText(fields.at(1), 'Rivas Cruz');
      await tester.enterText(fields.at(2), '048123775');
      await tester.enterText(fields.at(3), '78452310');
      await tester.pumpAndSettle();
    }

    Widget app(FakeApi fake, {DeviceInfoSource? device, DateTime Function()? clock}) => OnboardingApp(
          home: OnboardingFlow(
            api: fake.api,
            deviceInfo: device ?? FakeDeviceInfo(),
            clock: clock ?? DateTime.now,
          ),
        );

    testWidgets('completes the request from start to finish', (tester) async {
      final fake = FakeApi(submitErrors: [503]);
      await tester.pumpWidget(app(fake));
      expect(find.text('Tu cuenta, desde tu teléfono.'), findsOneWidget);

      await tap(tester, find.text('EMPEZAR'));

      // Privacy: does not move on without accepting.
      await tapContinue(tester);
      expect(
        find.text('Para continuar necesitamos que aceptes el aviso de privacidad y los términos y condiciones.'),
        findsOneWidget,
      );
      await acceptNotice(tester);
      await tapContinue(tester);

      // Basic data.
      expect(find.text('Paso 1 de 4'), findsOneWidget);
      await tapContinue(tester);
      expect(find.text('Escribe tus nombres.'), findsOneWidget);
      await fillBasicData(tester);
      await tapContinue(tester);

      // Income.
      expect(find.text('Paso 2 de 4'), findsOneWidget);
      await tap(tester, find.text('Remesas'));
      await tap(tester, find.text('USD 500.01 a 1,000'));
      await tapContinue(tester);

      // Expected activity.
      expect(find.text('Paso 3 de 4'), findsOneWidget);
      await tap(tester, find.text('Ahorro'));
      await tap(tester, find.text('Más de USD 1,000'));
      await tapContinue(tester);

      // Review.
      expect(find.text('Paso 4 de 4'), findsOneWidget);
      expect(find.text('Marta Alejandra Rivas Cruz'), findsOneWidget);
      expect(find.text('DUI 04812377-5 · Cel. 7845-2310'), findsOneWidget);
      expect(find.text('Remesas'), findsOneWidget);
      expect(find.text('USD 500.01 a 1,000 al mes'), findsOneWidget);
      expect(find.text('Ahorro'), findsOneWidget);
      expect(find.text('Más de USD 1,000 al mes'), findsOneWidget);

      // Every step was saved in the API, in the order the submit requires.
      expect(fake.privacyConsents, hasLength(1));
      expect(jsonDecode(fake.basicData.single.body), {
        'firstNames': 'Marta Alejandra',
        'lastNames': 'Rivas Cruz',
        'dui': '04812377-5',
        'mobilePhone': '7845-2310',
      });
      expect(fake.incomes, hasLength(1));
      expect(fake.activities, hasLength(1));

      // The first submit fails (503) and the retry works.
      await tap(tester, find.text('ENVIAR SOLICITUD'));
      expect(find.text('No pudimos enviar tu solicitud'), findsOneWidget);
      await tap(tester, find.text('REINTENTAR'));

      // The number is the one the server assigned, not a sample one.
      expect(fake.submits, hasLength(2));
      expect(find.text('¡Recibimos tu solicitud!'), findsOneWidget);
      expect(find.text('SOL-2026-00419'), findsOneWidget);
      expect(find.text('SOL-2026-00418'), findsNothing);
    });

    testWidgets('if recording the notice fails, the retry reuses the same request', (tester) async {
      final fake = FakeApi(privacyErrors: [503]);
      await tester.pumpWidget(app(fake));
      await tap(tester, find.text('EMPEZAR'));
      await acceptNotice(tester);

      await tapContinue(tester);
      expect(find.text('Paso 1 de 4'), findsNothing, reason: 'it does not move on without the notice recorded');
      await tapContinue(tester);

      expect(find.text('Paso 1 de 4'), findsOneWidget);
      expect(fake.starts, hasLength(1), reason: 'no second request is created');
      expect(fake.privacyConsents, hasLength(2));
    });

    testWidgets('basic data is saved in the API before moving on', (tester) async {
      final fake = FakeApi(basicDataErrors: [503]);
      await tester.pumpWidget(app(fake));
      await tap(tester, find.text('EMPEZAR'));
      await acceptNotice(tester);
      await tapContinue(tester);
      await fillBasicData(tester);

      await tapContinue(tester);
      expect(find.text('No pudimos guardar tus datos'), findsOneWidget);
      expect(find.text('Paso 1 de 4'), findsOneWidget);

      await tapContinue(tester);
      expect(find.text('Paso 2 de 4'), findsOneWidget);
      expect(fake.basicData, hasLength(2));
    });

    testWidgets('if the submit response was lost, it shows the number the request already has', (tester) async {
      // The server saved the submit but the phone did not get the response: the retry answers 409.
      final fake = FakeApi(submitErrors: [409], number: 'SOL-2026-00420')..submitted = true;
      await tester.pumpWidget(app(fake));
      await tap(tester, find.text('EMPEZAR'));
      await acceptNotice(tester);
      await tapContinue(tester);
      await fillBasicData(tester);
      await tapContinue(tester);
      await tap(tester, find.text('Remesas'));
      await tap(tester, find.text('USD 500.01 a 1,000'));
      await tapContinue(tester);
      await tap(tester, find.text('Ahorro'));
      await tap(tester, find.text('Más de USD 1,000'));
      await tapContinue(tester);

      await tap(tester, find.text('ENVIAR SOLICITUD'));
      expect(find.text('¡Recibimos tu solicitud!'), findsOneWidget);
      expect(find.text('SOL-2026-00420'), findsOneWidget);
    });

    testWidgets('the back arrow goes to the previous step', (tester) async {
      await tester.pumpWidget(app(FakeApi()));
      await tap(tester, find.text('EMPEZAR'));
      expect(find.text('Cuidamos tu cuenta desde el primer paso'), findsOneWidget);
      await tap(tester, find.byTooltip('Volver al paso anterior'));
      expect(find.text('Tu cuenta, desde tu teléfono.'), findsOneWidget);
    });

    group('privacy notice (VDI-45)', () {
      testWidgets('does not create the request until the notice is accepted', (tester) async {
        final fake = FakeApi();
        await tester.pumpWidget(app(fake));
        await tap(tester, find.text('EMPEZAR'));
        expect(find.text('Texto provisional · pendiente de revisión legal'), findsNothing);

        await tapContinue(tester);
        expect(fake.starts, isEmpty);

        await acceptNotice(tester);
        await tapContinue(tester);
        expect(fake.starts, hasLength(1));
        expect(fake.starts.single.url.path, '/api/onboarding/requests');
        expect(find.text('Paso 1 de 4'), findsOneWidget);
      });

      testWidgets('without the terms and conditions it does not move on', (tester) async {
        final fake = FakeApi();
        await tester.pumpWidget(app(fake));
        await tap(tester, find.text('EMPEZAR'));
        await tap(tester, find.text('He leído y acepto el aviso de privacidad.'));
        await tapContinue(tester);

        expect(find.text('Para continuar necesitamos que aceptes los términos y condiciones.'), findsOneWidget);
        expect(find.text('Para continuar necesitamos que aceptes el aviso de privacidad.'), findsNothing);
        expect(fake.starts, isEmpty);

        await tap(tester, find.text('He leído y acepto los términos y condiciones.'));
        await tapContinue(tester);
        expect(find.text('Paso 1 de 4'), findsOneWidget);
      });

      testWidgets('there is no demo data link on the basic data screen', (tester) async {
        await tester.pumpWidget(app(FakeApi()));
        await tap(tester, find.text('EMPEZAR'));
        await acceptNotice(tester);
        await tapContinue(tester);
        expect(find.textContaining('datos de ejemplo'), findsNothing);
      });

      testWidgets('when coming back, the notice stays accepted and no other request is created', (tester) async {
        final fake = FakeApi();
        await tester.pumpWidget(app(fake));
        await tap(tester, find.text('EMPEZAR'));
        await acceptNotice(tester);
        await tapContinue(tester);

        await tap(tester, find.byTooltip('Volver al paso anterior'));
        for (final checkbox in tester.widgetList<Checkbox>(find.byType(Checkbox))) {
          expect(checkbox.value, isTrue);
          expect(checkbox.onChanged, isNull, reason: 'a recorded consent cannot be withdrawn');
        }

        await tapContinue(tester);
        expect(find.text('Paso 1 de 4'), findsOneWidget);
        expect(fake.starts, hasLength(1));
      });

      testWidgets('if the connection fails it shows the error and allows a retry', (tester) async {
        final fake = FakeApi(failStartTimes: 1);
        await tester.pumpWidget(app(fake));
        await tap(tester, find.text('EMPEZAR'));
        await acceptNotice(tester);
        await tapContinue(tester);

        expect(find.text('No pudimos iniciar tu solicitud'), findsOneWidget);
        expect(find.text('Paso 1 de 4'), findsNothing);

        await tap(tester, find.text('REINTENTAR'));
        expect(find.text('Paso 1 de 4'), findsOneWidget);
        expect(fake.starts, hasLength(2));
      });
    });

    group('device fingerprint (VDI-40)', () {
      testWidgets('does not read the device before the notice is accepted', (tester) async {
        final fake = FakeApi();
        final device = FakeDeviceInfo();
        await tester.pumpWidget(app(fake, device: device));
        await tap(tester, find.text('EMPEZAR'));
        await tapContinue(tester);
        expect(device.reads, 0);
        expect(fake.signals, isEmpty);
      });

      testWidgets('after accepting it sends the fingerprint, model, system and version', (tester) async {
        final fake = FakeApi();
        await tester.pumpWidget(app(fake));
        await tap(tester, find.text('EMPEZAR'));
        await acceptNotice(tester);
        await tapContinue(tester);

        expect(fake.signals, hasLength(1));
        final req = fake.signals.single;
        expect(req.method, 'PUT');
        expect(req.url.path, '/api/onboarding/requests/11111111-2222-3333-4444-555555555555/signals');
        expect(
          jsonDecode(req.body),
          allOf(
            containsPair('deviceFingerprint', 'd4f1·9a3c·e7b2'),
            containsPair('deviceModel', 'Google Pixel 9'),
            containsPair('operatingSystem', 'Android 16'),
            containsPair('appVersion', '1.0.0+1'),
          ),
        );
      });

      testWidgets('if sending fails it does not block and retries on the next screen', (tester) async {
        final fake = FakeApi(failSignalsTimes: 1);
        await tester.pumpWidget(app(fake));
        await tap(tester, find.text('EMPEZAR'));
        await acceptNotice(tester);
        await tapContinue(tester);
        expect(find.text('Paso 1 de 4'), findsOneWidget);
        expect(fake.signals, hasLength(1));

        await fillBasicData(tester);
        await tapContinue(tester);
        expect(fake.signals, hasLength(2));
      });
    });

    group('interaction patterns (VDI-43)', () {
      testWidgets('sends the typing speed and the timing and attempts of each step', (tester) async {
        var now = DateTime.utc(2026, 10, 7, 10);
        void advance(int ms) => now = now.add(Duration(milliseconds: ms));
        final fake = FakeApi();
        await tester.pumpWidget(app(fake, clock: () => now));

        await tap(tester, find.text('EMPEZAR'));
        advance(8000);
        await acceptNotice(tester);
        await tapContinue(tester);

        // Basic data: one failed attempt, then typed letter by letter (one keystroke every 200 ms).
        advance(1000);
        await tapContinue(tester);
        Future<void> typeInto(int field, String text) async {
          for (var i = 1; i <= text.length; i++) {
            advance(200);
            await tester.enterText(find.byType(TextField).at(field), text.substring(0, i));
          }
          advance(2500); // pause between fields: does not count as typing time
        }

        await typeInto(0, 'Marta Alejandra');
        await typeInto(1, 'Rivas Cruz');
        await typeInto(2, '048123775');
        await typeInto(3, '78452310');
        await tapContinue(tester);

        await tap(tester, find.text('Remesas'));
        await tap(tester, find.text('USD 500.01 a 1,000'));
        advance(4000);
        await tapContinue(tester);

        await tap(tester, find.text('Ahorro'));
        await tap(tester, find.text('USD 200.01 a 500'));
        await tapContinue(tester);

        advance(3000);
        await tap(tester, find.text('ENVIAR SOLICITUD'));
        expect(find.text('¡Recibimos tu solicitud!'), findsOneWidget);
        await tester.pumpAndSettle();

        final last = jsonDecode(fake.signals.last.body) as Map<String, dynamic>;
        expect(last['deviceFingerprint'], 'd4f1·9a3c·e7b2', reason: 'the fingerprint is still sent (VDI-40)');
        expect(last['typingSpeedCpm'], 300);

        final steps = {for (final s in last['steps'] as List) s['step']: s};
        expect(steps.keys, ['PRIVACY_NOTICE', 'BASIC_DATA', 'INCOME', 'EXPECTED_ACTIVITY', 'REVIEW']);
        for (final s in steps.values) {
          expect(s['completedAt'], isNotNull, reason: '${s['step']} completed');
        }
        expect(steps['PRIVACY_NOTICE']['startedAt'], '2026-10-07T10:00:00.000Z');
        expect(steps['PRIVACY_NOTICE']['completedAt'], '2026-10-07T10:00:08.000Z');
        expect(steps['BASIC_DATA']['attempts'], 2);
        expect(steps['INCOME']['attempts'], 1);
      });

      testWidgets('measures nothing that is sent before the notice is accepted', (tester) async {
        final fake = FakeApi();
        await tester.pumpWidget(app(fake));
        await tap(tester, find.text('EMPEZAR'));
        await tapContinue(tester);
        await tapContinue(tester);
        expect(fake.calls.where((r) => r.url.path != '/api/catalogs'), isEmpty);
      });
    });

    group('income screen (VDI-48)', () {
      Future<void> goToIncome(WidgetTester tester, FakeApi fake) async {
        await tester.pumpWidget(app(fake));
        await tap(tester, find.text('EMPEZAR'));
        await acceptNotice(tester);
        await tapContinue(tester);
        await fillBasicData(tester);
        await tapContinue(tester);
        expect(find.text('Paso 2 de 4'), findsOneWidget);
      }

      testWidgets('shows only the options of the UIF catalog', (tester) async {
        final fake = FakeApi();
        await goToIncome(tester, fake);
        for (final text in [
          'Salario', 'Negocio propio', 'Remesas', 'Pensión', 'Otro', //
          'Hasta USD 500', 'USD 500.01 a 1,000', 'USD 1,000.01 a 2,500', 'Más de USD 2,500',
        ]) {
          expect(find.text(text), findsOneWidget, reason: text);
        }
        expect(find.byType(OptionCard), findsNWidgets(9));
        expect(find.text('USD 500 a 1,500'), findsNothing, reason: 'the hardcoded ranges are no longer used');
        expect(fake.catalogs, hasLength(1));
      });

      testWidgets('"Otro" asks for the detail and shows it in the review', (tester) async {
        await goToIncome(tester, FakeApi());
        await tap(tester, find.text('Otro'));
        await tap(tester, find.text('Hasta USD 500'));
        await tapContinue(tester);
        expect(find.text('Cuéntanos de dónde vienen tus ingresos.'), findsOneWidget);
        expect(find.text('Paso 2 de 4'), findsOneWidget);

        await tester.enterText(find.byType(TextField), 'Venta de artesanías');
        await tapContinue(tester);
        expect(find.text('Paso 3 de 4'), findsOneWidget);

        await tap(tester, find.text('Ahorro'));
        await tap(tester, find.text('USD 200.01 a 500'));
        await tapContinue(tester);
        expect(find.text('Otro: Venta de artesanías'), findsOneWidget);
        expect(find.text('Hasta USD 500 al mes'), findsOneWidget);
      });

      testWidgets('if the options do not load it shows the error and allows a retry', (tester) async {
        final fake = FakeApi(failCatalogsTimes: 1);
        await goToIncome(tester, fake);
        expect(find.text('No pudimos cargar las opciones'), findsOneWidget);
        expect(find.byType(OptionCard), findsNothing);

        await tap(tester, find.text('REINTENTAR'));
        expect(find.byType(OptionCard), findsNWidgets(9));
        expect(fake.catalogs, hasLength(2));
      });
    });

    group('saving the income (VDI-47)', () {
      Future<void> goToIncome(WidgetTester tester, FakeApi fake) async {
        await tester.pumpWidget(app(fake));
        await tap(tester, find.text('EMPEZAR'));
        await acceptNotice(tester);
        await tapContinue(tester);
        await fillBasicData(tester);
        await tapContinue(tester);
      }

      testWidgets('on continue it sends the source and range with the catalog codes', (tester) async {
        final fake = FakeApi();
        await goToIncome(tester, fake);
        await tap(tester, find.text('Remesas'));
        await tap(tester, find.text('USD 500.01 a 1,000'));
        expect(fake.incomes, isEmpty, reason: 'nothing is sent until Continuar is tapped');
        await tapContinue(tester);

        expect(find.text('Paso 3 de 4'), findsOneWidget);
        final req = fake.incomes.single;
        expect(req.method, 'PUT');
        expect(req.url.path, '/api/onboarding/requests/11111111-2222-3333-4444-555555555555/income');
        expect(jsonDecode(req.body), {'sourceCode': 'REMESAS', 'rangeCode': '500_1000'});
      });

      testWidgets('with "Otro" it also sends the detail', (tester) async {
        final fake = FakeApi();
        await goToIncome(tester, fake);
        await tap(tester, find.text('Otro'));
        await tap(tester, find.text('Hasta USD 500'));
        await tester.enterText(find.byType(TextField), '  Venta de artesanías ');
        await tapContinue(tester);
        expect(jsonDecode(fake.incomes.single.body), {
          'sourceCode': 'OTRO',
          'rangeCode': 'HASTA_500',
          'sourceDetail': 'Venta de artesanías',
        });
      });

      testWidgets('if it is not saved it does not move on; the retry does', (tester) async {
        final fake = FakeApi(incomeErrors: [503]);
        await goToIncome(tester, fake);
        await tap(tester, find.text('Salario'));
        await tap(tester, find.text('Hasta USD 500'));
        await tapContinue(tester);

        expect(find.text('No pudimos guardar tus ingresos'), findsOneWidget);
        expect(find.text('Paso 2 de 4'), findsOneWidget);

        await tap(tester, find.text('REINTENTAR'));
        expect(find.text('Paso 3 de 4'), findsOneWidget);
        expect(fake.incomes, hasLength(2));
      });

      testWidgets('if the request is no longer in progress it explains it', (tester) async {
        final fake = FakeApi(incomeErrors: [409]);
        await goToIncome(tester, fake);
        await tap(tester, find.text('Salario'));
        await tap(tester, find.text('Hasta USD 500'));
        await tapContinue(tester);
        expect(find.text('Tu solicitud ya fue enviada y no se puede modificar.'), findsOneWidget);
      });
    });

    group('expected activity (VDI-51)', () {
      Future<void> goToExpectedActivity(WidgetTester tester, FakeApi fake) async {
        await tester.pumpWidget(app(fake));
        await tap(tester, find.text('EMPEZAR'));
        await acceptNotice(tester);
        await tapContinue(tester);
        await fillBasicData(tester);
        await tapContinue(tester);
        await tap(tester, find.text('Salario'));
        await tap(tester, find.text('Hasta USD 500'));
        await tapContinue(tester);
        expect(find.text('Paso 3 de 4'), findsOneWidget);
      }

      testWidgets('options come only from the catalog: there is no free text field', (tester) async {
        await goToExpectedActivity(tester, FakeApi());
        for (final text in [
          'Pago de salario', 'Cobros de su negocio', 'Remesas familiares', 'Ahorro', //
          'Hasta USD 200', 'USD 200.01 a 500', 'USD 500.01 a 1,000', 'Más de USD 1,000',
        ]) {
          expect(find.text(text), findsOneWidget, reason: text);
        }
        expect(find.text('Tu empleador te depositará aquí el sueldo.'), findsOneWidget);
        expect(find.byType(OptionCard), findsNWidgets(8));
        expect(find.byType(TextField), findsNothing);
      });

      testWidgets('asks to choose a type and a range before continuing', (tester) async {
        final fake = FakeApi();
        await goToExpectedActivity(tester, fake);
        await tapContinue(tester);
        expect(find.text('Elige qué tipo de dinero manejarás.'), findsOneWidget);
        expect(find.text('Elige cuánto dinero moverás al mes.'), findsOneWidget);
        expect(fake.activities, isEmpty);
      });

      testWidgets('on continue it sends the codes and goes to the review', (tester) async {
        final fake = FakeApi();
        await goToExpectedActivity(tester, fake);
        await tap(tester, find.text('Remesas familiares'));
        await tap(tester, find.text('USD 200.01 a 500'));
        await tapContinue(tester);

        expect(find.text('Paso 4 de 4'), findsOneWidget);
        expect(find.text('Remesas familiares'), findsOneWidget);
        expect(find.text('USD 200.01 a 500 al mes'), findsOneWidget);
        final req = fake.activities.single;
        expect(req.method, 'PUT');
        expect(req.url.path, '/api/onboarding/requests/11111111-2222-3333-4444-555555555555/expected-activity');
        expect(jsonDecode(req.body), {'transactionTypeCode': 'REMESAS', 'monthlyAmountRangeCode': '200_500'});
      });

      testWidgets('if it is not saved it does not move on; the retry does', (tester) async {
        final fake = FakeApi(activityErrors: [503]);
        await goToExpectedActivity(tester, fake);
        await tap(tester, find.text('Ahorro'));
        await tap(tester, find.text('Hasta USD 200'));
        await tapContinue(tester);
        expect(find.text('No pudimos guardar esta información'), findsOneWidget);
        expect(find.text('Paso 3 de 4'), findsOneWidget);

        await tap(tester, find.text('REINTENTAR'));
        expect(find.text('Paso 4 de 4'), findsOneWidget);
        expect(fake.activities, hasLength(2));
      });
    });

    testWidgets('Banco Tangamandapio brand: full logo on welcome and emblem on the steps', (tester) async {
      await tester.pumpWidget(app(FakeApi()));
      expect(find.byType(FullLogo), findsOneWidget);
      expect(find.byType(Logo), findsNothing, reason: 'the brand is not repeated in the header on the welcome screen');
      expect(find.text('Ceiba'), findsNothing);

      await tap(tester, find.text('EMPEZAR'));
      expect(find.byType(Logo), findsOneWidget);
      expect(find.text('TANGAMANDAPIO'), findsOneWidget);
    });
  });
}
