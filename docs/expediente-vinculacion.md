# Expediente de vinculación (VDI-131, VDI-134)

Al firmar, el backend congela una copia inmutable de la evidencia que sustentó la aprobación (`binding_dossier`,
`BindingDossierService.freeze`). La consola la recibe en `bindingDossier` del detalle de `GET /api/console/requests/{id}`.

## Qué trae
DUI confirmado y número de cliente, consentimiento y versión del aviso, último cotejo RNPN, confianza de la prueba de vida,
score con el aporte de cada señal y los umbrales del modelo que lo calculó, bitácora de decisiones, y la firma (versión y
huella SHA-256 del contrato). Cada elemento lleva su fecha y hora.

## Inmutabilidad y retención
- Una solicitud tiene un solo expediente. La base rechaza editarlo siempre.
- `retainedUntil` = fecha de la firma + `app.dossier.retention-years` años (calendario de El Salvador), guardada junto con el
  expediente. La base rechaza borrarlo antes de esa fecha, también al borrar la solicitud completa (V32). Pasada la fecha,
  se puede borrar.
- Cambiar `app.dossier.retention-years` solo afecta a los expedientes que se firmen después: la fecha ya guardada no se mueve.

## Pendiente: plazo normativo
**El plazo no está confirmado por el PO.** El valor por defecto es de **10 años** contados desde la firma, elegido por ser
conservador (no borra antes de tiempo). Hay que confirmar con el PO (con la normativa vigente que aplica):
1. cuántos años se deben conservar, y
2. desde cuándo se cuentan (la firma, como se implementó, o el fin de la relación con el cliente).

Se cambia con la variable `APP_DOSSIER_RETENTION_YEARS` en cada ambiente, sin tocar el código.
