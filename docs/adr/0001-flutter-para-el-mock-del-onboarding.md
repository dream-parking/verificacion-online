# ADR 0001 · Flutter para el mock del onboarding de la banca móvil

Estado: Aceptada · 20 de octubre de 2026

## Contexto
El onboarding de la banca móvil necesita mostrarse funcionando a las
personas interesadas mientras avanza, no solo en diseños estáticos.
Esas demostraciones deben correr tanto en Android como en iOS, y
mantener dos bases de código nativas para un mock duplicaría el
esfuerzo en algo que es una referencia, no el producto final.

## Decisión
Construimos el mock del onboarding con Flutter, con una sola base de
código para ambos sistemas operativos. Se eligió a propósito, para que
los avances pudieran mostrarse funcionando en Android y en iOS.

Es una decisión de una vía: el mock queda escrito en Dart y Flutter, y
cambiar de tecnología después implicaría reescribirlo.

## Consecuencias
+ Un solo código para Android e iOS: los avances se muestran en ambos
  sistemas sin trabajo duplicado.

+ El proyecto (`onboarding/`) ya incluye las carpetas `android` e `ios`,
  listo para correr en ambas plataformas.

− Es difícil dar marcha atrás: cambiar de tecnología implica reescribir
  el mock.

− El código del mock no se reutiliza directamente si la banca móvil real
  usa otra tecnología.

− El equipo debe mantener el toolchain de Flutter y de ambos sistemas
  (Android Studio y Xcode).
