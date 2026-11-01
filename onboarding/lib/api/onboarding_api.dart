import 'dart:convert';
import 'dart:typed_data';

import 'package:http/http.dart' as http;

import '../env.dart';
import '../identity/identity_document.dart';
import '../onboarding/application_form.dart';
import '../signals/signals.dart';
import 'catalogs.dart';
import 'resume.dart';

/// Error talking to the API: no connection, timeout or an error response.
class ApiException implements Exception {
  const ApiException(this.message, {this.status, this.fields = const [], this.reason, this.side});

  /// Technical detail from the server, in English: for logs only, never shown to the customer.
  final String message;
  final int? status;

  /// Fields the API rejected (`errors` of a 400), for example `firstNames`.
  final List<String> fields;

  /// Why the DUI photos could not be read and which ones to take again (`reason` and `side` of a 422).
  final String? reason;
  final String? side;

  @override
  String toString() => 'ApiException($status): $message';
}

/// How a submitted request was left (`status` of `POST /submit`). The app only tells the person what happens next:
/// never the score or why.
enum SubmissionStatus {
  /// Received as usual (`COMPLETED`).
  completed,

  /// Held for a person of the bank to review it (`ENHANCED_REVIEW`, VDI-118).
  enhancedReview,

  /// The identity could not be verified yet and the API keeps trying (`AWAITING_RNPN`, VDI-112). The person repeats
  /// nothing, and the app never names the registry it is checked against.
  awaitingVerification;

  /// `null` while the request was not submitted (`IN_PROGRESS`) or was abandoned. A status this version of the app
  /// does not know yet is told as [completed]: the request was received.
  static SubmissionStatus? fromApi(Object? status) => switch (status) {
    'ENHANCED_REVIEW' => enhancedReview,
    'AWAITING_RNPN' => awaitingVerification,
    null || 'IN_PROGRESS' || 'ABANDONED' => null,
    _ => completed,
  };
}

/// A submitted request: its number and how it was left.
class Submission {
  const Submission(this.number, this.status);

  factory Submission.fromJson(Map<String, dynamic> json) =>
      Submission(json['number'] as String, SubmissionStatus.fromApi(json['status']) ?? SubmissionStatus.completed);

  final String number;
  final SubmissionStatus status;
}

/// Answer of `PUT /liveness` (VDI-116). With [checked] false the provider was not available: the request goes on and
/// the selfie does not count in the score. Either way the person is told the same.
class LivenessResult {
  const LivenessResult({required this.checked, required this.attempts});

  factory LivenessResult.fromJson(Map<String, dynamic> json) =>
      LivenessResult(checked: json['status'] == 'CHECKED', attempts: (json['attempts'] as num?)?.toInt() ?? 1);

  final bool checked;

  /// Selfies the API judged in this request.
  final int attempts;
}

/// The contract in force (`GET /contract`, VDI-130): the text to show and the version to sign. The fingerprint stays in
/// the API, which computes it again when the signature arrives.
class Contract {
  const Contract({required this.version, required this.text});

  factory Contract.fromJson(Map<String, dynamic> json) =>
      Contract(version: json['version'] as String, text: json['text'] as String);

  final String version;
  final String text;
}

/// Answer of `POST /signature` (VDI-130). The provider, the fingerprint and the simulation flag are not shown.
class ContractSignature {
  const ContractSignature({required this.contractVersion, required this.signedAt});

  factory ContractSignature.fromJson(Map<String, dynamic> json) => ContractSignature(
    contractVersion: json['contractVersion'] as String,
    signedAt: DateTime.parse(json['signedAt'] as String),
  );

  final String contractVersion;
  final DateTime signedAt;
}

/// Client for `/api/onboarding/requests` (see docs/integracion-api.md and docs/openapi.json).
class OnboardingApi {
  OnboardingApi({http.Client? client, String? baseUrl})
    : _client = client ?? http.Client(),
      _base = Uri.parse(requireApiUrl(baseUrl ?? apiBaseUrl));

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

  /// Submits the request and returns the number the server assigned to it (SOL-YYYY-NNNNN, unique) and how it was
  /// left.
  ///
  /// If the request was already submitted (for example, the response to a previous submit was lost
  /// and the client retried), it returns the number the request already has instead of failing.
  Future<Submission> submitRequest(String requestId) async {
    try {
      return Submission.fromJson(await _send('POST', '/api/onboarding/requests/$requestId/submit'));
    } on ApiException catch (e) {
      if (e.status != 409) rethrow;
      final current = await _send('GET', '/api/onboarding/requests/$requestId');
      if (current['number'] is String && SubmissionStatus.fromApi(current['status']) != null) {
        return Submission.fromJson(current);
      }
      rethrow;
    }
  }

