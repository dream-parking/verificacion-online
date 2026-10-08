# Definition of Done → controles automáticos

Definition of Done del equipo (Los Alan-bres Ágiles) y cómo se hace cumplir. "Automático" = bloquea el merge a `dev`, `QA` y `main` (regla `required checks`); "Manual" = lo verifica una persona.

| Criterio | Control | Tipo |
|---|---|---|
| Sin errores de sintaxis ni incongruencias de variables (no declaradas, sin usar) | Compilación/`flutter analyze`/`eslint`/`next build` en el CI + Quality Gate de Sonar: *Sonar way*: reliability **A** y maintainability **A** sobre el código nuevo (las variables sin usar son *code smells*) | Automático |
| Convención de nombres acordada | Reglas de nombres de Sonar (maintainability **A**) + lints (`eslint`, `flutter analyze`) | Automático |
| Pruebas unitarias con **≥ 40 %** de cobertura sobre el código fuente | Backend: JaCoCo `check` en `verify` (mínimo **70 %**, más estricto que la DoD). Onboarding: paso "Cobertura mínima" del CI (**40 %**). Sonar (gate *Sonar way*): cobertura del código **nuevo** ≥ 80 % | Automático |
| Revisado por Scrum Master y QA antes de integrar a la rama principal | Revisión de PR | Manual (ver "Pendiente") |
| Cada cambio en su propia rama; se unifica al main solo tras la revisión | Los `required checks` impiden el push directo a `dev`, `QA` y `main`; todo entra por PR | Automático |
| Desplegado en un ambiente accesible por internet en la nube | Despliegue automático de `dev` (Dev) y `QA` (QA) a Azure; ver `docs/cicd.md` | Automático |
| Segundo factor / TOTP, registro de usuarios nuevos | Criterios de aceptación del producto | Manual |
| Criterios de aceptación verificados por el Product Owner y visto bueno del asesor | Revisión de producto | Manual |

## Quality Gate de Sonar
Los tres proyectos usan el gate por defecto, *Sonar way*. En el plan gratis de SonarQube Cloud se puede **crear** un gate propio, pero no **asignarlo** a un proyecto (la API responde "Organization … is not allowed to modify Quality gates", comprobado el 2026-10-08), así que no es posible un gate "DoD Alan-bres" propio. Condiciones vigentes sobre el **código nuevo** de cada PR:

| Condición | Valor |
|---|---|
| Reliability / Maintainability / Security rating | A |
| Security hotspots reviewed | 100 % |
| Duplicated lines | ≤ 3 % |
| Coverage | **≥ 80 %** (el equipo decidió mantener este umbral, más estricto que el 40 % de la DoD) |

Sonar way no tiene una condición de cobertura **global**. El mínimo de 40 % sobre todo el código que pide la DoD se hace cumplir en el CI de cada componente (backend: JaCoCo 70 %; onboarding: paso "Cobertura mínima", 40 %).

## Pendiente
- **Webconsole** no tiene tests, así que no cumple el 40 %. Hasta que los tenga, su cobertura está excluida en `webconsole/sonar-project.properties` (incumple la DoD de forma explícita y temporal).
- **Revisión por Scrum Master y QA**: se puede exigir con la regla del repositorio (aprobaciones requeridas en el PR, o `CODEOWNERS` con sus usuarios de GitHub).
