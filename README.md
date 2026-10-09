<div align="center">

<img src="docs/readme/hero.svg" alt="Banco Tangamandapio · Verificación Online: apertura de cuenta digital con debida diligencia y señales antifraude" width="100%">

<br>

[![CI](https://github.com/dream-parking/verificacion-online/actions/workflows/ci.yml/badge.svg)](https://github.com/dream-parking/verificacion-online/actions/workflows/ci.yml)
[![Quality Gate](https://sonarcloud.io/api/project_badges/measure?project=dream-parking_verificacion-online&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=dream-parking_verificacion-online)
[![Cobertura backend](https://sonarcloud.io/api/project_badges/measure?project=dream-parking_verificacion-online&metric=coverage)](https://sonarcloud.io/summary/new_code?id=dream-parking_verificacion-online)

![Flutter](https://img.shields.io/badge/Flutter-App_móvil-0A402B?style=for-the-badge&logo=flutter&logoColor=9DDFA6)
![Next.js](https://img.shields.io/badge/Next.js_16-Consola-081B39?style=for-the-badge&logo=nextdotjs&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot_4-API-0A402B?style=for-the-badge&logo=springboot&logoColor=9DDFA6)
![Java](https://img.shields.io/badge/Java_21-081B39?style=for-the-badge&logo=openjdk&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL_17-0A402B?style=for-the-badge&logo=postgresql&logoColor=9DDFA6)
![Azure](https://img.shields.io/badge/Azure-Free_tier-081B39?style=for-the-badge&logo=microsoftazure&logoColor=white)

**[Consola Dev](https://console.dev.identidad.alambritos.online)** ·
**[API Dev](https://api.dev.identidad.alambritos.online/swagger-ui.html)** ·
**[Guía de integración](docs/integracion-api.md)** ·
**[CI/CD](docs/cicd.md)** ·
**[Definition of Done](docs/definition-of-done.md)**

</div>

<br>

## ✦ ¿Qué es Verificación Online?

**Banco Tangamandapio** quiere que cualquier persona en El Salvador abra su cuenta **desde el teléfono, sin filas y sin ir a una agencia**. Verificación Online es la plataforma que lo hace posible sin bajar la guardia: mientras el cliente llena su solicitud, el banco aplica **debida diligencia (Conozca a su Cliente)**, calcula un **score de riesgo** y reúne **señales antifraude** para que los analistas decidan con información.

<table>
<tr>
<td width="33%" valign="top">

### 📱 Para el cliente
Una app en **4 pasos y unos 5 minutos**: datos básicos, ingresos, el dinero que moverá la cuenta y un resumen para confirmar. Errores claros y en español.

</td>
<td width="33%" valign="top">

### 🛡️ Para el banco
Cada solicitud llega con su **score**, su **huella de dispositivo**, el **ritmo de escritura** y la **ubicación aproximada**. Al enviarse, el expediente queda **inmutable**.

</td>
<td width="33%" valign="top">

### 🧑‍💼 Para los analistas
Una **consola web** con roles para revisar solicitudes, tomar **alertas** de fraude, consultar la **regla de score** y administrar usuarios.

</td>
</tr>
</table>

<br>

## 🧭 El recorrido del cliente

<img src="docs/readme/journey.svg" alt="Recorrido del cliente: aviso de privacidad, datos básicos, ingresos, dinero de la cuenta y envío, con señales capturadas en segundo plano" width="100%">

> [!NOTE]
> **Primero el consentimiento.** Ninguna señal se captura antes de que la persona acepte el aviso de privacidad; el servidor guarda la versión del aviso que aceptó y desde qué IP.

| | Regla | Qué significa |
|:-:|---|---|
| 🎯 | **Score R-01** | Si el monto mensual esperado no supera **USD 500**, el riesgo es **bajo**; si lo supera, queda **pendiente de evaluación**. Umbral provisional, pendiente de confirmar con Conozca a su Cliente. |
| 🔒 | **Expediente inmutable** | Al salir de `IN_PROGRESS`, la base de datos rechaza editar o borrar las declaraciones. |
| 🧾 | **Número único** | El servidor asigna `SOL-AAAA-NNNNN` al enviar; reenviar responde `409` y nunca duplica. |
| 📍 | **Ubicación por IP** | La app no envía coordenadas: el servidor resuelve la ubicación aproximada a partir de la IP. |

<br>

## 🖥️ La consola de Conozca a su Cliente

<img src="docs/readme/console.svg" alt="Consola web: listado de solicitudes con estado y nivel de riesgo" width="100%">

<table>
<tr>
<td width="25%" valign="top" align="center">

**📋 Solicitudes**<br>
<sub>Búsqueda por nombre, DUI o número; detalle con ingresos, movimiento esperado, score, señales y línea de tiempo.</sub>

</td>
<td width="25%" valign="top" align="center">

**🚨 Bandeja de alertas**<br>
<sub>Filtros por estado, responsable, cuenta y fechas. Fraude y Administración pueden <i>tomar</i> una alerta.</sub>

</td>
<td width="25%" valign="top" align="center">

**⚖️ Regla de score**<br>
<sub>La regla vigente, su umbral, su versión y su estado (provisional, confirmada…).</sub>

</td>
<td width="25%" valign="top" align="center">

**👥 Usuarios**<br>
<sub>Altas, roles, bajas y contraseñas iniciales. Solo para administradores.</sub>

</td>
</tr>
</table>

<details>
<summary><b>Roles y catálogo de alertas</b></summary>
<br>

| Rol | Nombre en la consola | Puede |
|---|---|---|
| `KYC_LEAD` | Conozca a su Cliente | Ver solicitudes, alertas y la regla de score |
| `FRAUD_ANALYST` | Fraude y cumplimiento | Todo lo anterior y **tomar alertas** |
| `ADMIN` | Administración | Todo lo anterior y **gestionar usuarios** |

| Criticidad | Alerta |
|:-:|---|
| ![Crítica](https://img.shields.io/badge/◆-Crítica-8E1238?style=flat-square) | Movimientos por encima del perfil declarado · Ingresos de origen distinto al declarado |
| ![Alta](https://img.shields.io/badge/▲-Alta-B24A00?style=flat-square) | Actividad en horario nocturno · Dispositivo nuevo con ubicación distinta · Varias solicitudes desde el mismo dispositivo |
| ![Media](https://img.shields.io/badge/■-Media-7A4B00?style=flat-square) | Ritmo de escritura atípico · Monto declarado cercano al umbral |
| ![Baja](https://img.shields.io/badge/●-Baja-00448C?style=flat-square) | Cambio de teléfono durante la solicitud · Acceso fuera del horario habitual |

La sesión dura 8 horas; tras 5 intentos fallidos el correo se bloquea 15 minutos. El rol se lee en cada petición, así que un cambio o una baja aplica de inmediato.

</details>

<br>

## 🏛️ Arquitectura

```mermaid
%%{init: {'theme':'base','themeVariables':{'fontFamily':'Helvetica, Arial, sans-serif','lineColor':'#5B8C6B','primaryTextColor':'#1A1B1A'}}}%%
flowchart LR
    cliente(["👤 Cliente"]):::persona
    analista(["🧑‍💼 Analista"]):::persona

    subgraph canales["Canales"]
        app["📱 <b>App móvil</b><br/>Flutter · Android e iOS"]:::canal
        consola["🖥️ <b>Consola web</b><br/>Next.js 16 · React 19"]:::canal
    end

    subgraph azure["☁️ Azure · Free tier"]
        api["⚙️ <b>API</b><br/>Spring Boot 4 · Java 21<br/>Container Apps"]:::nucleo
        db[("🗄️ <b>PostgreSQL 17</b><br/>Flyway")]:::datos
        swa["Static Web Apps"]:::infra
    end

    geo["🌎 ip-api.com<br/>ubicación por IP"]:::externo

    cliente --> app
    analista --> consola
    consola -. alojada en .- swa
    app -- "/api/onboarding<br/>público" --> api
    consola -- "/api/console<br/>JWT + roles" --> api
    api --> db
    api -. IP del cliente .-> geo

    classDef persona fill:#9DDFA6,stroke:#0A402B,color:#1A1B1A,stroke-width:2px
    classDef canal fill:#0A402B,stroke:#9DDFA6,color:#FFFFFF,stroke-width:2px
    classDef nucleo fill:#081B39,stroke:#9DDFA6,color:#FFFFFF,stroke-width:2px
    classDef datos fill:#E3EDF8,stroke:#00448C,color:#00448C,stroke-width:2px
    classDef infra fill:#F5F7F9,stroke:#989696,color:#5B5B5B
    classDef externo fill:#FFFFFF,stroke:#989696,color:#5B5B5B,stroke-dasharray:4 4
    style canales fill:#E7F6E9,stroke:#0A402B,color:#0A402B
    style azure fill:#F5F7F9,stroke:#00448C,color:#00448C
```

<details>
<summary><b>Ciclo de vida de una solicitud y de una alerta</b></summary>
<br>

```mermaid
%%{init: {'theme':'base','themeVariables':{'primaryColor':'#E7F6E9','primaryBorderColor':'#0A402B','primaryTextColor':'#1A1B1A','lineColor':'#0A402B'}}}%%
stateDiagram-v2
    direction LR
    [*] --> IN_PROGRESS: POST /requests
    IN_PROGRESS --> COMPLETED: POST /submit
    IN_PROGRESS --> ABANDONED
    COMPLETED --> [*]
    ABANDONED --> [*]
    note right of COMPLETED: Expediente inmutable
```

```mermaid
%%{init: {'theme':'base','themeVariables':{'primaryColor':'#E3EDF8','primaryBorderColor':'#00448C','primaryTextColor':'#1A1B1A','lineColor':'#00448C'}}}%%
stateDiagram-v2
    direction LR
    [*] --> UNASSIGNED
    UNASSIGNED --> ASSIGNED: tomar alerta
    ASSIGNED --> IN_REVIEW
    IN_REVIEW --> CLOSED
    CLOSED --> [*]
```

</details>

<br>

## 🌎 Ambientes

| | 🧪 Dev | ✅ QA | 🏦 Prod |
|---|---|---|---|
| **Rama** | `dev` | `QA` | — |
| **Consola** | [console.dev.identidad…](https://console.dev.identidad.alambritos.online) | [console.qa.identidad…](https://console.qa.identidad.alambritos.online) | reservado |
| **API** | [api.dev.identidad…](https://api.dev.identidad.alambritos.online/swagger-ui.html) | [api.qa.identidad…](https://api.qa.identidad.alambritos.online/swagger-ui.html) | reservado |
| **App** | *Tangamandapio Dev* · Firebase | *Tangamandapio QA* · Firebase | — |

> [!TIP]
> La API escala a cero para no gastar: la primera petición tras un rato inactiva puede tardar **30 a 60 segundos**.

### 🌿 Flujo de ramas

```mermaid
%%{init: {'theme':'base','gitGraph':{'mainBranchName':'main','showCommitLabel':false},'themeVariables':{'git0':'#081B39','git1':'#9DDFA6','git2':'#0A402B','git3':'#00448C','gitBranchLabel0':'#FFFFFF','gitBranchLabel1':'#1A1B1A','gitBranchLabel2':'#FFFFFF','gitBranchLabel3':'#FFFFFF'}}}%%
gitGraph
    commit
    branch VDI-xx-feature
    commit
    commit
    checkout main
    merge VDI-xx-feature tag: "PR + CI"
    branch dev
    commit tag: "Deploy Dev"
    branch QA
    commit tag: "Cierre de sprint · Deploy QA"
```

Cada historia entra por **PR a `main`** (que integra y no despliega). Lo estable se promueve a **`dev`** para que QA lo pruebe, y al cerrar el sprint pasa a **`QA`**. Nadie hace push directo a esas tres ramas: los checks `test`, `lint-build` y `analyze-test` son obligatorios.

<br>

## 🚀 Empezar

```bash
git clone https://github.com/dream-parking/verificacion-online.git
```

<details>
<summary><b>⚙️ Backend</b> · Spring Boot + PostgreSQL</summary>
<br>

Requiere **Java 21** y **Docker**. Desde `backend/`:

```bash
docker compose up -d
```
```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

O, sin levantar Postgres a mano (usa uno desechable):

```bash
./mvnw spring-boot:test-run -Dspring-boot.run.profiles=dev
```

Swagger queda en `http://localhost:8080/swagger-ui.html`. Variables de la consola (primer administrador, clave JWT, CORS) en [`backend/.env.example`](backend/.env.example).

</details>

<details>
<summary><b>🖥️ Consola web</b> · Next.js</summary>
<br>

Requiere **Node 22**. Desde `webconsole/`:

```bash
npm install
```
```bash
npm run dev
```

Abre `http://localhost:3000`. Por defecto habla con `http://localhost:8080`; para usar otro API define `NEXT_PUBLIC_API_URL` o `NEXT_PUBLIC_APP_ENV=dev|qa`.

</details>

<details>
<summary><b>📱 App móvil</b> · Flutter</summary>
<br>

Desde `onboarding/`:

```bash
flutter pub get
```
```bash
flutter run
```

Usa la API de Dev por defecto. La guía completa (emuladores, celular por cable, QA, Firebase) está en [`onboarding/README.md`](onboarding/README.md).

</details>

<br>

## ✅ Calidad

<table>
<tr>
<td align="center" width="25%">

### `≥ 70 %`
<sub>Cobertura de líneas del <b>backend</b><br>JaCoCo en <code>./mvnw verify</code></sub>

</td>
<td align="center" width="25%">

### `≥ 40 %`
<sub>Cobertura de la <b>app móvil</b><br>paso “Cobertura mínima” del CI</sub>

</td>
<td align="center" width="25%">

### `≥ 80 %`
<sub>Cobertura del <b>código nuevo</b><br>Quality Gate <i>Sonar way</i></sub>

</td>
<td align="center" width="25%">

### `A · A · A`
<sub>Reliability, maintainability<br>y security en Sonar</sub>

</td>
</tr>
</table>

El CI vive en un solo workflow que solo ejecuta los componentes que cambiaron. Detalle en la [Definition of Done](docs/definition-of-done.md).

<br>

## 📚 Documentación

| | Documento | Para qué |
|:-:|---|---|
| 🔌 | [Integración con la API](docs/integracion-api.md) | Flujo móvil, endpoints de la consola, reglas de negocio y autenticación |
| 📜 | [`openapi.json`](docs/openapi.json) | Contrato OpenAPI 3.1 para generar clientes |
| 🚢 | [CI/CD](docs/cicd.md) | Ramas, ambientes, DNS, despliegues y SonarQube Cloud |
| ✅ | [Definition of Done](docs/definition-of-done.md) | Criterios del equipo y cómo se hacen cumplir |
| 🎨 | [Guía de estilos](docs/styles.md) | Tokens de color, tipografía y componentes |
| 💸 | [Revisión de costos](docs/cost-review.md) · [recursos de Azure](docs/azure-resources.csv) | Todo en planes gratuitos |

<details>
<summary><b>📁 Estructura del repositorio</b></summary>
<br>

```text
verificacion-online/
├── backend/        ⚙️  API Spring Boot: onboarding, riesgo, alertas, consola, seguridad
├── webconsole/     🖥️  Consola web Next.js para Conozca a su Cliente y Fraude
├── onboarding/     📱  App Flutter de apertura de cuenta y captura de señales
├── docs/           📚  Integración, CI/CD, DoD, estilos, costos e imágenes del README
└── .github/        🔁  Un CI por cambios + deploys a Azure y Firebase
```

</details>

<br>

<div align="center">

<img src="docs/readme/footer.svg" alt="Banco Tangamandapio: confianza que nos une, futuro que construimos" width="100%">

<sub>Hecho por <b>Los Alan-bres Ágiles</b> · ESEN</sub>

</div>
