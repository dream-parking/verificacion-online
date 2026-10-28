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

Orden: `POST /` → `PUT /{id}/privacy-consent` → `PUT /{id}/basic-data` → `PUT /{id}/identity-document` → `PUT /{id}/identity-document/confirmation` → `PUT /{id}/income` → `PUT /{id}/expected-activity` → `POST /{id}/submit` (y `PUT /{id}/signals` en cualquier momento antes del envío). `submit` exige los cinco pasos registrados en ese orden y responde `409` si falta alguno (`completedSteps` llega a 6 al enviar). Mientras la solicitud está `IN_PROGRESS` cada paso se puede volver a enviar para corregirlo; al salir de ese estado responde `409`.

| Método y ruta | Para qué | Tarea |
|---|---|---|
| `GET /api/catalogs` | Fuentes de ingreso, rangos de ingreso, tipos de movimiento y rangos de monto mensual. Códigos como `SALARIO`, `HASTA_500`, `PAGO_SALARIO`, `200_500`. | VDI-46, VDI-50 |
| `POST /` | Inicia la solicitud y devuelve su `id`. | |
| `PUT /{id}/privacy-consent` | Paso 1: `{"signalsAccepted": true}`. Guarda la versión vigente del aviso y la IP. | |
| `PUT /{id}/basic-data` | Paso 2: `firstNames`, `lastNames`, `dui` (`00000000-0`) y `mobilePhone` (`0000-0000`, empieza con 6 o 7). | |
| `PUT /{id}/identity-document` | Paso 3, después de los datos básicos (`409` si faltan): `multipart/form-data` con `front` y `back` (fotos del DUI, JPEG o PNG, hasta 5 MB cada una). Responde lo leído (`dui`, `firstNames`, `lastNames`, fechas y `gender`; `null` = no se leyó con claridad) para la pantalla «¿Leímos bien tus datos?». `status: FAILED` = el lector no está disponible: la persona escribe sus datos al confirmar. Fotos ilegibles: `422` con `reason` (`BLURRY`, `GLARE`, `CROPPED`, `TOO_DARK`, `NOT_A_DUI`, `WRONG_SIDES`, `OTHER`) y `side` (`FRONT`, `BACK`, `BOTH`; puede faltar). Las fotos solas no completan el paso. | VDI-79, VDI-80 |
| `PUT /{id}/identity-document/confirmation` | Paso 3: lo que la persona confirma o corrige: `dui`, `firstNames`, `lastNames`, `birthDate`, `expiryDate`. Completa el paso. `409` si no hay fotos legibles. Fotos nuevas después de confirmar piden confirmar otra vez (`submit` responde `409`). | VDI-78, VDI-79 |
| `PUT /{id}/income` | Paso 4: `sourceCode`, `rangeCode`, `sourceDetail` (obligatorio si es `OTRO`). | VDI-47 |
| `PUT /{id}/expected-activity` | Paso 5: `transactionTypeCode` y `monthlyAmountRangeCode`, ambos del catálogo (sin monto libre). Responde el score. | VDI-52, VDI-55 |
| `POST /{id}/submit` | Envía la solicitud. Responde el `number` único que asigna el servidor (`SOL-AAAA-NNNNN`). Si ya estaba enviada responde `409`: consultar `GET /{id}` para leer su número. | |
| `PUT /{id}/signals` | Huella, modelo, SO, ritmo de escritura y tiempo por paso. La IP la toma el servidor y con ella resuelve la ubicación aproximada (latitud, longitud y demás datos de ip-api.com); la app no envía coordenadas. Si no se puede ubicar (IP privada, proveedor caído o con límite), la consola recibe `locationStatus: UNAVAILABLE` y el motivo en `ipDetails.failure`; las demás señales se guardan igual. Aparte, la app envía `deviceLocationStatus` con el permiso de ubicación aproximada: `AVAILABLE` con `approximateLocation` (ciudad y país) y `countryIso`, `DENIED` si la persona eligió «Ahora no» o negó el permiso, o `UNAVAILABLE` si el teléfono no obtuvo la ciudad. Sin permiso la solicitud sigue y la consola muestra «Ubicación no disponible» en «Ciudad de la solicitud»; la ciudad solo se guarda con `AVAILABLE`. La app es la única fuente de los tiempos e intentos por paso; el servidor rechaza horas imposibles (antes de crear la solicitud o en el futuro, con 5 min de tolerancia). | VDI-41, VDI-42, VDI-43, VDI-67, VDI-73 |
| `GET /{id}`, `GET /{id}/risk-assessment` | Resumen y score vigente. | |
| `GET /{id}/resume` | Retomar una solicitud a medias: `state` (`RESUMABLE`, `EXPIRED` si pasaron más de 7 días sin actividad, `COMPLETED`, `ABANDONED`), `currentStep`, el estado de cada paso con su fecha de guardado (`DONE`, `SKIPPED` para el DUI solo si se configuró como opcional con `APP_IDENTITY_DOCUMENT_REQUIRED=false`, `CURRENT`, `PENDING`) y lo guardado (`saved`) para llenar las pantallas de nuevo. Fuera de `RESUMABLE` no trae `saved` ni `currentStep`. La app guarda el `id` de la solicitud en el teléfono. La ventana se cambia con `app.onboarding.resume-window-days`. | VDI-86 |
| `POST /{id}/abandon` | «Empezar de nuevo»: deja la solicitud en `ABANDONED` y registra el evento; la app abre una nueva con `POST /`. `409` si ya no está en progreso. | VDI-86 |

