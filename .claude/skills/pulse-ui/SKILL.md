---
name: pulse-ui
description: Design system for the PULSE health-dashboard app — a dark, data-first mobile UI built around color-coded metric rings. Use whenever building, restyling, or reviewing any screen, component, chart, icon, or layout for this app, so the output matches the system exactly.
---

# PULSE UI — design system skill

When building or changing ANY user interface for this app, follow this design system. Do not invent colors, spacing, or type sizes — use the tokens below.

## How to use this folder
1. **Read `DESIGN-SPEC.md` first** — it is the authoritative spec: exact tokens, component dimensions, the SVG ring geometry, states, motion, and layout rhythm.
2. **Use `tokens/design-tokens.css`** (or `tokens/design-tokens.json`) as the single source of truth for values. Import the CSS and reference the variables; never hardcode a hex or px that exists as a token.
3. **Copy from `components/`** — `screens-gallery.html` has finished example screens (Overview, Recovery, Strain, Sleep) that already follow the spec. Match their structure; adapt content.
4. **Icons:** use Material icons (outlined, thin, monochrome) or inline SVG drawn for this app. Do not add PNG assets taken from any other app.
5. **`reference/`** is where screenshots of target screens go. If a screenshot is present, replicate its composition using the spec's tokens.

## Android implementation (this repo)
The tokens are already mapped to Compose in `android/app/src/main/java/com/noop/pulse/PulseStyle.kt`
(`PulseColors`, `PulseText`, `PulseMetrics`, `PulseType` = Inter). Use those objects in Kotlin instead of
hex/dp literals.

**Visual style wins over the gallery:** the owner's reference screenshots set the look — Home with three
rings (white numbers), insight stack, monitor tiles, My day / My plan / dashboard; detail pages with
day tabs, a large ring with white number and SHARE, insight, activities and stats vs 30 days. The
`components/` gallery is only a token reference; do not switch to its single-ring layout or its "P" logo.
The bottom navigation stays as it is. Never commit screenshots of other apps to this repository.

## Core rules (non-negotiable)
- Dark only. Background `#0A0A0C`; cards `#15151A` at radius 16px.
- **Every screen centers on ONE metric ring** (diameter 180–230px, stroke 16px, round cap, starts at top) with a huge number (`Inter 700, ~62px`) in the middle.
- **Color encodes state, never decoration:** green `#16EC06` (high), yellow `#FFDE00` (mid), red `#FF2D55` (low); strain blue `#0093E7`; sleep `#7B9CFF`. Reserve these strictly for metric state.
- 4px spacing grid. Depth by surface tone, not shadows. Animate ring/bar fills in 300ms.
- Labels: UPPERCASE, 12px, tracking 0.10em, color `#9A9AA2`.

## Ring geometry (the signature element)
```
C = 2 · π · r                      // circumference
stroke-dasharray  = C
stroke-dashoffset = C · (1 − value/100)
transform: rotate(-90deg)          // start at top
```
Arc color = the semantic color for the current value.

See `DESIGN-SPEC.md` §5 for every component's exact dimensions and §10 for the raw tokens.
