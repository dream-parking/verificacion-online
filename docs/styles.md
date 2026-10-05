# Guía de estilos — Bancoagrícola (bancoagricola.com/personas)

> Extraída por ingeniería inversa del HTML/CSS servido en vivo (hojas de estilo propias `banco.min.css`, `banco-g.min.css`, `banco-ed.min.css` + Bootstrap 4 como base de grid/utilidades). Cada valor está marcado como ✅ exacto (tomado del código fuente / CSSOM) o ≈ estimado (inferido visualmente). No se incluye ningún valor "típico" que no se haya observado en la página.

---

## 1. Resumen

Bancoagrícola presenta un lenguaje visual **corporativo-moderno con acentos lúdicos**: tipografía de marca propia (familia "CIBFont"), una paleta neutra de negro/gris casi-negro (`#1A1B1A`) sobre blanco, y un amarillo de marca muy saturado (`#FDDA24`) como color de acción. Los bloques promocionales rompen la seriedad bancaria con bloques de color plano (naranja, púrpura, celeste, rosa) y fotografía de personas, mientras que los componentes funcionales (botones, formularios, tarjetas de producto) se mantienen sobrios, con bordes rectos o píldoras completas y casi sin sombras.

Principios de diseño deducidos:
- **Alto contraste tipográfico**: negro casi puro sobre blanco en todo el texto funcional (17:1 de contraste), nunca grises medios para texto de cuerpo largo.
- **Un solo acento de acción**: el amarillo `#FDDA24` se reserva casi exclusivamente para botones primarios y CTAs; los demás colores (naranja, púrpura, celeste, verde, rosa) se usan solo como fondos de bloques temáticos/ilustrativos, no como color de interacción.
- **Botones en píldora**: todo botón interactivo (primario y secundario) usa `border-radius: 50px`, nunca esquinas rectas.
- **Minimalismo de elevación**: casi no hay `box-shadow` en componentes estáticos; las sombras existentes son sutiles y se reservan para elementos flotantes (carrusel, botón de ayuda).
- **Mayúsculas para llamados a la acción**: los textos de botones usan `text-transform: uppercase`, mientras que títulos y cuerpo de texto usan case normal.

---

## 2. Colores

| Token | Hex | RGB | Rol | Dónde se usa |
|---|---|---|---|---|
| `--color-text-primary` | `#1A1B1A` | 26, 27, 26 | ✅ Color de texto dominante (84 apariciones, el valor de `color` más frecuente de toda la hoja de estilos) | Texto de párrafos, H1 del banner, texto sobre botón amarillo |
| `--color-text-secondary` | `#333333` | 51, 51, 51 | ✅ Texto secundario / enlaces por defecto (`a { color }`) | Enlaces de cuerpo, texto de listas de footer |
| `--color-text-muted` | `#5B5B5B` | 91, 91, 91 | ✅ Texto atenuado | Subtítulos, fondo de tarjeta `.card` |
| `--color-text-disabled` | `#888888` | 136, 136, 136 | ✅ Texto deshabilitado / placeholder | Elementos con baja énfasis (5 apariciones) |
| `--color-black` | `#000000` | 0, 0, 0 | ✅ Negro puro | Bordes de botón secundario outline, algunos textos |
| `--color-brand-yellow` | `#FDDA24` | 253, 218, 36 | ✅ Amarillo de marca — color de acción principal | Fondo de `.btn-primary`, iconos destacados, bordes de foco |
| `--color-brand-blue` | `#00448C` | 0, 68, 140 | ✅ Azul institucional secundario | `.btn-primary.btn-secondary`, texto `.title-service` |
| `--color-navy` | `#1E376E` | 30, 55, 110 | ✅ Variante azul oscuro (usada en una variante de `.btn-primary`) | Texto de botón primario en banners con fondo claro |
| `--color-footer-bg` | `#2C2A29` | 44, 42, 41 | ✅ Fondo oscuro casi negro | `.sectiongray`, footer, fondos "dark mode" de sección |
| `--color-gray-900-alt` | `#212529` | 33, 37, 41 | ✅ (heredado de Bootstrap `$gray-900`, usado también como color de texto propio) | `.form-bg-dark h2`, texto por defecto heredado |
| `--color-gray-border` | `#989696` | 152, 150, 150 | ✅ Borde inferior de pestañas de navegación | `.nav-link` (borde inferior 2px) |
| `--color-white` | `#FFFFFF` | 255, 255, 255 | ✅ Blanco | Fondos de sección, texto sobre fondos oscuros |