Reglas:

- **Score (modelo v2, VDI-82 y VDI-115):** un puntaje de 0 a 100 que suma los puntos de cada señal disponible; ninguna señal aprueba ni rechaza por sí sola. Bajo 0–39, medio 40–69, alto 70–100. Pesos máximos (suman 100): monto declarado 20, documento de identidad 15, ritmo de escritura 10, dispositivo 10, horario 5, ciudad 15, cotejo RNPN 15 y prueba de vida 10. La ciudad llega con VDI-72 y el cotejo RNPN con VDI-111, y hasta entonces no suman; las listas internas no tienen peso todavía. **Prueba de vida:** la severidad es 1 menos el nivel de confianza (con confianza 0.90 suma 1 de 10 puntos; con 0.20, 8), y desde el tercer intento nunca baja de 0.5. Si el proveedor no estuvo disponible o no hay prueba, no suma ni resta. Una señal no capturada no suma ni resta. Se calcula al declarar el monto y otra vez al enviar la solicitud, para contar la telemetría que llegue después. La respuesta (`score`, `modelVersion`, `signals`) trae el aporte de cada señal y queda guardada en el expediente. **Versiones:** un modelo nunca se edita; el v2 empezó con VDI-115 y las evaluaciones ya hechas conservan el v1 (con los pesos monto 25, documento 20, ritmo 15, dispositivo 10, horario 10 y ciudad 20). Reemplaza a la regla R-01, que quedó retirada.
- **Expediente inmutable (VDI-23):** al salir de `IN_PROGRESS` la base de datos rechaza editar o borrar las declaraciones (el `registeredAt` original se conserva siempre).
- Los rangos traen `minUsd` y `maxUsd` (primer y último monto; `null` si no tiene piso o techo).

### DUI (VDI-79, VDI-80)

