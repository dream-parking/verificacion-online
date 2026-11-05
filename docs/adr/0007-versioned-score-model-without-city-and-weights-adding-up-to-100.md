# ADR 0007 · Modelo de score versionado: Ciudad fuera y pesos que suman 100

Estado: Aceptada · 31 de octubre de 2026

## Contexto
- El score de riesgo suma `severidad x peso` de cada señal (monto, documento, ritmo de escritura, dispositivo,
  horario, cotejo RNPN y prueba de vida) y las pantallas lo muestran «de 100».
- La ciudad se captura (VDI-72) pero no se puede puntuar con certeza: su fila decía «No disponible · 0 de 15» y, en las
  evaluaciones viejas, «llega con la ubicación», que ya no era cierto (hallazgo B-14 de la QA del Sprint 4). Aun así su
  peso de 15 seguía en el modelo, así que ninguna solicitud podía pasar de 85.
- Las evaluaciones, los expedientes congelados y las decisiones guardan el modelo con el que se calcularon y la base no
  deja editarlos ni borrarlos, así que el modelo no se puede corregir en su lugar.

## Decisión
1. **Un modelo nunca se edita: cada cambio es una versión nueva.** La migración cierra la versión vigente (`valid_to`)
   y publica la siguiente con sus umbrales y pesos. Las evaluaciones anteriores conservan su versión.
2. **Ciudad sale del score** (PR 207, migración V42, modelo v3): se sigue capturando y mostrando con las señales del
   dispositivo, pero no tiene peso. El valor `CITY` se conserva en la base, en el contrato y en el enum porque las
   evaluaciones y los expedientes anteriores lo usan; la consola no lista la fila cuando no suma puntos.
3. **Los pesos vuelven a sumar 100** (migración V43, modelo v4): los 15 puntos de Ciudad se reparten en proporción al
   peso de cada señal, redondeando para que el total sea exactamente 100: monto 23, documento 18, ritmo 12,
   dispositivo 12, horario 6, cotejo RNPN 18 y prueba de vida 11.
4. **Los umbrales no cambian** (Bajo hasta 39, Medio hasta 69, revisión reforzada sobre 69). Los administradores los
   siguen cambiando con una versión nueva (VDI-120); los pesos solo cambian con una migración.

Es una decisión de dos vías: otro reparto es otra versión.

## Consecuencias
+ El máximo posible vuelve a ser 100 y coincide con lo que dicen las pantallas.
+ Ninguna evaluación guardada cambia: el expediente de una solicitud firmada muestra el modelo con el que se aprobó.
+ La regla de las versiones sirve para el siguiente cambio de pesos o de señales.
− Las solicitudes nuevas suben de score cerca de un 18 % respecto a las del modelo v3, y los umbrales se calibraron
  con el máximo anterior: una solicitud límite puede cambiar de nivel. Pendiente que el Product Owner confirme si
  39 y 69 se quedan.
− El enum `ScoreSignal` conserva `CITY` aunque ningún modelo vigente lo use: quitarlo exigiría migrar las evaluaciones
  y los expedientes inmutables.
− Quien compare solicitudes de dos versiones del modelo debe tener en cuenta que sus escalas son distintas.
