import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

import 'package:onboarding/api/onboarding_api.dart';
import 'package:onboarding/identity/document_camera.dart';
import 'package:onboarding/identity/identity_document.dart';
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

/// What the API reads from a legible DUI.
const duiReading = '{"status":"READ","attempts":1,"dui":"01234567-8","firstNames":"María José","lastNames":"Pérez López",'
    '"birthDate":"1990-04-12","issueDate":"2021-06-01","expiryDate":"2029-06-01","gender":"F"}';

/// 422 of the API for photos that cannot be read.
String unreadable(String reason, [String? side]) =>
    '{"status":422,"detail":"The identity document photos cannot be read: $reason","reason":"$reason"'
    '${side == null ? '' : ',"side":"$side"'}}';

/// Fake camera: counts the times it was opened and the photos it took.
class FakeCamera implements DocumentCamera {
  FakeCamera({this.denied = false, this.failTimes = 0});

  /// The permission is denied.
  final bool denied;

  /// The first `open()` calls fail without a permission problem.
  int failTimes;
  var opens = 0;
  var photos = 0;
  var disposed = 0;

  @override
  Future<CameraSession> open() async {
    opens++;
    if (denied) throw const CameraUnavailable(permissionDenied: true);
    if (failTimes > 0) {
      failTimes--;
      throw const CameraUnavailable();
    }
    return _FakeSession(this);
  }
}

class _FakeSession implements CameraSession {
  _FakeSession(this._camera);

  final FakeCamera _camera;

  @override
  Widget get preview => const ColoredBox(color: Colors.grey);

  @override
  Future<Uint8List> takePhoto() async => Uint8List.fromList([0xFF, 0xD8, ++_camera.photos]);

