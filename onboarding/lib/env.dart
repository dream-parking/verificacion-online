/// Build-time environment, injected with `--dart-define=APP_ENV=dev|qa`.
const appEnv = String.fromEnvironment('APP_ENV', defaultValue: 'local');