### Neutros adicionales observados
| Token | Hex | RGB | Dónde se usa |
|---|---|---|---|
| `--color-neutral-100` | `#F5F7F9` | 245, 247, 249 | ✅ Fondo de inputs / secciones suaves |
| `--color-neutral-200` | `#F4F4F4` | 244, 244, 244 | ✅ Fondo de sección "¿Qué quieres hacer hoy?" |
| `--color-neutral-300` | `#F7F7F7` | 247, 247, 247 | ✅ Fondo alternativo claro |
| `--color-neutral-400` | `#CCCCCC` | 204, 204, 204 | ✅ Bordes/separadores claros |

> ⚠️ **Inconsistencia detectada**: existen tres negros/grises casi-idénticos usados como "negro de marca": `#1A1B1A` (el principal, 84 usos), `#000000` puro (bordes de botón) y `#212529` (gris Bootstrap heredado sin tokenizar). Se recomienda unificar en un solo token `--color-ink`.

### Colores de bloques temáticos (no interactivos)
Estos colores aparecen como `background-color` de bloques promocionales/ilustrativos de la sección "¿Qué quieres hacer hoy?" y tarjetas de producto; no se usan en botones ni enlaces:

| Token | Hex | RGB | Dónde se usa |
|---|---|---|---|
| `--color-accent-pink` | `#F5B6CD` | 245, 182, 205 | ✅ Fondo de tarjeta temática (rosa pastel) |
| `--color-accent-skyblue` | `#59CBE8` | 89, 203, 232 | ✅ Fondo de tarjeta temática (celeste) |
| `--color-accent-orange` | `#FF7F41` | 255, 127, 65 | ✅ Fondo de banner hero y tarjeta temática (naranja) |
| `--color-accent-green` | `#00C389` | 0, 195, 137 | ✅ Fondo de tarjeta temática (verde) |
| `--color-accent-purple` | ≈ `#5B3A8E` | — | ≈ Fondo del bloque "Independiente y Negocios" (estimado visualmente; no capturado en las hojas de estilo analizadas, probablemente definido inline o vía imagen de fondo) |

### Colores semánticos
No identificado en la página un sistema semántico propio (éxito/error/advertencia/información) para mensajes de UI. Lo único disponible son las variables heredadas de Bootstrap, no utilizadas visualmente en el sitio público:

| Token | Hex | Origen |
|---|---|---|
| `--bs-success` | `#28A745` | ✅ Variable Bootstrap sin uso visual detectado en esta página |
| `--bs-info` | `#17A2B8` | ✅ Variable Bootstrap sin uso visual detectado en esta página |
| `--bs-warning` | `#FFC107` | ✅ Variable Bootstrap (muy cercana al amarillo de marca, pero no es el mismo token) |
| `--bs-danger` | `#DC3545` | ✅ Variable Bootstrap sin uso visual detectado en esta página |

### Estados: hover / active / focus / disabled
| Estado | Valor | Dónde |
|---|---|---|
| Hover de botón secundario blanco | `background-color: #2C2A29` (se rellena con el oscuro de marca) | ✅ `.btn.btn-secondary.white:hover` |
| Focus / resaltado de pestaña activa | fondo `#FFFFFF`, texto `#1A1B1A` | ✅ `.limbo-menu.firstmenu > ul > li > a.active` |
| Disabled de input | `background-color: #FFFFFF` (sin cambio visual fuerte; se apoya en `border` sólido) | ✅ `.form-control:disabled` |
| Focus de tarjeta/overlay (sombra) | `box-shadow: #FDDA24 0 0 0 1px` | ✅ hallado en una regla de la hoja `banco-g.min.css` |

### Gradientes y opacidades
No identificado en la página ningún `linear-gradient`/`radial-gradient` en CSS. Los íconos de contacto ("Chat en línea", "2210-0000", "Whatsapp", "Puntos de servicio") presentan un efecto multicolor tipo gradiente, pero es parte de la imagen/SVG del ícono, no de una propiedad `background` con gradiente CSS. Opacidades detectadas en código: `rgba(255,255,255,0.6)` (✅, overlay claro) y `rgba(0,0,0,0.38)` (✅, overlay oscuro).

