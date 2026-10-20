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
