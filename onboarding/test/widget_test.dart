import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

import 'package:onboarding/api/onboarding_api.dart';
import 'package:onboarding/identity/document_camera.dart';
import 'package:onboarding/identity/frame_quality.dart';
import 'package:onboarding/onboarding/privacy_notice.dart';
import 'package:onboarding/onboarding/saved_request.dart';
import 'package:onboarding/identity/identity_document.dart';
import 'package:onboarding/main.dart';
import 'package:onboarding/onboarding/application_form.dart';
import 'package:onboarding/onboarding/onboarding_flow.dart';
import 'package:onboarding/onboarding/widgets.dart';
import 'package:onboarding/signals/device_fingerprint.dart';
import 'package:onboarding/signals/device_location.dart';

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
const duiReading = '{"status":"READ","attempts":1,"dui":"04812377-5","firstNames":"Marta Alejandra","lastNames":"Rivas Cruz",'
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

  /// Points (0 to 1) where the person tapped to focus, as the session received them.
  final focusPoints = <Offset>[];

  /// The session that is open now, to push live-image hints into it.
  FakeCameraSession? current;

  /// What the live image says (VDI-77): the camera screen shows it.
  void hint(FrameHint hint) => current!.controller.add(hint);

  @override
  Future<CameraSession> open() async {
    opens++;
    if (denied) throw const CameraUnavailable(permissionDenied: true);
    if (failTimes > 0) {
      failTimes--;
      throw const CameraUnavailable();
    }
    return current = FakeCameraSession(this);
  }
}

class FakeCameraSession implements CameraSession {
  FakeCameraSession(this._camera);

  final FakeCamera _camera;

  final controller = StreamController<FrameHint>.broadcast();

  @override
  Stream<FrameHint> get hints => controller.stream;

  @override
  Widget get preview => const ColoredBox(color: Colors.grey);

  @override
  Future<void> focusAt(Offset point) async => _camera.focusPoints.add(point);

  @override
  Future<Uint8List> takePhoto() async => Uint8List.fromList([0xFF, 0xD8, ++_camera.photos]);

  @override
  Future<void> dispose() async {
    _camera.disposed++;
    await controller.close();
  }
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
    this.confirmationErrors = const [],
    this.resumeResponse,
    this.abandonStatus = 200,
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

  /// HTTP codes returned by the first `PUT /identity-document/confirmation` calls (then 200).
  List<int> confirmationErrors;

  /// Response of `GET /resume` as (HTTP code, body); without it the request does not exist (404).
  (int, String)? resumeResponse;

  /// HTTP code of `POST /abandon`.
  int abandonStatus;

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
  List<http.Request> get identityConfirmations =>
      calls.where((r) => r.url.path.endsWith('/identity-document/confirmation')).toList();
  List<http.Request> get submits => calls.where((r) => r.url.path.endsWith('/submit')).toList();
  List<http.Request> get incomes => calls.where((r) => r.url.path.endsWith('/income')).toList();
  List<http.Request> get activities => calls.where((r) => r.url.path.endsWith('/expected-activity')).toList();
  List<http.Request> get resumes => calls.where((r) => r.url.path.endsWith('/resume')).toList();
  List<http.Request> get abandons => calls.where((r) => r.url.path.endsWith('/abandon')).toList();
  List<http.Request> get catalogs => calls.where((r) => r.url.path == '/api/catalogs').toList();
  List<http.Request> get signals => calls.where((r) => r.url.path.endsWith('/signals')).toList();

  /// Requests that stay waiting until the test releases them, to see the wait messages. Keys: `start`
  /// (create the request), or the last part of the path (`basic-data`, `identity-document`, `submit`...).
  final holds = <String, Completer<void>>{};

  /// Holds the request [name] until [release] is called.
  void hold(String name) => holds[name] = Completer<void>();

  void release(String name) => holds[name]?.complete();

  String _holdName(http.BaseRequest req) =>
      req.method == 'POST' && req.url.path == '/api/onboarding/requests' ? 'start' : req.url.pathSegments.last;