### Notas de contraste WCAG (calculado sobre los pares reales de texto/fondo)
| Combinación | Ratio | AA texto normal (≥4.5:1) | AA texto grande (≥3:1) |
|---|---|---|---|
| `#1A1B1A` sobre blanco | 17.28:1 | ✅ Cumple | ✅ Cumple |
| `#1A1B1A` sobre amarillo `#FDDA24` (botón primario) | 12.53:1 | ✅ Cumple | ✅ Cumple |
| Blanco sobre footer `#2C2A29` | 14.28:1 | ✅ Cumple | ✅ Cumple |
| Blanco sobre azul `#00448C` | 9.52:1 | ✅ Cumple | ✅ Cumple |
| `#5B5B5B` sobre blanco | 6.79:1 | ✅ Cumple | ✅ Cumple |
| `#333333` sobre blanco | 12.63:1 | ✅ Cumple | ✅ Cumple |

Todas las combinaciones principales de texto detectadas cumplen AA incluso para texto normal; no se encontraron combinaciones de bajo contraste en los elementos inspeccionados.

---

## 3. Tipografía

### Familias tipográficas
| Familia | Fallback | Origen | Uso |
|---|---|---|---|
| **CIBFontSans** (cargada como `fontsans-bold`, `fontsans-light`, y también registrada como `"CIBFont Sans"` 300/700) | `Fallback, sans-serif` | ✅ `@font-face` con archivos `.ttf` propios del banco (CDN del banco) | Titulares, botones (`fontsans-bold` = peso 800 real aunque se declara "bold") |
| **opensans-regular / opensans-semibold / opensans-bold** | `Fallback, sans-serif` | ✅ `@font-face` con archivos `.ttf` propios (versión auto-hospedada de Open Sans, no vía Google Fonts) | Cuerpo de texto, navegación, formularios |
| **Open Sans** (vía Google Fonts, pesos 300/400/600/700/800 + itálicas) | sistema | ✅ `<link>` a `fonts.googleapis.com` | Carga adicional de Open Sans, redundante con la versión auto-hospedada |
| **Nunito** (vía Google Fonts, pesos 200/300/400/600/700 + itálicas) | sistema | ✅ `<link>` a `fonts.googleapis.com` | Botones primarios y algunos titulares de sección (`.btn-primary`, `.three-holder.blue`) |
| **CIBFontLogo** (`logo-bold`, `logo-light`) | — | ✅ `@font-face` propia | Wordmark del logo "Bancoagrícola" |
| **CIBFontNumerales** (`numerales`) | `Fallback, sans-serif` | ✅ `@font-face` propia | Posiblemente cifras destacadas (tipografía de números) |
| **CIBFontSerif-Bold** (`fontserif-bold`) | — | ✅ `@font-face` propia, declarada pero sin uso visible confirmado en esta página | No identificado su uso visual en esta página |

> ⚠️ **Inconsistencia**: el sitio carga **tres fuentes sans-serif redundantes** para roles muy similares (CIBFontSans auto-hospedada, Open Sans auto-hospedada, y Open Sans + Nunito vía Google Fonts), lo que añade peso de carga sin necesidad aparente.

### Escala tipográfica
| Token | Tamaño | Peso | Interlineado | Espaciado entre letras | Uso |
|---|---|---|---|---|---|
| `--text-h1-desktop` | 72px (✅) / 60px (✅, breakpoint intermedio) / 40px (✅, móvil) | 700 | 1.1 (✅, en el valor móvil) | normal | H1 del banner hero (`h1.first-holder.gray`) |
| `--text-h1-banner-alt` | 56px (✅) | 700 | — | normal | Variante `.h1banner` |
| `--text-h2-section` | 28px (✅) | 700 | — | normal | Títulos de sección ("¿Qué quieres hacer hoy?") — familia `fontsans-light` pese al peso 700 declarado (inconsistencia de nomenclatura de familia) |
| `--text-h3-card` | 18px (✅) | 700 | 20px (✅) | normal | Títulos de tarjeta de producto (`.h3banner`, `.title-service`) |
| `--text-body-lg` | 18px (✅) | 400 | — | normal | Párrafos destacados |
| `--text-body` | 16px (✅) | 400 | 24px (✅, botón) | normal | Cuerpo de texto general, navegación, botones |
| `--text-body-sm` | 14px (✅) | 400 | — | normal | Texto secundario, botón secundario |
| `--text-caption` | 12px–13px (✅) | 400 | — | normal | Texto pequeño / leyendas |
| `--text-micro` | 11px (✅) | 400 | — | normal | Texto mínimo (uso puntual) |
| `--text-letterspace-wide` | — | — | — | 1px (✅) | Enlaces de menú con subrayado (`.third.menu`) |
| `--text-letterspace-tight` | — | — | — | -0.7px (✅) | Uso puntual no identificado con certeza visual |

