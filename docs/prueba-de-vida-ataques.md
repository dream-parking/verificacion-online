# Prueba de vida: batería de ataques de presentación (VDI-116)

Plan para probar en teléfonos reales que la selfie de la app no se deja engañar con una foto impresa, una pantalla o un video. Se ejecuta a mano: la cámara no corre en el simulador ni en las pruebas automáticas.

## Cómo se defiende la app

La persona toma la selfie con el obturador de la cámara frontal, como las fotos del DUI. La app **no** hace un reto en el teléfono: hubo uno con ML Kit (mirar al frente, girar, parpadear), pero no terminaba en los teléfonos de prueba (en iOS se quedaba en «poca luz») y ML Kit impedía usar el simulador de iOS, así que se quitó.

La defensa está en el **API (VDI-114)**: la selfie va a `PUT /liveness`, que guarda el **nivel de confianza** del proveedor (0 a 1) y lo pasa al score (VDI-115). La app nunca lo recibe. La consola todavía no muestra la confianza como tal, pero sí la señal «Prueba de vida» en el score del detalle: sus puntos son `10 × (1 − confianza)` (con 0.90 suma 1 de 10; con 0.20, 8). Desde el tercer intento nunca baja de 5.

Ningún ataque se detiene en el teléfono: lo que se mide es que la confianza baje lo suficiente para que el score derive la solicitud a revisión reforzada (VDI-117).

## Preparación

- APK del ambiente **Dev** (artefacto de `onboarding-build` o Firebase App Distribution) en al menos un Android, y la app en un iPhone si hay quien la compile (sección 8 de `onboarding/README.md`).
- El API de Dev con el proveedor real: `APP_LIVENESS_PROVIDER=openai` y `APP_LIVENESS_ENABLED=true`. Con `simulated` la confianza es siempre la misma y no se está probando nada.
- Una persona voluntaria (la «víctima») y otra que hace de atacante. Material: foto impresa a color de la víctima (carta, buena calidad), la misma foto recortada como máscara (con los ojos recortados), un segundo teléfono o tablet con una foto y con un video de la víctima.
- Un DUI de prueba distinto por caso, para encontrar cada solicitud en la consola.

## Casos

En cada caso se llega hasta la selfie con una solicitud nueva, se toca «Tomar mi selfie» y se toma la foto con el obturador. «API» son los puntos de la señal «Prueba de vida» en el detalle de la consola (se calculan al enviar la solicitud, así que hay que terminarla).

| # | Caso | Cómo se hace | Esperado: API |
|---|---|---|---|
| 1 | Control: persona real | La víctima se toma la selfie con buena luz | 0 a 2 puntos (confianza ≥ 0.8) |
| 2 | Foto impresa | Foto impresa de la víctima frente a la cámara | Muchos puntos (confianza baja) |
| 3 | Máscara de papel con ojos recortados | El atacante se pone la foto recortada | Muchos puntos: anotar el valor |
| 4 | Foto en otra pantalla | Foto de la víctima en un segundo teléfono | Muchos puntos |
| 5 | Video en pantalla | Video de la víctima en un segundo teléfono, se toma la foto a mitad del video | Muchos puntos: anotar el valor |
| 6 | Dos personas | Víctima y atacante juntos en cuadro | `422 MULTIPLE_FACES`: la app dice «Aparecía más de una persona.» |
| 7 | Poca luz | Persona real en un cuarto oscuro | `422 TOO_DARK`: «Había poca luz en tu rostro.» (si está muy oscuro, la app apaga el obturador antes) |
| 8 | Mensajes al fallar | Repetir 6 y 7 y leer la pantalla de reintento | Nunca dice «foto», «pantalla», «sospecha» ni similar: solo consejos de luz y encuadre |

## Registro de resultados

Copiar esta tabla en el ticket VDI-116 (o en Confluence) y llenarla por teléfono probado.

| # | Teléfono y SO | Puntos de «Prueba de vida» | Nivel de riesgo final | Observaciones |
|---|---|---|---|---|
| 1 | | | | |
| 2 | | | | |
| 3 | | | | |
| 4 | | | | |
| 5 | | | | |
| 6 | | | | |
| 7 | | | | |
| 8 | | | | |

## Qué hacer con los resultados

- Si en los casos 2 a 5 la señal suma pocos puntos (confianza alta), el API no distingue ese ataque; es la limitación ya conocida del modelo de visión de una sola imagen (ver «Prueba de vida» en [integracion-api.md](integracion-api.md)) y es el argumento para un proveedor dedicado o para volver a un reto activo en el teléfono.
- Si el caso 1 suma muchos puntos en algún teléfono, la cámara frontal de ese modelo engaña al proveedor: anotarlo y abrir un bug.
