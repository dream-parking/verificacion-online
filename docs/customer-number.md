# Número de cliente (VDI-122, historia VDI-27; cubre VDI-26)

Cada persona cliente tiene un **número de cliente** único y permanente, como `CLI-0000001`.

## Cuándo se asigna

Cuando se **envía la primera solicitud** de la persona (`POST /api/onboarding/requests/{id}/submit`), sin importar en qué estado termine la solicitud (`COMPLETED`, `ENHANCED_REVIEW` o `AWAITING_RNPN`). Quien ya tiene un número lo conserva en todas las solicitudes siguientes. Hasta el primer envío el número es `null`.

## Por qué nunca se repite

- El número sale de la secuencia de la base `customer_number_seq`, así que dos envíos al mismo tiempo nunca pueden recibir el mismo. Una secuencia puede dejar huecos (un envío que se revierte gasta un número), pero nunca repite uno.
- `customer.number` tiene una restricción de unicidad y el formato `CLI-` más al menos siete dígitos.
- Un disparador rechaza cualquier cambio o borrado de un número ya asignado, así que ni siquiera SQL puede reescribirlo o reutilizarlo.
- Dos envíos de la **misma** persona al mismo tiempo: la fila de la persona se bloquea antes de leer el número, así que el segundo espera y encuentra el número que asignó el primero. Ambos terminan con el mismo número.
- La entidad mapea la columna como de solo lectura; solo `CustomerRepository.assignNumber` la escribe, y únicamente donde sigue nula. Por eso corregir los datos de la persona nunca puede sobrescribir un número asignado entretanto.

La migración `V30__customer_number.sql` también da un número a las personas que ya habían enviado una solicitud, en el orden de su primer envío, y la secuencia continúa después del último.

## Dónde se entrega

| Dónde | Campo |
|---|---|
| `GET /api/console/customers/{id}` y `GET /api/console/customers?dui=` | `number` |
| `GET /api/console/requests/{id}` | `customerNumber` |
| `GET /api/console/customers/risk-history?number=CLI-0000001` | ver abajo |

## Señales de riesgo por número de cliente

`GET /api/console/customers/risk-history?number=CLI-0000001` devuelve, por cada solicitud de la persona (la más antigua primero): su `status`, cada evaluación de score (`assessments`, la más antigua primero; la última es la vigente) y las decisiones tomadas al enviar (`decisions`, VDI-117). Para esto no se guarda nada: las solicitudes ya apuntan a la persona. Responde `404` cuando ninguna persona tiene ese número y `400` cuando el número no tiene el formato.

La app móvil no recibe el número de cliente.

## Qué no se hace aquí

- Vincular o cerrar VDI-26 con VDI-27 en Jira es una decisión del PO (anotada en el ticket).
- La pantalla de la consola que muestra el número es VDI-123.