### Pesos de fuente observados (por frecuencia en el código)
`700` (56 veces), `400` (44), `600` (21), `500` (16), `300` (13), `800` (8), `900` (8), `100` (2), `200` (2) — ✅. La variedad sugiere que, aunque hay una escala formal, en la práctica se usan casi todos los pesos disponibles de las familias cargadas.

### Cambios responsive de tamaño
✅ Confirmado para el H1 del hero: 72px (desktop ancho) → 60px (desktop) → 40px (tablet/móvil, con `line-height: 1.1`). El `.texto-fullw-banner h1` tiene su propia escala independiente: 40px → 24px. No se encontraron otras reglas `@media` que ajusten tamaños tipográficos fuera de estos dos casos.

---

## 4. Espaciado y layout

### Unidad base
≈ No hay una unidad base explícita tipo variable (`--space-*`) en el código. Los valores de `padding`/`margin` observados no siguen una escala de múltiplos limpios de 4px u 8px de forma consistente (aparecen valores como `9px 9px 9px 36px`, `18px 10px 8%`, porcentajes como `5%`, `6%`, `7% 5% 5%`). Se marca como **no identificado un sistema de espaciado tokenizado**; el layout se apoya mayormente en el grid de Bootstrap 4 y en paddings porcentuales ad-hoc por sección.

### Valores de espaciado más frecuentes (para referencia, no es una escala formal)
`0px` (✅, el más común), `5%`, `8%`, `6%`, `30px`, `10px`, `20px 10px`, `15px` — ✅ tomados directamente del CSS.

### Grid
✅ Basado en Bootstrap 4 estándar (clases `.col-12.col-sm-6.col-md-6.col-lg-4.col-xl-4` observadas en el DOM):
- Columnas: 12
- Breakpoints (variables `--breakpoint-*` del propio CSS): `xs: 0`, `sm: 576px`, `md: 768px`, `lg: 992px`, `xl: 1200px` — ✅
- Ancho máximo del contenedor: no se verificó explícitamente el `max-width` de `.container` en el CSS propio (valor estándar de Bootstrap 4 sería 540/720/960/1140px por breakpoint); **no confirmado con un valor propio distinto al de Bootstrap** — se marca ≈ estimado que usa los máximos por defecto de Bootstrap.
- Gutters: no identificado un valor de gutter personalizado (se asume el `15px`/lado por defecto de Bootstrap 4, ≈ estimado).

### Paddings y márgenes recurrentes por sección
- Secciones de banner: `padding: 5%` (✅) con variante `7% 5% 5%` (✅) en banners más altos.
- Tarjetas de servicio (`.cardbottom`): `padding: 8%` (✅).
- Botones: `padding-left/right: 30px` en primario grande (✅), `padding: 0px 18px 14px` en secundario (✅).
- Separación entre tarjetas (`.card`): `margin-bottom: 50px` (✅).

---

## 5. Efectos visuales

### Bordes
| Grosor | Color | Dónde |
|---|---|---|
| `0px` (sin borde) | — | ✅ Más frecuente (12 veces) — botones primarios, tarjetas |
| `1px solid` | `#000000` | ✅ Botón secundario outline |
| `1pt solid` | `#FFFFFF` | ✅ Botón secundario sobre fondos oscuros |
| `2px solid` | `#FDDA24` (amarillo) | ✅ Acento de borde destacado (2 apariciones) |
| `1px solid` | `#5B5B5B` / `#00448C` / `#2C2A29` | ✅ Variantes de borde en componentes de formulario/botón |