  @override
  Future<void> dispose() async => _camera.disposed++;
}

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
    this.identityResponses = const [],
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

  /// Responses of the first `PUT /identity-document` calls, as (HTTP code, body); then 200 with a reading.
  List<(int, String)> identityResponses;

  /// Number the server assigns on submit.
  final String number;

  /// Whether it was already submitted: `GET` of the request answers COMPLETED with [number].
  var submitted = false;
  final calls = <http.Request>[];

  List<http.Request> get starts =>
      calls.where((r) => r.method == 'POST' && r.url.path == '/api/onboarding/requests').toList();
  List<http.Request> get privacyConsents => calls.where((r) => r.url.path.endsWith('/privacy-consent')).toList();
  List<http.Request> get basicData => calls.where((r) => r.url.path.endsWith('/basic-data')).toList();
  List<http.Request> get identityUploads => calls.where((r) => r.url.path.endsWith('/identity-document')).toList();
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
            // A 400 comes like the real API: detail in English and the invalid fields in `errors`.
            final fields = errors.first == 400 ? ',"errors":{"firstNames":"must match \\"\\\\p{L}+\\""}' : '';
            return http.Response(
                '{"status":${errors.first},"detail":"Some fields are invalid: firstNames"$fields}', errors.first);
          }

          if (req.url.path.endsWith('/privacy-consent')) {
            return error(privacyErrors, (r) => privacyErrors = r) ??
                http.Response('{"status":"IN_PROGRESS","completedSteps":1}', 200);
          }
          if (req.url.path.endsWith('/basic-data')) {
            return error(basicDataErrors, (r) => basicDataErrors = r) ??
                http.Response('{"status":"IN_PROGRESS","completedSteps":2}', 200);
          }
          if (req.url.path.endsWith('/identity-document')) {
            if (identityResponses.isNotEmpty) {
              final (status, body) = identityResponses.first;
              identityResponses = identityResponses.sublist(1);
              return http.Response.bytes(utf8.encode(body), status);
            }
            return http.Response.bytes(utf8.encode(duiReading), 200);
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

    final shutter = find.byKey(const Key('shutter'));

    /// Photographs the front and the back of the DUI and moves on from the confirmation placeholder (VDI-78).
    Future<void> passDui(WidgetTester tester) async {
      await tap(tester, shutter);
      await tap(tester, shutter);
      await tapContinue(tester);
    }

    Widget app(FakeApi fake, {DeviceInfoSource? device, DateTime Function()? clock, FakeCamera? camera}) => OnboardingApp(
          home: OnboardingFlow(
            api: fake.api,
            deviceInfo: device ?? FakeDeviceInfo(),
            camera: camera ?? FakeCamera(),
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
      expect(find.text('Paso 1 de 5'), findsOneWidget);
      await tapContinue(tester);
      expect(find.text('Escribe tus nombres.'), findsOneWidget);
      await fillBasicData(tester);
      await tapContinue(tester);
      await passDui(tester);

      // Income.
      expect(find.text('Paso 3 de 5'), findsOneWidget);
      await tap(tester, find.text('Remesas'));
      await tap(tester, find.text('USD 500.01 a 1,000'));
      await tapContinue(tester);

      // Expected activity.
      expect(find.text('Paso 4 de 5'), findsOneWidget);
      await tap(tester, find.text('Ahorro'));
      await tap(tester, find.text('Más de USD 1,000'));
      await tapContinue(tester);

      // Review.
      expect(find.text('Paso 5 de 5'), findsOneWidget);
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
      expect(find.text('Paso 1 de 5'), findsNothing, reason: 'it does not move on without the notice recorded');
      await tapContinue(tester);

      expect(find.text('Paso 1 de 5'), findsOneWidget);
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
      expect(find.text('Paso 1 de 5'), findsOneWidget);

      await tapContinue(tester);
      await passDui(tester);
      expect(find.text('Paso 3 de 5'), findsOneWidget);
      expect(fake.basicData, hasLength(2));
    });

    testWidgets('if the API rejects a field, the message is in Spanish and names it', (tester) async {
      final fake = FakeApi(basicDataErrors: [400]);
      await tester.pumpWidget(app(fake));
      await tap(tester, find.text('EMPEZAR'));
      await acceptNotice(tester);
      await tapContinue(tester);
      await fillBasicData(tester);

      await tapContinue(tester);
      expect(find.text('Revisa estos datos e inténtalo de nuevo: nombres.'), findsOneWidget);
      expect(find.textContaining('Some fields'), findsNothing);
      expect(find.textContaining('firstNames'), findsNothing);
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
      await passDui(tester);
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
        expect(find.text('Paso 1 de 5'), findsOneWidget);
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
        expect(find.text('Paso 1 de 5'), findsOneWidget);
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
        expect(find.text('Paso 1 de 5'), findsOneWidget);
        expect(fake.starts, hasLength(1));
      });

      testWidgets('if the connection fails it shows the error and allows a retry', (tester) async {
        final fake = FakeApi(failStartTimes: 1);
        await tester.pumpWidget(app(fake));
        await tap(tester, find.text('EMPEZAR'));
        await acceptNotice(tester);
        await tapContinue(tester);

        expect(find.text('No pudimos iniciar tu solicitud'), findsOneWidget);
        expect(find.text('Paso 1 de 5'), findsNothing);

        await tap(tester, find.text('REINTENTAR'));
        expect(find.text('Paso 1 de 5'), findsOneWidget);
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
        expect(find.text('Paso 1 de 5'), findsOneWidget);
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
        await passDui(tester);

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
        expect(steps.keys, ['PRIVACY_NOTICE', 'BASIC_DATA', 'IDENTITY_DOCUMENT', 'INCOME', 'EXPECTED_ACTIVITY', 'REVIEW']);
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

    group('DUI capture (VDI-77)', () {
      Future<void> goToDui(WidgetTester tester, FakeApi fake, FakeCamera camera) async {
        await tester.pumpWidget(app(fake, camera: camera));
        await tap(tester, find.text('EMPEZAR'));
        await acceptNotice(tester);
        await tapContinue(tester);
        await fillBasicData(tester);
        await tapContinue(tester);
      }

      /// Takes the photos of the front and the back, which sends them to the API.
      Future<void> takeBoth(WidgetTester tester) async {
        await tap(tester, shutter);
        await tap(tester, shutter);
      }

      testWidgets('after the basic data it asks for the front and then the back, inside a frame with tips',
          (tester) async {
        final camera = FakeCamera();
        await goToDui(tester, FakeApi(), camera);

        expect(find.text('Paso 2 de 5'), findsOneWidget);
        expect(find.text('Coloca el frente de tu DUI dentro del marco'), findsOneWidget);
        for (final tip in ['Buena luz', 'Sin reflejos', 'Se ven las 4 esquinas']) {
          expect(find.text(tip), findsOneWidget);
        }
        expect(find.text('1 · Frente'), findsOneWidget);
        expect(find.text('2 · Reverso'), findsOneWidget);
        expect(find.text('CONTINUAR'), findsNothing, reason: 'the shutter is the only action while the camera is open');

        await tap(tester, shutter);
        expect(find.text('Ahora coloca el reverso de tu DUI dentro del marco'), findsOneWidget);
        expect(camera.photos, 1);
      });

      testWidgets('nothing is sent until both photos are taken', (tester) async {
        final fake = FakeApi();
        await goToDui(tester, fake, FakeCamera());
        await tap(tester, shutter);
        expect(fake.identityUploads, isEmpty);
      });

      testWidgets('both photos go together in one request and the reading is shown', (tester) async {
        final fake = FakeApi();
        await goToDui(tester, fake, FakeCamera());
        await takeBoth(tester);

        expect(fake.identityUploads, hasLength(1));
        final upload = fake.identityUploads.single;
        expect(upload.method, 'PUT');
        expect(upload.url.path, endsWith('/identity-document'));
        expect(upload.headers['content-type'], startsWith('multipart/form-data'));
        final body = latin1.decode(upload.bodyBytes);
        expect(body, allOf(contains('name="front"'), contains('name="back"')));

        expect(find.text('¿Leímos bien tus datos?'), findsOneWidget);
        expect(find.text('DUI: 01234567-8'), findsOneWidget);
        expect(find.text('Nombres: María José'), findsOneWidget);
        expect(find.text('Apellidos: Pérez López'), findsOneWidget);
        expect(find.text('Nacimiento: 1990-04-12'), findsOneWidget);
        expect(find.text('Vencimiento: 2029-06-01'), findsOneWidget);
      });

      testWidgets('a field the reader did not read clearly says so', (tester) async {
        final fake = FakeApi(identityResponses: [
          (200, '{"status":"READ","attempts":1,"dui":"01234567-8","firstNames":null,"lastNames":"Pérez"}'),
        ]);
        await goToDui(tester, fake, FakeCamera());
        await takeBoth(tester);
        expect(find.text('Nombres: No se leyó'), findsOneWidget);
        expect(find.text('Nacimiento: No se leyó'), findsOneWidget);
      });

      testWidgets('if the reader is unavailable the person is told they will type their data', (tester) async {
        final fake = FakeApi(identityResponses: [(200, '{"status":"FAILED","attempts":1}')]);
        await goToDui(tester, fake, FakeCamera());
        await takeBoth(tester);
        expect(find.text('No pudimos leer tus datos. Los escribirás en el siguiente paso.'), findsOneWidget);
      });

      testWidgets('an unreadable front says what happened, shows how to improve it and does not let it advance',
          (tester) async {
        final fake = FakeApi(identityResponses: [(422, unreadable('GLARE', 'FRONT'))]);
        await goToDui(tester, fake, FakeCamera());
        await takeBoth(tester);

        expect(find.text('Revisa la foto del frente'), findsOneWidget);
        expect(find.text('No pudimos leer tu DUI'), findsOneWidget);
        expect(find.text('Hay un reflejo sobre tus datos. Toma otra foto para poder seguir.'), findsOneWidget);
        expect(find.text('Para que salga bien'), findsOneWidget);
        for (final tip in UnreadablePhotos.tips) {
          expect(find.text(tip), findsOneWidget);
        }
        expect(find.text('CONTINUAR'), findsNothing);
        expect(find.text('¿Leímos bien tus datos?'), findsNothing);
        expect(find.text('Some fields'), findsNothing);
        expect(find.textContaining('GLARE'), findsNothing, reason: 'the code is never shown');
      });

      testWidgets('after an unreadable front it retakes only the front and sends the new one with the same back',
          (tester) async {
        final fake = FakeApi(identityResponses: [(422, unreadable('GLARE', 'FRONT'))]);
        final camera = FakeCamera();
        await goToDui(tester, fake, camera);
        await takeBoth(tester);
        final firstBody = latin1.decode(fake.identityUploads.single.bodyBytes);

        await tap(tester, find.text('TOMAR OTRA FOTO'));
        expect(find.text('Coloca el frente de tu DUI dentro del marco'), findsOneWidget);
        await tap(tester, shutter);

        expect(fake.identityUploads, hasLength(2), reason: 'the back is not asked for again');
        expect(camera.photos, 3);
        final secondBody = latin1.decode(fake.identityUploads.last.bodyBytes);
        expect(secondBody, isNot(firstBody), reason: 'the front is the new photo');
        expect(find.text('¿Leímos bien tus datos?'), findsOneWidget);
      });

      testWidgets('after an unreadable back it retakes only the back', (tester) async {
        final fake = FakeApi(identityResponses: [(422, unreadable('BLURRY', 'BACK'))]);
        final camera = FakeCamera();
        await goToDui(tester, fake, camera);
        await takeBoth(tester);

        expect(find.text('Revisa la foto del reverso'), findsOneWidget);
        expect(find.text('La foto salió borrosa. Toma otra con el teléfono bien quieto.'), findsOneWidget);
        await tap(tester, find.text('TOMAR OTRA FOTO'));
        expect(find.text('Ahora coloca el reverso de tu DUI dentro del marco'), findsOneWidget);
        await tap(tester, shutter);
        expect(camera.photos, 3);
        expect(find.text('¿Leímos bien tus datos?'), findsOneWidget);
      });

      testWidgets('when both photos fail it asks for both again, front first', (tester) async {
        final fake = FakeApi(identityResponses: [(422, unreadable('WRONG_SIDES', 'BOTH'))]);
        final camera = FakeCamera();
        await goToDui(tester, fake, camera);
        await takeBoth(tester);

        expect(find.text('Revisa las fotos de tu DUI'), findsOneWidget);
        await tap(tester, find.text('TOMAR OTRAS FOTOS'));
        expect(find.text('Coloca el frente de tu DUI dentro del marco'), findsOneWidget);
        await takeBoth(tester);
        expect(camera.photos, 4);
        expect(fake.identityUploads, hasLength(2));
      });

      testWidgets('a 422 without a side asks for both photos', (tester) async {
        final fake = FakeApi(identityResponses: [(422, unreadable('NOT_A_DUI'))]);
        await goToDui(tester, fake, FakeCamera());
        await takeBoth(tester);
        expect(find.text('Revisa las fotos de tu DUI'), findsOneWidget);
        expect(find.text('No parece que sea un DUI. Toma otra foto de tu DUI.'), findsOneWidget);
        expect(find.text('TOMAR OTRAS FOTOS'), findsOneWidget);
      });

      for (final (reason, message) in [
        ('CROPPED', 'No se ven las 4 esquinas de tu DUI. Toma otra foto con todo el documento dentro del marco.'),
        ('TOO_DARK', 'La foto salió muy oscura. Busca un lugar con más luz y toma otra.'),
        ('OTHER', 'No pudimos leer tu DUI. Toma otra foto para poder seguir.'),
      ]) {
        testWidgets('$reason shows its own message', (tester) async {
          final fake = FakeApi(identityResponses: [(422, unreadable(reason, 'FRONT'))]);
          await goToDui(tester, fake, FakeCamera());
          await takeBoth(tester);
          expect(find.text(message), findsOneWidget);
        });
      }

      testWidgets('a photo over 5 MB (413) asks to take it again', (tester) async {
        final fake = FakeApi(identityResponses: [(413, '{"status":413,"detail":"Payload too large"}')]);
        await goToDui(tester, fake, FakeCamera());
        await takeBoth(tester);
        expect(find.text('Una de las fotos pesa demasiado. Toma otra foto.'), findsOneWidget);
        expect(find.textContaining('Payload'), findsNothing);
        expect(find.text('TOMAR OTRAS FOTOS'), findsOneWidget);
      });

      testWidgets('a rejected upload (400) asks to take the photos again', (tester) async {
        final fake = FakeApi(identityResponses: [(400, '{"status":400,"detail":"The photo front is empty"}')]);
        await goToDui(tester, fake, FakeCamera());
        await takeBoth(tester);
        expect(find.text('No pudimos leer tu DUI. Toma otra foto para poder seguir.'), findsOneWidget);
        expect(find.textContaining('empty'), findsNothing);
      });

      testWidgets('without a connection it keeps the photos and the retry does not open the camera again',
          (tester) async {
        final fake = FakeApi(identityResponses: [(503, '{"status":503,"detail":"Service unavailable"}')]);
        final camera = FakeCamera();
        await goToDui(tester, fake, camera);
        await takeBoth(tester);

        expect(find.text('No pudimos revisar tu DUI'), findsOneWidget);
        expect(find.text('Revisa tu conexión a internet e inténtalo de nuevo.'), findsOneWidget);
        expect(find.textContaining('Service unavailable'), findsNothing);

        await tap(tester, find.text('REINTENTAR'));
        expect(fake.identityUploads, hasLength(2));
        expect(camera.photos, 2, reason: 'the photos were kept: no new ones were taken');
        expect(find.text('¿Leímos bien tus datos?'), findsOneWidget);
      });

      testWidgets('if the basic data is missing (409) it explains it', (tester) async {
        final fake = FakeApi(identityResponses: [(409, '{"status":409,"detail":"Basic data is required"}')]);
        await goToDui(tester, fake, FakeCamera());
        await takeBoth(tester);
        expect(
          find.text('No pudimos guardar tu DUI. Revisa que tus datos básicos estén completos e inténtalo de nuevo.'),
          findsOneWidget,
        );
      });

      testWidgets('without permission for the camera it explains how to allow it and lets the person retry',
          (tester) async {
        final camera = FakeCamera(denied: true);
        await goToDui(tester, FakeApi(), camera);

        expect(find.text('Necesitamos tu cámara'), findsOneWidget);
        expect(find.textContaining('Permítelo en los ajustes'), findsOneWidget);
        expect(shutter, findsNothing);

        await tap(tester, find.text('INTENTAR DE NUEVO'));
        expect(camera.opens, 2);
      });

      testWidgets('if the camera fails to open, it retries and then works', (tester) async {
        final camera = FakeCamera(failTimes: 1);
        await goToDui(tester, FakeApi(), camera);
        expect(find.text('No pudimos abrir la cámara. Cierra otras aplicaciones que la estén usando e inténtalo de nuevo.'),
            findsOneWidget);
        await tap(tester, find.text('INTENTAR DE NUEVO'));
        expect(shutter, findsOneWidget);
      });

      testWidgets('going back closes the camera and coming back starts again with the front', (tester) async {
        final camera = FakeCamera();
        await goToDui(tester, FakeApi(), camera);
        await tap(tester, shutter);
        expect(find.text('Ahora coloca el reverso de tu DUI dentro del marco'), findsOneWidget);

        await tap(tester, find.byTooltip('Volver al paso anterior'));
        expect(find.text('Paso 1 de 5'), findsOneWidget);
        expect(camera.disposed, 1);

        await tapContinue(tester);
        expect(find.text('Coloca el frente de tu DUI dentro del marco'), findsOneWidget);
      });

      testWidgets('the request is only reached with the notice accepted: the API gets the request id in the path',
          (tester) async {
        final fake = FakeApi();
        await goToDui(tester, fake, FakeCamera());
        await takeBoth(tester);
        expect(fake.identityUploads.single.url.path,
            '/api/onboarding/requests/11111111-2222-3333-4444-555555555555/identity-document');
      });

      testWidgets('the welcome screen announces the DUI step', (tester) async {
        await tester.pumpWidget(app(FakeApi()));
        expect(find.text('Tu DUI'), findsOneWidget);
        expect(find.text('Una foto del frente y otra del reverso.'), findsOneWidget);
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
        await passDui(tester);
        expect(find.text('Paso 3 de 5'), findsOneWidget);
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
        expect(find.text('¿Por qué lo preguntamos?', findRichText: true), findsNothing);
        expect(find.text('Cuéntanos de dónde vienen tus ingresos.'), findsOneWidget);
        expect(find.text('Paso 3 de 5'), findsOneWidget);

        await tester.enterText(find.byType(TextField), 'Venta de artesanías');
        await tapContinue(tester);
        expect(find.text('Paso 4 de 5'), findsOneWidget);

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
        await passDui(tester);
      }

      testWidgets('on continue it sends the source and range with the catalog codes', (tester) async {
        final fake = FakeApi();
        await goToIncome(tester, fake);
        await tap(tester, find.text('Remesas'));
        await tap(tester, find.text('USD 500.01 a 1,000'));
        expect(fake.incomes, isEmpty, reason: 'nothing is sent until Continuar is tapped');
        await tapContinue(tester);

        expect(find.text('Paso 4 de 5'), findsOneWidget);
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
        expect(find.text('Paso 3 de 5'), findsOneWidget);

        await tap(tester, find.text('REINTENTAR'));
        expect(find.text('Paso 4 de 5'), findsOneWidget);
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
        await passDui(tester);
        await tap(tester, find.text('Salario'));
        await tap(tester, find.text('Hasta USD 500'));
        await tapContinue(tester);
        expect(find.text('Paso 4 de 5'), findsOneWidget);
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

        expect(find.text('Paso 5 de 5'), findsOneWidget);
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
        expect(find.text('Paso 4 de 5'), findsOneWidget);

        await tap(tester, find.text('REINTENTAR'));
        expect(find.text('Paso 5 de 5'), findsOneWidget);
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
