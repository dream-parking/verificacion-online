# ADR 0002 · PostgreSQL como base de datos

Estado: Aceptada · 20 de octubre de 2026

## Contexto
Necesitamos una base de datos relacional para las solicitudes, los
clientes y la bitácora de riesgo. El equipo prefiere una base de datos
de código abierto: no hay licencias que pagar, corre igual en la PC de
cada quien (Docker, Testcontainers) que en Azure, y es fácil encontrar
documentación y ayuda.

Es una decisión de dos vías: el backend accede a los datos con JPA
(Hibernate), así que cambiar a otra base de datos relacional es
posible sin reescribir el dominio.

## Decisión
Usamos PostgreSQL. En Azure corre en un Flexible Server compartido por
Dev y QA, con una base de datos por ambiente. El esquema se versiona
con migraciones de Flyway.

## Consecuencias
+ Código abierto y sin costo de licencia; en Azure, el plan B1ms entra
  en la oferta gratuita de 12 meses.
+ Las pruebas usan un PostgreSQL real con Testcontainers, no una base
  en memoria que se comporte distinto.
+ Gracias a JPA, las entidades y la mayoría de las consultas no
  dependen del motor.
− La facilidad para cambiar es «relativa»: las migraciones de Flyway
  están en SQL de PostgreSQL y algunas usan funciones y triggers en
  PL/pgSQL (por ejemplo, el número de solicitud y la bitácora
  inmutable). Cambiar de motor obliga a reescribirlas.
− Cada consulta nativa o función propia de PostgreSQL que agreguemos
  hace más caro un cambio futuro.


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