# Definition of Done → controles automáticos

Definition of Done del equipo (Los Alan-bres Ágiles) y cómo se hace cumplir. "Automático" = bloquea el merge a `dev`, `QA` y `main` (regla `required checks`); "Manual" = lo verifica una persona.

| Criterio | Control | Tipo |
|---|---|---|
| Sin errores de sintaxis ni incongruencias de variables (no declaradas, sin usar) | Compilación/`flutter analyze`/`eslint`/`next build` en el CI + Quality Gate de Sonar: reliability **A** y maintainability **A** sobre el código nuevo (las variables sin usar son *code smells*) | Automático |
| Convención de nombres acordada | Reglas de nombres de Sonar (maintainability **A**) + lints (`eslint`, `flutter analyze`) | Automático |
| Pruebas unitarias con **≥ 40 %** de cobertura sobre el código fuente | Backend: JaCoCo `check` en `verify` (mínimo **70 %**, más estricto que la DoD). Onboarding: paso "Cobertura mínima" del CI (**40 %**). Sonar: condición de cobertura **global** ≥ 40 % | Automático |
| Revisado por Scrum Master y QA antes de integrar a la rama principal | Revisión de PR | Manual (ver "Pendiente") |
| Cada cambio en su propia rama; se unifica al main solo tras la revisión | Los `required checks` impiden el push directo a `dev`, `QA` y `main`; todo entra por PR | Automático |
| Desplegado en un ambiente accesible por internet en la nube | Despliegue automático de `dev` (Dev) y `QA` (QA) a Azure; ver `docs/cicd.md` | Automático |
| Segundo factor / TOTP, registro de usuarios nuevos | Criterios de aceptación del producto | Manual |
| Criterios de aceptación verificados por el Product Owner y visto bueno del asesor | Revisión de producto | Manual |

## Quality Gate de Sonar: "DoD Alan-bres"
El gate por defecto de Sonar (*Sonar way*) exige 80 % de cobertura sobre el **código nuevo**, que es más estricto que la DoD y distinto de lo que ella pide (40 % sobre **todo** el código). Se reemplaza por un gate propio (SonarQube Cloud → *Quality Gates* → copiar *Sonar way*):

| Condición | Ámbito | Valor |
|---|---|---|
| Reliability rating | Código nuevo | A |
| Maintainability rating | Código nuevo | A |
| Security rating | Código nuevo | A |
| Security hotspots reviewed | Código nuevo | 100 % |
| Duplicated lines | Código nuevo | ≤ 3 % |
| Coverage | **Código global** | **≥ 40 %** |
| ~~Coverage~~ | ~~Código nuevo ≥ 80 %~~ | eliminada |

Se asigna a los tres proyectos (`dream-parking_verificacion-online`, `…-webconsole`, `…-onboarding`).

## Pendiente
- **Webconsole** no tiene tests, así que no cumple el 40 %. Hasta que los tenga, su cobertura está excluida en `webconsole/sonar-project.properties` (incumple la DoD de forma explícita y temporal).
- **Revisión por Scrum Master y QA**: se puede exigir con la regla del repositorio (aprobaciones requeridas en el PR, o `CODEOWNERS` con sus usuarios de GitHub).
