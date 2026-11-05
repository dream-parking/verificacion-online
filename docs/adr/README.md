# Registro de decisiones de arquitectura (ADR)

Cada decisión importante va en su propio archivo `NNNN-titulo.md`, con
las secciones Contexto, Decisión y Consecuencias. Una decisión aceptada
no se edita: si cambia, se escribe un ADR nuevo que la reemplace.

| ADR | Decisión | Estado |
|---|---|---|
| [0002](0002-postgres-as-the-database.md) | PostgreSQL como base de datos | Aceptada |
| [0005](0005-v-request-signals-view-copied-in-migrations.md) | Aceptar la vista v_request_signals copiada en cada migración | Aceptada |
| [0006](0006-no-fallback-url-in-the-code.md) | Sin URL de respaldo en el código: el API se configura por variable y el build falla si falta | Aceptada |
| [0007](0007-versioned-score-model-without-city-and-weights-adding-up-to-100.md) | Modelo de score versionado: Ciudad fuera y pesos que suman 100 | Aceptada |
| [0008](0008-applied-migrations-are-not-reverted-and-the-java-enum-keeps-the-database-values.md) | Una migración aplicada no se revierte, y el enum de Java conserva todos los valores de la base | Aceptada |
| [0009](0009-console-types-come-from-the-openapi-contract.md) | Los tipos de la consola salen del contrato OpenAPI, no se escriben a mano | Aceptada |
