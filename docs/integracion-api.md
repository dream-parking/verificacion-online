# Integración con la API (Sprint 1)

## Qué se entrega al equipo de front y móvil

**[`docs/openapi.json`](openapi.json)** (OpenAPI 3.1) más esta guía. El JSON describe todas las rutas, los cuerpos con sus validaciones, las respuestas (incluido el cuerpo de cada error), el esquema de autenticación y los ambientes, y sirve para generar clientes (`openapi-generator`, `openapi-typescript`, etc.). Es válido según un validador independiente y una prueba de CI falla si el API cambia y el archivo no se regenera.

**Lo que el JSON no dice** y esta guía sí:

- El orden del flujo y qué estado tiene cada paso (sección "Flujo móvil").
- Las reglas de negocio (score, expediente inmutable).
- Que los códigos de catálogo salen de `GET /api/catalogs` y no se escriben a mano.
- Cómo se autentica la consola y qué rol hace qué.
- Que en las respuestas los campos opcionales pueden venir `null` (el JSON no marca como obligatorios los campos de respuesta).

Si la URL del API está disponible, el mismo documento se publica en `/v3/api-docs` y la interfaz en `/swagger-ui.html` (solo con el perfil `dev`: Dev, QA y local; Prod los tiene apagados). Regenerar el archivo tras cambiar el API:

```bash
cd backend && ./mvnw test -Dtest=OpenApiContractTests -Dopenapi.update=true
```

Ambientes: Dev `https://api.dev.identidad.alambritos.online`, QA `https://api.qa.identidad.alambritos.online`, local `http://localhost:8080`. Errores: `application/problem+json` con `status`, `title` y `detail`; `404` no existe, `400` datos inválidos, `409` estado no permitido, `401`/`403` sin sesión o sin permiso.

## Flujo móvil — `/api/onboarding/requests` (público)

Orden: `POST /` → `PUT /{id}/privacy-consent` → `PUT /{id}/identity-document` → `PUT /{id}/basic-data` → `PUT /{id}/income` → `PUT /{id}/expected-activity` → `POST /{id}/submit` (y `PUT /{id}/signals` en cualquier momento antes del envío). `submit` exige los cinco pasos registrados en ese orden y responde `409` si falta alguno (`completedSteps` llega a 6 al enviar). Mientras la solicitud está `IN_PROGRESS` cada paso se puede volver a enviar para corregirlo; al salir de ese estado responde `409`.

