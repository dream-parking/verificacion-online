# ADR 0009 · Los tipos de la consola salen del contrato OpenAPI, no se escriben a mano

Estado: Aceptada · 31 de octubre de 2026

## Contexto
- El backend publica su contrato en `docs/openapi.json`. La prueba `OpenApiContractTests` falla si el API cambió y el
  archivo no (se regenera con `-Dopenapi.update=true`), así que el archivo siempre describe el API real.
- La consola tenía tipos escritos a mano para el detalle de la solicitud, y era fácil que se separaran del API
  (VDI-127). En la QA del Sprint 4 aparecieron dos casos: `ArchivedDossier` se escribió a mano cuando el contrato ya traía
  `ArchivedDossierResponse` (B-5), y `openapi.d.ts` quedó 414 líneas atrás de `main` cuando VDI-142 agregó rutas y
  esquemas al contrato sin regenerarlo (B-11), sin que el CI lo notara.

## Decisión
1. **Los tipos de lo que viene del API se generan con `npm run gen:api`** (`openapi-typescript`, con
   `--properties-required-by-default`) a `webconsole/src/lib/console/openapi.d.ts`, y la consola los usa. No se
   duplican a mano.
2. **El CI lo exige:** el paso «Tipos al día con el contrato» corre `gen:api` y falla si `openapi.d.ts` cambia. Un PR
   que solo cambia `docs/openapi.json` también corre el job de la consola (PR 186).
3. **Un campo obligatorio que desaparece del contrato rompe la compilación** donde se use. Se comprobó quitando `id`
   de `ConsoleRequestDetail`: `tsc` falló en `RequestDetailView.tsx` y en `api.ts`.
4. Un valor que el contrato todavía anuncia se conserva en los tipos aunque la pantalla ya no lo muestre (por ejemplo
   `CITY` en `ScoreSignal`), porque las evaluaciones y los expedientes anteriores lo siguen trayendo.

Aplica a la consola web. La app móvil (Dart) escribe sus modelos a mano; cambiarlo sería otro ADR.

## Consecuencias
+ Un cambio del API que la consola no entiende se ve al compilar o en el CI, no en Dev.
+ Quien cambia el contrato debe regenerar los tipos en el mismo PR, y el CI se lo recuerda con el comando exacto.
+ Los tipos de la respuesta (`...Response`) son los de la consola, en lugar de otros parecidos.
− El archivo generado es grande y aparece en los PR que cambian el contrato.
− Generar los tipos exige tener `docs/openapi.json` al día, que en local exige Docker para regenerarlo desde el backend.
− Los tipos generados llevan los valores de enums que la pantalla ya no usa; la consola debe tolerar valores que no
  muestra.
