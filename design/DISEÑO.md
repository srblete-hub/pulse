# Referencia de diseño — interfaz propia para noop (Android)

Documento de trabajo. Las capturas de `referencias/` son **solo referencia de estructura**:
la app final tendrá identidad propia (nombre, logo, fuente, ilustraciones, textos y acento).

## Capturas recibidas

| Archivo | Contenido |
|---|---|
| `referencias/1.webp` | Inicio: cabecera, tres anillos grandes, sección "Get Started" con tarjetas, barra inferior |
| `referencias/2.webp` | Inicio con scroll: anillos compactos fijos arriba, más tarjetas de onboarding |
| `referencias/3.webp` | Inicio: "Looking Ahead" (progreso de calibración 0/7), inicio de "My Dashboard" |
| `referencias/4.webp` | Dashboard: tarjeta de personalización, filas de métricas (HRV, sueño, pasos, calorías), monitor de estrés |
| `referencias/5.webp` | Monitor de estrés (gráfica con estado vacío), gráfica combinada esfuerzo/recuperación |
| `referencias/6.webp` | Inicio, final: "Discover More" (tarjetas con miniatura a la izquierda) y pie con logotipo |
| `referencias/7.webp` | Pestaña Salud: estado de bienvenida superpuesto (ilustración + título + botón con borde degradado) |
| `referencias/8.webp` | Pestaña Salud: cabecera con brillo de color, tarjeta hero con progreso, Health Monitor (5 constantes) |
| `referencias/9.webp` | Pestaña Salud con scroll: Health Monitor, Stress Monitor (horas de estrés alto), "Upgrade to Access" |
| `referencias/10.webp` | Ajustes de compartir: opciones seleccionables en tarjetas, botón flotante circular "Hecho" |

### Notas por pantalla (capturas 6–10)

- **Inicio, final (6):** tarjetas horizontales con miniatura cuadrada redondeada a la izquierda y
  texto a la derecha; enlace de acción en azul en lugar del acento. Labs, tienda, referidos y membresía
  **no aplican** a noop (sin cuentas ni servidor): esa sección se reutiliza para accesos locales
  (exportar datos, conectar pulsera, guía de uso) o se omite.
- **Salud (7–9):**
  - Título de pantalla centrado en mayúsculas pequeñas; brillo de color difuso en la esquina superior.
  - Tarjeta hero con ilustración grande, título en mayúsculas, texto y barra de progreso ("faltan N días").
    En noop: progreso de calibración de la línea base.
  - **Monitor de salud**: 5 columnas separadas por líneas verticales finas (icono de línea, sigla en
    mayúsculas, indicador de estado). Métricas: respiración, SpO₂, FC en reposo, VFC, temperatura —
    todas las calcula ya noop.
  - Estado bloqueado: caja interior más oscura con texto y barra segmentada (una barra por noche).
  - **Monitor de estrés**: "estrés alto hoy" en horas, con rejilla de fondo tenue.
  - Primera visita: capa oscura con ilustración, título grande, texto y botón de contorno en degradado.
  - "Upgrade to Access" no aplica (todo es gratis y local).
- **Selección de opciones (10):** título grande de dos líneas, texto explicativo, separadores de sección
  (etiqueta en mayúsculas + línea), tarjetas seleccionables con borde de acento al estar activas,
  botón flotante circular verde con check y etiqueta "HECHO". Compartir con seguidores no aplica;
  el patrón se reutiliza para elegir opciones (p. ej. qué se exporta o qué muestra el widget).

| `referencias/11.webp` | Pestaña social: estado vacío "encuentra a tu gente", buscador, sección de equipos |
| `referencias/12.webp` | Pestaña social con scroll: fila de seguidores, referidos y plan familiar |
| `referencias/13.webp` | Coach IA: pantalla inmersiva oscura, avatar circular con brillo, texto de bienvenida |
| `referencias/14.webp` | Coach IA: igual que 13 con botón blanco "Empezar" |
| `referencias/15.webp` | Coach IA: paso de tutorial señalando el botón "+" (subir imagen / nueva conversación) |

### Notas por pantalla (capturas 11–15)

