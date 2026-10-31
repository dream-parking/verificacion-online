# Registro de decisiones de arquitectura (ADR)

Cada decisión importante va en su propio archivo `NNNN-titulo.md`, con
las secciones Contexto, Decisión y Consecuencias. Una decisión aceptada
no se edita: si cambia, se escribe un ADR nuevo que la reemplace.

| ADR | Decisión | Estado |
|---|---|---|
| [0002](0002-postgres-como-base-de-datos.md) | PostgreSQL como base de datos | Aceptada |
| [0005](0005-vista-v-request-signals-copiada-en-migraciones.md) | Aceptar la vista v_request_signals copiada en cada migración | Aceptada |
| [0006](0006-sin-url-de-respaldo-en-el-codigo.md) | Sin URL de respaldo en el código: el API se configura por variable y el build falla si falta | Aceptada |
| [0007](0007-modelo-de-score-versionado-sin-ciudad-y-con-pesos-que-suman-100.md) | Modelo de score versionado: Ciudad fuera y pesos que suman 100 | Aceptada |
| [0008](0008-migraciones-aplicadas-no-se-revierten-y-el-enum-de-java-conserva-los-valores-de-la-base.md) | Una migración aplicada no se revierte, y el enum de Java conserva todos los valores de la base | Aceptada |
| [0009](0009-los-tipos-de-la-consola-salen-del-contrato-openapi.md) | Los tipos de la consola salen del contrato OpenAPI, no se escriben a mano | Aceptada |
