# Health Dashboard — DESIGN SPEC (implementación)
> Especificación técnica completa para que un agente de IA reproduzca la UI al píxel: tokens, escalas, dimensiones por componente, geometría, estados y movimiento.

**Procedencia:** la arquitectura de información, la estructura y los componentes provienen del análisis del APK; los **valores numéricos exactos** (espaciado, tamaños, radios, duraciones) son las decisiones de este sistema de diseño, coherentes con el estilo dark + color semántico. Son implementables tal cual; cambia cualquier valor a tu gusto.

**Plataforma de referencia:** móvil, viewport base **390 × 844 pt** (iPhone 14/15). Todo en px lógicos (1x). Modo oscuro único.

---

## 1. Fundamentos

### 1.1 Rejilla y layout
- **Unidad base:** `4px`. Todo espaciado es múltiplo de 4.
- **Ancho de contenido:** viewport − 2 × margen lateral.
- **Margen lateral (padding horizontal de pantalla):** `20px` (tiles y cards), `24px` (texto de sección y cabeceras).
- **Gutter entre columnas de tiles:** `12px`.
- **Ritmo vertical entre bloques (sección → sección):** `18px` de padding-top en el título de sección.
- **Safe areas:** respeta `env(safe-area-inset-top/bottom)`. Barra inferior fija sobre el inset inferior.
- **Scroll:** vertical, barra oculta. El contenido deja `padding-bottom` = altura de la nav + 16.

### 1.2 Columnas
- **Tiles de métrica:** rejilla de **3 columnas** iguales, gap 12.
- **Pilares (resumen):** rejilla de **2 columnas**, gap 12.
- **Listas/factores/filas:** 1 columna a ancho completo.

### 1.3 Breakpoints
- App es de una sola columna móvil. En web, el contenido se centra en un contenedor máximo de `420px` (simula el teléfono). No hay layout multi-columna.

---

## 2. Color (tokens)

Formato: nombre · hex · uso. El color de estado **solo** codifica el valor de una métrica, nunca decora.

### 2.1 Superficies
| Token | Hex | Uso |
|---|---|---|
| `color.surface.base` | `#0A0A0C` | Fondo de pantalla |
| `color.surface.raised` | `#15151A` | Tarjetas, tiles |
| `color.surface.overlay` | `#1F1F24` | Modales, bottom sheets, menús |
| `color.surface.sunken` | `#070709` | Fondo de barras/pistas de progreso |
| `color.divider` | `#26262C` | Separadores, bordes sutiles, pistas |
| `color.border.subtle` | `#20202A` | Borde de tarjeta opcional (1px) |

### 2.2 Texto
| Token | Hex | Uso |
|---|---|---|
| `color.text.primary` | `#FFFFFF` | Números grandes, titulares |
| `color.text.secondary` | `#9A9AA2` | Etiquetas, descripciones, ejes |
| `color.text.tertiary` | `#5E5E66` | Deshabilitado, marcas de agua, mayúsculas finas |

### 2.3 Semántico (estado de métrica)
| Token | Hex | Significado |
|---|---|---|
| `color.status.high` | `#16EC06` | Óptimo (recovery 67–99%) |
| `color.status.mid` | `#FFDE00` | Moderado (34–66%) |
| `color.status.low` | `#FF2D55` | Bajo (0–33%) |
| `color.load` | `#0093E7` | Carga / esfuerzo (strain) |
| `color.sleep` | `#7B9CFF` | Métricas de sueño |
| `color.accent` | `#00E0C6` | Realces neutros, foco, logo, Coach |

### 2.4 Zonas de FC (5)
| Zona | Hex |
|---|---|
| Z1 (50–60%) | `#2F80ED` |
| Z2 (60–70%) | `#17A2B8` |
| Z3 (70–80%) | `#16EC06` |
| Z4 (80–90%) | `#FFDE00` |
| Z5 (90%+) | `#FF2D55` |