### Radios de borde (escala)
| Token | Valor | Uso |
|---|---|---|
| `--radius-none` | `0px` | ✅ Tarjetas (`.card`), inputs |
| `--radius-sm` | `4px` | ✅ Uso puntual |
| `--radius-md` | `8px` | ✅ Uso puntual |
| `--radius-lg` | `12px` | ✅ Uso puntual (contenedores) |
| `--radius-pill` | `50px` | ✅ Todos los botones (`.btn-primary`, `.btn-secondary`) |
| `--radius-pill-alt` | `100px` | ✅ Variante de píldora (2 apariciones, probablemente contenedor más grande) |

> Nota de inconsistencia: conviven dos valores de "píldora completa" (`50px` y `100px`) que visualmente producen el mismo resultado (esquina totalmente redondeada) en elementos de distinta altura; no es un error sino el efecto esperado de `border-radius` ≥ la mitad de la altura del elemento, pero convendría un único token semántico `--radius-full` en vez de dos valores fijos.

### Sombras / elevación (escala, con valores CSS completos)
| Token | CSS completo | Uso | Frecuencia |
|---|---|---|---|
| `--shadow-sm` | `rgba(0,0,0,0.75) 0px 0px 17px -10px` | ✅ Elevación sutil (tarjetas flotantes) | 2 |
| `--shadow-md` | `rgba(0,0,0,0.14) 0px 1px 8px, rgba(0,0,0,0.114) 0px 3px 8px` | ✅ Sombra estándar tipo "material" (carrusel, overlays) | 6 (la más usada) |
| `--shadow-lg` | `rgba(173,171,171,0.0) 0px 4px 10px` aprox. `#ADABAB 0px 4px 10px` | ✅ Elevación mayor (1 aparición) | 1 |
| `--shadow-focus` | `#FDDA24 0px 0px 0px 1px` | ✅ Anillo de foco/resalte con color de marca | 1 |
| `--shadow-card-legacy` | `rgba(0,0,0,0.75) 2px 2px 5px -1px` | ✅ Sombra de tarjeta de producto (fila de iconos inferiores) | 1 |
| ninguna | `none` | ✅ La mayoría de tarjetas y botones no llevan sombra | 2 |

### Transiciones y animaciones
No identificado un sistema extenso de transiciones. Solo tres declaraciones explícitas en todo el CSS propio:
- `transition: 0.4s` (✅, propiedad no especificada — transición genérica)
- `transition: max-height 0.2s ease-out` (✅, probablemente acordeones/menús desplegables)
- `transition: border-color 125ms ease-in` (✅, foco de input)

No se encontraron `@keyframes` propios en las hojas de estilo analizadas (no identificado en la página).

---

## 6. Componentes

### Botón primario (`.btn.btn-primary`)
- **Anatomía**: contenedor de texto en mayúsculas, sin ícono por defecto (aunque una variante "EMPIEZA HOY" incluye una flecha `→` como elemento adicional).
- **Variantes observadas**:
  - Estándar: fondo amarillo `#FDDA24`, texto `#1A1B1A`.
  - Variante azul marino: mismo fondo amarillo, texto `#1E376E` (✅, encontrada en una regla alternativa).
  - Variante "btn-secondary" anidada (`.btn-primary.btn-secondary`): fondo transparente, borde `1px solid #00448C`, texto `#00448C`, peso 800.
- **Estados**: no se detectó una regla `:hover`/`:focus`/`:disabled` explícita para el botón primario en el CSS propio (no identificado en la página; probablemente hereda el comportamiento por defecto del navegador/Bootstrap).
- **Especificaciones** (medidas computadas en vivo sobre un botón real del DOM):
  - `padding`: `6px 20px` (computado) / `30px` izquierda-derecha en la regla fuente para la variante grande
  - `font-size`: 16px, `font-weight`: 600–800 según variante
  - `border-radius`: 50px
  - `box-shadow`: none
  - `text-transform`: uppercase
- **Snippet CSS de referencia**:
```css
.btn.btn-primary {
  background: #FDDA24;
  color: #1A1B1A;
  border: 0;
  border-radius: 50px;
  font-family: "CIBFontSans", Nunito, Fallback, sans-serif;
  font-weight: 600; /* variantes con 800 */
  font-size: 16px;
  padding-left: 30px;
  padding-right: 30px;
  text-transform: uppercase;
}
```

