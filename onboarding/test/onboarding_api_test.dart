import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

import 'package:onboarding/api/onboarding_api.dart';
import 'package:onboarding/senales/huella_dispositivo.dart';
import 'package:onboarding/senales/senales.dart';

void main() {
  test('iniciarSolicitud devuelve el id', () async {
    final api = OnboardingApi(
      baseUrl: 'https://api.test',
      client: MockClient((req) async {
        expect(req.method, 'POST');
        expect(req.url.toString(), 'https://api.test/api/onboarding/requests');
        return http.Response('{"id":"abc","status":"IN_PROGRESS"}', 201);
      }),
    );
    expect(await api.iniciarSolicitud(), 'abc');
  });

  test('un error HTTP se convierte en ApiException con el detail', () async {
    final api = OnboardingApi(
      baseUrl: 'https://api.test',
      // El backend responde en UTF-8 (application/problem+json).
      client: MockClient(
        (_) async => http.Response.bytes(utf8.encode('{"status":409,"detail":"Ya no está en progreso"}'), 409),
      ),
    );
    expect(
      api.iniciarSolicitud(),
      throwsA(isA<ApiException>()
          .having((e) => e.status, 'status', 409)
          .having((e) => e.mensaje, 'mensaje', 'Ya no está en progreso')),
    );
  });

  test('sin conexión lanza ApiException', () async {
    final api = OnboardingApi(
      baseUrl: 'https://api.test',
      client: MockClient((_) async => throw http.ClientException('sin red')),
    );
    expect(api.iniciarSolicitud(), throwsA(isA<ApiException>()));
  });

  test('enviarSenales hace PUT con el JSON de las señales', () async {
    late http.Request enviada;
    final api = OnboardingApi(
      baseUrl: 'https://api.test',
      client: MockClient((req) async {
        enviada = req;
        return http.Response('', 204);
      }),
    );
    final senales = Senales()
      ..dispositivo = const DatosDispositivo(
        huella: 'd4f1·9a3c·e7b2',
        modelo: 'iPhone 15',
        sistemaOperativo: 'iOS 18.1',
        versionApp: '1.0.0+1',
      );
    await api.enviarSenales('abc', senales);
    expect(enviada.method, 'PUT');
    expect(enviada.url.toString(), 'https://api.test/api/onboarding/requests/abc/signals');
    expect(enviada.headers['Content-Type'], startsWith('application/json'));
    expect(jsonDecode(enviada.body)['deviceFingerprint'], 'd4f1·9a3c·e7b2');
  });

  test('catalogos lee las cuatro listas con código y etiqueta', () async {
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
    final c = await api.catalogos();
    expect(c.origenesIngreso.single.valor, 'PENSION');
    expect(c.origenesIngreso.single.etiqueta, 'Pensión');
    expect(c.rangosIngreso.single.valor, 'HASTA_500');
    expect(c.tiposMovimiento, isEmpty);
  });

  test('declararIngresos manda sourceDetail solo si hay detalle', () async {
    final cuerpos = <Object?>[];
    final api = OnboardingApi(
      baseUrl: 'https://api.test',
      client: MockClient((req) async {
        expect(req.method, 'PUT');
        expect(req.url.path, '/api/onboarding/requests/abc/income');
        cuerpos.add(jsonDecode(req.body));
        return http.Response('', 204);
      }),
    );
    await api.declararIngresos('abc', origen: 'SALARIO', rango: 'HASTA_500');
    await api.declararIngresos('abc', origen: 'OTRO', rango: 'MAS_2500', detalle: 'Herencia');
    expect(cuerpos, [
      {'sourceCode': 'SALARIO', 'rangeCode': 'HASTA_500'},
      {'sourceCode': 'OTRO', 'rangeCode': 'MAS_2500', 'sourceDetail': 'Herencia'},
    ]);
  });

  test('declararMovimiento manda el tipo y el rango de monto', () async {
    late http.Request enviada;
    final api = OnboardingApi(
      baseUrl: 'https://api.test',
      client: MockClient((req) async {
        enviada = req;
        return http.Response('{"level":"LOW"}', 200);
      }),
    );
    await api.declararMovimiento('abc', tipo: 'AHORRO', rangoMonto: 'HASTA_200');
    expect(enviada.method, 'PUT');
    expect(enviada.url.path, '/api/onboarding/requests/abc/expected-activity');
    expect(jsonDecode(enviada.body), {'transactionTypeCode': 'AHORRO', 'monthlyAmountRangeCode': 'HASTA_200'});
  });

  test('aceptarAviso registra la aceptación con la captura de señales', () async {
    late http.Request enviada;
    final api = OnboardingApi(
      baseUrl: 'https://api.test',
      client: MockClient((req) async {
        enviada = req;
        return http.Response('{"completedSteps":1}', 200);
      }),
    );
    await api.aceptarAviso('abc');
    expect(enviada.method, 'PUT');
    expect(enviada.url.path, '/api/onboarding/requests/abc/privacy-consent');
    expect(jsonDecode(enviada.body), {'signalsAccepted': true});
  });

  test('enviarDatosBasicos manda nombres, apellidos, DUI y celular', () async {
    late http.Request enviada;
    final api = OnboardingApi(
      baseUrl: 'https://api.test',
      client: MockClient((req) async {
        enviada = req;
        return http.Response('{"completedSteps":2}', 200);
      }),
    );
    await api.enviarDatosBasicos('abc', nombres: 'Ana', apellidos: 'Pérez', dui: '01234567-8', celular: '7123-4567');
    expect(enviada.method, 'PUT');
    expect(enviada.url.path, '/api/onboarding/requests/abc/basic-data');
    expect(jsonDecode(utf8.decode(enviada.bodyBytes)),
        {'firstNames': 'Ana', 'lastNames': 'Pérez', 'dui': '01234567-8', 'mobilePhone': '7123-4567'});
  });

  test('enviarSolicitud devuelve el número que asigna el servidor', () async {
    final api = OnboardingApi(
      baseUrl: 'https://api.test',
      client: MockClient((req) async {
        expect(req.method, 'POST');
        expect(req.url.path, '/api/onboarding/requests/abc/submit');
        return http.Response('{"number":"SOL-2026-00419","status":"COMPLETED"}', 200);
      }),
    );
    expect(await api.enviarSolicitud('abc'), 'SOL-2026-00419');
  });

  test('si la solicitud ya estaba enviada, enviarSolicitud devuelve su número', () async {
    final api = OnboardingApi(
      baseUrl: 'https://api.test',
      client: MockClient((req) async => req.method == 'POST'
          ? http.Response('{"status":409,"detail":"Onboarding request abc is COMPLETED"}', 409)
          : http.Response('{"number":"SOL-2026-00419","status":"COMPLETED"}', 200)),
    );
    expect(await api.enviarSolicitud('abc'), 'SOL-2026-00419');
  });

  test('si faltan pasos, enviarSolicitud falla con el detalle del servidor', () async {
    final api = OnboardingApi(
      baseUrl: 'https://api.test',
      client: MockClient((req) async => req.method == 'POST'
          ? http.Response('{"status":409,"detail":"Complete the 4 previous steps before submitting"}', 409)
          : http.Response('{"number":null,"status":"IN_PROGRESS"}', 200)),
    );
    expect(
      api.enviarSolicitud('abc'),
      throwsA(isA<ApiException>().having((e) => e.mensaje, 'mensaje', 'Complete the 4 previous steps before submitting')),
    );
  });
}
