# CI/CD

| Rama | Ambiente | WebConsole | Onboarding (Flutter) |
|---|---|---|---|
| `dev` | Dev | Deploy a `swa-verificaciononline-dev` | APK `APP_ENV=dev` (artefacto) |
| `QA` | QA | Deploy a `swa-verificaciononline-qa` | APK `APP_ENV=qa` (artefacto) |
| PR a `dev`/`QA`/`main` | — | lint + build | analyze + test |

Los tokens están como secret `AZURE_STATIC_WEB_APPS_API_TOKEN` en los GitHub Environments `dev` y `qa`.
Workflows en `.github/workflows/`. Flujo de ramas:
1. **`main`** es la rama de integración: cada feature entra por PR a `main` y ahí se junta el trabajo del equipo. No despliega nada, así que merges frecuentes y ediciones menores no disparan despliegues.
2. Cuando el equipo de desarrollo considera estable y probado lo que hay en `main`, se promueve con un PR de `main` a **`dev`**, que despliega el ambiente Dev, donde QA prueba.
3. Al cerrar el sprint, lo aprobado en `dev` se mergea con un PR a **`QA`**, que es la versión estable y despliega el ambiente QA.

Los PRs a `main`, `dev` y `QA` corren CI y no se puede hacer push directo a ninguna de las tres.

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
| `dev` | Dev | `ca-verificaciononline-api-dev` | `verificacion_dev` (rol `app_dev`) |
| `QA` | QA | `ca-verificaciononline-api-qa` | `verificacion_qa` (rol `app_qa`) |

- Región: South Central US (Central US no tenía capacidad para Container Apps al crear el entorno).
- Hosting: Azure Container Apps (consumo), escala a cero, máx. 1 réplica, 0.5 vCPU / 1 GiB, sin Log Analytics. La franja gratis es por suscripción: 180 000 vCPU-s, 360 000 GiB-s y 2 M de requests al mes. Primera petición tras inactividad = arranque en frío (~30-60 s).
- Base de datos: **un** servidor `psql-verificaciononline` (PostgreSQL 17, Burstable B1ms, 32 GiB) con **dos** bases. Cada rol solo accede a la suya. Firewall: solo "servicios de Azure".
- Conexión: la app lee `DB_URL`, `DB_USER` y `DB_PASSWORD` (esta última es un secret de la Container App). Flyway aplica las migraciones de `backend/src/main/resources/db/migration` al arrancar.
- Imagen: `ghcr.io/dream-parking/verificacion-online-backend` (tags `<sha>`, `dev`, `qa`). Es **pública** para que Azure la descargue sin credenciales sin necesitar un Container Registry (de pago).
- Deploy: `backend-deploy.yml` hace build, push, login OIDC con la identidad `sp-verificaciononline-github` (Contributor solo sobre `rg-verificaciononline`), actualiza la Container App y corre un smoke test a `/actuator/health/liveness`.
- Esquema: las tablas de dominio viven en `public`, junto a `flyway_schema_history`. `V2__esquema_ceiba.sql` las crea en el esquema `ceiba`, `V5__mover_a_public.sql` las traslada a `public` y `V10__translate_schema_to_english.sql` pasa todo el esquema a inglés (tablas, columnas, vistas, funciones, enums y sus valores). Las migraciones V2–V9 conservan los nombres en español de su momento; el modelo vigente es el de V10. Requiere la extensión `pg_trgm`: en el servidor, Server parameters → `azure.extensions` debe incluir `PG_TRGM`, o la migración falla al arrancar.
- Datos: `V3__datos_referencia.sql` (catálogos, regla R-01, tipos de alerta) va a todos los ambientes. `db/demo/` (V4, V6 y V9: usuarios ficticios, las 10 solicitudes y las 9 alertas de ejemplo de la consola) solo a local/dev/qa, vía el perfil `dev` (`application-dev.properties`). Por defecto (`application.properties`) solo se aplica `db/migration`, así que Prod es seguro sin configurar nada: basta con **no** activar el perfil `dev`. `backend-deploy.yml` define `SPRING_PROFILES_ACTIVE=dev` en las Container Apps de Dev y QA.
- Migraciones: las ya aplicadas (V1–V10) no se editan (Flyway valida el checksum y la app no arranca); cualquier cambio va en un archivo nuevo (V11, V12…), con nombres en inglés. No ejecutarlas a mano en la consola: usan `SET LOCAL search_path` y dependen de la transacción de Flyway.
- Local: `docker compose up -d` en `backend/` y `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`; o `./mvnw spring-boot:test-run -Dspring-boot.run.profiles=dev` con Postgres desechable (en IntelliJ: run configuration `TestBackendApplication (dev)`). Los tests usan `@ActiveProfiles("dev")`.
- Contra la base Dev desde tu PC: perfil `dev-remote` (`SPRING_PROFILES_ACTIVE=dev-remote`) más `DB_URL`, `DB_USER` y `DB_PASSWORD` de Dev (`sslmode=require`, y tu IP en el firewall del servidor). Incluye `dev` pero **no** ejecuta Flyway: las migraciones las aplica el despliegue de Azure al hacer merge a `dev` (Dev) o `QA`. Como `ddl-auto=validate` sigue activo, si tu rama cambia entidades y la migración aún no está en Dev, la app no arranca.
- Autenticación de la consola: `APP_JWT_SECRET`, `APP_BOOTSTRAP_ADMIN_EMAIL` y `APP_BOOTSTRAP_ADMIN_PASSWORD` van como variables/secrets de cada Container App (detalle y comandos en `docs/integracion-api.md`). Sin ellas la app arranca igual, pero nadie puede iniciar sesión en la consola y los tokens no sobreviven a un reinicio. `APP_CORS_ALLOWED_ORIGINS` debe incluir la URL de la consola del ambiente.
- Documentación de la API: con el perfil `dev` el backend publica Swagger UI en `/swagger-ui.html` y el OpenAPI en `/v3/api-docs` (springdoc). El archivo que se entrega a front y móvil es `docs/openapi.json`; una prueba falla si no coincide con el API. Por defecto (Prod) ambos están apagados (`application.properties`).