- **Lectura:** un modelo de visión de OpenAI (`gpt-6-astra` por defecto, `APP_OCR_MODEL`) por la Responses API, con la respuesta forzada a un esquema JSON y `store: false` (el proveedor no guarda las fotos). El prompt le pide no adivinar: un campo que no lee con certeza viene `null`. Costo aproximado: USD 0.03–0.08 por lectura.
- **Fotos:** se guardan cifradas con AES-256-GCM en `identity_document_image` (la llave es `APP_DOCUMENT_KEY`, nunca la base de datos), con el SHA-256 de la foto original. Se reemplazan si la persona vuelve a capturar mientras la solicitud está en progreso; después del envío la base de datos impide cambiarlas o borrarlas, igual que las declaraciones.
- **Comparación:** al confirmar se registra qué campos cambió la persona respecto a lo leído (`correctedFields`; los nombres se comparan sin mayúsculas, tildes ni espacios de más) y si el DUI y los nombres confirmados coinciden con los datos básicos (`duiMatchesDeclared`, `namesMatchDeclared`; se vuelve a comparar si después cambian los datos básicos). Una diferencia es una señal de riesgo, no un error. La consola además ve si el número leído tiene dígito verificador válido, si el documento está vencido, si la foto parece del documento físico (`looksAuthentic`) y la confianza de la lectura. La app no recibe esas señales.
- **Paso obligatorio:** la app ya tiene la captura (VDI-77), así que `APP_IDENTITY_DOCUMENT_REQUIRED` vale `true` por defecto y los ingresos no se pueden declarar sin confirmar el DUI. Con `false` los ingresos saltan el paso y la solicitud se envía sin DUI: solo para pruebas o uso local, nunca en Dev, QA ni producción.
- **Configuración:** `APP_OCR_ENABLED=true`, `OPENAI_API_KEY` y `APP_DOCUMENT_KEY` (`openssl rand -base64 32`) como secrets de la Container App. Sin `APP_DOCUMENT_KEY` la aplicación no arranca, salvo con el perfil `dev`, que usa una llave temporal (las fotos guardadas así no se pueden leer después de un reinicio).

### Prueba de vida (VDI-114)

- **Endpoint:** `PUT /api/onboarding/requests/{id}/liveness`, multipart con `selfie` (JPEG o PNG, hasta 5 MB). Responde `{ status, attempts }`: `CHECKED` si el proveedor contestó, `FAILED` si no estuvo disponible. En ambos casos la solicitud sigue; con `FAILED` la señal no suma ni resta. Se puede repetir mientras la solicitud está en progreso y cada intento cuenta en `attempts`. La app **no** recibe el nivel de confianza: es una señal de riesgo y se queda en el expediente.
- **Selfie que no sirve (`422`):** si la foto no se puede juzgar por cómo se tomó, el API responde `422` con `{ "reason": ... }` y la app pide repetirla con su propio mensaje: `BLURRY` (borrosa o movida), `TOO_DARK` (poca luz), `GLARE` (reflejos o contraluz), `FACE_NOT_VISIBLE` (sin rostro, cortado por el marco o tapado), `MULTIPLE_FACES` (más de un rostro) u `OTHER`. **No se guarda nada ni cuenta como intento**, así que la mala luz no pesa en el score ni reemplaza un resultado anterior bueno. El API **nunca da motivos de sospecha** (foto de pantalla, impresa…): eso solo baja el nivel de confianza, para no enseñar qué busca el sistema. Para probar este flujo en la app sin gastar con OpenAI, con `APP_LIVENESS_PROVIDER=simulated` se pone `APP_LIVENESS_SIMULATED_PROBLEM` (por ejemplo `TOO_DARK`) y toda selfie responde ese motivo.
- **Resultado:** en `liveness_check`, un registro por solicitud con el **nivel de confianza** (0 a 1, nunca un sí o no), el proveedor, el número de intentos y la fecha y hora del último intento. La selfie se guarda cifrada con AES-256-GCM (`DocumentCipher`, la misma llave `APP_DOCUMENT_KEY`) con su SHA-256; después del envío la base de datos impide cambiarla o borrarla. En la línea de tiempo queda el evento `LIVENESS_CHECKED`.
- **Proveedor:** `APP_LIVENESS_PROVIDER`. Por defecto `openai`: un modelo de visión (`gpt-6-astra`, `APP_LIVENESS_MODEL`) por la Responses API, con la respuesta forzada a un esquema JSON y `store: false`, igual que el DUI. Usa la misma `OPENAI_API_KEY` y se enciende con `APP_LIVENESS_ENABLED=true`; sin eso la prueba queda `FAILED`. **Limitación:** un modelo de visión mira una sola imagen, así que detecta peor una foto de pantalla, una impresión o una máscara que un servicio de prueba de vida dedicado (por ejemplo Azure AI Face, de acceso limitado). Por eso el nivel de confianza es una señal para el score, nunca una decisión. Con `simulated` el cliente contesta siempre `APP_LIVENESS_SIMULATED_CONFIDENCE` (0.90 por defecto): solo para desarrollo, nunca en Dev, QA ni producción reales. Un proveedor nuevo es otra implementación de `LivenessClient`, sin tocar el endpoint ni el expediente.
- **Score:** el nivel de confianza pasa al score como la señal «Prueba de vida» (VDI-115), con peso 10 en el modelo v2.
- **App (VDI-116):** la selfie es el paso 3 de 6, después de confirmar el DUI. La persona la toma con el obturador de la cámara frontal, dentro de un óvalo, igual que las fotos del DUI; solo una escena demasiado oscura apaga el obturador. La prueba de vida la decide el API con esa foto: la app no hace un reto propio (el reto con ML Kit se quitó porque no terminaba en los teléfonos de prueba y ML Kit impedía usar el simulador de iOS). El API no reporta la selfie al retomar una solicitud, así que la app recuerda en el teléfono si ya la mandó, para no pedirla otra vez. Batería de ataques de presentación: [prueba-de-vida-ataques.md](prueba-de-vida-ataques.md).

