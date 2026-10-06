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

| Backend Dev | https://api.dev.identidad.alambritos.online | `api.dev.identidad` → FQDN de `ca-verificaciononline-api-dev`, más TXT `asuid.api.dev.identidad` |
| Backend QA | https://api.qa.identidad.alambritos.online | `api.qa.identidad` → FQDN de `ca-verificaciononline-api-qa`, más TXT `asuid.api.qa.identidad` |

Los certificados son gestionados por Container Apps (gratis). Reservados sin DNS aún: `onboarding.*` y todo lo de Prod con el mismo patrón por ambiente.

## Backend (Spring Boot, `backend/`)

| Rama | Ambiente | Container App | Base de datos |
|---|---|---|---|
| `main` | Dev | `ca-verificaciononline-api-dev` | `verificacion_dev` (rol `app_dev`) |
| `QA` | QA | `ca-verificaciononline-api-qa` | `verificacion_qa` (rol `app_qa`) |

- Región: South Central US (Central US no tenía capacidad para Container Apps al crear el entorno).
- Hosting: Azure Container Apps (consumo), escala a cero, máx. 1 réplica, 0.5 vCPU / 1 GiB, sin Log Analytics. La franja gratis es por suscripción: 180 000 vCPU-s, 360 000 GiB-s y 2 M de requests al mes. Primera petición tras inactividad = arranque en frío (~30-60 s).
- Base de datos: **un** servidor `psql-verificaciononline` (PostgreSQL 17, Burstable B1ms, 32 GiB) con **dos** bases. Cada rol solo accede a la suya. Firewall: solo "servicios de Azure".
- Conexión: la app lee `DB_URL`, `DB_USER` y `DB_PASSWORD` (esta última es un secret de la Container App). Flyway aplica las migraciones de `backend/src/main/resources/db/migration` al arrancar.
- Imagen: `ghcr.io/dream-parking/verificacion-online-backend` (tags `<sha>`, `dev`, `qa`). Es **pública** para que Azure la descargue sin credenciales sin necesitar un Container Registry (de pago).
- Deploy: `backend-deploy.yml` hace test, build, push, login OIDC con la identidad `sp-verificaciononline-github` (Contributor solo sobre `rg-verificaciononline`), actualiza la Container App y corre un smoke test a `/actuator/health/liveness`.
- Esquema: las tablas de dominio viven en `public`, junto a `flyway_schema_history`. `V2__esquema_ceiba.sql` las crea en el esquema `ceiba` y `V5__mover_a_public.sql` las traslada a `public` y elimina `ceiba`. Requiere la extensión `pg_trgm`: en el servidor, Server parameters → `azure.extensions` debe incluir `PG_TRGM`, o la migración falla al arrancar.
- Datos: `V3__datos_referencia.sql` (catálogos, regla R-01, tipos de alerta) va a todos los ambientes. `db/demo/` (V4, V6 y V9: usuarios ficticios, las 10 solicitudes y las 9 alertas de ejemplo de la consola) solo a local/dev/qa, vía el perfil `dev` (`application-dev.properties`). Por defecto (`application.properties`) solo se aplica `db/migration`, así que Prod es seguro sin configurar nada: basta con **no** activar el perfil `dev`. `backend-deploy.yml` define `SPRING_PROFILES_ACTIVE=dev` en las Container Apps de Dev y QA.
- Migraciones: V1–V5 no se editan una vez aplicadas (Flyway valida el checksum y la app no arranca); cualquier cambio va en un archivo nuevo (V6, V7…). No ejecutarlas a mano en la consola: usan `SET LOCAL search_path` y dependen de la transacción de Flyway.
- Local: `docker compose up -d` en `backend/` y `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`; o `./mvnw spring-boot:test-run -Dspring-boot.run.profiles=dev` con Postgres desechable (en IntelliJ: run configuration `TestBackendApplication (dev)`). Los tests usan `@ActiveProfiles("dev")`.
- Contra la base Dev desde tu PC: perfil `dev-remote` (`SPRING_PROFILES_ACTIVE=dev-remote`) más `DB_URL`, `DB_USER` y `DB_PASSWORD` de Dev (`sslmode=require`, y tu IP en el firewall del servidor). Incluye `dev` pero **no** ejecuta Flyway: las migraciones las aplica el despliegue de Azure al hacer merge a `main` (Dev) o `QA`. Como `ddl-auto=validate` sigue activo, si tu rama cambia entidades y la migración aún no está en Dev, la app no arranca.
- Autenticación de la consola: `APP_JWT_SECRET`, `APP_BOOTSTRAP_ADMIN_EMAIL` y `APP_BOOTSTRAP_ADMIN_PASSWORD` van como variables/secrets de cada Container App (detalle y comandos en `docs/integracion-api.md`). Sin ellas la app arranca igual, pero nadie puede iniciar sesión en la consola y los tokens no sobreviven a un reinicio. `APP_CORS_ALLOWED_ORIGINS` debe incluir la URL de la consola del ambiente.
- Documentación de la API: con el perfil `dev` el backend publica Swagger UI en `/swagger-ui.html` y el OpenAPI en `/v3/api-docs` (springdoc). El archivo que se entrega a front y móvil es `docs/openapi.json`; una prueba falla si no coincide con el API. Por defecto (Prod) ambos están apagados (`application.properties`).

### Paso manual único: hacer público el paquete
GitHub no permite cambiar la visibilidad de un paquete por API. Tras el **primer** push de la imagen: GitHub → organización `dream-parking` → Packages → `verificacion-online-backend` → Package settings → Change visibility → Public. Si falla, revisar en la organización que se permita crear paquetes públicos. Luego, re-ejecutar el workflow.

### Costo de Postgres
Verificado el 2026-10-06 en Cost Management: los medidores son `B1MS Compute - Free` y `Storage Data Stored - Free` con costo 0.00 USD, así que aplica la oferta gratis de 12 meses (B1ms, 750 h/mes y 32 GiB en total). Cuidado: un segundo servidor B1ms o más de 32 GiB se cobrarían, y la oferta vence 12 meses después de la fecha de alta de la cuenta de Azure (no de este servidor); conviene confirmar esa fecha en el portal.