| Método y ruta | Para qué | Tarea |
|---|---|---|
| `GET /api/catalogs` | Fuentes de ingreso, rangos de ingreso, tipos de movimiento y rangos de monto mensual. Códigos como `SALARIO`, `HASTA_500`, `PAGO_SALARIO`, `200_500`. | VDI-46, VDI-50 |
| `POST /` | Inicia la solicitud y devuelve su `id`. | |
| `PUT /{id}/privacy-consent` | Paso 1: `{"signalsAccepted": true}`. Guarda la versión vigente del aviso y la IP. | |
| `PUT /{id}/identity-document` | Paso 2: `multipart/form-data` con `front` y `back` (fotos del DUI, JPEG o PNG, hasta 5 MB cada una). Responde lo leído (`dui`, `firstNames`, `lastNames`, fechas y `gender`) para pre-llenar los datos básicos. `status: FAILED` = el lector no está disponible: el paso avanza y la persona escribe sus datos. Fotos ilegibles: `422` con `reason` (`BLURRY`, `GLARE`, `CROPPED`, `TOO_DARK`, `NOT_A_DUI`, `WRONG_SIDES`, `OTHER`) y el paso no avanza. Ver "DUI" abajo. | VDI-79, VDI-80 |
| `PUT /{id}/basic-data` | Paso 3: `firstNames`, `lastNames`, `dui` (`00000000-0`) y `mobilePhone` (`0000-0000`, empieza con 6 o 7). Es la pantalla de confirmar o corregir lo leído del DUI: el servidor registra qué campos cambió la persona. | VDI-78 |
| `PUT /{id}/income` | Paso 4: `sourceCode`, `rangeCode`, `sourceDetail` (obligatorio si es `OTRO`). | VDI-47 |
| `PUT /{id}/expected-activity` | Paso 5: `transactionTypeCode` y `monthlyAmountRangeCode`, ambos del catálogo (sin monto libre). Responde el score. | VDI-52, VDI-55 |
| `POST /{id}/submit` | Envía la solicitud. Responde el `number` único que asigna el servidor (`SOL-AAAA-NNNNN`). Si ya estaba enviada responde `409`: consultar `GET /{id}` para leer su número. | |
| `PUT /{id}/signals` | Huella, modelo, SO, ritmo de escritura y tiempo por paso. La IP la toma el servidor y con ella resuelve la ubicación aproximada (latitud, longitud y demás datos de ip-api.com); la app no envía coordenadas. Si no se puede ubicar (IP privada, proveedor caído o con límite), la consola recibe `locationStatus: UNAVAILABLE` y el motivo en `ipDetails.failure`; las demás señales se guardan igual. La app es la única fuente de los tiempos e intentos por paso; el servidor rechaza horas imposibles (antes de crear la solicitud o en el futuro, con 5 min de tolerancia). | VDI-41, VDI-42, VDI-43, VDI-67 |
| `GET /{id}`, `GET /{id}/risk-assessment` | Resumen y score vigente. | |

Reglas:

- **Score (R-01):** un rango de monto mensual cuyo techo no supera USD 500 (`HASTA_200`, `200_500`) da riesgo bajo; `500_1000` y `MAS_1000` quedan `PENDING_REVIEW`. El umbral es provisional (VDI-54).
- **Expediente inmutable (VDI-23):** al salir de `IN_PROGRESS` la base de datos rechaza editar o borrar las declaraciones (el `registeredAt` original se conserva siempre).
- Los rangos traen `minUsd` y `maxUsd` (primer y último monto; `null` si no tiene piso o techo).

### DUI (VDI-79, VDI-80)

- **Lectura:** un modelo de visión de OpenAI (`gpt-6-astra` por defecto, `APP_OCR_MODEL`) por la Responses API, con la respuesta forzada a un esquema JSON y `store: false` (el proveedor no guarda las fotos). El prompt le pide no adivinar: un campo que no lee con certeza viene `null`. Costo aproximado: USD 0.03–0.08 por lectura.
- **Fotos:** se guardan cifradas con AES-256-GCM en `identity_document_image` (la llave es `APP_DOCUMENT_KEY`, nunca la base de datos), con el SHA-256 de la foto original. Se reemplazan si la persona vuelve a capturar mientras la solicitud está en progreso; después del envío la base de datos impide cambiarlas o borrarlas, igual que las declaraciones.
- **Comparación:** al guardar los datos básicos se registra si el DUI confirmado coincide con el leído y qué campos corrigió la persona (`correctedFields`; los nombres se comparan sin mayúsculas, tildes ni espacios de más). La consola además ve si el número leído tiene dígito verificador válido, si el documento está vencido, si la foto parece del documento físico (`looksAuthentic`) y la confianza de la lectura. La app no recibe esas señales.
- **Paso opcional mientras la app no tenga la pantalla:** con `APP_IDENTITY_DOCUMENT_REQUIRED=false` (el valor por defecto por ahora) los datos básicos saltan el paso del DUI. Cambiarlo a `true` en Dev y QA cuando la app publique la captura (VDI-77).
- **Configuración:** `APP_OCR_ENABLED=true`, `OPENAI_API_KEY` y `APP_DOCUMENT_KEY` (`openssl rand -base64 32`) como secrets de la Container App. Sin `APP_DOCUMENT_KEY` la aplicación no arranca, salvo con el perfil `dev`, que usa una llave temporal (las fotos guardadas así no se pueden leer después de un reinicio).