### Firma electrónica del contrato (VDI-130)

- **Contrato vigente:** `GET /api/onboarding/contract` devuelve `{ version, text, fingerprint }`. La app muestra `text` y, al firmar, manda esa misma `version`. El texto lo guarda el backend, así que la app nunca manda el texto ni la huella.
- **Firmar:** `POST /api/onboarding/requests/{id}/signature` con `{ "contractVersion": "2026.1" }`. Responde `201` con `{ requestId, contractVersion, contractFingerprint, signedAt, provider, simulated }`.
- **Quién puede firmar:** solo una solicitud enviada y aprobada (`COMPLETED`), **una sola vez**. Una en progreso, abandonada, en revisión reforzada (`ENHANCED_REVIEW`) o en espera del RNPN (`AWAITING_RNPN`) responde `409`, igual que la apertura de cuenta. También responde `409` un segundo intento o una versión que no es la vigente (la app vuelve a pedir el contrato).
- **Rechazo (`422`):** el servicio de firma puede rechazarla. No se guarda la firma, queda el evento `CONTRACT_SIGNATURE_REJECTED` y se puede intentar de nuevo.
- **Evidencia:** en `contract_signature`, una fila por solicitud que nadie puede cambiar ni borrar. Guarda la versión, la huella SHA-256 del texto, la fecha y hora, el proveedor, la IP de la conexión, el `User-Agent` y la huella del dispositivo que mandó la app en `/signals`. En la línea de tiempo queda `CONTRACT_SIGNED`. La app no recibe la IP ni el dispositivo.
- **Simulado:** todavía no hay proveedor de firma (`APP_SIGNATURE_PROVIDER=simulated`). Rechaza el DUI `00000040-8` y firma cualquier otro. Detalle: [signature.md](signature.md).

## Consola web — `/api/console` (requiere sesión)

