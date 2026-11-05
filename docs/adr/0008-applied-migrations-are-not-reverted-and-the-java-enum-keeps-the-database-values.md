# ADR 0008 · Una migración aplicada no se revierte, y el enum de Java conserva todos los valores de la base

Estado: Aceptada · 31 de octubre de 2026

## Contexto
- Al cierre del Sprint 4 el Product Owner pausó todo lo de producto y solicitud de productos. Se revirtió el código (PR 202 y
  PR 203), pero las migraciones V39 (catálogo de productos) y V41 (solicitud de producto) ya estaban aplicadas en Dev.
  Flyway no deja cambiar una migración aplicada, y PostgreSQL no permite quitar un valor de un tipo `ENUM` una vez
  agregado (`ALTER TYPE request_event_type ADD VALUE 'PRODUCT_REQUESTED'`, en V41).
- Se dejaron V39 y V41 a propósito, pero el revert también quitó `PRODUCT_REQUESTED` del enum `RequestEventType` de
  Java. Como la base se mapea con `NAMED_ENUM`, leer un evento con un valor que el enum no conoce rompe: el detalle de
  SOL-2026-00451, cuya línea de tiempo tenía el evento «Producto elegido», respondió 500 en Dev (hallazgo B-16).
- Lo mismo pasaría con cualquier valor de enum, de una funcionalidad retirada o no, que exista en la base.

## Decisión
1. **Una migración aplicada en algún ambiente no se revierte ni se edita.** Retirar una funcionalidad es una
   migración nueva, si la base debe cambiar, y se hace con el código que ya no la usa. Por defecto, el esquema se queda.
2. **Todo valor de un tipo `ENUM` de la base debe existir en su enum de Java**, aunque ningún código lo escriba ya.
   `RequestEventType.PRODUCT_REQUESTED` se conserva con un comentario que lo explica.
3. **Lo hace cumplir una prueba**, `cadaTipoDeEventoDeLaBaseExisteEnElEnum` (`BackendApplicationTests`), que lee
   `enum_range` de la base y exige que el enum de Java los contenga todos. El valor también sigue en
   `docs/openapi.json`, porque el contrato lo anuncia y la prueba de contrato compara ambos.
4. Un esquema con tablas o valores que ningún código usa es la consecuencia aceptada. Se retiran, si se puede, con
   una migración nueva y deliberada cuando la funcionalidad se descarte para siempre (un valor de enum no se puede quitar).

Es una decisión de dos vías para el código (basta con volver a usar el valor) y de una vía para las migraciones.

## Consecuencias
+ Retirar una funcionalidad ya no rompe las solicitudes antiguas que dejaron datos de ella.
+ El problema se descubre en la CI y no en Dev: la prueba de enums falla si falta un valor.
+ Si el Product Owner reactiva lo de producto, el esquema ya está listo y el revert se deshace con un revert.
− Quedan tablas y un valor de enum sin uso mientras la pausa dure; la prueba de tablas de `public` cuenta esas tablas
  (45 con V39 y V41) y hay que actualizarla si se agregan o se quitan.
− Los enums de Java llevan valores que ya ningún código usa, y cada uno necesita su comentario para que nadie los borre
  por limpieza.