### 2.5 Backgrounds de resalte de celda (si se usan)
Opacidad baja sobre superficie: estado high/mid/low al 10–14% alpha. Nunca como fondo de pantalla.

---

## 3. Tipografía

**Familia:** sans geométrica/grotesca. Fallback: `Inter, -apple-system, "Segoe UI", Roboto, sans-serif`.
**Antialias:** activado. **Números:** usar `font-variant-numeric: tabular-nums` en métricas que cambian.

| Rol | Token | Weight | Size | Line-height | Tracking | Transform |
|---|---|---|---|---|---|---|
| Métrica (display) | `text.metric` | 700 | **62px** | 1.0 | −0.02em | — |
| Métrica secundaria | `text.metric.sm` | 700 | 30px | 1.0 | −0.02em | — |
| Título de pantalla | `text.title` | 700 | 22px | 1.2 | −0.01em | — |
| Título de tile/card | `text.tileTitle` | 600 | 18px | 1.3 | 0 | — |
| Cuerpo | `text.body` | 400 | 15px | 1.5 | 0 | — |
| Etiqueta | `text.label` | 600 | 12px | 1.3 | 0.10em | UPPERCASE |
| Caption/mini | `text.caption` | 600 | 10px | 1.3 | 0.08em | UPPERCASE |
| Unidad (junto a número) | `text.unit` | 500 | 0.4× del número | — | 0 | — |

Reglas: el número de métrica dentro del anillo usa `text.metric`; su símbolo (`%`) va a `~0.4×` como superíndice (`vertical-align: top`). Etiquetas y ejes siempre en `text.label`/`text.caption` en mayúsculas con tracking.

---

## 4. Espaciado, radios, elevación, movimiento

### 4.1 Escala de espaciado
`4, 8, 12, 16, 20, 24, 32, 40, 48` → tokens `spacing.1…9`. No usar valores fuera de escala.

### 4.2 Radios
| Token | Valor | Uso |
|---|---|---|
| `radius.card` | 16px | Tarjetas, tiles |
| `radius.sheet` | 20px | Bottom sheet (solo esquinas superiores) |
| `radius.button` | 8px | Botones |
| `radius.input` | 12px | Campos |
| `radius.pill` | 999px | Chips, barras, dots |
| `radius.phone` | 40px | Marco del dispositivo (mockups) |

### 4.3 Elevación
La profundidad es por **tono de superficie**, no por sombra. Única sombra permitida (menús/sheets flotantes):
`shadow.overlay = 0 8px 24px rgba(0,0,0,0.5)`.

### 4.4 Movimiento
| Token | Valor | Uso |
|---|---|---|
| `motion.fast` | 150ms | Hover, toggles, color de estado |
| `motion.base` | 300ms | Relleno de anillo/barras al recalcular |
| `motion.slow` | 500ms | Entrada de pantalla, sheets |
| `easing.standard` | `cubic-bezier(0.4, 0, 0.2, 1)` | General |
| `easing.decelerate` | `cubic-bezier(0, 0, 0.2, 1)` | Entradas |

Los anillos y barras animan su relleno con `motion.base` + `easing.standard`. Nada de rebotes.

---

## 5. Componentes (dimensiones exactas)

### 5.1 Metric Ring (el componente central)
- **Diámetro exterior:** 230px (pantalla pillar grande) / 200px (overview) / 180px (detalle secundario).
- **Grosor del arco (stroke):** 16px (grande) / 15px (medio).
- **Radio del trazo:** `r = (diámetro − stroke) / 2`. Ej.: 230px → `r = 107`… en la práctica usar `r = 100` con viewBox 230 y stroke 16 (deja aire).
- **Pista (fondo del anillo):** mismo `r` y stroke, color `color.divider` (`#1e1e24`).
- **Cap:** `stroke-linecap: round`.
- **Rotación:** `-90deg` (empieza arriba).
- **Geometría del relleno (SVG):**
  - `C = 2 · π · r` (circunferencia).
  - `stroke-dasharray = C`.
  - `stroke-dashoffset = C · (1 − valor/100)`.
  - Ej. r=100 → C≈628; valor 68% → offset ≈ 201.