### Botón secundario (`.btn.btn-secondary`)
- **Anatomía**: borde sólido + fondo transparente, texto en mayúsculas.
- **Variantes**: `.white` (borde y texto blancos, para fondos oscuros/imágenes), estándar (borde y texto negros).
- **Estados**: `:hover` definido solo para la variante `.white` → `background-color: #2C2A29` (se rellena con el oscuro de marca, el texto permanece blanco).
- **Especificaciones**:
```css
.btn.btn-secondary {
  background: transparent;
  font-family: Nunito;
  font-weight: 500;
  border: 1pt solid #000;
  color: #000;
  border-radius: 50px;
  font-size: 14px;
  height: 25px;
  padding: 0 18px 14px;
  margin-right: 22px;
}
.btn.btn-secondary.white {
  color: #fff;
  border: 1pt solid #fff;
}
.btn.btn-secondary.white:hover {
  background-color: #2C2A29;
}
```

### Tarjetas de producto / servicio (`.card`, `.cardhorizontal`, `.cardtop`/`.cardbottom`)
- **Anatomía**: imagen superior de ancho completo (`.cardtop`, fondo de imagen) + bloque de contenido inferior (`.cardbottom`) con ícono/badge, título subrayado y párrafo descriptivo, cerrando con un botón outline en píldora.
- **Variantes**: tarjeta vertical con imagen (`.card`, min-height 600px, fondo gris `#5B5B5B` cuando no hay imagen cargada), tarjeta horizontal (`.cardhorizontal`, 396px de alto, fondo blanco).
- **Estados**: no se detectaron estados `:hover`/`:focus` explícitos en el CSS propio para las tarjetas (no identificado en la página).
- **Especificaciones**:
```css
.card {
  min-height: 600px;
  background-color: #5B5B5B;
  margin-bottom: 50px;
  border: 0;
  border-radius: 0;
  padding-bottom: 36px;
}
.cardbottom { min-height: 215px; padding: 8%; }
.cardhorizontal { height: 396px; background-color: #fff; }
```

### Navegación principal (`.limbo-menu.firstmenu`, `.nav-link`)
- **Anatomía**: barra superior de utilidad (negro, enlaces "ATENCIÓN AL CLIENTE" / "PUNTOS DE SERVICIO" en píldora blanca outline) + barra de marca (blanco, logo + botón "E-BANCA PERSONAS") + barra de navegación secundaria (enlaces: Cuentas, Tarjetas, Créditos, Inversiones, Seguros, Salvadoreños en el Exterior, Promociones).
- **Estados**: el enlace activo de pestaña (`.active`) cambia a fondo blanco y texto `#1A1B1A`; los enlaces de pestaña (`.nav-link`) llevan un borde inferior de 2px en gris `#989696` por defecto.
- **Especificaciones**:
```css
.limbo-menu.firstmenu > ul > li > a { padding: 18px 10px 8%; }
.limbo-menu.firstmenu > ul > li > a.active {
  background: #fff;
  color: #1A1B1A;
}
.nav-item a.nav-link {
  font-size: 16px;
  background: #fff;
  border-bottom: 2px solid #989696;
}
```

### Formularios / inputs (`.form-control`)
- **Anatomía**: campo de línea inferior (sin caja completa), estilo "underline input" en vez de recuadro tradicional.
- **Estados**: `disabled`/`[readonly]` → fondo blanco sin cambio visual fuerte.
- **Especificaciones**:
```css
.form-white .form-control,
.form-bg-dark .form-control {
  border-width: medium medium 1px;
  border-style: none none solid;
  border-color: currentcolor currentcolor #2C2A29;
  border-radius: 0;
  font-family: opensans-regular, Fallback, sans-serif;
  font-weight: 400;
  font-size: 16px;
}
```

### Badges / etiquetas de categoría (visual, sobre tarjetas de servicio)
- **Anatomía**: píldora pequeña con ícono + texto corto (ej. "Cuentas", "Educación Financiera", "Canales digitales"), fondo de color claro correspondiente al tema de la tarjeta.
- **Variantes**: fondo blanco semitransparente sobre imagen, fondo de color sólido claro (celeste) sobre fondo blanco.
- **Especificaciones**: ≈ estimado visualmente — padding pequeño (~4–8px), `border-radius` alto tipo píldora, tipografía ~12–13px. No se pudo aislar la regla CSS exacta de esta badge en el muestreo de componentes realizado (no identificado con exactitud en el código revisado).

