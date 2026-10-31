# ADR-0003: Dos ambientes, Dev y QA, con las variables de entorno como único acoplamiento 

Estado: Aceptada · 20 de octubre de 2026

## Contexto
El equipo necesita un lugar donde equivocarse sin consecuencias y otro
donde entregar software confiable. Con un solo ambiente, o la versión
estable deja de ser confiable, o nadie se atreve a probar cosas nuevas.
Además, no queremos que el código quede atado a una sola estrategia de
despliegue: hoy usamos Azure en franjas gratuitas, y eso puede cambiar.

## Decisión
Mantenemos dos ambientes a propósito, como dos vías:

- **Dev** (rama `dev`): para probar, equivocarse y volver a iterar.
- **QA** (rama `QA`): para entregar software confiable y funcionando.
  Solo recibe lo que ya se aprobó en Dev, por PR.

Las dos vías usan el mismo código. Lo único que se acopla al ambiente
son las variables de entorno (base de datos, secretos, CORS, `APP_ENV`).
El código no ramifica por nombre de ambiente: lee configuración.

## Consecuencias
+ Dev puede romperse sin afectar la versión que entregamos desde QA.
+ QA es confiable: solo avanza por PRs aprobados y lo que corre ya se
  probó en Dev.
+ El código no depende de la estrategia de despliegue. Cambiar de host o
  agregar un tercer ambiente (Prod) es cambiar variables y pipeline, no
  la aplicación.
+ Es barato: caben en las franjas gratuitas (un servidor PostgreSQL con
  dos bases y dos Container Apps que escalan a cero).
− Hay dos ambientes que mantener: si una variable falta en QA, solo se
  descubre al desplegar ahí.
− Dev y QA comparten servidor de base de datos y presupuesto gratuito:
  están aislados por base y por rol, no por infraestructura.
− Hoy QA corre con el mismo perfil de Spring que Dev (datos demo y
  Swagger), así que no se parece a producción en eso. Prod no debe
  activar ese perfil.
