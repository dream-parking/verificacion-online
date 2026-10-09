import 'dart:convert';

import 'package:http/http.dart' as http;

import '../env.dart';
import '../signals/signals.dart';
import 'catalogs.dart';

/// Error talking to the API: no connection, timeout or an error response.
class ApiException implements Exception {
  const ApiException(this.message, {this.status, this.fields = const []});

  /// Technical detail from the server, in English: for logs only, never shown to the customer.
  final String message;
  final int? status;

  /// Fields the API rejected (`errors` of a 400), for example `firstNames`.
  final List<String> fields;

  @override
  String toString() => 'ApiException($status): $message';
}

/// Client for `/api/onboarding/requests` (see docs/integracion-api.md and docs/openapi.json).
class OnboardingApi {
  OnboardingApi({http.Client? client, String baseUrl = apiBaseUrl})
    : _client = client ?? http.Client(),
      _base = Uri.parse(baseUrl);

  final http.Client _client;
  final Uri _base;

  /// The Dev/QA API scales to zero: the first request can take ~30-60 s.
  static const _timeout = Duration(seconds: 60);

  /// Income sources and ranges, transaction types and monthly amount ranges.
  Future<Catalogs> catalogs() async => Catalogs.fromJson(await _send('GET', '/api/catalogs'));

  /// Starts a request and returns its `id`.
  Future<String> startRequest() async {
    final json = await _send('POST', '/api/onboarding/requests');
    return json['id'] as String;
  }

  /// Records the acceptance of the privacy notice, including the capture of signals.
  /// The server stores the notice version in force and the IP it was accepted from.
  Future<void> acceptPrivacyNotice(String requestId) async {
    await _send('PUT', '/api/onboarding/requests/$requestId/privacy-consent', body: {'signalsAccepted': true});
  }

  /// Basic data step: DUI formatted 00000000-0 and a 0000-0000 mobile number starting with 6 or 7.
  /// It can be sent again to correct it while the request is in progress.
  Future<void> sendBasicData(String requestId, {
    required String firstNames,
    required String lastNames,
    required String dui,
    required String mobilePhone,
  }) async {
    await _send('PUT', '/api/onboarding/requests/$requestId/basic-data', body: {
      'firstNames': firstNames,
      'lastNames': lastNames,
      'dui': dui,
      'mobilePhone': mobilePhone,
    });
  }

  /// Submits the request and returns the number the server assigned to it (SOL-YYYY-NNNNN, unique).
  ///
  /// If the request was already submitted (for example, the response to a previous submit was lost
  /// and the client retried), it returns the number the request already has instead of failing.
  Future<String> submitRequest(String requestId) async {
    try {
      final json = await _send('POST', '/api/onboarding/requests/$requestId/submit');
      return json['number'] as String;
    } on ApiException catch (e) {
      if (e.status != 409) rethrow;
      final current = await _send('GET', '/api/onboarding/requests/$requestId');
      final number = current['number'];
      if (current['status'] == 'COMPLETED' && number is String) return number;
      rethrow;
    }
  }

  /// Income step (VDI-47): catalog codes; `detail` only when the source is `OTRO`.
  /// The server stores the date and time of the declaration. It can be sent again to correct it
  /// while the request is in progress; afterwards it responds 409.
  Future<void> declareIncome(String requestId, {
    required String source,
    required String range,
    String? detail,
  }) async {
    await _send('PUT', '/api/onboarding/requests/$requestId/income', body: {
      'sourceCode': source,
      'rangeCode': range,
      'sourceDetail': ?detail,
    });
  }

  /// Expected activity step (VDI-51): catalog codes, no free amount.
  /// The API responds with the risk score; it is not shown to the customer.
  Future<void> declareExpectedActivity(String requestId, {
    required String transactionType,
    required String amountRange,
  }) async {
    await _send('PUT', '/api/onboarding/requests/$requestId/expected-activity', body: {
      'transactionTypeCode': transactionType,
      'monthlyAmountRangeCode': amountRange,
    });
  }

  /// Saves the request's signals. The server takes the IP address and the user agent.
  Future<void> sendSignals(String requestId, Signals signals) async {
    await _send('PUT', '/api/onboarding/requests/$requestId/signals', body: signals.toJson());
  }

  Future<Map<String, dynamic>> _send(String method, String path, {Object? body}) async {
    final req = http.Request(method, _base.resolve(path))..headers['Accept'] = 'application/json';
    if (body != null) {
      req.headers['Content-Type'] = 'application/json';
      req.body = jsonEncode(body);
    }

    final http.Response res;
    try {
      res = await http.Response.fromStream(await _client.send(req).timeout(_timeout));
    } on Exception catch (e) {
      throw ApiException('Could not connect to the server ($e)');
    }

    if (res.statusCode < 200 || res.statusCode >= 300) {
      throw ApiException(_detail(res), status: res.statusCode, fields: _fields(res));
    }
    if (res.body.isEmpty) return const {};
    return jsonDecode(utf8.decode(res.bodyBytes)) as Map<String, dynamic>;
  }

  /// Names of the invalid fields of a validation error (`errors` in the problem+json).
  static List<String> _fields(http.Response res) {
    try {
      final errors = (jsonDecode(utf8.decode(res.bodyBytes)) as Map<String, dynamic>)['errors'];
      return errors is Map ? errors.keys.map((k) => k.toString()).toList() : const [];
    } on FormatException {
      return const [];
    }
  }

  /// Errors come as `application/problem+json` with `detail`.
  static String _detail(http.Response res) {
    try {
      final json = jsonDecode(utf8.decode(res.bodyBytes)) as Map<String, dynamic>;
      return (json['detail'] ?? json['title'] ?? 'Error ${res.statusCode}').toString();
    } on FormatException {
      return 'Error ${res.statusCode}';
    }
  }
}
