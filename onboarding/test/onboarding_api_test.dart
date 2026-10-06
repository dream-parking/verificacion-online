import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

import 'package:onboarding/api/onboarding_api.dart';

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
}
