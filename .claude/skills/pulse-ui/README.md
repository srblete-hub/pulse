# PULSE UI Kit

Sistema de diseño para una app de salud/fitness en modo oscuro, centrada en anillos de métrica codificados por color. Listo para soltar en tu repo **y** para usarse como skill de Claude Code.

## Contenido
```
pulse-ui-kit/
├─ SKILL.md              ← manifiesto de skill (Claude Code lo carga al tocar UI)
├─ CLAUDE.md             ← instrucción corta para el repo (apunta al spec)
├─ DESIGN-SPEC.md        ← spec técnica completa (autoritativa)
├─ DESIGN.md             ← versión narrativa del sistema (estilo)
├─ tokens/
│  ├─ design-tokens.json ← tokens machine-readable
│  └─ design-tokens.css  ← custom properties, importar directo
├─ components/
│  ├─ screens-gallery.html     ← pantallas de ejemplo (Overview, Recovery, Strain, Sleep)
│  └─ analytics-prototype.html ← motor de métricas en vivo (recovery/strain reales)
└─ reference/            ← pon aquí capturas de las pantallas objetivo
```

## Cómo usarlo

**Como skill de Claude Code:** copia la carpeta a `.claude/skills/pulse-ui/` de tu proyecto. Claude la cargará al construir UI.

**Como referencia de repo:** deja `CLAUDE.md` en la raíz (o añade su línea a tu `CLAUDE.md`), e importa `tokens/design-tokens.css` en tu app.

**Flujo recomendado para máxima fidelidad:**
1. Pon una captura de la pantalla objetivo en `reference/`.
2. Pide: *"construye esta pantalla replicando reference/<img>, usando los tokens de tokens/design-tokens.css y las reglas de DESIGN-SPEC.md"*.
3. Itera 1-2 vueltas.

## Nota de procedencia
Estructura, componentes e información de pantallas derivan de ingeniería inversa de una app de referencia. Los valores numéricos (color, tipografía, espaciado) son decisiones coherentes de este sistema, no medidas de ninguna otra app — sustituibles. Si capturas los reales, cámbialos en `tokens/` y el resto se mantiene consistente.
