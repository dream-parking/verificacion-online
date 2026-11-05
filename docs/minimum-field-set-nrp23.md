# Set mínimo de campos y su sustento (VDI-85)

Criterio 7 de VDI-22: cada campo del set mínimo tiene documentado si lo exige la NRP-23 u otra razón.

## Alcance

El paso «Datos básicos» de la app («Cuéntanos quién eres») pide solo **cuatro** datos, todos obligatorios (criterio 1 de
VDI-22). El API los recibe en `PUT /api/onboarding/requests/{id}/basic-data` (`BasicDataRequest`) y no acepta otros:

| Campo del API | Dato | Formato que valida el API |
|---|---|---|
| `firstNames` | Nombres | Letras latinas (tildes, diéresis y ñ), unidas por espacio, apóstrofo o guion; máximo 100 (`@PersonName`, VDI-88) |
| `lastNames` | Apellidos | Igual que los nombres |
| `dui` | Número de DUI | `00000000-0` (`@Dui`) |
| `mobilePhone` | Teléfono celular | `7000-0000`, empieza con 6 o 7 (`@MobilePhone`) |

Se guardan en `customer` (un cliente por DUI, `uq_cliente_dui`) y como copia en `onboarding_request` (lo que la persona
declaró en esa solicitud).

## Qué es la NRP-23

Las «Normas Técnicas para la Gestión de la Seguridad de la Información» (NRP-23) del Banco Central de Reserva tratan de
**cómo proteger** la información: gobierno de la seguridad, SGSI, seguridad lógica, ciberseguridad, respaldos y
tercerización. **No enumeran qué datos de identificación debe pedir una entidad a su cliente.** Por eso ninguno de los
cuatro campos es exigido *por la NRP-23*; la NRP-23 sí exige cómo tratarlos una vez recolectados.

La razón para **pedir** cada dato viene de la debida diligencia del cliente (prevención de lavado de dinero: la norma
técnica de gestión de riesgos de LA/FT y el instructivo de la UIF) o del negocio. Esas normas no están en este
repositorio: lo marcado como *por confirmar* debe validarlo Conozca a su Cliente con el texto vigente.

Los artículos de la NRP-23 se citan por su posición en el texto (el documento no trae la numeración impresa); coinciden
con las referencias internas de la propia norma (art. 10 SGSI, art. 22 adquisición y desarrollo, art. 29 documentación).

## Sustento por campo

| Campo | ¿Lo exige la NRP-23? | Por qué se pide | Para qué lo usa el sistema |
|---|---|---|---|
| **Nombres** | No | Identificación del cliente en la debida diligencia (KYC) — *por confirmar con la norma LA/FT / instructivo UIF* | Expediente; se compara con lo leído en la foto del DUI (`namesMatchDeclared`, VDI-81) |
| **Apellidos** | No | Igual que los nombres — *por confirmar* | Igual que los nombres |
| **DUI** | No | Documento de identidad del cliente en la debida diligencia — *por confirmar* | Identifica al cliente (único por DUI); se compara con la foto (`duiMatchesDeclared`); su dígito verificador cuenta en el score (VDI-2) |
| **Teléfono celular** | No | Razón comercial: contactar a la persona durante y después de la apertura. *Confirmar si la norma LA/FT lo pide como dato de contacto* | Contacto; un celular no puede pertenecer a otro DUI (409 al registrar) |

## Obligaciones de la NRP-23 que aplican a estos datos

| Obligación (NRP-23) | Qué pide | Estado en el backend |
|---|---|---|
| Considerando 9 · Integridad | Información íntegra «de acuerdo a la veracidad de los documentos legales de donde fue extraída» | ✅ Los datos declarados se comparan con los leídos del DUI (VDI-79, VDI-81) |
| Art. 22 a) · Controles al ingreso | Controles al ingreso, acceso, transmisión, procesamiento y salida de información | ✅ El API valida formato y caracteres de los cuatro campos y responde 400 (VDI-88). ⚠️ El dígito verificador del DUI no se valida al ingresar: solo se evalúa en el score |
| Art. 22 b) · Cifrado | Cifrado que proteja el almacenamiento y transporte de la información crítica | ⚠️ Las fotos del DUI se cifran con AES-256-GCM (VDI-80), pero DUI, nombres y celular se guardan en texto plano. El cifrado en reposo y TLS dependen de la configuración de Azure Database for PostgreSQL, fuera del repositorio |
| Art. 28 · Privacidad | Proteger la confidencialidad de los datos personales de los clientes | ⚠️ `GET /api/onboarding/requests/{id}/resume` es público y devuelve DUI, nombres y celular a quien conozca el id de la solicitud (UUID). La consola exige sesión |
| Art. 11 · Seguridad lógica (pistas de auditoría) | Seguimiento del acceso y uso de los sistemas para detectar actividades no autorizadas | ⚠️ `access_audit` registra quién ve las fotos del DUI, pero no quién abre el detalle de una solicitud (`GET /api/console/requests/{id}`) con estos datos |

## Brechas anotadas

Quedan fuera de VDI-85 (que solo pide documentar el sustento) y conviene llevarlas a tareas propias:

1. **Auditar la consulta de datos personales** en la consola: registrar en `access_audit` cada apertura del detalle de
   una solicitud (art. 11).
2. **Proteger `/resume`**: atar la consulta a algo más que el id de la solicitud (por ejemplo el dispositivo de VDI-4) o
   no devolver el DUI completo (art. 28).
3. **Cifrar DUI y celular en reposo** a nivel de aplicación, como las fotos, o documentar la configuración de Azure que
   lo cubre (art. 22 b).
4. **Validar el dígito verificador del DUI al ingresar** los datos básicos (art. 22 a), si el negocio lo acepta.
5. **Confirmar con Conozca a su Cliente** la norma LA/FT que exige cada dato (columna *por confirmar*).
