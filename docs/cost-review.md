# Revisión de costos Azure (2026-10-01)

Suscripción "Azure subscription 1" (ID omitido porque el repo es público) — **Pay-As-You-Go, spending limit: Off** (no hay tope automático de gasto).

| Hallazgo | Detalle |
|---|---|
| Recursos existentes antes del setup | 0 (los RG `db2` y `soldadura-de-sedas-rg` estaban vacíos) |
| Costo histórico (jul–oct 2026) | USD 2.85 total: Container Registry 2.85, Key Vault 0.003, Log Analytics 0, SQL Database 0 |
| Costo actual | Sin cargos en sep/oct: esos recursos ya fueron eliminados |
| Budgets / alertas de costo | Ninguno al inicio; se creó `budget-verificaciononline-1usd` (1 USD/mes, avisos al 50% y 100% real y 100% forecast) |
| Defender for Cloud | `Microsoft.Security` no registrado (sin planes de pago activos) |

## Decisiones para mantenerse en Free tier

- **WebConsole**: Azure Static Web Apps, plan **Free** (100 GB/mes, 10 apps/suscripción, 250 MB por app). Un SWA por ambiente (`dev`, `qa`).
- **Onboarding (Flutter)**: no requiere hosting. El CI genera el APK como artefacto de GitHub Actions.
- Evitar: Container Registry, App Service Plan (Basic+), Log Analytics/App Insights con ingesta alta, Key Vault premium, Front Door, bases de datos de pago.
- GitHub Actions en repo privado: minutos gratuitos limitados; los runners macOS cuentan 10x, por eso solo se compila Android en Linux.

Cada recurso creado se registra en [`azure-resources.csv`](azure-resources.csv).

## Backend (2026-10-05)

- Container Apps (consumo, escala a cero) y el registro en GHCR público: 0 USD dentro de la franja gratis.
- Se evitó Azure Container Registry (causa del costo de jul-ago: 2.85 USD).
- PostgreSQL Flexible Server B1ms 32 GiB: **costo condicionado**, ver `docs/cicd.md`.