- **Color del arco:** según la lógica semántica (§7).
- **Centro:** número en `text.metric` del color del arco; debajo etiqueta en `text.label` del mismo color; opcional subtítulo en `text.secondary` `11px`.

### 5.2 Pillar Tile (resumen)
- **Fondo:** `surface.raised`, `radius.card`, padding `16px`.
- **Layout:** fila superior (nombre en `text.label` + dot de 8px del color de la métrica), número en `text.metric.sm`, barra de progreso debajo.
- **Barra:** alto 6px, `radius.pill`, pista `#26262C`, relleno del color de la métrica, ancho = valor%.
- **Altura resultante:** ~118px.

### 5.3 Stat Tile (métrica suelta)
- **Fondo:** `surface.raised`, `radius.card`, padding `14px 12px`.
- **Estructura:** etiqueta `text.caption` (`color.text.tertiary`) arriba; valor en 20–22px/700 con unidad en `text.unit` (`color.text.secondary`); opcional línea de tendencia (`↑/↓ +n`) en `10px` del color de estado.
- **Rejilla:** 3 por fila, gap 12.

### 5.4 Factor / zone row (barra con etiqueta)
- **Fila:** etiqueta (`12px`, `text.secondary`, ancho fijo ~96px) · barra flexible (alto 8–10px, `radius.pill`, pista `#1e1e24`) · valor (`12px`/600, ancho fijo ~56px, alineado a la derecha).
- **Separación vertical entre filas:** 8–11px.

### 5.5 Trend Graph (barras)
- **Altura del área:** 60–70px.
- **Barra:** `flex:1`, gap 7px, `radius` `4px 4px 2px 2px`, min-height 8px, alto = valor normalizado.
- **Color por barra:** estado de ese día (high/mid/low) o color de la métrica.
- **Etiqueta de eje bajo cada barra:** `text.caption`, `color.text.tertiary`.

### 5.6 Sleep stages (hipnograma simplificado)
- Barras contiguas, gap 3px, alto 60px, `radius` `4px 4px 2px 2px`. Colores por fase: despierto `#3a3a46`, ligero `#5566aa`, REM `#9db0ff`, profundo `#7B9CFF`. Debajo, fila de 3 captions (inicio · despertares · fin).

### 5.7 Bottom Navigation
- **Altura:** 62–78px (incluye safe-area inferior).
- **Fondo:** `rgba(14,14,17,0.92)` + `backdrop-filter: blur(12px)`, borde superior `color.divider` 1px.
- **Ítems:** distribuidos `flex:1`, columna (icono 20–22px + label `text.caption`).
- **Icono:** stroke 2px, `fill:none`, color `color.text.tertiary`; activo `color.text.primary`.
- **Badge de notificación:** dot 7px `color.status.low` arriba-derecha del icono.
- **Touch target mínimo:** 44 × 44px.

### 5.8 Botones
| Variante | Fondo | Texto | Borde |
|---|---|---|---|
| Primario | color de métrica o `accent` | `#04120E`/`#fff` según contraste | — |
| Secundario | `surface.overlay` | `text.primary` | 1px `#2c2c36` |
| Fantasma | transparente | `accent` | — |
- **Altura:** 44–48px. **Padding:** `12px 24px`. **Radio:** `radius.button`. **Peso del texto:** 600, 14px.
- **Pressed:** escala 0.98 + brillo −8%. **Disabled:** opacidad 0.4.

### 5.9 Header / brand bar
- **Padding:** `18–22px 20px 8px`.
- **Izquierda:** logo (monograma en anillo 30px + wordmark `17px`/800 tracking 0.14em). **Derecha:** avatar 32–36px circular.
- Debajo: fecha en `text.label` (`color.text.secondary`) y, en detalle, título en `text.title`.

