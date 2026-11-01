# ADR 0006 · Sin URL de respaldo en el código: el API se configura por variable y el build falla si falta

Estado: Aceptada · 28 de octubre de 2026

## Contexto
- El ADR 0003 dejó las variables de entorno como único acoplamiento entre el código y el ambiente. Aun así, la
  app móvil (`onboarding/lib/env.dart`) traía las URL de Dev y de QA escritas en el código y elegía una según
  `APP_ENV`; la consola y el backend tenían el mismo problema (VDI-128, VDI-139, VDI-140).
- Una URL de respaldo hace que un build al que se le olvidó la variable funcione igual, pero llamando a otro
  ambiente: un APK de QA contra Dev, sin que nada avise. Con tres ambientes en el horizonte (Prod) el error se
  vuelve más caro.
- La consola ya resolvió lo mismo en VDI-140: su build falla con un mensaje claro si falta la URL.

## Decisión
Ningún componente lleva URL de ambiente en el código. La dirección del API sale solo de una variable (en la app,
`--dart-define=API_URL`; en la consola, `NEXT_PUBLIC_API_URL`; en el backend, `APP_OPENAPI_SERVERS`) y cada
workflow de despliegue la define según la rama. Si falta, el componente falla con un mensaje que dice cómo
definirla; un build de Dev o QA tampoco acepta una URL que apunte a la máquina local.

En la app, `requireApiUrl` lanza el error al crear el cliente del API, y `main` muestra una pantalla que dice que
falta `API_URL` en vez de abrir el flujo. `APP_ENV` solo nombra la app instalada (Dev y QA a la vez); no elige el API.

Es una decisión de dos vías: volver a una URL por defecto es cambiar una línea.

## Consecuencias
+ Un build sin su variable ya no llama a otro ambiente en silencio: falla, o la pantalla lo dice.
+ Agregar Prod, o cambiar de dominio, es cambiar el workflow, no el código (en línea con el ADR 0003).
+ La misma regla en los tres componentes: quien conoce una URL es el workflow de su ambiente.
− En local, `flutter run` ya no basta: hay que pasar `--dart-define=API_URL=...` siempre (el README de
  `onboarding/` trae los tres comandos: Dev, QA y el backend de la Mac).
− Un build de producción de la app sin `API_URL` solo se descubre al abrir la app, no al compilar; hoy lo evita que
  `onboarding-build.yml` siempre la pasa. Una verificación al compilar sería trabajo aparte.
