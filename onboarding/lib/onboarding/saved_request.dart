import 'package:shared_preferences/shared_preferences.dart';

/// The `id` of the request in progress, kept in the phone so the person can pick it up when the app opens again
/// (VDI-87). It is only an identifier: nothing personal is stored.
abstract class SavedRequestStore {
  Future<String?> read();

  Future<void> save(String id);

  Future<void> clear();
}

/// Keeps the `id` in the app preferences.
class PreferencesSavedRequestStore implements SavedRequestStore {
  const PreferencesSavedRequestStore();

  static const _key = 'onboarding.request_id';

  @override
  Future<String?> read() async =>
      (await SharedPreferences.getInstance()).getString(_key);

  @override
  Future<void> save(String id) async =>
      (await SharedPreferences.getInstance()).setString(_key, id);

  @override
  Future<void> clear() async =>
      (await SharedPreferences.getInstance()).remove(_key);
}
