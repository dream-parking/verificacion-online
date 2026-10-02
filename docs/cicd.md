# CI/CD

| Rama | Ambiente | WebConsole | Onboarding (Flutter) |
|---|---|---|---|
| `main` | Dev | Deploy a `swa-verificaciononline-dev` | APK `APP_ENV=dev` (artefacto) |
| `QA` | QA | Deploy a `swa-verificaciononline-qa` | APK `APP_ENV=qa` (artefacto) |
| PR a `main`/`QA` | — | lint + build | analyze + test |

Los tokens están como secret `AZURE_STATIC_WEB_APPS_API_TOKEN` en los GitHub Environments `dev` y `qa`.
Workflows en `.github/workflows/`. Flujo: feature → PR a `main` (Dev) → merge de `main` a `QA` (QA).
