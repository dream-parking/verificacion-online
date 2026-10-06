/// Build-time environment, injected with `--dart-define=APP_ENV=dev|qa`.
const appEnv = String.fromEnvironment('APP_ENV', defaultValue: 'local');

/// URL base de la API. Se puede forzar con `--dart-define=API_URL=http://10.0.2.2:8080`
/// (backend corriendo en la Mac, visto desde el emulador de Android).
/// Sin `API_URL`, `local` usa la API de Dev para no tener que levantar el backend.
const apiBaseUrl = String.fromEnvironment(
  'API_URL',
  defaultValue: appEnv == 'qa'
      ? 'https://api.qa.identidad.alambritos.online'
      : 'https://api.dev.identidad.alambritos.online',
);