### 5.10 Bottom sheet / Modal
- **Radio superior:** `radius.sheet`. **Fondo:** `surface.overlay`. **Handle:** barra 36×4px, `#3a3a42`, centrada, margen-top 10px.
- **Padding:** `20px`. **Header fijo** opcional (título + cerrar `ic_close` 24px). **Footer fijo** opcional (botón primario a ancho completo).
- **Backdrop:** `rgba(0,0,0,0.6)`.

### 5.11 Coach card
- **Fondo:** degradado sutil `linear-gradient(135deg, #0c1a18, #15151a)`, borde 1px `#163`, `radius.card`, padding 14–16px.
- **Icono:** círculo 30–34px, degradado radial del `accent`, con la inicial/monograma.
- **Texto:** etiqueta "COACH" en `text.label` (`accent`) + cuerpo `12–13px` `#d6d6db`, line-height 1.45.

---

## 6. Plantillas de pantalla (rhythm vertical)

### 6.1 Overview
`header` → `fecha` → `ring 200px` (padding vertical 12/2) → `pilares (2 col)` → `título sección` → `tiles (3 col)` → … → `bottom nav`.

### 6.2 Pillar detail (Recovery/Strain/Sleep)
`header` → `título` → `subtítulo` → `ring 180px` → `título sección "qué lo explica"` → `factor rows` → `coach card`.

### 6.3 Lista (actividades, comunidad)
`header` → `título` → filas de card (`surface.raised`, `radius.card`, 14px padding, 12px gap).

Orden de lectura siempre: **lo primero es el número/estado**, luego lo que lo explica. Un protagonista por pantalla.

---

## 7. Lógica de color semántico (reglas)

```
función colorEstado(recoveryPct):
  si recoveryPct >= 67 → status.high
  si recoveryPct >= 34 → status.mid
  si no               → status.low
```
- **Strain** siempre `color.load`, sea cual sea el valor (la escala 0–21 no cambia de color; solo el relleno).
- **Sueño** siempre `color.sleep`.
- El **mismo color** del estado se aplica al número, a la etiqueta y al arco a la vez (coherencia).
- Zonas de FC usan su paleta fija de 5 (§2.4).

---

## 8. Estados

| Estado | Regla |
|---|---|
| Loading | Skeletons: bloques `surface.raised` con shimmer de `#1e1e24→#26262c`, `radius.card`. El anillo muestra solo la pista. |
| Empty | Ilustración/ícono tenue + texto `text.secondary` + CTA secundario. Nunca un 0 que parezca dato real. |
| Sin baseline | Si falta histórico para calcular (p. ej. recovery), mostrar "—" y un aviso, **no** un número inventado. |
| Error | Toast/inline con `status.low` y acción de reintento. |
| Pressed/active | Escala 0.98, brillo −8%. |

---

## 9. Accesibilidad
- **Contraste:** texto primario sobre superficies ≥ 7:1; etiquetas secundarias ≥ 4.5:1. El amarillo `status.mid` sobre negro cumple; sobre blanco no se usa.
- **Touch targets:** mínimo 44×44px.
- **No depender solo del color:** acompañar el estado (verde/amarillo/rojo) con el número y, si cabe, una etiqueta ("Verde"/"Óptimo").
- **Texto dinámico:** la escala tipográfica debe escalar con los ajustes del sistema sin romper el layout (usar unidades relativas donde se pueda).

---

## 10. Tokens machine-readable