### Footer
- **Anatomía**: franja de contacto clara con íconos a color sobre fondo blanco, seguida de un bloque oscuro (`#1A1B1A`/`#2C2A29`) con 4 columnas de enlaces, redes sociales (íconos circulares outline) y una franja final de copyright con logos "Bancoagrícola" y "Grupo Cibest".
- **Especificaciones**: ≈ fondo oscuro estimado en `#1A1B1A` (muy próximo al `#2C2A29` documentado para `.sectiongray`; ambos conviven como "oscuro de marca" — ver inconsistencia de la sección 2).

---

## 7. Iconografía e imágenes

- **Estilo de iconos**: predominantemente de **línea (outline)**, trazo fino, monocromáticos en negro/gris para la sección "Productos y servicios" (cuentas, tarjetas, créditos, seguros, etc.), y a **color tipo gradiente multicolor** para los iconos de contacto (chat, teléfono, correo, WhatsApp, ubicación) — ≈ estimado visualmente que son imágenes SVG/PNG individuales, no un set de iconos de una librería CSS identificable (no se detectó Font Awesome, Material Icons ni similar en las hojas de estilo).
- **Tamaños**: ≈ estimados entre 32–40px para iconos de línea de producto, ≈ 40–48px para los iconos de contacto a color.
- **Grosor de trazo**: ≈ fino y uniforme (~1.5–2px aparente) en los iconos de línea.
- **Librería**: no identificado en la página el uso de una librería de iconos reconocible; parecen assets propios (imágenes) servidos desde `/web/templates/Principalnew2/assets/img/`.

### Tratamiento de imágenes
- **Proporciones**: las imágenes de banner/hero ocupan el ancho completo del bloque con `background-size: cover` y `background-position` centrada (✅, confirmado en `.cardtop`, `.imgofcard`, `.home-bannerminimal`).
- **Radios**: `0px` — no se observaron esquinas redondeadas en imágenes de fondo de tarjetas (✅).
- **Overlays**: se detectó un overlay decorativo (`.diverlines`, imagen `lineas.png` superpuesta con `z-index: 2`) sobre imágenes de banner — ✅.
- **Filtros**: no identificado el uso de filtros CSS (`filter: grayscale/blur/etc.`) en la página.

---

## 8. Patrones y convenciones

- Los bloques temáticos/promocionales (hero, "Independiente y Negocios", promoción de tarjetas) usan **fondo de color plano a sangre completa** con fotografía de una persona superpuesta, nunca imágenes de stock genéricas "flotando" sobre blanco.
- Los CTAs principales llevan **texto corto en mayúsculas + verbo de acción** ("QUIERO MI CUENTA", "EMPIEZA HOY", "CONOCE MÁS", "VER TUTORIALES").
- Los títulos de tarjeta de producto van **subrayados** incluso sin ser enlaces visitados tradicionales, reforzando que son clicables.
- La cuadrícula de "Productos y servicios" (3 columnas × 3 filas) combina ícono + título + descripción corta, sin botón individual por ítem (todo el bloque es clicable).

**Hacer ✓ / Evitar ✗**
- ✓ Usar `#FDDA24` únicamente para la acción principal de una vista; evitar duplicarlo como fondo decorativo.
- ✓ Mantener todos los botones en píldora completa (`border-radius: 50px`).
- ✓ Reservar mayúsculas para botones/CTAs; no usarlas en títulos de contenido largo.
- ✗ No mezclar los tres grises "oscuro de marca" (`#1A1B1A`, `#212529`, `#2C2A29`) sin un token único — genera inconsistencia sutil entre secciones.
- ✗ No introducir una cuarta familia tipográfica sans-serif; ya existen tres (CIBFontSans, Open Sans auto-hospedada, Open Sans/Nunito de Google Fonts) cubriendo el mismo rol.

---

## 9. Design tokens