1. `POST /api/console/auth/login` con `{ email, password }` devuelve `accessToken` (vigencia `expiresIn` segundos, 8 h) y el usuario.
2. Enviar `Authorization: Bearer <accessToken>` en todo lo demás. Un `401` significa token vencido, usuario desactivado o sin sesión: volver al login. El rol se lee de la base en cada petición, así que un cambio de rol o una baja aplica de inmediato.
3. Tras 5 intentos fallidos seguidos el correo se bloquea 15 minutos (`429` con `Retry-After`). Todo fallo de login responde lo mismo, sin distinguir si el correo existe.
4. CORS: el origen de la consola debe estar en `APP_CORS_ALLOWED_ORIGINS` (por defecto `http://localhost:3000`).

| Método y ruta | Rol | Tarea |
|---|---|---|
| `GET /requests?status=&riskLevel=&q=&page=&size=` | cualquiera | VDI-56 |
| `GET /requests/locations` (ubicación según la IP de cada solicitud, para el mapa, en una sola llamada; sin datos del DUI) | cualquiera | VDI-109 |
| `GET /requests/{id}` (DUI leído y confirmado con los campos corregidos, ingresos, movimiento, score con el aporte de cada señal, señales, pasos, línea de tiempo): sin caché; cada consulta queda en la auditoría de accesos (`VIEW_REQUEST_DETAIL`) | cualquiera | VDI-44, 49, 53, 56, 81, 109 |
| `GET /requests/{id}/identity-document/{side}` (`FRONT` o `BACK`): la foto descifrada, sin caché; cada consulta queda en la auditoría de accesos | cualquiera | VDI-80, VDI-81 |
| `GET /score-model` (rangos y pesos del modelo vigente) | cualquiera | VDI-82 |
| `GET /admin/score-model/versions` (historial de versiones del modelo: umbrales, `publishedBy`, `validFrom`, `validTo` e `inForce`) | `ADMIN` | VDI-120 |
| `POST /admin/score-model/versions` con `{ lowMax, mediumMax }` (publica una versión nueva con los umbrales nuevos y los pesos de la vigente) | `ADMIN` | VDI-120 |
| `GET /admin/profile-tolerance` (tolerancia vigente del perfil declarado y bitácora de cambios) | `ADMIN` | VDI-137 |
| `PUT /admin/profile-tolerance` con `{ tolerancePercent }` (cambia la tolerancia; entero de 0 a 45) | `ADMIN` | VDI-137 |
| `GET /dossiers?requestNumber=` o `?customerNumber=` (expediente de vinculación archivado; cada consulta queda en la auditoría de accesos) | cualquiera | VDI-135 |
| `GET /score-rules` (reglas vigentes; R-01 está retirada) | cualquiera | VDI-57 |
| `GET /alerts?status=&assigneeId=&account=&from=&to=` | cualquiera | VDI-62 |
| `POST /alerts/{id}/take` (la toma el usuario de la sesión) | `FRAUD_ANALYST`, `ADMIN` | VDI-61 |
| `GET /auth/me`, `POST /auth/change-password` | cualquiera | |
| `GET /users` (activos; `includeInactive=true` solo `ADMIN`) | cualquiera | |
| `POST /users`, `PUT /users/{id}`, `POST /users/{id}/password` | `ADMIN` | |

### Umbrales del score configurables (VDI-120)

Un administrador cambia dónde terminan los niveles del score sin desplegar código: `POST /api/console/admin/score-model/versions` con `{ "lowMax": 39, "mediumMax": 69 }` (fin del nivel Bajo y fin del nivel Medio; arriba de `mediumMax` el nivel es Alto y, con VDI-117, la solicitud pasa a revisión reforzada).

