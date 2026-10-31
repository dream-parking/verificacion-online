import 'package:shared_preferences/shared_preferences.dart';

import '../api/onboarding_api.dart';

/// What the phone remembers of a request that was already submitted, so the app can show it when it opens again
/// instead of the welcome. The number and how it was left are not personal data; [signed] says the contract was signed
/// from this phone.
class SubmittedRequest {
  const SubmittedRequest(this.number, this.status, {this.signed = false});

  final String number;
  final SubmissionStatus status;
  final bool signed;
}

/// The `id` of the request in progress, kept in the phone so the person can pick it up when the app opens again
/// (VDI-87). It is only an identifier: nothing personal is stored. Once the request is submitted it also keeps its
/// number and status, to show them when the app opens again.
abstract class SavedRequestStore {
  Future<String?> read();

  /// Keeps [id] as the request of this phone and forgets what was kept of a submitted one.
  Future<void> save(String id);

  /// Whether the selfie of request [id] was already received (VDI-116). The API does not report it when a request is
  /// picked up, and taking it again would count as one more attempt.
  Future<bool> selfieTaken(String id);

  Future<void> saveSelfieTaken(String id);

  /// What was kept of request [id] after it was submitted; `null` if it was not.
  Future<SubmittedRequest?> submitted(String id);

  /// Keeps request [id] as submitted, with its number and how it was left. The selfie is no longer needed.
  Future<void> saveSubmitted(String id, SubmittedRequest request);

  /// Forgets the request, its selfie and what was kept after submitting it.
  Future<void> clear();
}

/// Keeps the `id` in the app preferences.
class PreferencesSavedRequestStore implements SavedRequestStore {
  const PreferencesSavedRequestStore();

  static const _key = 'onboarding.request_id';

  /// The `id` of the request whose selfie was received.
  static const _selfieKey = 'onboarding.selfie_request_id';

  // What is kept of the submitted request; `_submittedKey` holds the `id` it belongs to.
  static const _submittedKey = 'onboarding.submitted_request_id';
  static const _numberKey = 'onboarding.submitted_number';
  static const _statusKey = 'onboarding.submitted_status';
  static const _signedKey = 'onboarding.submitted_signed';

  @override
  Future<String?> read() async =>
      (await SharedPreferences.getInstance()).getString(_key);

  @override
  Future<void> save(String id) async {
    final preferences = await SharedPreferences.getInstance();
    await _forgetSubmitted(preferences);
    await preferences.setString(_key, id);
  }

  @override
  Future<bool> selfieTaken(String id) async =>
      (await SharedPreferences.getInstance()).getString(_selfieKey) == id;

  @override
  Future<void> saveSelfieTaken(String id) async =>
      (await SharedPreferences.getInstance()).setString(_selfieKey, id);

  @override
  Future<SubmittedRequest?> submitted(String id) async {
    final preferences = await SharedPreferences.getInstance();
    final number = preferences.getString(_numberKey);
    if (number == null || preferences.getString(_submittedKey) != id) return null;
    final status = SubmissionStatus.values.asNameMap()[preferences.getString(_statusKey)] ?? SubmissionStatus.completed;
    return SubmittedRequest(number, status, signed: preferences.getBool(_signedKey) ?? false);
  }

  @override
  Future<void> saveSubmitted(String id, SubmittedRequest request) async {
    final preferences = await SharedPreferences.getInstance();
    await preferences.setString(_key, id);
    await preferences.setString(_submittedKey, id);
    await preferences.setString(_numberKey, request.number);
    await preferences.setString(_statusKey, request.status.name);
    await preferences.setBool(_signedKey, request.signed);
    await preferences.remove(_selfieKey);
  }

  @override
  Future<void> clear() async {
    final preferences = await SharedPreferences.getInstance();
    await preferences.remove(_key);
    await preferences.remove(_selfieKey);
    await _forgetSubmitted(preferences);
  }

  Future<void> _forgetSubmitted(SharedPreferences preferences) async {
    for (final key in [_submittedKey, _numberKey, _statusKey, _signedKey]) {
      await preferences.remove(key);
    }
  }
}
