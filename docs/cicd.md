# CI/CD

| Rama | Ambiente | WebConsole | Onboarding (Flutter) |
|---|---|---|---|
| `main` | Dev | Deploy a `swa-verificaciononline-dev` | APK `APP_ENV=dev` (artefacto) |
| `QA` | QA | Deploy a `swa-verificaciononline-qa` | APK `APP_ENV=qa` (artefacto) |
| PR a `main`/`QA` | — | lint + build | analyze + test |

Los tokens están como secret `AZURE_STATIC_WEB_APPS_API_TOKEN` en los GitHub Environments `dev` y `qa`.
Workflows en `.github/workflows/`. Flujo: feature → PR a `main` (Dev) → merge de `main` a `QA` (QA).

## URLs y DNS

Dominio base: `identidad.alambritos.online` (DNS en Namecheap). Convención: `<app>.<ambiente>.identidad.alambritos.online`; Prod sin segmento de ambiente.

| Ambiente | URL | CNAME (host en Namecheap) → destino |
|---|---|---|
| Dev | https://console.dev.identidad.alambritos.online | `console.dev.identidad` → `mango-glacier-088cb1210.5.azurestaticapps.net` |
| QA | https://console.qa.identidad.alambritos.online | `console.qa.identidad` → `green-field-020ffdc10.6.azurestaticapps.net` |
| Prod (reservado) | `console.identidad.alambritos.online` | pendiente: tercer SWA Free |

Reservados sin DNS aún: `api.*` y `onboarding.*` con el mismo patrón por ambiente.