- **Pestaña social (11–12):** necesita cuentas y servidor (seguidores, equipos, referidos, plan
  familiar), así que **no aplica** a noop tal cual. Patrones que sí sirven:
  - Barra superior: icono a la izquierda, título centrado en mayúsculas, lupa a la derecha.
  - Estado vacío dentro de una tarjeta con **borde fino y sin relleno**: ilustración, título centrado,
    texto centrado y botón ancho (primario blanco con texto oscuro, secundario gris oscuro).
  - Cabecera de sección con título grande a la izquierda y enlace en mayúsculas con flecha a la derecha.
  - Separadores horizontales finos entre bloques.
  - Fila de navegación: icono + título en mayúsculas + subtítulo.
  - **Propuesta:** la 3.ª pestaña pasa a ser **Actividad** (entrenamientos, diario/hábitos, historial),
    que noop ya tiene pero repartidos por la app.
- **Coach (13–15):** se mapea a `CoachScreen.kt` (noop ya tiene coach con la clave de IA del usuario).
  - Pantalla a sangre con fondo casi negro y degradado azul-violeta muy oscuro arriba; hoja con
    esquinas superiores redondeadas sobre la barra superior.
  - Chip arriba a la izquierda (avatar pequeño + modelo/versión) e icono de historial a la derecha.
  - Avatar del coach: círculo con aro en degradado azul→violeta y brillo exterior. **Avatar propio**
    (no el monograma de la referencia).
  - Tutorial por pasos: texto grande a la izquierda (≈22 sp) y botón píldora blanco con texto oscuro
    en mayúsculas ("EMPEZAR", "SIGUIENTE").
  - Botón "+" cuadrado redondeado con borde luminoso en degradado, abajo a la izquierda junto al campo
    de texto.
  - El chip de promoción y el carrito **no aplican**.
- **Coach, resto del tutorial (capturas 16–20, recibidas en el chat; no llegaron como archivo):**
  - Pasos: dos formas de hablar con el coach → escribir → dictar por voz → historial de conversaciones.
    Último paso con botón "EMPEZAR" en lugar de "SIGUIENTE".
  - Cada paso **resalta el control del que habla** con un borde luminoso en degradado azul→violeta
    y brillo exterior (campo de texto, botón de micrófono, icono de historial).
  - Barra de entrada: botón "+" cuadrado redondeado gris a la izquierda y campo de texto en píldora
    ancha con placeholder ("Pregunta lo que quieras…") y micrófono a la derecha.
  - En noop: la voz ya existe (`CoachVoiceInput.kt`); el historial se guarda solo en el teléfono.
- **Coach, conversación (`referencias/21.webp`):** mensaje del coach a ancho completo sin burbuja,
  pregunta final en negrita, acciones pequeñas debajo (copiar, me gusta, no me gusta).
  **Sugerencias rápidas** en chips blancos tipo píldora con scroll horizontal encima de la barra de
  entrada. En noop el saludo usa el nombre del perfil local y textos propios.
- **Ajustes (`referencias/22–24.webp`):** pantalla con "X" a la izquierda y título centrado en mayúsculas.
  - Grupos con etiqueta en mayúsculas gris y tracking amplio ("CUENTA Y AJUSTES", "SOPORTE").
  - Filas en tarjetas grises redondeadas: icono de línea a la izquierda, título en mayúsculas con
    tracking amplio, subtítulo opcional en gris.
  - Fila destacada con borde en degradado (p. ej. "Primera semana": guía de calibración).
  - Botón de salida en píldora con contorno blanco y versión de la app centrada debajo.
  - Carrusel de membresía, referidos, regalos y "mi cuenta" **no aplican**.
  - Equivalencias en noop: Dispositivo (`DevicesScreen.kt`), Actividad, Diario, Ciclo hormonal
    (`CycleTrackerDialog.kt`), Ocultar métricas, Integraciones (Health Connect, `DataSourcesScreen.kt`),
    Notificaciones, Idioma (`AppLanguage.kt`), Exportar datos, Privacidad, Coach IA
    (`CoachSettingsScreen.kt`), Tutoriales (`HowNoopWorksScreen.kt`), Acerca de.
    "Cerrar sesión" no existe (no hay cuenta); en su lugar, "Copia de seguridad".
- **Botón circular de la barra inferior** (todas las capturas): abre el coach. Usará el mismo avatar
  propio con aro en degradado.

### Componentes que salen de aquí

`RingMetric` (grande / compacto), `ActionCard` (ilustración a derecha o miniatura a izquierda),
`MetricRow`, `SectionHeader`, `VitalsStrip` (5 columnas), `LockedProgress` (barra segmentada),
`EmptyChart`, `SelectableCard`, `ConfirmFab`, `FloatingBottomBar`, `DaySelector`, `GlowHeader`.

## Qué tomamos (patrones generales de UX)

