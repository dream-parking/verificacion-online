/// Build-time environment, injected with `--dart-define=APP_ENV=dev|qa`.
const appEnv = String.fromEnvironment('APP_ENV', defaultValue: 'local');

/// Base URL of the API, injected with `--dart-define=API_URL=...` (VDI-141). There is no fallback on purpose: a build
/// without it would silently call another environment. Use [requireApiUrl] to read it.
const apiBaseUrl = String.fromEnvironment('API_URL');

final _localHost = RegExp(r'^https?://(localhost|127\.0\.0\.1|\[::1\]|10\.0\.2\.2)(:|/|$)', caseSensitive: false);

/// The API URL to use, or a [StateError] that says how to define it. A Dev or QA build must not point to the
/// developer's machine (`localhost`, or `10.0.2.2`, which is the Mac as seen from the Android emulator).
String requireApiUrl(String url, {String env = appEnv}) {
  final value = url.trim();
  if (value.isEmpty) {
    throw StateError(
      'API_URL is missing. The app has no fallback URL: pass --dart-define=API_URL=<api url> '
      '(see onboarding/README.md).',
    );
  }
  if ((env == 'dev' || env == 'qa') && _localHost.hasMatch(value)) {
    throw StateError('API_URL points to the local machine ($value) in a $env build.');
  }
  return value;
}