### Análisis estático: SonarQube Cloud
- El repo es público, así que SonarQube Cloud es gratis; no hay VM ni recurso de Azure (nada que registrar en `azure-resources.csv`). Organización `alambritos-esen`; un proyecto por componente:

| Componente | Proyecto de Sonar | CI (job) | Cobertura |
|---|---|---|---|
| Backend | `dream-parking_verificacion-online` | `ci` → job `test` | JaCoCo (`target/site/jacoco/jacoco.xml`) |
| Webconsole | `dream-parking_verificacion-online-webconsole` | `ci` → job `lint-build` | Excluida: aún no hay tests (`sonar.coverage.exclusions` en `webconsole/sonar-project.properties`; quitarla al agregar tests) |
| Onboarding (Flutter) | `dream-parking_verificacion-online-onboarding` | `ci` → job `analyze-test` | `flutter test --coverage` → `coverage/lcov.info` |

- **Ahorro de minutos de Actions:** los tres CI viven en un solo workflow (`.github/workflows/ci.yml`). Un job inicial (`changes`) mira qué carpetas tocó el PR y solo se ejecutan los jobs de esos componentes; los demás se omiten con `if`, GitHub los reporta como *skipped* (cuenta como éxito para los checks obligatorios) y no gastan minutos. Los PRs en borrador no corren nada. Un PR de solo documentación (`*.md`) gasta ~1 minuto. Si se modifica `ci.yml`, se prueba todo.
- Los deploys (`backend-deploy`, `webconsole-deploy`, `onboarding-build`) **no** repiten tests ni análisis: a `dev`/`QA` solo se llega por PR, y el check obligatorio del componente ya corrió sobre ese mismo resultado del merge. Tampoco se disparan si el push solo trae `*.md`, tests (`backend/src/test/`, `onboarding/test/`) o, en el APK, `onboarding/ios/`. Todos los jobs tienen `timeout-minutes` para que uno colgado (p. ej. esperando el Quality Gate) no queme minutos. Se descartó un runner propio: el repo es público y un runner propio es inseguro con PRs de forks.
- Cada CI corre el análisis al final del job (backend: `sonar:sonar` con Maven, reusa `target/`; los otros: `SonarSource/sonarqube-scan-action`). Si el Quality Gate falla, falla el job. Sin el secret `SONAR_TOKEN` (forks) el paso se omite. El token es el secret de repositorio `SONAR_TOKEN`.
- Los CI corren en todo PR a `dev`, `QA` y `main` (sin filtro de rutas) para poder exigirlos. También corren en push a `main`: la rama principal de Sonar es `main` (el plan gratis no permite cambiarla) y es donde se integra todo el trabajo, así que la línea base de Sonar se actualiza con cada merge y los PRs a `main` se comparan contra ella.
- Checks obligatorios: la regla (ruleset) `required checks` en `dev`, `QA` y `main` exige `test`, `lint-build` y `analyze-test`. No se exige "SonarCloud Code Analysis" porque los tres proyectos publican un check con ese mismo nombre y se pisarían; el Quality Gate ya hace fallar el job de cada CI.
- Alta de un proyecto nuevo: basta con agregar su `sonar-project.properties` y el paso de Sonar; el primer análisis lo crea. Los proyectos creados así quedan privados; para hacerlos públicos: proyecto → *Administration → Permissions → Project visibility*. Desactivar **Automatic Analysis** en cada uno (*Administration → Analysis Method*).

### Paso manual único: hacer público el paquete
GitHub no permite cambiar la visibilidad de un paquete por API. Tras el **primer** push de la imagen: GitHub → organización `dream-parking` → Packages → `verificacion-online-backend` → Package settings → Change visibility → Public. Si falla, revisar en la organización que se permita crear paquetes públicos. Luego, re-ejecutar el workflow.

### Costo de Postgres
Verificado el 2026-10-06 en Cost Management: los medidores son `B1MS Compute - Free` y `Storage Data Stored - Free` con costo 0.00 USD, así que aplica la oferta gratis de 12 meses (B1ms, 750 h/mes y 32 GiB en total). Cuidado: un segundo servidor B1ms o más de 32 GiB se cobrarían, y la oferta vence 12 meses después de la fecha de alta de la cuenta de Azure (no de este servidor); conviene confirmar esa fecha en el portal.
