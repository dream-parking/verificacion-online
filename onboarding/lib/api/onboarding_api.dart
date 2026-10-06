import 'dart:convert';

import 'package:http/http.dart' as http;

import '../env.dart';
import '../senales/senales.dart';

/// Error al hablar con la API: sin conexión, tiempo agotado o respuesta con error.
class ApiException implements Exception {
  const ApiException(this.mensaje, {this.status});

  final String mensaje;
  final int? status;

  @override
  String toString() => 'ApiException($status): $mensaje';
}

/// Cliente de `/api/onboarding/requests` (ver docs/integracion-api.md y docs/openapi.json).
class OnboardingApi {
  OnboardingApi({http.Client? client, String baseUrl = apiBaseUrl})
    : _client = client ?? http.Client(),
      _base = Uri.parse(baseUrl);

  final http.Client _client;
  final Uri _base;

  /// La API de Dev/QA escala a cero: la primera petición puede tardar ~30-60 s.
  static const _timeout = Duration(seconds: 60);

  /// Inicia una solicitud y devuelve su `id`.
  Future<String> iniciarSolicitud() async {
    final json = await _enviar('POST', '/api/onboarding/requests');
    return json['id'] as String;
  }

  /// Guarda las señales de la solicitud. La IP y el user agent los toma el servidor.
  Future<void> enviarSenales(String solicitudId, Senales senales) async {
    await _enviar('PUT', '/api/onboarding/requests/$solicitudId/signals', cuerpo: senales.toJson());
  }

  Future<Map<String, dynamic>> _enviar(String metodo, String ruta, {Object? cuerpo}) async {
    final req = http.Request(metodo, _base.resolve(ruta))..headers['Accept'] = 'application/json';
    if (cuerpo != null) {
      req.headers['Content-Type'] = 'application/json';
      req.body = jsonEncode(cuerpo);
    }

    final http.Response res;
    try {
      res = await http.Response.fromStream(await _client.send(req).timeout(_timeout));
    } on Exception catch (e) {
      throw ApiException('No se pudo conectar con el servidor ($e)');
    }

    if (res.statusCode < 200 || res.statusCode >= 300) {
      throw ApiException(_detalle(res), status: res.statusCode);
    }
    if (res.body.isEmpty) return const {};
    return jsonDecode(utf8.decode(res.bodyBytes)) as Map<String, dynamic>;
  }

  /// Los errores vienen como `application/problem+json` con `detail`.
  static String _detalle(http.Response res) {
    try {
      final json = jsonDecode(utf8.decode(res.bodyBytes)) as Map<String, dynamic>;
      return (json['detail'] ?? json['title'] ?? 'Error ${res.statusCode}').toString();
    } on FormatException {
      return 'Error ${res.statusCode}';
    }
  }
}