- **Tema oscuro** con fondo en degradado vertical muy sutil y tarjetas un tono más claras, esquinas ~20 dp.
- **Cabecera**: avatar con iniciales + racha a la izquierda; selector de día (‹ HOY ›) en el centro; batería de la pulsera a la derecha.
- **Tres métricas principales** (Sueño · Recuperación · Esfuerzo) como anillos grandes; al hacer scroll se
  colapsan a una fila compacta fija (anillo pequeño + etiqueta).
- **Etiquetas en mayúsculas con tracking amplio** para títulos de sección y nombres de métrica; títulos de
  sección grandes en caja mixta.
- **Tarjetas de acción**: título, 2–3 líneas de descripción, enlace en mayúsculas con color de acento y flecha,
  ilustración a la derecha. La primera tarjeta destacada lleva borde en degradado.
- **Filas de métrica** a ancho completo: icono de línea + etiqueta en mayúsculas + chevron.
- **Gráficas**: rejilla tenue, ejes en gris, estado vacío centrado con icono y "SIN DATOS", barra de
  progreso segmentada para calibración.
- **Semáforo** para recuperación (verde / amarillo / rojo) y un azul para esfuerzo.
- **Barra inferior flotante** con 4 pestañas (icono de línea + texto) y un botón circular aparte a la
  derecha (en nuestra app: acceso al coach).

## Qué NO se copia

- Logotipo y wordmark de WHOOP, ni imitaciones con su tipografía.
- La tipografía propia de WHOOP.
- Las ilustraciones 3D (pulsera, diario, despertador, tubo de ensayo, manos, zapatillas).
- Los textos de las tarjetas, palabra por palabra.
- Nombres de funciones de su marca ("WHOOP Humans", etc.).

## Sistema propio (propuesta inicial, pendiente de aprobar)

**Nombre de trabajo:** pendiente (el usuario propuso "Lhoop"; se usará con logo y tipografía propios).

**Tipografía** — una sola familia: *Figtree* (Google Fonts, licencia OFL, se incluye en el APK).
Sans geométrica-humanista, la más cercana en sensación de lectura a la de las capturas.

| Rol | Peso | Tamaño | Notas |
|---|---|---|---|
| Número del anillo | Bold 700 | 40 sp | Números tabulares (`tnum`) |
| Título de sección ("Mi panel") | SemiBold 600 | 28 sp | Caja mixta |
| Título de tarjeta | Medium 500 | 20 sp | Caja mixta |
| Texto de tarjeta | Regular 400 | 16 sp, interlineado 1.35 | `textSecondary` |
| Etiqueta / enlace de acción | Bold 700 | 13–14 sp | MAYÚSCULAS, tracking +0.12 em |
| Barra inferior | Medium 500 | 12 sp | |

Alternativa si no convence: *Montserrat* (más ancha y geométrica, sobre todo en mayúsculas).
El logotipo usa otra tipografía distinta.

**Paleta** (tokens para `Theme.kt`):

| Token | Valor | Uso |
|---|---|---|
| `bgTop` / `bgBottom` | `#1E2328` → `#101315` | Fondo degradado |
| `surface` | `#262C32` | Tarjetas |
| `surfaceRaised` | `#313840` | Filas, chips, selector de día |
| `outline` | `#3A424A` | Anillos vacíos, bordes |
| `textPrimary` | `#F2F4F5` | Títulos |
| `textSecondary` | `#A9B0B6` | Descripciones |
| `accent` | `#2FD3C6` (turquesa) | Enlaces y acciones — distinto del magenta de referencia |
| `recoveryHigh/Mid/Low` | `#3DDC84` / `#F5C542` / `#FF5A5F` | Semáforo de recuperación |
| `strain` | `#3B9CFF` | Esfuerzo |
| `sleep` | `#8C7CFF` | Sueño |

**Iconos:** Material Symbols Outlined (Apache 2.0) o set propio de línea de 1.5 dp.
**Ilustraciones:** propias y sencillas (vectores planos), o sin ilustración en la primera versión.

## Mapa a pantallas de noop

- Inicio → `android/app/src/main/java/com/noop/ui/AppRoot.kt`, `DashboardCards.kt`
- Tema global → `ui/Theme.kt`
- Barra inferior → `ui/BottomBarStyle.kt` y `AppRoot.kt`
- Gráficas → `ui/Charts.kt`

## Pendiente

- Resto de capturas (el usuario las envía de 5 en 5).
- Confirmar nombre, logo y color de acento.
