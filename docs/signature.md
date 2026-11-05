# Firma electrónica del contrato (VDI-130, historia VDI-15)

La persona cliente de una solicitud aprobada firma desde la app el contrato de apertura de cuenta. Todavía no hay un proveedor de firma, así que por ahora el servicio es **simulado**, igual que el RNPN de VDI-111. El resto de la aplicación solo conoce la interfaz `ContractSigner`, de modo que un proveedor real pueda reemplazarlo sin tocar nada más.

## El contrato

El texto lo guarda el backend: `backend/src/main/resources/contracts/contrato-apertura-<version>.txt`, elegido por `app.contract.version` (`2026.1`). El texto actual es un **texto de prueba** marcado como tal; el equipo legal del banco entrega el real.

- `GET /api/onboarding/contract` devuelve `version`, `text` y `fingerprint` (SHA-256 del texto en UTF-8, en hexadecimal y minúsculas). Los saltos de línea se leen como `\n`, por lo que la huella no depende de cómo git descargó el archivo.
- La app muestra el texto y firma enviando la `version` que mostró. El backend calcula la huella por su cuenta, así que una firma siempre prueba cuáles palabras exactas se firmaron.
- Una versión nueva es un archivo nuevo y un `APP_CONTRACT_VERSION` nuevo. Una solicitud que intenta firmar una versión que ya no está vigente recibe `409` y debe mostrar el contrato otra vez.

## Quién puede firmar

`POST /api/onboarding/requests/{id}/signature` con `{ "contractVersion": "2026.1" }`:

| Situación | Respuesta |
|---|---|
| Enviada y aprobada (`COMPLETED`), primera vez | `201`, la firma |
| La misma solicitud otra vez | `409`, ya firmada |
| `ENHANCED_REVIEW`, `AWAITING_RNPN`, `IN_PROGRESS` o `ABANDONED` | `409`, la misma regla que para abrir una cuenta |
| `contractVersion` no es la vigente | `409` |
| Falta `contractVersion` o está en blanco | `400` |
| Solicitud desconocida | `404` |
| El servicio de firma la rechaza | `422`; no se guarda nada como firma y la persona puede intentarlo de nuevo |

Dos firmas de la misma solicitud al mismo tiempo: `contract_signature.request_id` es único, así que solo una pasa y la otra responde `409`.

## Qué se guarda

Tabla `contract_signature` (migración `V33__contract_signature.sql`), solo de agregado: la base rechaza las actualizaciones y los borrados; solo se borra en cascada al eliminar la solicitud completa.

| Columna | |
|---|---|
| `contract_version`, `contract_fingerprint` | Qué texto se firmó |
| `signed_at` | Fecha y hora de la firma |
| `provider` | `simulated` mientras no haya un proveedor real |
| `ip` | Dirección del cliente que hizo la llamada de firma |
| `device` | `User-Agent` de la llamada de firma |
| `device_fingerprint` | Huella del dispositivo que la app envió en `/signals`, si la hubo |

La línea de tiempo recibe `CONTRACT_SIGNED`, o `CONTRACT_SIGNATURE_REJECTED` con el motivo. La app no recibe la IP ni el dispositivo.

## DUI de prueba del servicio simulado

| DUI | Resultado |
|---|---|
| `00000040-8` | Rechazada (`422`) |
| cualquier otro DUI | Firmada |

`00000040-8` es un DUI válido y el RNPN simulado lo da por coincidente, así que su solicitud se aprueba y llega a la firma. La lista se configura con `APP_SIGNATURE_SIMULATED_REJECTED_DUIS` (separada por comas).

## Configuración

| Variable | Valor por defecto | |
|---|---|---|
| `APP_CONTRACT_VERSION` | `2026.1` | Versión del contrato vigente; su archivo de texto debe existir o el backend no arranca. |
| `APP_SIGNATURE_PROVIDER` | `simulated` | El único proveedor por ahora. |
| `APP_SIGNATURE_SIMULATED_REJECTED_DUIS` | `00000040-8` | DUI que el servicio simulado rechaza. |

## Expediente de vinculación

Al firmar se congela el expediente de vinculación (VDI-131, `BindingDossierService.freeze`) en la misma transacción, con la versión del contrato, la huella y la fecha de la firma; una firma rechazada no congela ninguno. Ver `docs/binding-dossier.md`. Las migraciones son `V35` (expediente) y `V36` (retención), numeradas después de `V33` y `V34` de esta.

## Qué no se hace aquí

- **IP detrás de un proxy:** la IP es la dirección del cliente que ve el backend (`getRemoteAddr`), igual que en las demás señales. Detrás del ingress de Azure puede ser la del proxy, a menos que se confíe en las cabeceras reenviadas.
- **Proveedor real:** un servicio real es una llamada de red. Entonces debería ejecutarse fuera de la transacción de la base, como el RNPN.
