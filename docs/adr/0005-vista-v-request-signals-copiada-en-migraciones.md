# ADR 0005 · Aceptar la vista v_request_signals copiada en cada migración

Estado: Aceptada · 22 de octubre de 2026

## Contexto
- Las señales de riesgo se calculan en SQL, dentro de la vista `v_request_signals`. Cada señal nueva
  exigió recrear la vista completa en una migración nueva (V10, V11, V14 y V18, entre el 6 y el 13
  de octubre). La regla del horario nocturno además se repite como texto en `ScoreEngine.java`.
- Calcular las señales fuera de SQL y reescribir `ScoreEngine` con sus pruebas costaría
  ≈ 14 h, casi cinco veces el máximo de 3 h por tarea del Sprint 4. Desde V18 no ha hecho falta otra
  señal, así que el interés es medio.
- Esas horas están reservadas para pagar la deuda que sí cobra intereses (taller de deuda técnica,
  hallazgos 1, 2, 3, 4 y 8).

## Decisión
Aceptamos cargar con la vista copiada y con la regla nocturna repetida en `ScoreEngine.java`. No la
reescribimos antes de la Feria. Si hay que agregar una señal, se hace como hasta ahora: nueva
migración que recrea la vista y se ajusta `ScoreEngine`.

Revisamos esta decisión si se piden dos o más señales nuevas, o si la vista y `ScoreEngine` dan
resultados distintos.

## Consecuencias
+ Se evitan ≈ 14 h de reescritura y el riesgo de tocar el motor de riesgo, una zona crítica; el
  tiempo va a lo que sí cobra intereses (hallazgos 1, 2, 3, 4 y 8).
+ Una prueba de consistencia (VDI-129, 1 h) falla si la regla de la vista y la de `ScoreEngine.java`
  dejan de coincidir, así que olvidar uno de los dos ya no pasa inadvertido.
- Cambiar la regla del horario nocturno sigue exigiendo editar la vista (nueva migración) y
  `ScoreEngine.java`; la prueba avisa si se olvida uno, pero el doble trabajo sigue.
- Cada migración nueva de la vista copia el SQL completo, así que las diferencias entre versiones
  son difíciles de revisar.
- Si llegan varias señales nuevas, el costo de acumularlas crecerá y habrá que pagar la deuda
  entonces, con un diseño más grande.
- La zona horaria repetida en cinco servicios (hallazgo 5b) no entra en este ADR: es desorden y queda en «si sobra
  tiempo».
