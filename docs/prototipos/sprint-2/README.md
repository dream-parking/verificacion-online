# Prototipo del Sprint 2

Pantallas en HTML estático, exportadas del lienzo de diseño del Sprint 2. Abrir [`index.html`](index.html) en el navegador: desde ahí se llega a todas las pantallas, y los botones de cada una enlazan con la siguiente.

Lienzo original (editable): https://claude.ai/artifact/35dGM4tBJHR9FGu3TTCTRL

## App de apertura (`movil/`)

| Pantalla | Historias |
|---|---|
| [Bienvenida](movil/Bienvenida.html) · 5 pasos, con el DUI como paso nuevo | VDI-10, VDI-22 |
| [Retomar](movil/Retomar.html) · la solicitud a medias al volver a abrir la app | VDI-86, VDI-87 |
| [Aviso](movil/Aviso.html) · aviso de privacidad aprobado por Legal, con su versión | VDI-92, VDI-93 |
| [Ubicacion](movil/Ubicacion.html) · permiso de ubicación aproximada, que se puede rechazar | VDI-96, VDI-73 |
| [DatosBasicos](movil/DatosBasicos.html) · validación de nombres y apellidos | VDI-89 |
| [DuiCaptura](movil/DuiCaptura.html) · captura guiada del frente del DUI | VDI-77 |
| [DuiIlegible](movil/DuiIlegible.html) · aviso de foto ilegible | VDI-77 |
| [DuiConfirmar](movil/DuiConfirmar.html) · confirmar o corregir los datos leídos por OCR | VDI-78 |

## Consola (`consola/`)

| Pantalla | Historias |
|---|---|
| [Solicitudes](consola/Solicitudes.html) · listado con el score de 0 a 100 y la ciudad | VDI-84 |
| [Detalle](consola/Detalle.html) · desglose del score, DUI, ciudad, consentimiento y versiones del perfil | VDI-84, VDI-81, VDI-97, VDI-90 |

## Datos de ejemplo

- Los pesos de cada señal y los rangos del score (Bajo 0–39, Medio 40–69, Alto 70–100) son de ejemplo: los define VDI-82.
- RNPN, prueba de vida y listas internas aparecen como «Llega en otro sprint»: no suman ni restan al score.
- La versión del aviso queda como `[X.Y]` hasta que Legal apruebe el texto.
- Las fotos del DUI son dibujos de relleno, no imágenes reales.