### 10.1 JSON
```json
{
  "color": {
    "surface": { "base": "#0A0A0C", "raised": "#15151A", "overlay": "#1F1F24", "sunken": "#070709" },
    "divider": "#26262C", "border": "#20202A",
    "text": { "primary": "#FFFFFF", "secondary": "#9A9AA2", "tertiary": "#5E5E66" },
    "status": { "high": "#16EC06", "mid": "#FFDE00", "low": "#FF2D55" },
    "load": "#0093E7", "sleep": "#7B9CFF", "accent": "#00E0C6",
    "zones": ["#2F80ED", "#17A2B8", "#16EC06", "#FFDE00", "#FF2D55"]
  },
  "type": {
    "family": "Inter, -apple-system, 'Segoe UI', Roboto, sans-serif",
    "metric": { "w": 700, "size": 62, "lh": 1.0, "tracking": "-0.02em" },
    "title": { "w": 700, "size": 22, "lh": 1.2, "tracking": "-0.01em" },
    "tileTitle": { "w": 600, "size": 18, "lh": 1.3 },
    "body": { "w": 400, "size": 15, "lh": 1.5 },
    "label": { "w": 600, "size": 12, "lh": 1.3, "tracking": "0.10em", "upper": true },
    "caption": { "w": 600, "size": 10, "lh": 1.3, "tracking": "0.08em", "upper": true }
  },
  "spacing": [4, 8, 12, 16, 20, 24, 32, 40, 48],
  "radius": { "card": 16, "sheet": 20, "button": 8, "input": 12, "pill": 999 },
  "layout": { "screenPadX": 20, "sectionPadX": 24, "tileGap": 12, "cols": 3 },
  "motion": { "fast": 150, "base": 300, "slow": 500, "easing": "cubic-bezier(0.4,0,0.2,1)" },
  "ring": { "diameter": [230, 200, 180], "stroke": 16, "cap": "round", "rotate": -90 },
  "nav": { "height": 72, "icon": 22, "blur": 12 },
  "button": { "height": 46, "padX": 24, "padY": 12, "radius": 8 }
}
```

### 10.2 CSS custom properties
```css
:root{
  --color-surface-base:#0A0A0C; --color-surface-raised:#15151A; --color-surface-overlay:#1F1F24;
  --color-divider:#26262C;
  --color-text-1:#FFFFFF; --color-text-2:#9A9AA2; --color-text-3:#5E5E66;
  --color-status-high:#16EC06; --color-status-mid:#FFDE00; --color-status-low:#FF2D55;
  --color-load:#0093E7; --color-sleep:#7B9CFF; --color-accent:#00E0C6;
  --font-display:'Inter',-apple-system,'Segoe UI',Roboto,sans-serif;
  --text-metric:62px; --text-title:22px; --text-tile-title:18px; --text-body:15px; --text-label:12px; --text-caption:10px;
  --space-4:4px; --space-8:8px; --space-12:12px; --space-16:16px; --space-20:20px; --space-24:24px; --space-32:32px;
  --radius-card:16px; --radius-sheet:20px; --radius-button:8px; --radius-pill:999px;
  --motion-base:300ms; --easing:cubic-bezier(0.4,0,0.2,1);
  --shadow-overlay:0 8px 24px rgba(0,0,0,.5);
}
```

---

## 11. Prompt para una IA (resumen operativo)

> Diseña una app móvil de salud en **modo oscuro** sobre fondo `#0A0A0C`. Cada pantalla gira en torno a **un anillo circular** (diámetro 200–230px, stroke 16px, cap redondo, empieza arriba) con un **número enorme** (`Inter 700, 62px`) en el centro. **El color codifica el estado**: verde `#16EC06` (alto), amarillo `#FFDE00` (medio), rojo `#FF2D55` (bajo); strain en azul `#0093E7`; sueño en azul claro `#7B9CFF`. Usa tarjetas `#15151A` con radio 16px sobre el fondo, padding 16px, separadas 12px. Tipografía: números grandes en 700, etiquetas en mayúsculas 12px con tracking 0.10em y color `#9A9AA2`. Rejilla de 3 columnas para tiles de métrica, 2 para pilares. Barra inferior translúcida con blur. Profundidad por tono de superficie, sin sombras. Anima el relleno de anillos y barras en 300ms. Reserva el verde/amarillo/rojo **solo** para estado de métrica, nunca decoración.

---
*Estructura, componentes e IA extraídos del APK de referencia. Valores numéricos: decisiones de este sistema de diseño, coherentes con el estilo. Sustituibles.*
