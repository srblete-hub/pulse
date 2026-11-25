# Health Dashboard — UX/UI Design System
> Dark, data-first wearable dashboard. Each screen is a quiet near-black surface where a single color-coded metric and its ring or graph carry the hierarchy, and everything else recedes.

**Theme:** dark

**Principio rector.** Es un panel de salud oscuro y minimalista. La pantalla es casi negra y el color aparece solo donde significa algo: un **sistema semáforo** para el estado de recuperación (verde = óptimo, amarillo = moderado, rojo = bajo) y un **azul** para la carga/esfuerzo. Cada vista se organiza alrededor de un anillo o medidor con un número grande; debajo, tarjetas y gráficos que explican ese número. La UI se define como un conjunto de "modelos" rellenables por datos (arquitectura dirigida por contenido), de modo que las pantallas se montan a partir de componentes configurables y el contenido puede cambiar sin publicar una nueva versión.

## Tokens — Colors

El color no es decorativo: **codifica el estado del dato**. Un mismo componente (anillo, barra, texto) cambia de verde a amarillo a rojo según el valor.

| Name | Value | Token | Role |
|------|-------|-------|------|
| Surface Base | `#0A0A0C` | `--color-surface-base` | Fondo principal de pantalla |
| Surface Raised | `#16161A` | `--color-surface-raised` | Tarjetas, tiles |
| Surface Overlay | `#1F1F24` | `--color-surface-overlay` | Modales, menús, bottom sheets |
| Divider | `#2A2A30` | `--color-divider` | Separadores sutiles |
| Status High | `#16EC06` | `--color-status-high` | Estado óptimo (67–99%) |
| Status Mid | `#FFDE00` | `--color-status-mid` | Estado moderado (34–66%) |
| Status Low | `#FF0026` | `--color-status-low` | Estado bajo (0–33%) |
| Load | `#0093E7` | `--color-load` | Carga / esfuerzo (azul) |
| Sleep | `#7BA0FF` | `--color-sleep` | Métricas de sueño |
| Accent | `#00E0C6` | `--color-accent` | Realces neutros, enlaces, foco |
| Text Primary | `#FFFFFF` | `--color-text-primary` | Números grandes y titulares |
| Text Secondary | `#9A9AA2` | `--color-text-secondary` | Etiquetas, descripciones, ejes |
| Text Tertiary | `#5E5E66` | `--color-text-tertiary` | Texto deshabilitado / marcas de agua |

> Regla: los colores de estado (verde/amarillo/rojo) se reservan **exclusivamente** para representar el valor de una métrica. Nunca como decoración.

## Tokens — Typography

Una **sans geométrica/grotesca limpia**; el número de la métrica es el protagonista y debe destacar sobre todo lo demás.

### Display — número de métrica
- **Familia:** `Grotesque Sans` (fallback `Inter`, system-ui)
- **Weights:** 600, 700
- **Sizes:** 48–72px
- **Line height:** 1.0
- **Role:** El valor central de cada pantalla, dentro del anillo.

### Heading — títulos de sección / tile
- **Weights:** 600 · **Sizes:** 20–28px · **Line height:** 1.2

### Body — texto y descripciones
- **Weight:** 400 · **Sizes:** 15–16px · **Line height:** 1.5

### Label / Caption — etiquetas, ejes, unidades
- **Weight:** 500 · **Sizes:** 11–13px · **Line height:** 1.3 · mayúsculas con tracking amplio (`0.08em`)

### Type Scale

| Role | Weight | Size | Line Height | Tracking | Token |
|------|--------|------|-------------|----------|-------|
| metric-display | 700 | 64px | 1.0 | -0.02em | `--text-metric` |
| screen-title | 600 | 24px | 1.2 | -0.01em | `--text-title` |
| tile-title | 600 | 18px | 1.3 | 0 | `--text-tile-title` |
| body | 400 | 16px | 1.5 | 0 | `--text-body` |
| label | 500 | 12px | 1.3 | 0.08em | `--text-label` |

## Tokens — Spacing & Shapes

**Base unit:** 4px · **Density:** compact-comfortable (móvil)

### Spacing Scale
| Name | Value | Token |
|------|-------|-------|
| 4 | 4px | `--spacing-4` |
| 8 | 8px | `--spacing-8` |
| 12 | 12px | `--spacing-12` |
| 16 | 16px | `--spacing-16` |
| 20 | 20px | `--spacing-20` |
| 24 | 24px | `--spacing-24` |
| 32 | 32px | `--spacing-32` |
| 40 | 40px | `--spacing-40` |