## Consola web — `/api/console` (requiere sesión)

1. `POST /api/console/auth/login` con `{ email, password }` devuelve `accessToken` (vigencia `expiresIn` segundos, 8 h) y el usuario.
2. Enviar `Authorization: Bearer <accessToken>` en todo lo demás. Un `401` significa token vencido, usuario desactivado o sin sesión: volver al login. El rol se lee de la base en cada petición, así que un cambio de rol o una baja aplica de inmediato.
3. Tras 5 intentos fallidos seguidos el correo se bloquea 15 minutos (`429` con `Retry-After`). Todo fallo de login responde lo mismo, sin distinguir si el correo existe.
4. CORS: el origen de la consola debe estar en `APP_CORS_ALLOWED_ORIGINS` (por defecto `http://localhost:3000`).

| Método y ruta | Rol | Tarea |
|---|---|---|
| `GET /requests?status=&riskLevel=&q=&page=&size=` | cualquiera | VDI-56 |
| `GET /requests/{id}` (DUI leído, ingresos, movimiento, score y regla, señales, pasos, línea de tiempo) | cualquiera | VDI-44, 49, 53, 56, 81 |
| `GET /requests/{id}/identity-document/{side}` (`FRONT` o `BACK`): la foto descifrada, sin caché; cada consulta queda en la auditoría de accesos | cualquiera | VDI-80, VDI-81 |
| `GET /score-rules` | cualquiera | VDI-57 |
| `GET /alerts?status=&assigneeId=&account=&from=&to=` | cualquiera | VDI-62 |
| `POST /alerts/{id}/take` (la toma el usuario de la sesión) | `FRAUD_ANALYST`, `ADMIN` | VDI-61 |
| `GET /auth/me`, `POST /auth/change-password` | cualquiera | |
| `GET /users` (activos; `includeInactive=true` solo `ADMIN`) | cualquiera | |
| `POST /users`, `PUT /users/{id}`, `POST /users/{id}/password` | `ADMIN` | |

## Usuarios y primer acceso

Los usuarios los crea un `ADMIN` desde la consola (`POST /api/console/users`, con contraseña inicial que la persona cambia al entrar). El primer administrador sale de variables de entorno, porque sin él nadie puede entrar:

| Variable | Para qué |
|---|---|
| `APP_BOOTSTRAP_ADMIN_EMAIL`, `APP_BOOTSTRAP_ADMIN_PASSWORD` | Crea el primer administrador al arrancar si ese correo no existe; nunca modifica a un usuario existente. Contraseña de 10 a 72 caracteres. |
| `APP_JWT_SECRET` | Clave de firma de los tokens (32 caracteres o más). Si falta se usa una aleatoria: funciona, pero los tokens dejan de valer en cada reinicio. |

Los usuarios demo de V4 (Gerardo, Ana, Luis, Karen) no tienen contraseña: un administrador se la define con `POST /users/{id}/password`.

Pendiente en Azure antes de que sirva en Dev y QA (una vez por ambiente): definir esas tres variables como secrets de la Container App, por ejemplo:

```bash
az containerapp secret set -n ca-verificaciononline-api-dev -g rg-verificaciononline \
  --secrets jwt-secret=<32+ caracteres> admin-password=<10+ caracteres>
az containerapp update -n ca-verificaciononline-api-dev -g rg-verificaciononline \
  --set-env-vars APP_JWT_SECRET=secretref:jwt-secret APP_BOOTSTRAP_ADMIN_PASSWORD=secretref:admin-password \
  APP_BOOTSTRAP_ADMIN_EMAIL=<correo>
```

Local: exportar las mismas variables antes de arrancar (ver `backend/.env.example`).

## Calidad

`./mvnw verify` falla si la cobertura de líneas del backend baja de 70 % (informe en `backend/target/site/jacoco`).
