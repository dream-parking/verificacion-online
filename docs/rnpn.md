# Cotejo con el RNPN (VDI-111, historia VDI-17)

Cuando se envía una solicitud (`POST /api/onboarding/requests/{id}/submit`), el backend coteja con el RNPN los datos del DUI que la persona **confirmó** (número, nombres, apellidos y fecha de nacimiento). No existe una API pública del RNPN, así que por ahora el servicio es **simulado**; el resto de la aplicación solo conoce la interfaz `RnpnClient`, de modo que el servicio real pueda reemplazarlo sin tocar nada más.

## Resultados

| Resultado | Significado | Qué pasa con la solicitud | Puntos de la señal `RNPN` |
|---|---|---|---|
| `MATCH` | El RNPN tiene los mismos datos. | Sigue como siempre. | 0 |
| `NO_MATCH` | El RNPN respondió y algún dato difiere (`mismatchedFields`). | Sigue; la persona analista ve cuáles datos difieren. | todos (18 en el modelo de score v4 vigente) |
| `UNAVAILABLE` | El RNPN no respondió tras los reintentos. | Estado `AWAITING_RNPN`: no se aprueba automáticamente y no se puede abrir una cuenta. | la mitad (9 de 18) |

Ningún resultado aprueba o rechaza una solicitud por sí solo: la señal solo suma al score.

## Reintentos

Si el RNPN no responde, se le vuelve a preguntar hasta completar `app.rnpn.attempts` intentos en total (3 por defecto), esperando `app.rnpn.retry-delay` tras el primer fallo (500 ms) y el doble tras cada siguiente (500 ms, luego 1 s). El RNPN se consulta **fuera de cualquier transacción de la base**, para que una respuesta lenta no retenga una conexión.

## Qué se guarda

Tabla `rnpn_check`, solo de agregado (la base rechaza las actualizaciones y los borrados; solo se borra en cascada al eliminar la solicitud completa): el resultado, el proveedor, el número de `attempts`, los `mismatched_fields`, el `failure` cuando el RNPN no respondió, y la fecha y hora. La línea de tiempo recibe un evento `RNPN_CHECKED`.

El detalle de la consola (`GET /api/console/requests/{id}`) devuelve el último cotejo como `rnpn`: `outcome`, `simulated` (verdadero mientras el RNPN es un sustituto, lo cual la consola debe decir), `provider`, `attempts`, `mismatchedFields` y `checkedAt`. El listado de solicitudes filtra las que esperan con `?status=AWAITING_RNPN`.

La app móvil no recibe nada sobre el RNPN: después de enviar solo lee `status`, que es `AWAITING_RNPN` cuando la solicitud espera. No debe mencionar el RNPN ni pedirle a la persona que lo intente de nuevo (los reintentos son de la API).

Si una solicitud espera al RNPN **y** su score supera el umbral, el estado sigue siendo `AWAITING_RNPN`; la decisión de retenerla para revisión reforzada igualmente queda en el registro de auditoría y en la línea de tiempo.

## DUI de prueba del RNPN simulado

La respuesta depende solo del número de DUI de los datos básicos y del DUI confirmado:

| DUI | Resultado |
|---|---|
| `00000020-4` | `NO_MATCH`, con `LAST_NAMES` y `BIRTH_DATE` como campos que no coinciden |
| `00000030-1` | `UNAVAILABLE` (nunca responde, así que la solicitud espera tras 3 intentos) |
| cualquier otro DUI, por ejemplo `01234567-8` | `MATCH` |

Ambos son números de DUI válidos (el dígito verificador es correcto). Las listas se configuran con `APP_RNPN_SIMULATED_NO_MATCH_DUIS` y `APP_RNPN_SIMULATED_UNAVAILABLE_DUIS` (separadas por comas).

## Configuración

| Variable | Valor por defecto | |
|---|---|---|
| `APP_RNPN_PROVIDER` | `simulated` | El único proveedor por ahora. |
| `APP_RNPN_ATTEMPTS` | `3` | Intentos en total, al menos 1. |
| `APP_RNPN_RETRY_DELAY` | `500ms` | Espera tras el primer intento fallido; se duplica tras cada siguiente. |

## Qué no se hace aquí

- Una forma de liberar una solicitud que espera (un nuevo intento del cotejo con el RNPN, o una persona analista que decida): una solicitud en `AWAITING_RNPN` no avanza por sí sola.
- `GET /resume` sigue respondiendo `COMPLETED` para una solicitud que espera, igual que para una retenida en revisión reforzada. Si la app necesita distinguirlas cuando la persona la reabre (VDI-112), eso requiere una decisión con el equipo de la app.