### Border Radius
| Element | Value |
|---------|-------|
| cards / tiles | 16px |
| bottom sheets (superior) | 20px |
| buttons | 8px |
| pills / chips | 999px |
| input fields | 12px |

### Layout
- **Screen padding (horizontal):** 16–20px
- **Gap entre tiles:** 12–16px
- **Card padding:** 16px
- **Safe area:** respeta notch y barra inferior

### Elevation
La profundidad se expresa con **tono de superficie**, no con sombras. `Surface Base` → `Surface Raised` → `Surface Overlay`. Sombras solo sutiles para menús flotantes: `0 8px 24px rgba(0,0,0,0.5)`.

## Components

Biblioteca de componentes a construir. Todos reciben sus valores por datos.

### Metric Ring / Gauge
**Role:** El componente central de cada pantalla principal.

Anillo circular con el número grande en el centro, coloreado según el sistema semántico. Fondo `--color-surface-base`, relleno del arco según el valor. Variantes: completo (pantalla de detalle) y compacto (tile de resumen).

### Pillar Tile
**Role:** Resumen de una métrica en la home.

Tile sobre `--color-surface-raised`, radio 16px, con mini-anillo o valor + etiqueta. Abre la pantalla de detalle correspondiente.

### Trend Graph
**Role:** Evolución de una métrica en el tiempo.

Barras o líneas sobre fondo oscuro, color por estado. Acompañado opcionalmente de una tarjeta descriptiva con el gráfico arriba y la explicación debajo.

### Content Card
**Role:** Bloque de contenido, insight o CTA.

Variantes: imagen de héroe con CTA, texto con ilustración, solo texto, tarjeta de acción. Radio 16px, imagen opcional, título + cuerpo + botón.

### Coach / Chat Entry
**Role:** Asistente conversacional.

Entrada tipo chat con texto generado y botones de sugerencia; burbujas sobre superficie elevada.

### Calendar Heatmap
**Role:** Hábitos, diario y seguimiento por día.

Cuadrícula de días coloreada por estado; cabecera de mes y leyenda.

### Bottom Sheet / Modal
**Role:** Detalle, confirmaciones y tours guiados.

Header y footer fijos opcionales, radio superior 20px, fondo `--color-surface-overlay`. Incluye toasts, diálogos y modales a pantalla completa, y flujos de onboarding paso a paso (spotlight).

### Buttons
**Role:** Acciones.

Primario de color sólido; secundario con borde sutil; botón con progreso integrado; botón de acción en toast. Radio 8px, padding 12×24px.

### Celebration Overlay
**Role:** Logros y hitos.

Animaciones a pantalla completa (Lottie) con media + caption.

### Stat / Pill / Chip
**Role:** Datos sueltos, etiquetas y filtros.

Valor con flecha de tendencia, contadores, tags de contexto, feedback binario (sí/no).

## Do's and Don'ts

### Do
- Mantén el fondo casi negro y reserva el color para el estado del dato.
- Haz que cada pantalla gire en torno a UN número grande dentro de un anillo; lo demás lo explica.
- Codifica el valor con color de forma consistente en anillos, barras y textos.
- Usa tarjetas de radio ~16px sobre superficies ligeramente elevadas; jerarquía por elevación de tono, no por bordes.
- Minimiza el texto; etiquetas cortas en mayúsculas con tracking para los metadatos.
- Define cada elemento como un modelo rellenable por datos.

### Don't
- No introduzcas colores decorativos ni fondos claros: rompen la lectura del estado.
- No uses el verde/amarillo/rojo para nada que no sea estado de métrica.
- No pongas varios números compitiendo en la misma vista; un protagonista por pantalla.
- No uses sombras fuertes para tarjetas; la profundidad es por tono de superficie.
- No satures la pantalla: el espacio negro es parte del diseño.

## Layout / Information Architecture

Estructura de pantallas sugerida:

