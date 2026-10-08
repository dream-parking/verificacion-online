/// Build-time environment, injected with `--dart-define=APP_ENV=dev|qa`.
const appEnv = String.fromEnvironment('APP_ENV', defaultValue: 'local');

/// Base URL of the API. It can be forced with `--dart-define=API_URL=http://10.0.2.2:8080`
/// (backend running on the Mac, as seen from the Android emulator).
/// Without `API_URL`, `local` uses the Dev API so the backend does not have to be running.
const apiBaseUrl = String.fromEnvironment(
  'API_URL',
  defaultValue: appEnv == 'qa'
      ? 'https://api.qa.identidad.alambritos.online'
      : 'https://api.dev.identidad.alambritos.online',
);
