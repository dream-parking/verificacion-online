# Pruebas del sprint 4 – Fernando

Guía para las tres pruebas manuales pendientes. Anota el resultado de cada paso (✓ / ✗ y una captura si falla).

## Preparación

Correr la app contra Dev, desde `onboarding/` (la app no trae URL en el código, VDI-141):

```bash
flutter run --dart-define=API_URL=https://api.dev.identidad.alambritos.online
```

La primera petición puede tardar hasta un minuto si el servidor estaba dormido. La consola del equipo se usa para ver el resultado de la solicitud.

## 1. Nombre distinto al del DUI (score y textos del RNPN, B-8)

**Qué cambió (PR 201):** si los nombres confirmados del DUI no coinciden con los de los datos básicos, la señal «Documento de identidad» suma 0.6 de severidad (9 de 15 puntos en el modelo v2); antes sumaba 0.2 (3 puntos). Un número de DUI distinto suma 0.3. En la fila Ciudad el desglose ahora dice «Aún no se evalúa en el score».

Pasos:

1. Crear una solicitud **nueva** en la app (no retomar una anterior: el score de las ya evaluadas no cambia).
2. En los datos básicos escribir un nombre y apellidos **distintos** a los que trae el DUI que se va a fotografiar.
3. Tomar el DUI, confirmar los datos leídos, tomar la selfie, firmar y enviar.
4. Abrir la solicitud en la consola y revisar el desglose del score.

Resultado esperado:

- [ ] «Documento de identidad» aporta más puntos que antes (hasta 9 de 15 por la diferencia de nombres) y el hallazgo dice «los nombres no coinciden con los de los datos básicos».
- [ ] La fila Ciudad dice «Aún no se evalúa en el score».
- [ ] La sección RNPN está en español: «✓ Coincide», «✕ No coincide», «○ No disponible»; filas «Fecha del cotejo», «Intentos», «No coinciden»; campos «Número de DUI», «Nombres», «Apellidos», «Fecha de nacimiento».
- [ ] Marca «simulado» visible mientras el RNPN sea el servicio de prueba.

Para ver los tres textos del RNPN usar los DUI de prueba (`docs/rnpn.md`): `00000020-4` da «No coincide» (apellidos y fecha de nacimiento), `00000030-1` da «No disponible» (queda «En espera»), cualquier otro (por ejemplo `01234567-8`) da «Coincide».

## 2. App móvil

### VDI-132 – contrato y firma

- [ ] Después de la selfie aparece el contrato; se puede leer completo.
- [ ] No se puede firmar sin aceptar; al firmar pasa al siguiente paso.
- [ ] Sin conexión al cargar o firmar: mensaje de error en español y se puede reintentar.
- [ ] Reabrir la app con la solicitud ya firmada: no pide firmar otra vez.
- [ ] En la consola el expediente muestra la firma.

### VDI-141 – URL del API por `--dart-define`

- [ ] Con `--dart-define=API_URL=<url de Dev>` la app funciona normal.
- [ ] Sin `--dart-define` (`flutter run` a secas) la pantalla dice que falta `API_URL` y no llama a ningún ambiente.
- [ ] Con `APP_ENV=dev` o `qa` y una `API_URL` a `localhost` o `10.0.2.2` la app la rechaza.

Pruebas automáticas relacionadas (desde `onboarding/`):

```bash
flutter analyze
```

```bash
flutter test
```

## 3. CP-135 – teclado y lector de pantalla (si alcanza el tiempo)

Con TalkBack (Android) o VoiceOver (iOS), y con teclado en un emulador o navegador:

- [ ] Se puede recorrer cada pantalla en orden lógico, sin saltos ni elementos atrapados.
- [ ] Cada botón y campo tiene una etiqueta que el lector dice con sentido (no «botón sin nombre»).
- [ ] Los errores se anuncian al aparecer.
- [ ] Las opciones de selección (por ejemplo rango de monto) dicen cuál está elegida.
- [ ] Contrato y firma se pueden leer y aceptar solo con el lector.
- [ ] El foco visible se distingue en todo momento.

Anotar cada pantalla donde falle, con el paso y qué dijo el lector.
