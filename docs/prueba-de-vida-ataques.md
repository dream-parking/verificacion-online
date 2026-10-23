# Prueba de vida: batería de ataques de presentación (VDI-116)

Plan para probar en teléfonos reales que la selfie de la app no se deja engañar con una foto impresa, una pantalla o un video. Se ejecuta a mano: la cámara y ML Kit no corren en el simulador ni en las pruebas automáticas (esas cubren la lógica del reto con una cámara simulada, en `onboarding/test/liveness_challenge_test.dart` y `widget_test.dart`).

## Cómo se defiende la app

Son dos capas independientes:

1. **En el teléfono (reto activo).** La cámara frontal busca el rostro con ML Kit (`google_mlkit_face_detection`, sin enviar nada a internet) y pide, en orden: mirar al frente, girar la cabeza hacia un lado y parpadear. Los tres pasos los tiene que hacer **el mismo rostro** (ML Kit le da un número de seguimiento; si cambia, el reto empieza de nuevo), y la foto se toma sola cuando ese rostro vuelve a mirar al frente con los ojos abiertos. Si en 45 s no se completa, la app muestra «No pudimos completar la prueba» **sin decir por qué** y no envía nada (no cuenta como intento). La lógica está en `onboarding/lib/liveness/liveness_challenge.dart`.
2. **En el API (VDI-114).** La selfie va a `PUT /liveness`, que guarda el **nivel de confianza** del proveedor (0 a 1) y lo pasa al score (VDI-115). La app nunca lo recibe. La consola todavía no muestra la confianza como tal, pero sí la señal «Prueba de vida» en el score del detalle: sus puntos son `10 × (1 − confianza)` (con 0.90 suma 1 de 10; con 0.20, 8). Desde el tercer intento nunca baja de 5.

Un ataque solo pasa si supera las dos capas. Una foto quieta no supera la primera; un video que gire la cabeza y parpadee puede superarla, y para eso está la segunda.

## Preparación

- APK del ambiente **Dev** (artefacto de `onboarding-build` o Firebase App Distribution) en al menos un Android, y la app en un iPhone si hay quien la compile (`flutter run` con Xcode y CocoaPods).
- El API de Dev con el proveedor real: `APP_LIVENESS_PROVIDER=openai` y `APP_LIVENESS_ENABLED=true`. Con `simulated` la confianza es siempre la misma y la capa del API no se está probando.
- Una persona voluntaria (la «víctima») y otra que hace de atacante. Material: foto impresa a color de la víctima (carta, buena calidad), la misma foto recortada como máscara (con los ojos recortados), un segundo teléfono o tablet con una foto y con un video de la víctima girando la cabeza y parpadeando.
- Un DUI de prueba distinto por caso, para encontrar cada solicitud en la consola.

## Casos

En cada caso se llega hasta la selfie con una solicitud nueva y se toca «Empezar la prueba». «Reto» es lo que hace la app; «API» son los puntos de la señal «Prueba de vida» en el detalle de la consola cuando la selfie llega (se calculan al enviar la solicitud, así que hay que terminarla).

| # | Caso | Cómo se hace | Esperado: reto | Esperado: API |
|---|---|---|---|---|
| 1 | Control: persona real | La víctima sigue las indicaciones con buena luz | Se completa solo, sin tocar la pantalla | 0 a 2 puntos (confianza ≥ 0.8) |
| 2 | Foto impresa | Se pone la foto impresa frente a la cámara, quieta | Se queda en «Gira la cabeza»; a los 45 s «No pudimos completar la prueba» | No llega selfie |
| 3 | Foto impresa movida | Se gira y se inclina la hoja para simular el giro | Puede pasar el giro; se queda en «Parpadea» (una foto no cierra los ojos) | No llega selfie |
| 4 | Máscara de papel con ojos recortados | El atacante se pone la foto recortada y parpadea detrás | Puede completarse (los ojos son reales) | Muchos puntos (confianza baja): anotar el valor |
| 5 | Foto en otra pantalla | Foto de la víctima en un segundo teléfono, quieta | Igual que el caso 2 | No llega selfie |
| 6 | Video en pantalla (replay) | Video de la víctima girando y parpadeando, en un segundo teléfono | Puede completarse si el video hace los pasos en orden | Muchos puntos (confianza baja): anotar el valor |
| 7 | Cambio de persona | La víctima mira al frente; sale de cuadro y entra el atacante a girar y parpadear | El reto vuelve a «Mira al frente» al cambiar el rostro | Solo llega la selfie de quien terminó el reto |
| 8 | Dos personas | Víctima y atacante juntos en cuadro | «Solo tú debes aparecer en la cámara»; no avanza | No llega selfie |
| 9 | Poca luz | Persona real en un cuarto oscuro | «Hay poca luz…»; no avanza | Si el API responde `422 TOO_DARK`, la app dice «Había poca luz en tu rostro.» |
| 10 | Mensajes al fallar | Repetir 2, 6 y 9 y leer la pantalla de reintento | Nunca dice «foto», «pantalla», «sospecha» ni similar: solo consejos de luz y encuadre | — |

Los casos 4 y 6 pueden superar el reto del teléfono por diseño: el reto prueba que hay movimiento y parpadeo, no que la piel sea real. Lo que se mide ahí es que la confianza del API baje lo suficiente para que el score derive la solicitud a revisión reforzada (VDI-117).

## Registro de resultados

Copiar esta tabla en el ticket VDI-116 (o en Confluence) y llenarla por teléfono probado.

| # | Teléfono y SO | Reto (pasó / no pasó, en qué paso se quedó) | Puntos de «Prueba de vida» | Nivel de riesgo final | Observaciones |
|---|---|---|---|---|---|
| 1 | | | | | |
| 2 | | | | | |
| 3 | | | | | |
| 4 | | | | | |
| 5 | | | | | |
| 6 | | | | | |
| 7 | | | | | |
| 8 | | | | | |
| 9 | | | | | |
| 10 | | | | | |

## Qué hacer con los resultados

- Si un caso 2, 3, 5 u 8 completa el reto, es un bug de la app: anotar el teléfono y abrir un bug con el paso en que debió detenerse.
- Si en los casos 4 o 6 la señal suma pocos puntos (confianza alta), la capa del API no distingue ese ataque; es la limitación ya conocida del modelo de visión de una sola imagen (ver «Prueba de vida» en [integracion-api.md](integracion-api.md)) y es el argumento para un proveedor dedicado.
- Si el caso 1 falla seguido en algún teléfono (no detecta el giro o el parpadeo), ajustar los umbrales de `LivenessChallenge` (`turnYaw`, `closedEyes`, `steadyFrames`) y repetir.
