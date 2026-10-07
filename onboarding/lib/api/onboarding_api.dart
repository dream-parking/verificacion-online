import 'dart:convert';

import 'package:http/http.dart' as http;

import '../env.dart';
import '../senales/senales.dart';
import 'catalogos.dart';

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

  /// Opciones de origen y rango de ingresos, tipos de movimiento y rangos de monto.
  Future<Catalogos> catalogos() async => Catalogos.fromJson(await _enviar('GET', '/api/catalogs'));

  /// Inicia una solicitud y devuelve su `id`.
  Future<String> iniciarSolicitud() async {
    final json = await _enviar('POST', '/api/onboarding/requests');
    return json['id'] as String;
  }

  /// Registra la aceptación del aviso de privacidad, incluida la captura de señales.
  /// El servidor guarda la versión vigente del aviso y la IP desde la que se aceptó.
  Future<void> aceptarAviso(String solicitudId) async {
    await _enviar('PUT', '/api/onboarding/requests/$solicitudId/privacy-consent', cuerpo: {'signalsAccepted': true});
  }

  /// Paso de datos básicos: DUI con formato 00000000-0 y celular 0000-0000 que empiece con 6 o 7.
  /// Se puede volver a enviar para corregirlos mientras la solicitud está en progreso.
  Future<void> enviarDatosBasicos(String solicitudId, {
    required String nombres,
    required String apellidos,
    required String dui,
    required String celular,
  }) async {
    await _enviar('PUT', '/api/onboarding/requests/$solicitudId/basic-data', cuerpo: {
      'firstNames': nombres,
      'lastNames': apellidos,
      'dui': dui,
      'mobilePhone': celular,
    });
  }

  /// Envía la solicitud y devuelve el número que le asignó el servidor (SOL-AAAA-NNNNN, único).
  ///
  /// Si la solicitud ya estaba enviada (por ejemplo, se perdió la respuesta de un envío anterior y el
  /// cliente reintentó), devuelve el número que ya tiene en vez de fallar.
  Future<String> enviarSolicitud(String solicitudId) async {
    try {
      final json = await _enviar('POST', '/api/onboarding/requests/$solicitudId/submit');
      return json['number'] as String;
    } on ApiException catch (e) {
      if (e.status != 409) rethrow;
      final actual = await _enviar('GET', '/api/onboarding/requests/$solicitudId');
      final numero = actual['number'];
      if (actual['status'] == 'COMPLETED' && numero is String) return numero;
      rethrow;
    }
  }

  /// Paso de ingresos (VDI-47): códigos del catálogo; `detalle` solo cuando el origen es `OTRO`.
  /// El servidor guarda la fecha y hora de la declaración. Se puede volver a enviar para corregirla
  /// mientras la solicitud está en progreso; después responde 409.
  Future<void> declararIngresos(String solicitudId, {
    required String origen,
    required String rango,
    String? detalle,
  }) async {
    await _enviar('PUT', '/api/onboarding/requests/$solicitudId/income', cuerpo: {
      'sourceCode': origen,
      'rangeCode': rango,
      'sourceDetail': ?detalle,
    });
  }

  /// Paso de movimiento esperado (VDI-51): códigos del catálogo, sin monto libre.
  /// La API responde el score de riesgo; al cliente no se le muestra.
  Future<void> declararMovimiento(String solicitudId, {required String tipo, required String rangoMonto}) async {
    await _enviar('PUT', '/api/onboarding/requests/$solicitudId/expected-activity', cuerpo: {
      'transactionTypeCode': tipo,
      'monthlyAmountRangeCode': rangoMonto,
    });
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