- **Nunca se edita una versión.** El cambio cierra la versión vigente (`validTo`), publica la siguiente copiando sus pesos y se aplica a las evaluaciones que vengan después. Las evaluaciones ya hechas conservan la versión con la que se calcularon (`modelVersion`).
- **Validación:** enteros con 0 ≤ `lowMax` < `mediumMax` < 100, y distintos de los vigentes. Si no se cumple responde `400` con `errors` por campo (`lowMax`, `mediumMax`) y un mensaje en español, y no se guarda nada.
- **Bitácora:** cada versión guarda quién la publicó (`publishedBy`, el nombre del usuario de la consola; vacío en las versiones publicadas por una migración) y cuándo (`validFrom`). `GET /api/console/admin/score-model/versions` las devuelve de la más reciente a la más antigua, con la vigente en `inForce: true`. Cada publicación también queda en la auditoría de accesos (`SCORE_MODEL_PUBLISH`).
- **Permisos:** solo `ADMIN`; un analista recibe `403` (puede seguir leyendo el modelo vigente con `GET /api/console/score-model`).
- **Pesos:** este endpoint no los cambia. Un cambio de pesos es una versión publicada por una migración, como el v2 de VDI-115.

### Tolerancia de desviación del perfil configurable (VDI-137)

El contraste del perfil declarado (VDI-3) marca una desviación cuando una cifra del mes pasa lo declarado más la tolerancia: con 20 % y USD 350 declarados, 420.00 está dentro y 420.01 es desviación. Un administrador cambia la tolerancia sin desplegar: `PUT /api/console/admin/profile-tolerance` con `{ "tolerancePercent": 25 }`. Antes era la variable `APP_PROFILE_TOLERANCE_PERCENT`, que ya no se usa; la migración V34 empieza con 20.

- **Aplica al siguiente movimiento** que reporte el core: la tolerancia se lee de la base con cada movimiento.
- **Validación:** entero de 0 a 45 y distinto del vigente. Si no se cumple responde `400` con `errors.tolerancePercent` en español, y no se guarda nada.
- **Bitácora:** `GET /api/console/admin/profile-tolerance` devuelve la vigente (`tolerancePercent`, con `minPercent` y `maxPercent`) y `changes`, del más reciente al más antiguo: valor nuevo, anterior (`previousPercent`), quién (`changedBy`) y cuándo (`changedAt`). El valor inicial no tiene autor. La base no deja editar ni borrar un cambio, y cada uno queda también en la auditoría de accesos (`PROFILE_TOLERANCE_CHANGE`).
- **Cada desviación guarda su tolerancia:** `tolerancePercent` en cada desviación de `profile-comparison` y de `newDeviations`, y en la evidencia de su alerta. Las registradas antes de V34 quedan con 20. Si después sube la tolerancia, una desviación ya registrada sigue apareciendo en la comparación del mes con la tolerancia y el límite con que se detectó, porque ya levantó una alerta.
- **Permisos:** solo `ADMIN`; los demás roles reciben `403`. La tolerancia vigente también sale en `tolerancePercent` de `GET /accounts/{id}/profile-comparison`.

### Consulta del expediente de vinculación (VDI-135)

Al firmar el contrato (VDI-130) se congela el expediente de vinculación de la solicitud (VDI-131, `docs/expediente-vinculacion.md`). La consola lo busca con `GET /api/console/dossiers` y **uno** de dos filtros:

- `requestNumber=SOL-2026-00001`: el expediente de esa solicitud, `{ requestNumber, customerNumber, dossier }`. `404` si no hay solicitud con ese número o si aún no firmó.
- `customerNumber=CLI-0000001`: la lista de expedientes del cliente, uno por solicitud firmada (un cliente puede tener varias solicitudes, una por tipo de cuenta), del más reciente al más antiguo. `404` si no hay cliente con ese número; `[]` si el cliente aún no firmó ninguna.
- Ninguno o los dos filtros, o un número con otro formato: `400`.
- Una solicitud tiene un solo expediente, así que buscarla por su número o por el del cliente devuelve el mismo (`dossier.id`).
- **Auditoría:** cada consulta con respuesta queda en la auditoría de accesos (`VIEW_BINDING_DOSSIER`, `entity_name = binding_dossier`, `entity_id` = el número consultado), con el usuario, la IP y la fecha y hora. La respuesta no se guarda en caché.
- **Permisos:** cualquier usuario de la consola con sesión, igual que el detalle de una solicitud.

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