- **Home / resumen:** vista general con tiles de cada métrica principal y tendencias.
- **Readiness / recuperación:** anillo + factores que lo explican (variabilidad, FC en reposo, sueño).
- **Load / esfuerzo:** anillo de carga del día, actividades y entreno.
- **Sleep / sueño:** duración, fases, consistencia y deuda.
- **Coach:** asistente conversacional y recomendaciones.
- **Trends:** evolución a lo largo del tiempo de cada métrica.
- **Community:** perfiles, equipos, logros.
- **Journal:** registro diario de hábitos.
- **Device / settings:** dispositivo, integraciones, cuenta.

Navegación: barra inferior con las métricas principales; cada una abre su pantalla de anillo + detalle, y el detalle se despliega en bottom sheets.

## Imagery

Fotografía e ilustración de estilo oscuro y atlético. Iconografía monocroma, fina y consistente sobre fondo oscuro; el color se reserva para el estado del dato. Ilustraciones cálidas solo en onboarding y celebraciones.

## Agent Prompt Guide

Quick Color Reference:
- Surface Base `#0A0A0C` — fondo de pantalla
- Surface Raised `#16161A` — tarjetas y tiles
- Status High/Mid/Low verde/amarillo/rojo — estado de la métrica
- Load `#0093E7` — esfuerzo
- Text Primary `#FFFFFF`, Secondary `#9A9AA2`

Prompts:
- "Crea una pantalla de recuperación oscura: fondo casi negro, un anillo circular grande centrado con el porcentaje en blanco y el arco coloreado verde/amarillo/rojo según el valor; debajo, tiles de métricas sobre superficie elevada con radio 16px."
- "Crea la home: barra inferior de métricas, y tiles de cada métrica con mini-anillo coloreado por estado y una etiqueta corta en mayúsculas."
- "Crea un bottom sheet de detalle con header fijo, un gráfico de barras coloreado por estado y un texto explicativo debajo."

## Quick Start

### CSS Custom Properties

```css
:root {
  /* Surfaces (dark) */
  --color-surface-base: #0A0A0C;
  --color-surface-raised: #16161A;
  --color-surface-overlay: #1F1F24;
  --color-divider: #2A2A30;

  /* Semantic metric colors — no decorativos */
  --color-status-high: #16EC06;
  --color-status-mid: #FFDE00;
  --color-status-low: #FF0026;
  --color-load: #0093E7;
  --color-sleep: #7BA0FF;
  --color-accent: #00E0C6;

  /* Text */
  --color-text-primary: #FFFFFF;
  --color-text-secondary: #9A9AA2;
  --color-text-tertiary: #5E5E66;

  /* Typography */
  --font-display: 'Inter', ui-sans-serif, system-ui, sans-serif;
  --text-metric: 64px;
  --text-title: 24px;
  --text-tile-title: 18px;
  --text-body: 16px;
  --text-label: 12px;
  --font-weight-regular: 400;
  --font-weight-medium: 500;
  --font-weight-semibold: 600;
  --font-weight-bold: 700;

  /* Spacing */
  --spacing-4: 4px;
  --spacing-8: 8px;
  --spacing-12: 12px;
  --spacing-16: 16px;
  --spacing-20: 20px;
  --spacing-24: 24px;
  --spacing-32: 32px;
  --spacing-40: 40px;

  /* Radius */
  --radius-card: 16px;
  --radius-sheet: 20px;
  --radius-button: 8px;
  --radius-input: 12px;
  --radius-pill: 999px;

  /* Elevation */
  --shadow-overlay: 0 8px 24px rgba(0,0,0,0.5);
}
```

### Tailwind v4

```css
@theme {
  --color-surface-base: #0A0A0C;
  --color-surface-raised: #16161A;
  --color-surface-overlay: #1F1F24;
  --color-status-high: #16EC06;
  --color-status-mid: #FFDE00;
  --color-status-low: #FF0026;
  --color-load: #0093E7;
  --color-sleep: #7BA0FF;
  --color-accent: #00E0C6;
  --color-text-primary: #FFFFFF;
  --color-text-secondary: #9A9AA2;

  --font-display: 'Inter', ui-sans-serif, system-ui, sans-serif;
  --text-metric: 64px;
  --text-title: 24px;
  --text-body: 16px;
  --text-label: 12px;

  --spacing-4: 4px;
  --spacing-8: 8px;
  --spacing-12: 12px;
  --spacing-16: 16px;
  --spacing-20: 20px;
  --spacing-24: 24px;
  --spacing-32: 32px;

  --radius-card: 16px;
  --radius-sheet: 20px;
  --radius-button: 8px;
  --radius-pill: 999px;
}
```
