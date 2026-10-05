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

## Backend (Spring Boot, `backend/`)

| Rama | Ambiente | Container App | Base de datos |
|---|---|---|---|
| `main` | Dev | `ca-verificaciononline-api-dev` | `verificacion_dev` (rol `app_dev`) |
| `QA` | QA | `ca-verificaciononline-api-qa` | `verificacion_qa` (rol `app_qa`) |

- Región: South Central US (Central US no tenía capacidad para Container Apps al crear el entorno).
- Hosting: Azure Container Apps (consumo), escala a cero, máx. 1 réplica, 0.5 vCPU / 1 GiB, sin Log Analytics. La franja gratis es por suscripción: 180 000 vCPU-s, 360 000 GiB-s y 2 M de requests al mes. Primera petición tras inactividad = arranque en frío (~30-60 s).
- Base de datos: **un** servidor `psql-verificaciononline` (PostgreSQL 17, Burstable B1ms, 32 GiB) con **dos** bases. Cada rol solo accede a la suya. Firewall: solo "servicios de Azure".
- Conexión: la app lee `DB_URL`, `DB_USER` y `DB_PASSWORD` (esta última es un secret de la Container App). Flyway aplica las migraciones de `backend/src/main/resources/db/migration` al arrancar.
- Imagen: `ghcr.io/dream-parking/verificacion-online-backend` (tags `<sha>`, `dev`, `qa`). Es **pública** para que Azure la descargue sin credenciales y no hacer falta un Container Registry (de pago).
- Deploy: `backend-deploy.yml` hace test, build, push, login OIDC con la identidad `sp-verificaciononline-github` (Contributor solo sobre `rg-verificaciononline`), actualiza la Container App y corre un smoke test a `/actuator/health/liveness`.
- Local: `docker compose up -d` en `backend/` y `./mvnw spring-boot:run`; o `./mvnw spring-boot:test-run` con Postgres desechable.

### Paso manual único: hacer público el paquete
GitHub no permite cambiar la visibilidad de un paquete por API. Tras el **primer** push de la imagen: GitHub → organización `dream-parking` → Packages → `verificacion-online-backend` → Package settings → Change visibility → Public. Si falla, revisar en la organización que se permita crear paquetes públicos. Luego, re-ejecutar el workflow.

### Costo de Postgres
La oferta gratis (12 meses, B1ms + 32 GiB) solo aplica a cuentas nuevas; no se puede verificar por CLI. Si no aplica, cuesta ~16 USD/mes. Revisar Cost Management 24 h después de crear el servidor; si cobra, borrarlo o moverlo a una alternativa gratis.