  OnboardingApi get api => OnboardingApi(
        baseUrl: 'https://api.test',
        client: MockClient((req) async {
          calls.add(req);
          await holds[_holdName(req)]?.future;
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
          if (req.url.path.endsWith('/identity-document/confirmation')) {
            return error(confirmationErrors, (r) => confirmationErrors = r) ??
                http.Response('{"status":"IN_PROGRESS","completedSteps":3}', 200);
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
          if (req.url.path.endsWith('/resume')) {
            final (status, body) = resumeResponse ?? (404, '{"status":404,"detail":"Request not found"}');
            return http.Response.bytes(utf8.encode(body), status);
          }
          if (req.url.path.endsWith('/abandon')) {
            return http.Response('{"status":"ABANDONED"}', abandonStatus);
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

/// Id the fake API gives to the requests it creates.
const requestId = '11111111-2222-3333-4444-555555555555';

/// Request id kept in memory, instead of the phone preferences.
class FakeSavedRequestStore implements SavedRequestStore {
  FakeSavedRequestStore([this.id]);

  String? id;

  @override
  Future<String?> read() async => id;

  @override
  Future<void> save(String value) async => id = value;

  @override
  Future<void> clear() async => id = null;
}

/// `GET /resume` of a request left at [step], with what the API saved up to there.
const _savedSteps = ['BASIC_DATA', 'IDENTITY_DOCUMENT', 'INCOME', 'EXPECTED_ACTIVITY', 'REVIEW'];

/// `steps` of `GET /resume`: the ones before [current] done and saved on 8 Oct, [current] on its turn, the rest pending.
String _stepsJson(String current) {
  final at = _savedSteps.indexOf(current);
  return '[{"step":"PRIVACY_NOTICE","state":"DONE","savedAt":"2026-10-08T20:00:00Z"},'
      '${[for (var i = 0; i < _savedSteps.length; i++) '{"step":"${_savedSteps[i]}","state":"${i < at ? 'DONE' : i == at ? 'CURRENT' : 'PENDING'}","savedAt":${i < at ? '"2026-10-08T21:42:00Z"' : 'null'}}'].join(',')}]';
}

String resumeJson(String step, {String state = 'RESUMABLE', bool confirmedDui = true}) =>
    state != 'RESUMABLE'
    ? '{"requestId":"$requestId","state":"$state","firstName":"Marta"}'
    : '{"requestId":"$requestId","state":"RESUMABLE","currentStep":"$step","firstName":"Marta","steps":${_stepsJson(step)},'
          '"saved":{${confirmedDui ? '"identityDocument":{"confirmed":true,"ocrStatus":"READ"},' : ''}"basicData":{"firstNames":"Marta Alejandra","lastNames":"Rivas Cruz","dui":"04812377-5","mobilePhone":"7845-2310"},'
          '"income":{"sourceCode":"SALARIO","rangeCode":"500_1000"},'
          '"expectedActivity":{"transactionTypeCode":"AHORRO","monthlyAmountRangeCode":"200_500"}}}';

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

/// Fake approximate location: answers [result] and counts how many times it was asked.
class FakeLocation implements DeviceLocationSource {
  FakeLocation([this.result = sanSalvador]);

  static const sanSalvador =
      DeviceLocation(DeviceLocationStatus.available, place: 'San Salvador, El Salvador', countryIso: 'SV');

  final DeviceLocation result;
  var reads = 0;

  /// While it is not completed the location is still being looked up.
  Completer<void>? holdUntil;

  @override
  Future<DeviceLocation> read() async {
    reads++;
    await holdUntil?.future;
    return result;
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

    /// Taps the main button of the screen: «Continuar», or «Aceptar y continuar» on the privacy notice.
    Future<void> tapContinue(WidgetTester tester) {
      final accept = find.text('Aceptar y continuar');
      return tap(tester, accept.evaluate().isNotEmpty ? accept : find.text('Continuar'));
    }

    /// Checks the privacy notice and the terms and conditions.
    Future<void> acceptNotice(WidgetTester tester) async {
      await tap(tester, find.byType(Checkbox).at(0));
      await tap(tester, find.byType(Checkbox).at(1));
    }

    /// Location permission screen (VDI-73): "Ahora no".
    Future<void> skipLocation(WidgetTester tester) => tap(tester, find.text('Ahora no'));

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

    /// Photographs the front and the back of the DUI and confirms the data that was read (VDI-77 and VDI-78).
    Future<void> passDui(WidgetTester tester) async {
      await tap(tester, shutter);
      await tap(tester, shutter);
      await tap(tester, find.text('Mis datos son correctos'));
    }

    Widget app(
      FakeApi fake, {
      DeviceInfoSource? device,
      DeviceLocationSource? location,
      DateTime Function()? clock,
      FakeCamera? camera,
      SavedRequestStore? store,
    }) =>
        OnboardingApp(
          home: OnboardingFlow(
            api: fake.api,
            deviceInfo: device ?? FakeDeviceInfo(),
            location: location ?? FakeLocation(),
            camera: camera ?? FakeCamera(),
            savedRequest: store ?? FakeSavedRequestStore(),
            clock: clock ?? DateTime.now,
          ),
        );

    testWidgets('completes the request from start to finish', (tester) async {
      final fake = FakeApi(submitErrors: [503]);
      await tester.pumpWidget(app(fake));
      expect(find.text('Tu cuenta, desde tu teléfono.'), findsOneWidget);

      await tap(tester, find.text('Empezar'));

      // Privacy: does not move on without accepting.
      await tapContinue(tester);
      expect(
        find.text('Para continuar necesitamos que aceptes el aviso de privacidad y los términos y condiciones.'),
        findsOneWidget,
      );
      await acceptNotice(tester);
      await tapContinue(tester);

      // Approximate location: allowed.
      expect(find.text('¿Desde qué ciudad nos escribes?'), findsOneWidget);
      await tap(tester, find.text('Permitir ubicación aproximada'));

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
      expect(find.text('Tu DUI'), findsOneWidget);
      expect(find.text('DUI 04812377-5 · Vence 01/06/2029'), findsOneWidget);
      expect(find.text('Confirmado · foto del frente y del reverso'), findsOneWidget);
      expect(find.text('Editar'), findsNWidgets(3), reason: 'the DUI card is informative: it has no edit link');
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
      await tap(tester, find.text('Enviar solicitud'));
      expect(find.text('No pudimos enviar tu solicitud'), findsOneWidget);
      await tap(tester, find.text('Reintentar'));

      // The number is the one the server assigned, not a sample one.
      expect(fake.submits, hasLength(2));
      expect(find.text('¡Recibimos tu solicitud!'), findsOneWidget);
      expect(find.text('SOL-2026-00419'), findsOneWidget);
      expect(find.text('SOL-2026-00418'), findsNothing);
    });

    testWidgets('names start with a capital letter by default: the keyboard capitalizes each word', (tester) async {
      TextCapitalization capitalization(int field) => tester.widget<TextField>(find.byType(TextField).at(field)).textCapitalization;

      await tester.pumpWidget(app(FakeApi()));
      await tap(tester, find.text('Empezar'));
      await acceptNotice(tester);
      await tapContinue(tester);
      await skipLocation(tester);

      // Basic data: first and last names in capitals; DUI and phone are numbers.
      expect([for (var i = 0; i < 4; i++) capitalization(i)],
          [TextCapitalization.words, TextCapitalization.words, TextCapitalization.none, TextCapitalization.none]);
      await fillBasicData(tester);
      await tapContinue(tester);

      // Confirmation of the DUI data: DUI, first names, last names, birth date, expiry date.
      await tap(tester, shutter);
      await tap(tester, shutter);
      expect([for (var i = 0; i < 5; i++) capitalization(i)], [
        TextCapitalization.none,
        TextCapitalization.words,
        TextCapitalization.words,
        TextCapitalization.none,
        TextCapitalization.none,
      ]);
    });

    testWidgets('the main button is gray until the screen is complete, but it can still be tapped', (tester) async {
      bool ready() => tester.widget<PrimaryButton>(find.byType(PrimaryButton)).ready;

      await tester.pumpWidget(app(FakeApi()));
      expect(ready(), isTrue, reason: 'the welcome has nothing to fill in');
      await tap(tester, find.text('Empezar'));

      // Privacy notice: both boxes.
      expect(ready(), isFalse);
      await tap(tester, find.byType(Checkbox).at(0));
      expect(ready(), isFalse, reason: 'the terms and conditions are missing');
      await tap(tester, find.byType(Checkbox).at(1));
      expect(ready(), isTrue);
      await tapContinue(tester);
      await skipLocation(tester);

      // Basic data: the four fields.
      expect(ready(), isFalse);
      await tester.enterText(find.byType(TextField).at(0), 'Marta Alejandra');
      await tester.pumpAndSettle();
      expect(ready(), isFalse, reason: 'there is still the rest');
      await tapContinue(tester);
      expect(find.text('Escribe tus apellidos.'), findsOneWidget, reason: 'gray, but tapping it says what is missing');
      expect(ready(), isFalse);
      await fillBasicData(tester);
      expect(ready(), isTrue);
      await tapContinue(tester);

      // Confirmation of the DUI data: it comes filled in and goes gray if something is cleared.
      await tap(tester, shutter);
      await tap(tester, shutter);
      expect(ready(), isTrue);
      await tester.enterText(find.byType(TextField).at(0), '');
      await tester.pumpAndSettle();
      expect(ready(), isFalse);
      await tester.enterText(find.byType(TextField).at(0), '048123775');
      await tester.pumpAndSettle();
      expect(ready(), isTrue);
      await tap(tester, find.text('Mis datos son correctos'));

      // Income: source and range.
      expect(ready(), isFalse);
      await tap(tester, find.text('Remesas'));
      expect(ready(), isFalse, reason: 'the range is missing');
      await tap(tester, find.text('USD 500.01 a 1,000'));
      expect(ready(), isTrue);
      await tapContinue(tester);

      // Expected activity: type and range.
      expect(ready(), isFalse);
      await tap(tester, find.text('Ahorro'));
      expect(ready(), isFalse);
      await tap(tester, find.text('Más de USD 1,000'));
      expect(ready(), isTrue);
      await tapContinue(tester);

      // Review: nothing to fill in.
      expect(find.text('Revisa tu solicitud'), findsOneWidget);
      expect(ready(), isTrue);
    });

    testWidgets('if recording the notice fails, the retry reuses the same request', (tester) async {
      final fake = FakeApi(privacyErrors: [503]);
      await tester.pumpWidget(app(fake));
      await tap(tester, find.text('Empezar'));
      await acceptNotice(tester);

      await tapContinue(tester);
      expect(find.text('Paso 1 de 5'), findsNothing, reason: 'it does not move on without the notice recorded');
      await tapContinue(tester);
      await skipLocation(tester);

      expect(find.text('Paso 1 de 5'), findsOneWidget);
      expect(fake.starts, hasLength(1), reason: 'no second request is created');
      expect(fake.privacyConsents, hasLength(2));
    });

    testWidgets('basic data is saved in the API before moving on', (tester) async {
      final fake = FakeApi(basicDataErrors: [503]);
      await tester.pumpWidget(app(fake));
      await tap(tester, find.text('Empezar'));
      await acceptNotice(tester);
      await tapContinue(tester);
      await skipLocation(tester);
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
      await tap(tester, find.text('Empezar'));
      await acceptNotice(tester);
      await tapContinue(tester);
      await skipLocation(tester);
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
      await tap(tester, find.text('Empezar'));
      await acceptNotice(tester);
      await tapContinue(tester);
      await skipLocation(tester);
      await fillBasicData(tester);
      await tapContinue(tester);
      await passDui(tester);
      await tap(tester, find.text('Remesas'));
      await tap(tester, find.text('USD 500.01 a 1,000'));
      await tapContinue(tester);
      await tap(tester, find.text('Ahorro'));
      await tap(tester, find.text('Más de USD 1,000'));
      await tapContinue(tester);

      await tap(tester, find.text('Enviar solicitud'));
      expect(find.text('¡Recibimos tu solicitud!'), findsOneWidget);
      expect(find.text('SOL-2026-00420'), findsOneWidget);
    });

    testWidgets('the back arrow goes to the previous step', (tester) async {
      await tester.pumpWidget(app(FakeApi()));
      await tap(tester, find.text('Empezar'));
      expect(find.text('Cuidamos tu cuenta desde el primer paso'), findsOneWidget);
      await tap(tester, find.byTooltip('Volver al paso anterior'));
      expect(find.text('Tu cuenta, desde tu teléfono.'), findsOneWidget);
    });

    group('privacy notice (VDI-45)', () {
      testWidgets('does not create the request until the notice is accepted', (tester) async {
        final fake = FakeApi();
        await tester.pumpWidget(app(fake));
        await tap(tester, find.text('Empezar'));
        expect(find.text('Texto provisional · pendiente de revisión legal'), findsNothing);

        await tapContinue(tester);
        expect(fake.starts, isEmpty);

        await acceptNotice(tester);
        await tapContinue(tester);
        await skipLocation(tester);
        expect(fake.starts, hasLength(1));
        expect(fake.starts.single.url.path, '/api/onboarding/requests');
        expect(find.text('Paso 1 de 5'), findsOneWidget);
      });

      testWidgets('without the terms and conditions it does not move on', (tester) async {
        final fake = FakeApi();
        await tester.pumpWidget(app(fake));
        await tap(tester, find.text('Empezar'));
        await tap(tester, find.text('He leído y acepto el aviso de privacidad.'));
        await tapContinue(tester);

        expect(find.text('Para continuar necesitamos que aceptes los términos y condiciones.'), findsOneWidget);
        expect(find.text('Para continuar necesitamos que aceptes el aviso de privacidad.'), findsNothing);
        expect(fake.starts, isEmpty);

        await tap(tester, find.text('He leído y acepto los términos y condiciones.'));
        await tapContinue(tester);
        await skipLocation(tester);
        expect(find.text('Paso 1 de 5'), findsOneWidget);
      });

      testWidgets('there is no demo data link on the basic data screen', (tester) async {
        await tester.pumpWidget(app(FakeApi()));
        await tap(tester, find.text('Empezar'));
        await acceptNotice(tester);
        await tapContinue(tester);
        await skipLocation(tester);
        expect(find.textContaining('datos de ejemplo'), findsNothing);
      });

      testWidgets('when coming back, the notice stays accepted and no other request is created', (tester) async {
        final fake = FakeApi();
        await tester.pumpWidget(app(fake));
        await tap(tester, find.text('Empezar'));
        await acceptNotice(tester);
        await tapContinue(tester);
        await skipLocation(tester);

        // Basic data → location → privacy.
        await tap(tester, find.byTooltip('Volver al paso anterior'));
        expect(find.text('¿Desde qué ciudad nos escribes?'), findsOneWidget);
        await tap(tester, find.byTooltip('Volver al paso anterior'));
        for (final checkbox in tester.widgetList<Checkbox>(find.byType(Checkbox))) {
          expect(checkbox.value, isTrue);
          expect(checkbox.onChanged, isNull, reason: 'a recorded consent cannot be withdrawn');
        }

        await tapContinue(tester);
        await skipLocation(tester);
        expect(find.text('Paso 1 de 5'), findsOneWidget);
        expect(fake.starts, hasLength(1));
      });

      testWidgets('if the connection fails it shows the error and allows a retry', (tester) async {
        final fake = FakeApi(failStartTimes: 1);
        await tester.pumpWidget(app(fake));
        await tap(tester, find.text('Empezar'));
        await acceptNotice(tester);
        await tapContinue(tester);

        expect(find.text('No pudimos iniciar tu solicitud'), findsOneWidget);
        expect(find.text('Paso 1 de 5'), findsNothing);

        await tap(tester, find.text('Reintentar'));
        await skipLocation(tester);
        expect(find.text('Paso 1 de 5'), findsOneWidget);
        expect(fake.starts, hasLength(2));
      });
    });

    group('device fingerprint (VDI-40)', () {
      testWidgets('does not read the device before the notice is accepted', (tester) async {
        final fake = FakeApi();
        final device = FakeDeviceInfo();
        await tester.pumpWidget(app(fake, device: device));
        await tap(tester, find.text('Empezar'));
        await tapContinue(tester);
        expect(device.reads, 0);
        expect(fake.signals, isEmpty);
      });

      testWidgets('after accepting it sends the fingerprint, model, system and version', (tester) async {
        final fake = FakeApi();
        await tester.pumpWidget(app(fake));
        await tap(tester, find.text('Empezar'));
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
        await tap(tester, find.text('Empezar'));
        await acceptNotice(tester);
        await tapContinue(tester);
        expect(find.text('¿Desde qué ciudad nos escribes?'), findsOneWidget);
        expect(fake.signals, hasLength(1));

        await skipLocation(tester);
        expect(find.text('Paso 1 de 5'), findsOneWidget);
        expect(fake.signals, hasLength(2));
      });
    });

    group('approximate location (VDI-73)', () {
      Future<void> goToLocation(WidgetTester tester, FakeApi fake, FakeLocation location) async {
        await tester.pumpWidget(app(fake, location: location));
        await tap(tester, find.text('Empezar'));
        await acceptNotice(tester);
        await tapContinue(tester);
        expect(find.text('¿Desde qué ciudad nos escribes?'), findsOneWidget);
      }

      Map<String, dynamic> lastSignals(FakeApi fake) => jsonDecode(fake.signals.last.body) as Map<String, dynamic>;

      testWidgets('looks like the design: illustration, grey box with three points, two buttons', (tester) async {
        await goToLocation(tester, FakeApi(), FakeLocation());

        expect(find.byKey(const Key('location-illustration')), findsOneWidget);
        expect(find.text('Saber tu ciudad nos ayuda a detectar si alguien intenta abrir una cuenta a tu nombre desde otro lugar.'),
            findsOneWidget);
        final box = find.byKey(const Key('location-points'));
        expect(box, findsOneWidget);
        for (final text in [
          'Solo guardamos ciudad y país.',
          'No guardamos tu dirección ni tus coordenadas.',
          'Si prefieres no compartirla, puedes completar tu solicitud igual.',
        ]) {
          expect(find.descendant(of: box, matching: find.text(text, findRichText: true)), findsOneWidget, reason: text);
        }
        expect(find.descendant(of: box, matching: find.byIcon(Icons.check)), findsNWidgets(3));

        expect(find.widgetWithText(FilledButton, 'Permitir ubicación aproximada'), findsOneWidget);
        expect(find.widgetWithText(OutlinedButton, 'Ahora no'), findsOneWidget, reason: '«Ahora no» is an outlined button');
      });

      testWidgets('«ciudad y país» goes in bold', (tester) async {
        await goToLocation(tester, FakeApi(), FakeLocation());
        final rich = tester.widget<RichText>(find.descendant(
          of: find.byKey(const Key('location-points')),
          matching: find.byWidgetPredicate((w) => w is RichText && w.text.toPlainText() == 'Solo guardamos ciudad y país.'),
        ));
        final bold = <String>[];
        rich.text.visitChildren((span) {
          if (span is TextSpan && span.style?.fontWeight == FontWeight.w800) bold.add(span.text ?? '');
          return true;
        });
        expect(bold, ['ciudad y país']);
      });

      testWidgets('is asked after the notice, never before', (tester) async {
        final fake = FakeApi();
        final location = FakeLocation();
        await tester.pumpWidget(app(fake, location: location));
        await tap(tester, find.text('Empezar'));
        await tapContinue(tester);
        expect(find.text('¿Desde qué ciudad nos escribes?'), findsNothing);
        expect(location.reads, 0);
      });

      testWidgets('with permission it sends only the city and the country', (tester) async {
        final fake = FakeApi();
        final location = FakeLocation();
        await goToLocation(tester, fake, location);

        await tap(tester, find.text('Permitir ubicación aproximada'));
        expect(location.reads, 1);
        expect(find.text('Paso 1 de 5'), findsOneWidget);
        final body = lastSignals(fake);
        expect(body['deviceLocationStatus'], 'AVAILABLE');
        expect(body['approximateLocation'], 'San Salvador, El Salvador');
        expect(body['countryIso'], 'SV');
        expect(body.keys, isNot(contains('latitude')));
      });

      // CP-41: without permission the request goes on and the location is saved as not available.
      testWidgets('"Ahora no" goes on and sends the location as not available', (tester) async {
        final fake = FakeApi();
        final location = FakeLocation();
        await goToLocation(tester, fake, location);

        await skipLocation(tester);
        expect(location.reads, 0, reason: 'the system permission is not asked');
        expect(find.text('Paso 1 de 5'), findsOneWidget);
        final body = lastSignals(fake);
        expect(body['deviceLocationStatus'], 'DENIED');
        expect(body.containsKey('approximateLocation'), isFalse);
        expect(body.containsKey('countryIso'), isFalse);
      });

      testWidgets('if the system permission is refused it goes on and sends it as not available', (tester) async {
        final fake = FakeApi();
        await goToLocation(tester, fake, FakeLocation(const DeviceLocation.denied()));

        await tap(tester, find.text('Permitir ubicación aproximada'));
        expect(find.text('Paso 1 de 5'), findsOneWidget);
        expect(lastSignals(fake)['deviceLocationStatus'], 'DENIED');
      });

      testWidgets('if the phone cannot find the city it goes on and says so', (tester) async {
        final fake = FakeApi();
        await goToLocation(tester, fake, FakeLocation(const DeviceLocation.unavailable()));

        await tap(tester, find.text('Permitir ubicación aproximada'));
        expect(find.text('Paso 1 de 5'), findsOneWidget);
        expect(lastSignals(fake)['deviceLocationStatus'], 'UNAVAILABLE');
      });

      testWidgets('the decision is still sent with the last signals, before submitting', (tester) async {
        final fake = FakeApi();
        await goToLocation(tester, fake, FakeLocation());
        await skipLocation(tester);
        await fillBasicData(tester);
        await tapContinue(tester);
        await passDui(tester);
        await tap(tester, find.text('Remesas'));
        await tap(tester, find.text('USD 500.01 a 1,000'));
        await tapContinue(tester);
        await tap(tester, find.text('Ahorro'));
        await tap(tester, find.text('USD 200.01 a 500'));
        await tapContinue(tester);
        await tap(tester, find.text('Enviar solicitud'));

        expect(find.text('¡Recibimos tu solicitud!'), findsOneWidget);
        expect(lastSignals(fake)['deviceLocationStatus'], 'DENIED');
      });
    });

    group('interaction patterns (VDI-43)', () {
      testWidgets('sends the typing speed and the timing and attempts of each step', (tester) async {
        var now = DateTime.utc(2026, 10, 7, 10);
        void advance(int ms) => now = now.add(Duration(milliseconds: ms));
        final fake = FakeApi();
        await tester.pumpWidget(app(fake, clock: () => now));

        await tap(tester, find.text('Empezar'));
        advance(8000);
        await acceptNotice(tester);
        await tapContinue(tester);
        await skipLocation(tester);

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
        await tap(tester, find.text('Enviar solicitud'));
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
        await tap(tester, find.text('Empezar'));
        await tapContinue(tester);
        await tapContinue(tester);
        expect(fake.calls.where((r) => r.url.path != '/api/catalogs'), isEmpty);
      });
    });

    group('names with hyphen and apostrophe (VDI-89)', () {
      Future<void> goToBasicData(WidgetTester tester, FakeApi fake) async {
        await tester.pumpWidget(app(fake));
        await tap(tester, find.text('Empezar'));
        await acceptNotice(tester);
        await tapContinue(tester);
        await skipLocation(tester);
      }

      testWidgets('a compound surname with hyphen and apostrophe is accepted and sent with a plain apostrophe',
          (tester) async {
        final fake = FakeApi();
        await goToBasicData(tester, fake);
        await fillBasicData(tester);
        await tester.enterText(find.byType(TextField).at(0), 'María-José');
        await tester.enterText(find.byType(TextField).at(1), 'Rivas-Cruz  D’Aubuisson');
        await tapContinue(tester);

        expect(find.textContaining('solo con letras'), findsNothing);
        final body = jsonDecode(fake.basicData.single.body) as Map<String, dynamic>;
        expect(body['firstNames'], 'María-José');
        expect(body['lastNames'], "Rivas-Cruz D'Aubuisson");
      });

      testWidgets('a symbol is rejected with the message of the design and nothing is sent', (tester) async {
        final fake = FakeApi();
        await goToBasicData(tester, fake);
        await fillBasicData(tester);
        await tester.enterText(find.byType(TextField).at(0), 'Marta Alej@ndra');
        await tapContinue(tester);

        expect(
          find.text("Escribe tus nombres solo con letras. También puedes usar espacios, apóstrofo (') o guion (-)."),
          findsOneWidget,
        );
        expect(fake.basicData, isEmpty);
      });

      testWidgets('it says up front that names go up to 100 characters, and the hint goes away with an error',
          (tester) async {
        await goToBasicData(tester, FakeApi());
        expect(find.text('Hasta 100 caracteres.'), findsNWidgets(2), reason: 'under nombres and apellidos');
        expect(find.text('Guardamos tu avance en cada paso.'), findsOneWidget);

        await fillBasicData(tester);
        await tester.enterText(find.byType(TextField).at(0), 'Ana2');
        await tapContinue(tester);
        expect(find.text('Hasta 100 caracteres.'), findsOneWidget, reason: 'the first field shows its error instead');
      });

      testWidgets('a hyphen at the end is rejected', (tester) async {
        final fake = FakeApi();
        await goToBasicData(tester, fake);
        await fillBasicData(tester);
        await tester.enterText(find.byType(TextField).at(1), 'Rivas-');
        await tapContinue(tester);
        expect(find.textContaining('Escribe tus apellidos solo con letras'), findsOneWidget);
        expect(fake.basicData, isEmpty);
      });
    });

    group('privacy notice (VDI-92)', () {
      Future<void> goToNotice(WidgetTester tester, FakeApi fake) async {
        await tester.pumpWidget(app(fake));
        await tap(tester, find.text('Empezar'));
      }

      testWidgets('shows the version badge, the four data it collects and the closing text', (tester) async {
        await goToNotice(tester, FakeApi());

        expect(find.text('Cuidamos tu cuenta desde el primer paso'), findsOneWidget);
        expect(find.text('Aviso aprobado por Legal · versión 2026.1'), findsOneWidget);
        expect(find.text('Mientras llenas tu solicitud, recopilamos algunos datos para protegerte contra el fraude:'),
            findsOneWidget);
        for (final (title, description) in [
          ('Tu ubicación aproximada', 'La ciudad desde donde haces la solicitud, no tu dirección exacta.'),
          ('El tipo de dispositivo', 'Modelo de teléfono y sistema, para reconocer si es el mismo en otra ocasión.'),
          ('Cómo usas la aplicación', 'Por ejemplo, el tiempo que tardas en cada paso y tu ritmo al escribir.'),
          ('Las fotos de tu DUI', 'Frente y reverso, guardadas de forma segura en tu expediente.'),
        ]) {
          expect(find.text(title), findsOneWidget, reason: title);
          expect(find.text(description), findsOneWidget, reason: description);
        }
        expect(find.text('Leer el aviso de privacidad completo'), findsOneWidget);
        expect(find.byType(Checkbox), findsNWidgets(2));
      });

      testWidgets('the button says «Aceptar y continuar» and the date and version are announced', (tester) async {
        await goToNotice(tester, FakeApi());
        expect(find.text('Aceptar y continuar'), findsOneWidget);
        expect(find.text('Continuar'), findsNothing);
        expect(find.text('Guardamos la fecha y la versión del aviso que aceptas.'), findsOneWidget);
      });

      testWidgets('without accepting both boxes it does not move on and names what is missing', (tester) async {
        final fake = FakeApi();
        await goToNotice(tester, fake);
        await tap(tester, find.byType(Checkbox).at(0));
        await tapContinue(tester);
        expect(find.text('Para continuar necesitamos que aceptes los términos y condiciones.'), findsOneWidget);
        expect(fake.starts, isEmpty);
        expect(find.text('Cuidamos tu cuenta desde el primer paso'), findsOneWidget);
      });

      Finder noticeSheet() => find.byType(BottomSheet);

      /// Scrolls the full notice until [finder] shows.
      Future<void> scrollToNotice(WidgetTester tester, Finder finder) => tester.scrollUntilVisible(
            finder,
            200,
            scrollable: find.descendant(of: noticeSheet(), matching: find.byType(Scrollable)).first,
          );

      testWidgets('the full notice opens from the link, shows the final text with its version and closes', (tester) async {
        await goToNotice(tester, FakeApi());
        await tap(tester, find.text('Leer el aviso de privacidad completo'));

        expect(find.text('Aviso de privacidad'), findsOneWidget);
        expect(find.descendant(of: noticeSheet(), matching: find.text('Aviso aprobado por Legal · versión 2026.1')), findsOneWidget);
        expect(find.text(PrivacyNotice.fullIntro), findsOneWidget);

        await tap(tester, find.text('Cerrar'));
        expect(find.text('Aviso de privacidad'), findsNothing);
        expect(find.text('Aceptar y continuar'), findsOneWidget);
      });

      testWidgets('the full notice has every section with its text, and nothing is marked as provisional',
          (tester) async {
        await goToNotice(tester, FakeApi());
        await tap(tester, find.text('Leer el aviso de privacidad completo'));

        expect(PrivacyNotice.fullSections, hasLength(6));
        for (final section in PrivacyNotice.fullSections) {
          await scrollToNotice(tester, find.text(section.title));
          expect(find.text(section.title), findsOneWidget, reason: section.title);
          for (final paragraph in section.paragraphs) {
            await scrollToNotice(tester, find.text(paragraph));
            expect(find.text(paragraph), findsOneWidget, reason: paragraph);
          }
        }
        expect(find.textContaining('provisional'), findsNothing);
        expect(find.textContaining('Texto provisional'), findsNothing);
        expect(find.textContaining('[X.Y]'), findsNothing);
      });

      testWidgets('the paragraphs of the full notice are justified', (tester) async {
        await goToNotice(tester, FakeApi());
        await tap(tester, find.text('Leer el aviso de privacidad completo'));

        expect(tester.widget<Text>(find.text(PrivacyNotice.fullIntro)).textAlign, TextAlign.justify);
        for (final section in PrivacyNotice.fullSections) {
          for (final paragraph in section.paragraphs) {
            await scrollToNotice(tester, find.text(paragraph));
            expect(tester.widget<Text>(find.text(paragraph)).textAlign, TextAlign.justify, reason: paragraph);
          }
        }
      });

      testWidgets('the notice tells what the app really collects: the DUI photos, the approximate location, the device',
          (tester) async {
        final text = [for (final s in PrivacyNotice.fullSections) ...s.paragraphs].join(' ');
        for (final word in ['fotos de tu DUI', 'ciudad y el país', 'dirección ni tus coordenadas', 'dispositivo', 'ritmo al escribir']) {
          expect(text, contains(word), reason: word);
        }
      });

      testWidgets('on a small screen the notice still scrolls to the end and can be closed', (tester) async {
        tester.view.physicalSize = const Size(360, 480); // short: the notice has to scroll
        addTearDown(tester.view.resetPhysicalSize);
        await goToNotice(tester, FakeApi());
        await tap(tester, find.text('Leer el aviso de privacidad completo'));

        final last = PrivacyNotice.fullSections.last.paragraphs.last;
        await scrollToNotice(tester, find.text(last));
        expect(find.text(last), findsOneWidget);
        expect(find.text('Cerrar'), findsOneWidget, reason: 'the button stays in view');
        await tap(tester, find.text('Cerrar'));
        expect(find.text('Aviso de privacidad'), findsNothing);
      });

      testWidgets('the version that is shown is the one the server records when the notice is accepted', (tester) async {
        expect(PrivacyNotice.version, '2026.1');
      });

      testWidgets('after accepting, the other screens keep «Continuar»', (tester) async {
        await goToNotice(tester, FakeApi());
        await acceptNotice(tester);
        await tapContinue(tester);
        await skipLocation(tester);
        expect(find.text('Continuar'), findsOneWidget);
        expect(find.text('Aceptar y continuar'), findsNothing);
      });
    });

    group('welcome (VDI-22)', () {
      testWidgets('says 7 minutes and asks to have the DUI at hand', (tester) async {
        await tester.pumpWidget(app(FakeApi()));
        expect(find.text('Tu cuenta, desde tu teléfono.'), findsOneWidget);
        expect(find.text('Sin filas y sin ir a una agencia. Ten tu DUI a la mano: te toma unos 7 minutos.'),
            findsOneWidget);
        expect(find.text('Unos 7 minutos'), findsOneWidget);
        expect(find.textContaining('5 minutos'), findsNothing);
      });

      testWidgets('lists the five steps in order, all with the same number circle and no badge', (tester) async {
        await tester.pumpWidget(app(FakeApi()));
        final steps = [
          ('Datos básicos', 'Tu nombre, DUI y celular.'),
          ('Tu DUI', 'Una foto del frente y otra del reverso.'),
          ('Tus ingresos', 'De dónde viene tu dinero y cuánto ganas.'),
          ('El dinero de tu cuenta', 'Qué dinero esperas mover cada mes.'),
          ('Revisar y enviar', 'Confirmas todo antes de mandarlo.'),
        ];
        double? previousTop;
        for (final (title, description) in steps) {
          await tester.ensureVisible(find.text(title));
          expect(find.text(description), findsOneWidget, reason: title);
          final top = tester.getTopLeft(find.text(title)).dy;
          if (previousTop != null) expect(top, greaterThan(previousTop), reason: '$title comes after the previous step');
          previousTop = top;
        }
        for (final n in ['1', '2', '3', '4', '5']) {
          expect(find.text(n), findsOneWidget, reason: 'number $n');
        }
        expect(find.textContaining('NUEVO', findRichText: true), findsNothing);
        expect(find.textContaining('Nuevo'), findsNothing);

        // Every number sits in a circle of the same color.
        final colors = {
          for (final n in ['1', '2', '3', '4', '5'])
            (tester.widget<Container>(find.ancestor(of: find.text(n), matching: find.byType(Container)).first).decoration
                    as BoxDecoration)
                .color,
        };
        expect(colors, hasLength(1), reason: 'the DUI is not highlighted');
      });
    });

    group('DUI capture (VDI-77)', () {
      Future<void> goToDui(WidgetTester tester, FakeApi fake, FakeCamera camera) async {
        await tester.pumpWidget(app(fake, camera: camera));
        await tap(tester, find.text('Empezar'));
        await acceptNotice(tester);
        await tapContinue(tester);
        await skipLocation(tester);
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

        expect(find.text('Paso 2 de 5 · Tu DUI'), findsOneWidget);
        expect(find.text('Coloca el frente de tu DUI dentro del marco'), findsOneWidget);
        for (final tip in ['Buena luz', 'Sin reflejos', 'Se ven las 4 esquinas']) {
          expect(find.text(tip), findsOneWidget);
        }
        expect(find.text('1 · Frente'), findsOneWidget);
        expect(find.text('2 · Reverso'), findsOneWidget);
        expect(find.text('Continuar'), findsNothing, reason: 'the shutter is the only action while the camera is open');

        await tap(tester, shutter);
        expect(find.text('Ahora coloca el reverso de tu DUI dentro del marco'), findsOneWidget);
        expect(camera.photos, 1);
      });

      testWidgets('the camera screen has its own dark header with back and help, and the two sides', (tester) async {
        await goToDui(tester, FakeApi(), FakeCamera());
        expect(find.byTooltip('Volver al paso anterior'), findsOneWidget);
        expect(find.byTooltip('Ayuda para tomar la foto'), findsOneWidget);
        expect(find.text('1 · Frente'), findsOneWidget);
        expect(find.text('2 · Reverso'), findsOneWidget);
        expect(find.byType(StepProgress), findsNothing, reason: 'the camera has its own two-segment progress');
        expect(find.byType(Logo), findsNothing, reason: 'the camera takes the whole screen');
      });

      testWidgets('the help opens the tips to take the photo and closes', (tester) async {
        await goToDui(tester, FakeApi(), FakeCamera());
        await tap(tester, find.byTooltip('Ayuda para tomar la foto'));
        expect(find.text('Para que salga bien'), findsOneWidget);
        for (final tip in UnreadablePhotos.tips) {
          expect(find.text(tip), findsOneWidget);
        }
        await tap(tester, find.text('Entendido'));
        expect(find.text('Para que salga bien'), findsNothing);
        expect(shutter, findsOneWidget);
      });

      testWidgets('the live hint follows the image: lit and steady, too dark, moving', (tester) async {
        final camera = FakeCamera();
        await goToDui(tester, FakeApi(), camera);

        expect(find.text('Mantén el teléfono quieto'), findsOneWidget, reason: 'before it knows anything');
        camera.hint(FrameHint.good);
        await tester.pumpAndSettle();
        expect(find.text('Bien, mantén el teléfono quieto'), findsOneWidget);

        camera.hint(FrameHint.tooDark);
        await tester.pumpAndSettle();
        expect(find.text('Hay poca luz. Busca un lugar más iluminado.'), findsOneWidget);
        expect(find.text('Bien, mantén el teléfono quieto'), findsNothing);

        camera.hint(FrameHint.moving);
        await tester.pumpAndSettle();
        expect(find.text('Mueve menos el teléfono.'), findsOneWidget);

        camera.hint(FrameHint.good);
        await tester.pumpAndSettle();
        expect(find.text('Bien, mantén el teléfono quieto'), findsOneWidget);
      });

      testWidgets('tapping the image focuses there, with the point given in 0 to 1 of the image', (tester) async {
        final camera = FakeCamera();
        await goToDui(tester, FakeApi(), camera);

        final area = find.byKey(const Key('camera-preview'));
        final rect = tester.getRect(area);
        await tester.tapAt(rect.topLeft + Offset(rect.width * 0.25, rect.height * 0.75));
        await tester.pump();

        expect(camera.focusPoints, hasLength(1));
        expect(camera.focusPoints.single.dx, closeTo(0.25, 0.02));
        expect(camera.focusPoints.single.dy, closeTo(0.75, 0.02));
      });

      testWidgets('a ring shows where it focused and goes away by itself', (tester) async {
        await goToDui(tester, FakeApi(), FakeCamera());
        expect(find.byKey(const Key('focus-ring')), findsNothing);

        await tester.tap(find.byKey(const Key('camera-preview')));
        await tester.pump();
        expect(find.byKey(const Key('focus-ring')), findsOneWidget);

        await tester.pump(const Duration(seconds: 2));
        expect(find.byKey(const Key('focus-ring')), findsNothing);
      });

      testWidgets('tapping again moves the ring and focuses again', (tester) async {
        final camera = FakeCamera();
        await goToDui(tester, FakeApi(), camera);
        final rect = tester.getRect(find.byKey(const Key('camera-preview')));
        await tester.tapAt(rect.center);
        await tester.pump(const Duration(milliseconds: 100));
        await tester.tapAt(rect.topLeft + const Offset(40, 40));
        await tester.pump();
        expect(camera.focusPoints, hasLength(2));
        expect(find.byKey(const Key('focus-ring')), findsOneWidget, reason: 'only one ring at a time');
      });

      testWidgets('the shutter still takes the photo after tapping to focus', (tester) async {
        final camera = FakeCamera();
        await goToDui(tester, FakeApi(), camera);
        await tester.tap(find.byKey(const Key('camera-preview')));
        await tester.pump();
        await tap(tester, shutter);
        expect(camera.photos, 1);
      });

      testWidgets('when it is very dark the shutter is off, it says why, and no photo is taken', (tester) async {
        final camera = FakeCamera();
        await goToDui(tester, FakeApi(), camera);

        camera.hint(FrameHint.veryDark);
        await tester.pumpAndSettle();
        expect(find.text('Está demasiado oscuro para tomar la foto. Busca un lugar con más luz.'), findsOneWidget);

        await tester.tap(shutter);
        await tester.pump();
        expect(camera.photos, 0, reason: 'the shutter does nothing in the dark');
        expect(find.text('Coloca el frente de tu DUI dentro del marco'), findsOneWidget);
      });

      testWidgets('the shutter says it is not available while it is too dark, for screen readers', (tester) async {
        final camera = FakeCamera();
        await goToDui(tester, FakeApi(), camera);
        final handle = tester.ensureSemantics();
        camera.hint(FrameHint.veryDark);
        await tester.pumpAndSettle();
        expect(tester.getSemantics(shutter), matchesSemantics(label: 'Tomar foto. No disponible: hay muy poca luz.', isButton: true, hasTapAction: false));
        handle.dispose();
      });

      testWidgets('the shutter works again as soon as there is light', (tester) async {
        final camera = FakeCamera();
        await goToDui(tester, FakeApi(), camera);
        camera.hint(FrameHint.veryDark);
        await tester.pumpAndSettle();
        camera.hint(FrameHint.good);
        await tester.pumpAndSettle();
        await tap(tester, shutter);
        expect(camera.photos, 1);
      });

      testWidgets('dim light only warns: the photo can be taken', (tester) async {
        final camera = FakeCamera();
        await goToDui(tester, FakeApi(), camera);
        camera.hint(FrameHint.tooDark);
        await tester.pumpAndSettle();
        await tap(tester, shutter);
        expect(camera.photos, 1);
      });

      testWidgets('a bad hint never blocks the photo: the person can still take it', (tester) async {
        final camera = FakeCamera();
        await goToDui(tester, FakeApi(), camera);
        camera.hint(FrameHint.tooDark);
        await tester.pumpAndSettle();
        await tap(tester, shutter);
        expect(camera.photos, 1);
        expect(find.text('Ahora coloca el reverso de tu DUI dentro del marco'), findsOneWidget);
      });

      testWidgets('the hint starts over for the second photo', (tester) async {
        final camera = FakeCamera();
        await goToDui(tester, FakeApi(), camera);
        camera.hint(FrameHint.good);
        await tester.pumpAndSettle();
        await tap(tester, shutter);
        expect(find.text('Mantén el teléfono quieto'), findsOneWidget, reason: 'the other side has not been seen yet');
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
        expect(find.text('Continuar'), findsNothing);
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

        await tap(tester, find.text('Tomar otra foto'));
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
        await tap(tester, find.text('Tomar otra foto'));
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
        await tap(tester, find.text('Tomar otras fotos'));
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
        expect(find.text('Tomar otras fotos'), findsOneWidget);
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
        expect(find.text('Tomar otras fotos'), findsOneWidget);
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

        await tap(tester, find.text('Reintentar'));
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

        await tap(tester, find.text('Intentar de nuevo'));
        expect(camera.opens, 2);
      });

      testWidgets('if the camera fails to open, it retries and then works', (tester) async {
        final camera = FakeCamera(failTimes: 1);
        await goToDui(tester, FakeApi(), camera);
        expect(find.text('No pudimos abrir la cámara. Cierra otras aplicaciones que la estén usando e inténtalo de nuevo.'),
            findsOneWidget);
        await tap(tester, find.text('Intentar de nuevo'));
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

    group('confirm the DUI data (VDI-78)', () {
      final confirmButton = find.text('Mis datos son correctos');

      Future<void> goToDui(WidgetTester tester, FakeApi fake, FakeCamera camera) async {
        await tester.pumpWidget(app(fake, camera: camera));
        await tap(tester, find.text('Empezar'));
        await acceptNotice(tester);
        await tapContinue(tester);
        await skipLocation(tester);
        await fillBasicData(tester);
        await tapContinue(tester);
        await tap(tester, shutter);
        await tap(tester, shutter);
      }

      Future<void> goToConfirmation(WidgetTester tester, FakeApi fake, [FakeCamera? camera]) =>
          goToDui(tester, fake, camera ?? FakeCamera());

      /// Text of the n-th field: 0 DUI, 1 first names, 2 last names, 3 birth date, 4 expiry date.
      String field(WidgetTester tester, int n) => tester.widget<TextField>(find.byType(TextField).at(n)).controller!.text;
      Future<void> type(WidgetTester tester, int n, String text) async {
        await tester.enterText(find.byType(TextField).at(n), text);
        await tester.pumpAndSettle();
      }

      const notClear = 'No lo leímos con claridad. Revísalo.';

      /// Taps «Mis datos son correctos»; if the person changed the data so it no longer matches the basic data,
      /// the warning comes up and it is accepted with «Continuar de todos modos».
      Future<void> confirm(WidgetTester tester) async {
        await tap(tester, confirmButton);
        final anyway = find.text('Continuar de todos modos');
        if (anyway.evaluate().isNotEmpty) await tap(tester, anyway);
      }

      testWidgets('shows the five data that were read, the photos and the button', (tester) async {
        await goToConfirmation(tester, FakeApi());

        expect(find.text('¿Leímos bien tus datos?'), findsOneWidget);
        expect(find.text('Los sacamos de las fotos de tu DUI. Corrige lo que no esté bien.'), findsOneWidget);
        expect(find.text('Paso 2 de 5'), findsOneWidget);
        expect(
          [for (var i = 0; i < 5; i++) field(tester, i)],
          ['04812377-5', 'Marta Alejandra', 'Rivas Cruz', '12/04/1990', '01/06/2029'],
        );
        for (final label in ['Número de DUI', 'Nombres', 'Apellidos', 'Fecha de nacimiento', 'Vence el']) {
          expect(find.text(label), findsOneWidget);
        }
        expect(find.text('Frente'), findsOneWidget);
        expect(find.text('Reverso'), findsOneWidget);
        expect(find.text('Repetir'), findsNWidgets(2));
        expect(find.text(notClear), findsNothing, reason: 'everything was read clearly');
        expect(confirmButton, findsOneWidget);
        expect(find.text('Continuar'), findsNothing);
      });

      testWidgets('confirming sends the data with the dates as year-month-day and moves on', (tester) async {
        final fake = FakeApi();
        await goToConfirmation(tester, fake);
        await confirm(tester);

        expect(jsonDecode(fake.identityConfirmations.single.body), {
          'dui': '04812377-5',
          'firstNames': 'Marta Alejandra',
          'lastNames': 'Rivas Cruz',
          'birthDate': '1990-04-12',
          'expiryDate': '2029-06-01',
        });
        expect(find.text('Paso 3 de 5'), findsOneWidget);
      });

      testWidgets('the person can correct any of the five data and the corrected ones are sent', (tester) async {
        final fake = FakeApi();
        await goToConfirmation(tester, fake);
        await type(tester, 0, '09876543-2');
        await type(tester, 1, '  Maria   Jose ');
        await type(tester, 2, 'Perez Lopez');
        await type(tester, 3, '13/04/1990');
        await type(tester, 4, '02/06/2030');
        await confirm(tester);

        expect(jsonDecode(fake.identityConfirmations.single.body), {
          'dui': '09876543-2',
          'firstNames': 'Maria Jose',
          'lastNames': 'Perez Lopez',
          'birthDate': '1990-04-13',
          'expiryDate': '2030-06-02',
        });
      });

      testWidgets('dates are typed with the mask: only digits, slashes appear by themselves', (tester) async {
        await goToConfirmation(tester, FakeApi());
        await type(tester, 3, '14031991');
        expect(field(tester, 3), '14/03/1991');
        await type(tester, 3, '1a4-0b3 1991999');
        expect(field(tester, 3), '14/03/1991');
        await type(tester, 0, '048123775');
        expect(field(tester, 0), '04812377-5');
      });

      testWidgets('a datum that was not read is empty and marked to review, and does not let the person continue',
          (tester) async {
        final fake = FakeApi(identityResponses: [
          (200, '{"status":"READ","attempts":1,"dui":"04812377-5","firstNames":"Marta Alejandra","lastNames":"Rivas Cruz",'
              '"birthDate":null,"expiryDate":"2029-06-01"}'),
        ]);
        await goToConfirmation(tester, fake);

        expect(field(tester, 3), '');
        expect(find.text(notClear), findsOneWidget, reason: 'only the birth date is marked');

        await confirm(tester);
        expect(find.text('Escribe tu fecha de nacimiento como día/mes/año, por ejemplo 14/03/1991.'), findsOneWidget);
        expect(fake.identityConfirmations, isEmpty, reason: 'nothing is sent while a datum is invalid');
        expect(find.text('Paso 2 de 5'), findsOneWidget);

        await type(tester, 3, '14031991');
        expect(find.text(notClear), findsNothing, reason: 'the mark goes away when the person writes it');
        await confirm(tester);
        expect(fake.identityConfirmations, hasLength(1));
        expect(find.text('Paso 3 de 5'), findsOneWidget);
      });

      testWidgets('a datum read badly is marked too, and the person can fix it', (tester) async {
        final fake = FakeApi(identityResponses: [
          (200, '{"status":"READ","attempts":1,"dui":"0123456-8","firstNames":"M4ria","lastNames":"Pérez",'
              '"birthDate":"1990-04-12","expiryDate":"2029-06-01"}'),
        ]);
        await goToConfirmation(tester, fake);

        expect(find.text(notClear), findsNWidgets(2), reason: 'DUI and first names');
        expect(field(tester, 0), '0123456-8');
        await type(tester, 0, '04812377-5');
        await type(tester, 1, 'Maria');
        expect(find.text(notClear), findsNothing);
        await confirm(tester);
        expect(fake.identityConfirmations, hasLength(1));
      });

      testWidgets('if the reader is unavailable it says so, everything is empty and nothing is marked', (tester) async {
        final fake = FakeApi(identityResponses: [(200, '{"status":"FAILED","attempts":1}')]);
        await goToConfirmation(tester, fake);

        expect(find.text('No pudimos leer las fotos de tu DUI. Escribe tus datos tal como aparecen en él.'), findsOneWidget);
        expect([for (var i = 0; i < 5; i++) field(tester, i)], ['', '', '', '', '']);
        expect(find.text(notClear), findsNothing);

        await confirm(tester);
        expect(fake.identityConfirmations, isEmpty);
        expect(find.text('Escribe tu DUI con el formato 00000000-0.'), findsOneWidget);
        expect(find.text('Escribe tus nombres.'), findsOneWidget);
        expect(find.text('Escribe tus apellidos.'), findsOneWidget);

        await type(tester, 0, '012345678');
        await type(tester, 1, 'María');
        await type(tester, 2, 'Pérez');
        await type(tester, 3, '12041990');
        await type(tester, 4, '01062029');
        await confirm(tester);
        expect(fake.identityConfirmations, hasLength(1));
      });

      testWidgets('odd values are rejected with a clear message and nothing is sent', (tester) async {
        final fake = FakeApi();
        await goToConfirmation(tester, fake);

        await type(tester, 0, '12345');
        await type(tester, 1, 'Ana 😀');
        await type(tester, 2, '');
        await type(tester, 3, '31021990');
        await type(tester, 4, '01011980');
        await confirm(tester);

        expect(find.text('Escribe tu DUI con el formato 00000000-0.'), findsOneWidget);
        expect(
          find.text("Escribe tus nombres solo con letras. También puedes usar espacios, apóstrofo (') o guion (-)."),
          findsOneWidget,
        );
        expect(find.text('Escribe tus apellidos.'), findsOneWidget);
        expect(find.text('Escribe tu fecha de nacimiento como día/mes/año, por ejemplo 14/03/1991.'), findsOneWidget);
        expect(find.text('La fecha de vencimiento debe ser posterior a la de nacimiento.'), findsNothing,
            reason: 'with an invalid birth date there is nothing to compare the expiry with');
        expect(fake.identityConfirmations, isEmpty);

        await type(tester, 3, '12041990');
        await confirm(tester);
        expect(find.text('La fecha de vencimiento debe ser posterior a la de nacimiento.'), findsOneWidget);

        await type(tester, 3, '01012999');
        await confirm(tester);
        expect(find.text('La fecha de nacimiento debe ser anterior a hoy.'), findsOneWidget);
      });

      testWidgets('an expired DUI is not accepted: the message names the date and the person can correct a misread',
          (tester) async {
        final fake = FakeApi(identityResponses: [
          (200, '{"status":"READ","attempts":1,"dui":"04812377-5","firstNames":"Marta Alejandra","lastNames":"Rivas Cruz",'
              '"birthDate":"1990-04-12","expiryDate":"2020-06-01"}'),
        ]);
        await goToConfirmation(tester, fake);

        expect(field(tester, 4), '01/06/2020');
        expect(find.text(notClear), findsNothing, reason: 'it was read clearly');
        expect(find.textContaining('venció'), findsNothing, reason: 'the message appears when trying to continue');

        await confirm(tester);
        expect(find.text('Tu DUI venció el 01/06/2020. Para abrir tu cuenta necesitamos un DUI vigente.'),
            findsOneWidget);
        expect(fake.identityConfirmations, isEmpty);
        expect(find.text('Paso 2 de 5'), findsOneWidget);

        await type(tester, 4, '01062029');
        expect(find.textContaining('venció'), findsNothing);
        await confirm(tester);
        expect(jsonDecode(fake.identityConfirmations.single.body)['expiryDate'], '2029-06-01');
        expect(find.text('Paso 3 de 5'), findsOneWidget);
      });

      testWidgets('repeating the front takes only that photo and sends both to be read again', (tester) async {
        final fake = FakeApi();
        final camera = FakeCamera();
        await goToConfirmation(tester, fake, camera);
        await type(tester, 1, 'Maria Jose');

        await tap(tester, find.text('Repetir').first);
        expect(find.text('Coloca el frente de tu DUI dentro del marco'), findsOneWidget);
        expect(find.text('Ahora coloca el reverso de tu DUI dentro del marco'), findsNothing);
        await tap(tester, shutter);

        expect(fake.identityUploads, hasLength(2));
        expect(camera.photos, 3, reason: 'only the front was photographed again');
        expect(find.text('¿Leímos bien tus datos?'), findsOneWidget);
        expect(field(tester, 1), 'Maria Jose', reason: 'what the person corrected is kept');
      });

      testWidgets('repeating the back asks only for the back', (tester) async {
        final camera = FakeCamera();
        await goToConfirmation(tester, FakeApi(), camera);
        await tap(tester, find.text('Repetir').last);
        expect(find.text('Ahora coloca el reverso de tu DUI dentro del marco'), findsOneWidget);
        await tap(tester, shutter);
        expect(camera.photos, 3);
        expect(find.text('¿Leímos bien tus datos?'), findsOneWidget);
      });

      testWidgets('going back while repeating a photo returns to the confirmation, not to the basic data',
          (tester) async {
        final fake = FakeApi();
        await goToConfirmation(tester, fake);
        await tap(tester, find.text('Repetir').first);
        await tap(tester, find.byTooltip('Volver al paso anterior'));
        expect(find.text('¿Leímos bien tus datos?'), findsOneWidget);
        expect(fake.identityUploads, hasLength(1));
      });

      testWidgets('if the repeated photo is unreadable it explains and the confirmation comes back after retaking it',
          (tester) async {
        final fake = FakeApi(identityResponses: [
          (200, duiReading),
          (422, unreadable('GLARE', 'FRONT')),
        ]);
        await goToConfirmation(tester, fake);
        await tap(tester, find.text('Repetir').first);
        await tap(tester, shutter);
        expect(find.text('Revisa la foto del frente'), findsOneWidget);

        await tap(tester, find.text('Tomar otra foto'));
        await tap(tester, shutter);
        expect(find.text('¿Leímos bien tus datos?'), findsOneWidget);
        expect(fake.identityUploads, hasLength(3));
      });

      testWidgets('while saving it cannot be tapped twice and a failure keeps the data and allows a retry',
          (tester) async {
        final fake = FakeApi(confirmationErrors: [503]);
        await goToConfirmation(tester, fake);
        await type(tester, 1, 'Maria');
        await confirm(tester);

        expect(find.text('No pudimos guardar los datos de tu DUI'), findsOneWidget);
        expect(find.text('Revisa tu conexión a internet e inténtalo de nuevo.'), findsOneWidget);
        expect(find.text('¿Leímos bien tus datos?'), findsOneWidget);
        expect(field(tester, 1), 'Maria');

        await tap(tester, find.text('Reintentar'));
        expect(fake.identityConfirmations, hasLength(2));
        expect(find.text('Paso 3 de 5'), findsOneWidget);
      });

      testWidgets('a rejected field (400) is named in Spanish, without the server text', (tester) async {
        final fake = FakeApi(confirmationErrors: [400]);
        await goToConfirmation(tester, fake);
        await confirm(tester);
        expect(find.textContaining('Revisa estos datos e inténtalo de nuevo'), findsOneWidget);
        expect(find.textContaining('Some fields'), findsNothing);
      });

      testWidgets('a 409 does not say that the request was submitted', (tester) async {
        final fake = FakeApi(confirmationErrors: [409]);
        await goToConfirmation(tester, fake);
        await confirm(tester);
        expect(
          find.text('No pudimos guardar los datos de tu DUI. Vuelve a tomar las fotos e inténtalo de nuevo.'),
          findsOneWidget,
        );
        expect(find.textContaining('ya fue enviada'), findsNothing);
      });

      testWidgets('typing and attempts of the confirmation count in the DUI step of the signals', (tester) async {
        final fake = FakeApi();
        await goToConfirmation(tester, fake);
        await type(tester, 3, '');
        await confirm(tester); // fails: empty birth date
        await type(tester, 3, '12041990');
        await confirm(tester);
        await tester.pumpAndSettle();

        final last = jsonDecode(fake.signals.last.body) as Map<String, dynamic>;
        final dui = (last['steps'] as List).firstWhere((s) => s['step'] == 'IDENTITY_DOCUMENT') as Map<String, dynamic>;
        expect(dui['attempts'], 2);
        expect(dui['completedAt'], isNotNull);
        expect((last['steps'] as List).where((s) => s['step'] == 'IDENTITY_DOCUMENT'), hasLength(1));
      });

      testWidgets('the confirmation does not appear if the photos were not read: back goes to the capture',
          (tester) async {
        final camera = FakeCamera();
        await goToConfirmation(tester, FakeApi(), camera);
        await tap(tester, find.byTooltip('Volver al paso anterior'));
        expect(find.text('Coloca el frente de tu DUI dentro del marco'), findsOneWidget);
      });
    });

    group('declared data against the DUI', () {
      final confirmButton = find.text('Mis datos son correctos');

      Future<void> goToConfirmation(WidgetTester tester, FakeApi fake) async {
        await tester.pumpWidget(app(fake, camera: FakeCamera()));
        await tap(tester, find.text('Empezar'));
        await acceptNotice(tester);
        await tapContinue(tester);
        await skipLocation(tester);
        await fillBasicData(tester); // Marta Alejandra Rivas Cruz, DUI 04812377-5
        await tapContinue(tester);
        await tap(tester, shutter);
        await tap(tester, shutter);
      }

      String reading({String dui = '04812377-5', String first = 'Marta Alejandra', String last = 'Rivas Cruz'}) =>
          '{"status":"READ","attempts":1,"dui":"$dui","firstNames":"$first","lastNames":"$last",'
          '"birthDate":"1990-04-12","expiryDate":"2029-06-01"}';

      const other = '{"status":"READ","attempts":1,"dui":"07145973-5","firstNames":"Nayeli Aimy",'
          '"lastNames":"Santacruz Ayala","birthDate":"2006-09-03","expiryDate":"2032-09-18"}';

      testWidgets('when the DUI is the same person there is no warning and it goes on', (tester) async {
        final fake = FakeApi();
        await goToConfirmation(tester, fake);
        await tap(tester, confirmButton);
        expect(find.text('Tus datos no coinciden con tu DUI'), findsNothing);
        expect(fake.identityConfirmations, hasLength(1));
        expect(find.text('Paso 3 de 5'), findsOneWidget);
      });

      testWidgets('accents, capitals and extra spaces in the DUI do not trigger the warning', (tester) async {
        final fake = FakeApi(identityResponses: [(200, reading(first: 'MARTA  ALEJANDRA', last: 'rivas cruz'))]);
        await goToConfirmation(tester, fake);
        await tap(tester, confirmButton);
        expect(find.text('Tus datos no coinciden con tu DUI'), findsNothing);
        expect(fake.identityConfirmations, hasLength(1));
      });

      testWidgets('a different person: it warns, shows both sides and sends nothing yet', (tester) async {
        final fake = FakeApi(identityResponses: [(200, other)]);
        await goToConfirmation(tester, fake);
        await tap(tester, confirmButton);

        expect(find.text('Tus datos no coinciden con tu DUI'), findsOneWidget);
        expect(find.text('Lo que escribiste al inicio es distinto de lo que dice tu DUI.'), findsOneWidget);
        final sheet = find.byType(BottomSheet);
        for (final text in [
          'Número de DUI', 'Escribiste: 04812377-5', 'Tu DUI dice: 07145973-5',
          'Nombres', 'Escribiste: Marta Alejandra', 'Tu DUI dice: Nayeli Aimy',
          'Apellidos', 'Escribiste: Rivas Cruz', 'Tu DUI dice: Santacruz Ayala',
        ]) {
          expect(find.descendant(of: sheet, matching: find.text(text)), findsOneWidget, reason: text);
        }
        expect(find.text('Corregir mis datos'), findsOneWidget);
        expect(find.text('Continuar de todos modos'), findsOneWidget);
        expect(fake.identityConfirmations, isEmpty, reason: 'the person has not decided yet');
      });

      testWidgets('only what differs is listed', (tester) async {
        final fake = FakeApi(identityResponses: [(200, reading(last: 'López Peña'))]);
        await goToConfirmation(tester, fake);
        await tap(tester, confirmButton);
        final sheet = find.byType(BottomSheet);
        expect(find.descendant(of: sheet, matching: find.text('Apellidos')), findsOneWidget);
        expect(find.text('Escribiste: Rivas Cruz'), findsOneWidget);
        expect(find.text('Tu DUI dice: López Peña'), findsOneWidget);
        expect(find.descendant(of: sheet, matching: find.text('Nombres')), findsNothing);
        expect(find.descendant(of: sheet, matching: find.text('Número de DUI')), findsNothing);
      });

      testWidgets('«Continuar de todos modos» does not block: it sends the data of the DUI and moves on', (tester) async {
        final fake = FakeApi(identityResponses: [(200, other)]);
        await goToConfirmation(tester, fake);
        await tap(tester, confirmButton);
        await tap(tester, find.text('Continuar de todos modos'));

        expect(fake.identityConfirmations, hasLength(1));
        final body = jsonDecode(fake.identityConfirmations.single.body) as Map<String, dynamic>;
        expect(body['dui'], '07145973-5');
        expect(body['firstNames'], 'Nayeli Aimy');
        expect(find.text('Paso 3 de 5'), findsOneWidget);
      });

      testWidgets('after accepting it once, a retry of the same data does not ask again', (tester) async {
        final fake = FakeApi(identityResponses: [(200, other)], confirmationErrors: [503]);
        await goToConfirmation(tester, fake);
        await tap(tester, confirmButton);
        await tap(tester, find.text('Continuar de todos modos'));
        expect(find.text('No pudimos guardar los datos de tu DUI'), findsOneWidget);

        await tap(tester, find.text('Reintentar'));
        expect(find.text('Tus datos no coinciden con tu DUI'), findsNothing);
        expect(fake.identityConfirmations, hasLength(2));
        expect(find.text('Paso 3 de 5'), findsOneWidget);
      });

      testWidgets('«Corregir mis datos» goes to the basic data with what was written, and after fixing it comes back',
          (tester) async {
        final fake = FakeApi(identityResponses: [(200, other)]);
        await goToConfirmation(tester, fake);
        await tap(tester, confirmButton);
        await tap(tester, find.text('Corregir mis datos'));

        expect(find.text('Cuéntanos quién eres'), findsOneWidget);
        expect(tester.widget<TextField>(find.byType(TextField).at(0)).controller!.text, 'Marta Alejandra');

        await tester.enterText(find.byType(TextField).at(0), 'Nayeli Aimy');
        await tester.enterText(find.byType(TextField).at(1), 'Santacruz Ayala');
        await tester.enterText(find.byType(TextField).at(2), '071459735');
        await tapContinue(tester);

        expect(find.text('¿Leímos bien tus datos?'), findsOneWidget, reason: 'it does not ask for the photos again');
        expect(fake.identityUploads, hasLength(1));
        expect(fake.basicData, hasLength(2), reason: 'the corrected basic data was saved');

        await tap(tester, confirmButton);
        expect(find.text('Tus datos no coinciden con tu DUI'), findsNothing, reason: 'now they match');
        expect(fake.identityConfirmations, hasLength(1));
        expect(find.text('Paso 3 de 5'), findsOneWidget);
      });

      testWidgets('going back from the basic data after «Corregir» goes to the previous step as always', (tester) async {
        final fake = FakeApi(identityResponses: [(200, other)]);
        await goToConfirmation(tester, fake);
        await tap(tester, confirmButton);
        await tap(tester, find.text('Corregir mis datos'));
        await tap(tester, find.byTooltip('Volver al paso anterior'));
        expect(find.text('¿Desde qué ciudad nos escribes?'), findsOneWidget);
      });

      testWidgets('closing the warning without choosing leaves everything as it was', (tester) async {
        final fake = FakeApi(identityResponses: [(200, other)]);
        await goToConfirmation(tester, fake);
        await tap(tester, confirmButton);
        await tester.binding.handlePopRoute(); // the system back closes the sheet
        await tester.pumpAndSettle();

        expect(find.text('Tus datos no coinciden con tu DUI'), findsNothing);
        expect(find.text('¿Leímos bien tus datos?'), findsOneWidget);
        expect(fake.identityConfirmations, isEmpty);
      });

      testWidgets('correcting the DUI fields so they match the declared data also removes the warning', (tester) async {
        final fake = FakeApi(identityResponses: [(200, other)]);
        await goToConfirmation(tester, fake);
        await tester.enterText(find.byType(TextField).at(0), '048123775');
        await tester.enterText(find.byType(TextField).at(1), 'Marta Alejandra');
        await tester.enterText(find.byType(TextField).at(2), 'Rivas Cruz');
        await tester.pumpAndSettle();
        await tap(tester, confirmButton);
        expect(find.text('Tus datos no coinciden con tu DUI'), findsNothing);
        expect(fake.identityConfirmations, hasLength(1));
      });

      testWidgets('it only warns once the data is valid: a missing field is asked first', (tester) async {
        final fake = FakeApi(identityResponses: [(200, other)]);
        await goToConfirmation(tester, fake);
        await tester.enterText(find.byType(TextField).at(3), '');
        await tester.pumpAndSettle();
        await tap(tester, confirmButton);
        expect(find.text('Tus datos no coinciden con tu DUI'), findsNothing);
        expect(find.textContaining('Escribe tu fecha de nacimiento'), findsOneWidget);
      });
    });

    group('wait messages', () {
      Future<void> acceptAndContinue(WidgetTester tester) async {
        await tap(tester, find.text('Empezar'));
        await acceptNotice(tester);
      }

      Future<void> toBasicData(WidgetTester tester) async {
        await acceptAndContinue(tester);
        await tapContinue(tester);
        await skipLocation(tester);
        await fillBasicData(tester);
      }

      testWidgets('while the request is created it says «Preparando tu solicitud…» and goes away when it is ready',
          (tester) async {
        final fake = FakeApi()..hold('start');
        await tester.pumpWidget(app(fake));
        await acceptAndContinue(tester);
        await tester.tap(find.text('Aceptar y continuar'));
        await tester.pump();

        expect(find.text('Preparando tu solicitud…'), findsOneWidget);
        expect(find.text('Esto puede tardar unos segundos.'), findsOneWidget);
        fake.release('start');
        await tester.pumpAndSettle();
        expect(find.text('Preparando tu solicitud…'), findsNothing);
        expect(find.text('¿Desde qué ciudad nos escribes?'), findsOneWidget);
      });

      testWidgets('while the location is looked up it says «Buscando tu ciudad…»', (tester) async {
        final location = FakeLocation()..holdUntil = Completer<void>();
        await tester.pumpWidget(app(FakeApi(), location: location));
        await acceptAndContinue(tester);
        await tapContinue(tester);
        await tester.tap(find.text('Permitir ubicación aproximada'));
        await tester.pump();

        expect(find.text('Buscando tu ciudad…'), findsOneWidget);
        location.holdUntil!.complete();
        await tester.pumpAndSettle();
        expect(find.text('Buscando tu ciudad…'), findsNothing);
        expect(find.text('Cuéntanos quién eres'), findsOneWidget);
      });

      testWidgets('while the basic data is saved it says «Guardando tu información…»', (tester) async {
        final fake = FakeApi()..hold('basic-data');
        await tester.pumpWidget(app(fake));
        await toBasicData(tester);
        await tester.tap(find.text('Continuar'));
        await tester.pump();

        expect(find.text('Guardando tu información…'), findsOneWidget);
        expect(find.text('Un momento, por favor.'), findsOneWidget);
        fake.release('basic-data');
        await tester.pumpAndSettle();
        expect(find.text('Guardando tu información…'), findsNothing);
      });

      testWidgets('while the DUI photos are read it says «Revisando tu DUI…» with its detail', (tester) async {
        final fake = FakeApi()..hold('identity-document');
        await tester.pumpWidget(app(fake));
        await toBasicData(tester);
        await tapContinue(tester);
        await tap(tester, shutter);
        await tester.tap(shutter);
        await tester.pump();

        expect(find.text('Revisando tu DUI…'), findsOneWidget);
        expect(find.text('Estamos leyendo tus fotos. Puede tardar unos segundos.'), findsOneWidget);
        fake.release('identity-document');
        await tester.pumpAndSettle();
        expect(find.text('Revisando tu DUI…'), findsNothing);
        expect(find.text('¿Leímos bien tus datos?'), findsOneWidget);
      });

      testWidgets('while the request is submitted it says «Enviando tu solicitud…» and asks not to close the app',
          (tester) async {
        final fake = FakeApi();
        await tester.pumpWidget(app(fake));
        await toBasicData(tester);
        await tapContinue(tester);
        await passDui(tester);
        await tap(tester, find.text('Remesas'));
        await tap(tester, find.text('USD 500.01 a 1,000'));
        await tapContinue(tester);
        await tap(tester, find.text('Ahorro'));
        await tap(tester, find.text('Más de USD 1,000'));
        await tapContinue(tester);

        fake.hold('submit');
        await tester.tap(find.text('Enviar solicitud'));
        await tester.pump();
        expect(find.text('Enviando tu solicitud…'), findsOneWidget);
        expect(find.text('No cierres la aplicación.'), findsOneWidget);

        fake.release('submit');
        await tester.pumpAndSettle();
        expect(find.text('Enviando tu solicitud…'), findsNothing);
        expect(find.text('¡Recibimos tu solicitud!'), findsOneWidget);
      });

      testWidgets('after the last photo the camera screen behind the message does not break while the answer takes long',
          (tester) async {
        final fake = FakeApi()..hold('identity-document');
        final camera = FakeCamera();
        await tester.pumpWidget(app(fake, camera: camera));
        await toBasicData(tester);
        await tapContinue(tester);
        await tap(tester, shutter);
        await tester.tap(shutter);
        await tester.pump();
        await tester.pump(const Duration(seconds: 2));
        camera.hint(FrameHint.good); // the live hint keeps arriving and rebuilds the screen
        await tester.pump();

        expect(tester.takeException(), isNull);
        await tester.tap(shutter, warnIfMissed: false); // a third tap changes nothing
        await tester.pump();
        expect(camera.photos, 2);
        fake.release('identity-document');
        await tester.pumpAndSettle();
        expect(tester.takeException(), isNull);
      });

      testWidgets('the wait message is centered inside the screen margins', (tester) async {
        final fake = FakeApi()..hold('identity-document');
        await tester.pumpWidget(app(fake));
        await toBasicData(tester);
        await tapContinue(tester);
        await tap(tester, shutter);
        await tester.tap(shutter);
        await tester.pump();

        final detail = tester.getRect(find.text('Estamos leyendo tus fotos. Puede tardar unos segundos.'));
        expect(detail.left, greaterThanOrEqualTo(24 - 0.01));
        expect(detail.right, lessThanOrEqualTo(390 - 24 + 0.01));
        expect(detail.center.dx, closeTo(195, 1));
        fake.release('identity-document');
        await tester.pumpAndSettle();
      });
    });

    group('income screen (VDI-48)', () {
      Future<void> goToIncome(WidgetTester tester, FakeApi fake) async {
        await tester.pumpWidget(app(fake));
        await tap(tester, find.text('Empezar'));
        await acceptNotice(tester);
        await tapContinue(tester);
        await skipLocation(tester);
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

        await tap(tester, find.text('Reintentar'));
        expect(find.byType(OptionCard), findsNWidgets(9));
        expect(fake.catalogs, hasLength(2));
      });
    });

    group('saving the income (VDI-47)', () {
      Future<void> goToIncome(WidgetTester tester, FakeApi fake) async {
        await tester.pumpWidget(app(fake));
        await tap(tester, find.text('Empezar'));
        await acceptNotice(tester);
        await tapContinue(tester);
        await skipLocation(tester);
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

        await tap(tester, find.text('Reintentar'));
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
        await tap(tester, find.text('Empezar'));
        await acceptNotice(tester);
        await tapContinue(tester);
        await skipLocation(tester);
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

        await tap(tester, find.text('Reintentar'));
        expect(find.text('Paso 5 de 5'), findsOneWidget);
        expect(fake.activities, hasLength(2));
      });
    });

    group('picking up a request left halfway (VDI-87)', () {
      Future<FakeApi> openWith(
        WidgetTester tester,
        String step, {
        FakeSavedRequestStore? store,
        int status = 200,
        String? body,
      }) async {
        final fake = FakeApi(
          resumeResponse: (status, body ?? resumeJson(step)),
        );
        await tester.pumpWidget(
          app(fake, store: store ?? FakeSavedRequestStore(requestId)),
        );
        await tester.pumpAndSettle();
        return fake;
      }

      testWidgets(
        'the request id is kept in the phone when the notice is accepted',
        (tester) async {
          final store = FakeSavedRequestStore();
          await tester.pumpWidget(app(FakeApi(), store: store));
          await tap(tester, find.text('Empezar'));
          expect(
            store.id,
            isNull,
            reason: 'nothing is kept before the request exists',
          );
          await acceptNotice(tester);
          await tapContinue(tester);
          expect(store.id, requestId);
        },
      );

      testWidgets('without a saved request it does not ask the API', (
        tester,
      ) async {
        final fake = FakeApi();
        await tester.pumpWidget(app(fake));
        await tester.pumpAndSettle();
        expect(fake.resumes, isEmpty);
        expect(find.text('Empezar'), findsOneWidget);
      });

      testWidgets(
        'it offers to continue or to start over, and says where the person was',
        (tester) async {
          final fake = await openWith(tester, 'INCOME');
          expect(
            fake.resumes.single.url.path,
            '/api/onboarding/requests/$requestId/resume',
          );
          expect(find.text('¡Hola de nuevo, Marta!'), findsOneWidget);
          expect(find.textContaining('Marta'), findsWidgets);
          expect(find.textContaining('Tus ingresos'), findsWidgets);
          expect(find.text('Continuar donde quedé'), findsOneWidget);
          expect(find.text('Empezar de nuevo'), findsOneWidget);
          expect(find.text('Empezar'), findsNothing);
        },
      );

      testWidgets('the welcome lists the steps: the saved ones with their date, where it was left and the pending ones', (tester) async {
        await openWith(tester, 'INCOME');
        expect(find.text('Paso 3 de 5'), findsOneWidget);
        expect(find.textContaining('Guardado el'), findsNWidgets(2));
        expect(find.text('Aquí te quedaste'), findsOneWidget);
        expect(find.text('Pendiente'), findsNWidgets(2));
        expect(find.text('Si empiezas de nuevo, tendrás que escribir tus datos otra vez.'), findsOneWidget);
      });

      testWidgets('with the DUI unconfirmed the list puts the person back at the DUI, whatever step the API says', (tester) async {
        await openWith(tester, 'INCOME', body: resumeJson('INCOME', confirmedDui: false));
        expect(find.text('Paso 2 de 5'), findsOneWidget);
        expect(find.textContaining('Guardado el'), findsOneWidget);
        expect(find.text('Aquí te quedaste'), findsOneWidget);
        expect(find.text('Pendiente'), findsNWidgets(3));
      });

      testWidgets(
        'continuing goes to the step where it was, without creating another request or sending signals',
        (tester) async {
          final fake = await openWith(tester, 'INCOME');
          await tap(tester, find.text('Continuar donde quedé'));
          expect(find.text('Paso 3 de 5'), findsOneWidget);
          expect(fake.starts, isEmpty);
          expect(fake.privacyConsents, isEmpty);
          expect(
            fake.signals,
            isEmpty,
            reason: 'new signals would replace the ones already saved',
          );
        },
      );

      testWidgets('what was saved fills the screens again', (tester) async {
        await openWith(tester, 'BASIC_DATA');
        await tap(tester, find.text('Continuar donde quedé'));
        expect(find.text('Paso 1 de 5'), findsOneWidget);
        final fields = tester
            .widgetList<TextField>(find.byType(TextField))
            .toList();
        expect(fields.map((f) => f.controller?.text), [
          'Marta Alejandra',
          'Rivas Cruz',
          '04812377-5',
          '7845-2310',
        ]);
      });

      testWidgets(
        'at the review it shows what was saved and submits the same request',
        (tester) async {
          final store = FakeSavedRequestStore(requestId);
          final fake = await openWith(tester, 'REVIEW', store: store);
          await tap(tester, find.text('Continuar donde quedé'));
          expect(find.text('Revisa tu solicitud'), findsOneWidget);
          expect(find.text('Marta Alejandra Rivas Cruz'), findsOneWidget);
          expect(find.text('DUI 04812377-5 · Cel. 7845-2310'), findsOneWidget);
          expect(find.text('Salario'), findsOneWidget);
          expect(find.text('Ahorro'), findsOneWidget);
          expect(find.text('Tu DUI'), findsOneWidget);
          expect(find.text('Ya guardamos las fotos y los datos de tu DUI'), findsOneWidget);
          expect(find.text('Confirmado · las fotos no se guardan en tu teléfono'), findsOneWidget);

          await tap(tester, find.text('Enviar solicitud'));
          expect(
            fake.submits.single.url.path,
            '/api/onboarding/requests/$requestId/submit',
          );
          expect(find.text('¡Recibimos tu solicitud!'), findsOneWidget);
          expect(
            store.id,
            isNull,
            reason: 'a submitted request is not offered again',
          );
        },
      );

      testWidgets('at the DUI step it says the photos must be taken again, then the camera opens', (tester) async {
        await openWith(tester, 'IDENTITY_DOCUMENT');
        expect(find.textContaining('volver a tomar las fotos de tu DUI'), findsOneWidget, reason: 'the welcome already warns it');
        await tap(tester, find.text('Continuar donde quedé'));
        expect(find.text('Vuelve a tomar las fotos de tu DUI'), findsOneWidget);
        await tap(tester, find.text('Entendido'));
        expect(shutter, findsOneWidget);
      });

      testWidgets('if the DUI was never confirmed it does not skip it, even if the API moved on', (tester) async {
        await openWith(tester, 'INCOME', body: resumeJson('INCOME', confirmedDui: false));
        await tap(tester, find.text('Continuar donde quedé'));
        expect(find.text('Vuelve a tomar las fotos de tu DUI'), findsOneWidget);
        await tap(tester, find.text('Entendido'));
        expect(shutter, findsOneWidget);
        expect(find.text('Paso 3 de 5'), findsNothing);
      });

      testWidgets('with the DUI already confirmed it is not asked again and back skips its empty screens', (tester) async {
        await openWith(tester, 'INCOME');
        await tap(tester, find.text('Continuar donde quedé'));
        expect(find.text('Vuelve a tomar las fotos de tu DUI'), findsNothing);
        await tap(tester, find.byTooltip('Volver al paso anterior'));
        expect(find.text('Paso 1 de 5'), findsOneWidget);
      });

      testWidgets(
        '«Empezar de nuevo» abandons the request, forgets it and shows the normal welcome',
        (tester) async {
          final store = FakeSavedRequestStore(requestId);
          final fake = await openWith(tester, 'INCOME', store: store);
          await tap(tester, find.text('Empezar de nuevo'));
          expect(
            fake.abandons.single.url.path,
            '/api/onboarding/requests/$requestId/abandon',
          );
          expect(store.id, isNull);
          expect(find.text('¡Hola de nuevo, Marta!'), findsNothing);
          expect(find.text('Empezar'), findsOneWidget);
        },
      );

      testWidgets(
        'if abandoning fails (it was already closed) it starts over anyway',
        (tester) async {
          final store = FakeSavedRequestStore(requestId);
          final fake = FakeApi(
            resumeResponse: (200, resumeJson('INCOME')),
            abandonStatus: 409,
          );
          await tester.pumpWidget(app(fake, store: store));
          await tester.pumpAndSettle();
          await tap(tester, find.text('Empezar de nuevo'));
          expect(store.id, isNull);
          expect(find.text('Empezar'), findsOneWidget);
        },
      );

      for (final state in ['EXPIRED', 'COMPLETED', 'ABANDONED']) {
        testWidgets(
          'a $state request is forgotten and the normal welcome is shown',
          (tester) async {
            final store = FakeSavedRequestStore(requestId);
            await openWith(
              tester,
              '',
              store: store,
              body: resumeJson('', state: state),
            );
            expect(store.id, isNull);
            expect(find.text('¡Hola de nuevo, Marta!'), findsNothing);
            expect(find.text('Empezar'), findsOneWidget);
          },
        );
      }

      testWidgets('a request that does not exist is forgotten', (tester) async {
        final store = FakeSavedRequestStore(requestId);
        final fake = FakeApi();
        await tester.pumpWidget(app(fake, store: store));
        await tester.pumpAndSettle();
        expect(store.id, isNull);
        expect(find.text('Empezar'), findsOneWidget);
      });

      testWidgets(
        'without connection the welcome is shown and the request is kept for next time',
        (tester) async {
          final store = FakeSavedRequestStore(requestId);
          await openWith(
            tester,
            '',
            store: store,
            status: 503,
            body: '{"status":503}',
          );
          expect(store.id, requestId);
          expect(find.text('¡Hola de nuevo, Marta!'), findsNothing);
          expect(find.text('Empezar'), findsOneWidget);
        },
      );
    });

    testWidgets('Banco Tangamandapio brand: full logo on welcome and emblem on the steps', (tester) async {
      await tester.pumpWidget(app(FakeApi()));
      expect(find.byType(FullLogo), findsOneWidget);
      expect(find.byType(Logo), findsNothing, reason: 'the brand is not repeated in the header on the welcome screen');
      expect(find.text('Ceiba'), findsNothing);

      await tap(tester, find.text('Empezar'));
      expect(find.byType(Logo), findsOneWidget);
      expect(find.text('TANGAMANDAPIO'), findsOneWidget);
    });
  });
}