```css
:root {
  /* Color — texto y neutros */
  --color-ink: #1A1B1A;          /* ✅ texto principal, 84 usos */
  --color-text-secondary: #333333; /* ✅ */
  --color-text-muted: #5B5B5B;     /* ✅ */
  --color-text-disabled: #888888;  /* ✅ */
  --color-black: #000000;          /* ✅ */
  --color-white: #FFFFFF;          /* ✅ */
  --color-gray-900-alt: #212529;   /* ✅ heredado de Bootstrap, usado también como texto propio */
  --color-gray-border: #989696;    /* ✅ */
  --color-neutral-100: #F5F7F9;    /* ✅ */
  --color-neutral-200: #F4F4F4;    /* ✅ */
  --color-neutral-300: #F7F7F7;    /* ✅ */
  --color-neutral-400: #CCCCCC;    /* ✅ */

  /* Color — marca */
  --color-brand-yellow: #FDDA24;   /* ✅ acción principal */
  --color-brand-blue: #00448C;     /* ✅ secundario institucional */
  --color-navy: #1E376E;           /* ✅ variante de texto sobre amarillo */
  --color-footer-bg: #2C2A29;      /* ✅ fondo oscuro de sección */

  /* Color — bloques temáticos (no interactivos) */
  --color-accent-pink: #F5B6CD;    /* ✅ */
  --color-accent-skyblue: #59CBE8; /* ✅ */
  --color-accent-orange: #FF7F41;  /* ✅ */
  --color-accent-green: #00C389;   /* ✅ */
  --color-accent-purple: #5B3A8E;  /* ≈ estimado */

  /* Tipografía */
  --font-sans-brand: "CIBFontSans", "CIBFont Sans", Fallback, sans-serif; /* ✅ */
  --font-sans-body: opensans-regular, "Open Sans", Fallback, sans-serif; /* ✅ */
  --font-sans-alt: Nunito, Fallback, sans-serif; /* ✅ */
  --font-logo: logo-bold, logo-light, sans-serif; /* ✅ */

  --text-h1-desktop: 72px;   /* ✅ */
  --text-h1-tablet: 60px;    /* ✅ */
  --text-h1-mobile: 40px;    /* ✅ */
  --text-h1-banner-alt: 56px;/* ✅ */
  --text-h2-section: 28px;   /* ✅ */
  --text-h3-card: 18px;      /* ✅ */
  --text-body-lg: 18px;      /* ✅ */
  --text-body: 16px;         /* ✅ */
  --text-body-sm: 14px;      /* ✅ */
  --text-caption: 12px;      /* ✅ */
  --text-micro: 11px;        /* ✅ */

  --font-weight-light: 300;   /* ✅ */
  --font-weight-regular: 400; /* ✅ */
  --font-weight-medium: 500;  /* ✅ */
  --font-weight-semibold: 600;/* ✅ */
  --font-weight-bold: 700;    /* ✅ */
  --font-weight-extrabold: 800;/* ✅ */

  --line-height-tight: 1.1;   /* ✅ */
  --line-height-base: 24px;   /* ✅ */
  --letter-spacing-wide: 1px; /* ✅ */
  --letter-spacing-tight: -0.7px; /* ✅ */

  /* Layout */
  --breakpoint-sm: 576px; /* ✅ */
  --breakpoint-md: 768px; /* ✅ */
  --breakpoint-lg: 992px; /* ✅ */
  --breakpoint-xl: 1200px;/* ✅ */

  /* Radios */
  --radius-none: 0px;   /* ✅ */
  --radius-sm: 4px;     /* ✅ */
  --radius-md: 8px;     /* ✅ */
  --radius-lg: 12px;    /* ✅ */
  --radius-pill: 50px;  /* ✅ */

  /* Sombras */
  --shadow-sm: rgba(0, 0, 0, 0.75) 0px 0px 17px -10px;                       /* ✅ */
  --shadow-md: rgba(0, 0, 0, 0.14) 0px 1px 8px, rgba(0, 0, 0, 0.114) 0px 3px 8px; /* ✅ */
  --shadow-card-legacy: rgba(0, 0, 0, 0.75) 2px 2px 5px -1px;                /* ✅ */
  --shadow-focus: #FDDA24 0px 0px 0px 1px;                                   /* ✅ */

  /* Transiciones */
  --transition-generic: 0.4s;                        /* ✅ */
  --transition-collapse: max-height 0.2s ease-out;   /* ✅ */
  --transition-input-focus: border-color 125ms ease-in; /* ✅ */
}
```
