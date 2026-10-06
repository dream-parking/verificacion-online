# Integración con la API (Sprint 1)

La documentación interactiva está en `/swagger-ui.html` (perfiles `dev` y `dev-remote`). Errores: `application/problem+json` (`status`, `title`, `detail`); `404` no existe, `400` datos inválidos, `409` estado no permitido.

## App móvil — `/api/onboarding/requests`

| Método y ruta | Para qué | Tarea |
|---|---|---|
| `GET /api/catalogs` | Fuentes de ingreso, rangos de ingreso, tipos de movimiento y rangos de monto mensual. **Úsalo en vez de listas fijas**: los códigos son `SALARIO`, `HASTA_500`, `PAGO_SALARIO`, `200_500`, etc. | VDI-46, VDI-50 |
| `POST /` | Inicia la solicitud y devuelve su `id`. | |
| `PUT /{id}/income` | Paso 2: `sourceCode`, `rangeCode`, `sourceDetail` (obligatorio si es `OTRO`). | VDI-47 |
| `PUT /{id}/expected-activity` | Paso 3: `transactionTypeCode` y `monthlyAmountRangeCode`, ambos del catálogo (sin monto libre). Responde el score. | VDI-52, VDI-55 |
| `PUT /{id}/signals` | Huella, modelo, SO, ubicación, ritmo de escritura y tiempo por paso. La IP la toma el servidor. | VDI-41, VDI-42 |
| `GET /{id}`, `GET /{id}/risk-assessment` | Resumen y score vigente. | |

## Consola web — `/api/console`

| Método y ruta | Para qué | Tarea |
|---|---|---|
| `GET /requests?status=&riskLevel=&q=&page=&size=` | Listado paginado (`page` desde 0, `size` máx. 100). | VDI-56 |
| `GET /requests/{id}` | Detalle: ingresos, movimiento, score y regla, señales, pasos y línea de tiempo. | VDI-44, 49, 53, 56 |
| `GET /score-rules` | Regla vigente y umbral (solo lectura). | VDI-57 |
| `GET /alerts?status=&assigneeId=&account=&from=&to=` | Bandeja ordenada por criticidad (`from`/`to`: `aaaa-mm-dd`). | VDI-62 |
| `POST /alerts/{id}/take` con `{ "userId": … }` | Tomar una alerta sin dueño; `409` si ya la tomaron. | VDI-61 |
| `GET /users` | Usuarios activos (provisional hasta tener login). | |

CORS: la consola debe estar en `APP_CORS_ALLOWED_ORIGINS` (por defecto `http://localhost:3000`).

## Calidad

`./mvnw verify` falla si la cobertura de líneas del backend baja de 70 % (informe en `backend/target/site/jacoco`).

## Reglas de negocio que el contrato no dice

- **Score (R-01):** un rango de monto mensual cuyo techo no supera USD 500 (`HASTA_200`, `200_500`) da riesgo bajo; `500_1000` y `MAS_1000` quedan `PENDING_REVIEW`. El umbral es provisional (VDI-54).
- **Expediente inmutable (VDI-23):** mientras la solicitud está `IN_PROGRESS` se puede corregir el paso 2 o 3 (el `registeredAt` original se conserva); al salir de ese estado la base de datos rechaza editar o borrar las declaraciones, y la API responde `409`.
- Los rangos del catálogo traen `minUsd` y `maxUsd` (primer y último monto del rango; `null` si no tiene piso o techo).
