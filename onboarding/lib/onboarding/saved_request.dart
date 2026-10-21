import 'package:shared_preferences/shared_preferences.dart';

/// The `id` of the request in progress, kept in the phone so the person can pick it up when the app opens again
/// (VDI-87). It is only an identifier: nothing personal is stored.
abstract class SavedRequestStore {
  Future<String?> read();

  Future<void> save(String id);

  /// Whether the selfie of request [id] was already received (VDI-116). The API does not report it when a request is
  /// picked up, and taking it again would count as one more attempt.
  Future<bool> selfieTaken(String id);

  Future<void> saveSelfieTaken(String id);

  /// Forgets the request and its selfie.
  Future<void> clear();
}

/// Keeps the `id` in the app preferences.
class PreferencesSavedRequestStore implements SavedRequestStore {
  const PreferencesSavedRequestStore();

  static const _key = 'onboarding.request_id';

  /// The `id` of the request whose selfie was received.
  static const _selfieKey = 'onboarding.selfie_request_id';

  @override
  Future<String?> read() async =>
      (await SharedPreferences.getInstance()).getString(_key);

  @override
  Future<void> save(String id) async =>
      (await SharedPreferences.getInstance()).setString(_key, id);

  @override
  Future<bool> selfieTaken(String id) async =>
      (await SharedPreferences.getInstance()).getString(_selfieKey) == id;

  @override
  Future<void> saveSelfieTaken(String id) async =>
      (await SharedPreferences.getInstance()).setString(_selfieKey, id);

  @override
  Future<void> clear() async {
    final preferences = await SharedPreferences.getInstance();
    await preferences.remove(_key);
    await preferences.remove(_selfieKey);
  }
}