  /// How a request that was submitted stands now (`GET /requests/{id}`): its number and status, which is all the app
  /// reads (the answer carries more for the console, and it is ignored). `null` when it is still in progress or was
  /// abandoned. Throws an [ApiException] 404 if it does not exist.
  Future<Submission?> submission(String requestId) async {
    final json = await _send('GET', '/api/onboarding/requests/$requestId');
    final status = SubmissionStatus.fromApi(json['status']);
    final number = json['number'];
    return status == null || number is! String ? null : Submission(number, status);
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

  /// DUI step (VDI-77): sends both photos together (JPEG or PNG, up to 5 MB each) and returns what was read
  /// so the person can confirm it. If the photos cannot be read it throws an [ApiException] 422 with `reason`
  /// and `side`; it can be repeated while the request is in progress.
  Future<IdentityReading> uploadIdentityDocument(String requestId, {required Uint8List front, required Uint8List back}) async {
    final req = http.MultipartRequest('PUT', _base.resolve('/api/onboarding/requests/$requestId/identity-document'))
      ..headers['Accept'] = 'application/json'
      ..files.add(http.MultipartFile.fromBytes('front', front, filename: 'front.jpg'))
      ..files.add(http.MultipartFile.fromBytes('back', back, filename: 'back.jpg'));
    return IdentityReading.fromJson(await _deliver(req));
  }

  /// DUI step (VDI-78): the data the person confirmed or corrected on «¿Leímos bien tus datos?». It completes the
  /// step. The API keeps what was read next to what was confirmed and compares it with the basic data.
  Future<void> confirmIdentityDocument(String requestId, {
    required String dui,
    required String firstNames,
    required String lastNames,
    required DateTime birthDate,
    required DateTime expiryDate,
  }) async {
    await _send('PUT', '/api/onboarding/requests/$requestId/identity-document/confirmation', body: {
      'dui': dui,
      'firstNames': firstNames,
      'lastNames': lastNames,
      'birthDate': dateForApi(birthDate),
      'expiryDate': dateForApi(expiryDate),
    });
  }

  /// Selfie with the liveness check (VDI-116): JPEG up to 5 MB. Returns whether the provider checked it; the level of
  /// confidence stays in the API. A selfie that cannot be judged because of how it was taken throws an
  /// [ApiException] 422 with `reason`, and does not count as an attempt.
  Future<LivenessResult> checkLiveness(String requestId, Uint8List selfie) async {
    final req = http.MultipartRequest('PUT', _base.resolve('/api/onboarding/requests/$requestId/liveness'))
      ..headers['Accept'] = 'application/json'
      ..files.add(http.MultipartFile.fromBytes('selfie', selfie, filename: 'selfie.jpg'));
    return LivenessResult.fromJson(await _deliver(req));
  }

  /// VDI-132: the contract text in force, to show before signing.
  Future<Contract> contract() async => Contract.fromJson(await _send('GET', '/api/onboarding/contract'));

  /// VDI-132: signs the [contractVersion] the person was shown. A 409 means the request already signed, is not
  /// approved or the version is no longer in force; a 422 means the signature service rejected it and the person can
  /// try again.
  Future<ContractSignature> signContract(String requestId, String contractVersion) async => ContractSignature.fromJson(
    await _send('POST', '/api/onboarding/requests/$requestId/signature', body: {'contractVersion': contractVersion}),
  );

  /// VDI-87: where a request was left and what was saved. Throws an [ApiException] 404 if it does not exist.
  Future<ResumeInfo> resume(String requestId) async =>
      ResumeInfo.fromJson(await _send('GET', '/api/onboarding/requests/$requestId/resume'));

  /// VDI-87: «Empezar de nuevo». Leaves the request abandoned; 409 if it is no longer in progress.
  Future<void> abandon(String requestId) async {
    await _send('POST', '/api/onboarding/requests/$requestId/abandon');
  }

  Future<Map<String, dynamic>> _send(String method, String path, {Object? body}) async {
    final req = http.Request(method, _base.resolve(path))..headers['Accept'] = 'application/json';
    if (body != null) {
      req.headers['Content-Type'] = 'application/json';
      req.body = jsonEncode(body);
    }
    return _deliver(req);
  }

  Future<Map<String, dynamic>> _deliver(http.BaseRequest req) async {
    final http.Response res;
    try {
      res = await http.Response.fromStream(await _client.send(req).timeout(_timeout));
    } on Exception catch (e) {
      throw ApiException('Could not connect to the server ($e)');
    }

    if (res.statusCode < 200 || res.statusCode >= 300) {
      final problem = _problem(res);
      throw ApiException(
        _detail(res),
        status: res.statusCode,
        fields: _fields(res),
        reason: problem['reason'] as String?,
        side: problem['side'] as String?,
      );
    }
    if (res.body.isEmpty) return const {};
    return jsonDecode(utf8.decode(res.bodyBytes)) as Map<String, dynamic>;
  }

  static Map<String, dynamic> _problem(http.Response res) {
    try {
      return jsonDecode(utf8.decode(res.bodyBytes)) as Map<String, dynamic>;
    } on FormatException {
      return const {};
    }
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
