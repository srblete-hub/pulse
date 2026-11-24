package com.noop.pulse

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.noop.R
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.noop.ui.NoopType

/**
 * Pulse design tokens — "Health Dashboard" design system (dark, data-first). The screen is near-black;
 * colour appears only where it means something: green / yellow / red for a metric's state, blue for
 * load, periwinkle for sleep, teal as the neutral accent. Depth comes from surface tone, not shadows.
 * Every Pulse screen reads colours, type and spacing from here and nowhere else.
 */
object PulseColors {
    // Surfaces
    val base = Color(0xFF0A0A0C)
    val raised = Color(0xFF16161A)
    val overlay = Color(0xFF1F1F24)
    val divider = Color(0xFF2A2A30)
    val bgTop = base
    val bgMid = base
    val inset = Color(0xFF111114)
    val chip = overlay
    val track = divider
    val hairline = divider
    val hairlineStrong = Color(0xFF3A3A42)

    // Text
    val text = Color(0xFFFFFFFF)
    val textSecondary = Color(0xFF9A9AA2)
    val textTertiary = Color(0xFF5E5E66)
    val textQuiet = Color(0xFFC8C8CE)

    // Semantic — metric state only, never decoration
    val statusHigh = Color(0xFF16EC06)
    val statusMid = Color(0xFFFFDE00)
    val statusLow = Color(0xFFFF0026)
    val load = Color(0xFF0093E7)
    val sleep = Color(0xFF7BA0FF)
    val sleepDeep = Color(0xFF4F72E0)
    val accent = Color(0xFF00E0C6)
    val accentHover = Color(0xFF5CF0DE)
    val onAccent = Color(0xFF0A0A0C)

    // Aliases kept so every screen resolves through the tokens above.
    val recoveryGreen = statusHigh
    val recoveryYellow = statusMid
    val recoveryRed = statusLow
    val strain = load
    val violet = accent
    val gold = accent
    val accentFillTop = raised
    val accentFillBottom = raised
    /** Stress is a metric too: calm → sleep blue, elevated → yellow, high → red. */
    val stressLow = sleep
    val stressLowLabel = sleep
    val stressMid = statusMid
    val stressHigh = statusLow

    val screenBrush: Brush = SolidColor(base)
    val accentBrush: Brush = SolidColor(accent)
    val accentFill: Brush = SolidColor(raised)

    fun recovery(pct: Double?): Color = when {
        pct == null -> track
        pct >= 67 -> statusHigh
        pct >= 34 -> statusMid
        else -> statusLow
    }
}

/** Inter (SIL Open Font License, see assets/licenses/Inter-OFL.txt); Inter Display for metric numbers. */
object PulseType {
    val sans = FontFamily(
        Font(R.font.inter_400, FontWeight.Normal),
        Font(R.font.inter_500, FontWeight.Medium),
        Font(R.font.inter_600, FontWeight.SemiBold),
        Font(R.font.inter_700, FontWeight.Bold),
        Font(R.font.inter_700, FontWeight.ExtraBold),
    )
    val display = FontFamily(Font(R.font.inter_display_700, FontWeight.Bold), Font(R.font.inter_display_700, FontWeight.ExtraBold))
}

object PulseText {
    private fun label(size: TextUnit, weight: FontWeight = FontWeight.Medium, tracking: Float = 0.08f) =
        TextStyle(fontFamily = PulseType.sans, fontWeight = weight, fontSize = size, lineHeight = (size.value * 1.3f).sp, letterSpacing = tracking.em)
    private fun num(size: TextUnit, weight: FontWeight = FontWeight.Bold) =
        TextStyle(fontFamily = PulseType.display, fontWeight = weight, fontSize = size, lineHeight = size, letterSpacing = (-0.02f).em)

    /** label — uppercase metadata, ring captions, CTA links (12 sp, 500, +0.08 em). */
    val overline = label(11.sp)
    val overlineLg = label(12.sp)
    val tabTitle = label(12.sp, FontWeight.SemiBold)
    val chartAxis = label(10.sp, tracking = 0.02f)
    val small = label(12.sp, FontWeight.SemiBold, 0.02f)
    val wordmark = label(17.sp, FontWeight.SemiBold)

    /** Numbers — the protagonist of each view. */
    val ringNumber = num(20.sp)
    val ringNumberLg = num(24.sp)
    val bigNumber = num(18.sp)
    val heroNumber = num(48.sp)
    /** metric-display (64 sp) — the one number at the centre of a detail screen's ring. */
    val metric = num(64.sp)

    val headline = TextStyle(fontFamily = PulseType.sans, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 29.sp, letterSpacing = (-0.01f).em)
    val sectionTitle = TextStyle(fontFamily = PulseType.sans, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 24.sp)
    val cardTitleLg = TextStyle(fontFamily = PulseType.sans, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 23.sp)
    val cardTitle = TextStyle(fontFamily = PulseType.sans, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp)
    val body = TextStyle(fontFamily = PulseType.sans, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp)
    val bodyLg = TextStyle(fontFamily = PulseType.sans, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp)
}

object PulseMetrics {
    val gutter = 16.dp
    val cardRadius = 16.dp
    val sheetRadius = 20.dp
    val buttonRadius = 8.dp
    val inputRadius = 12.dp
    val cardPad = 16.dp
    val pillRadius = 999.dp
    val sectionGap = 32.dp
    val tileGap = 12.dp
    val screenPad = 16.dp
    /** Space reserved at the bottom of scrolling tab screens so the floating bar never covers content. */
    val barClearance = 110.dp
}

fun Modifier.pulseClick(onClick: () -> Unit): Modifier = this.clickable(
    interactionSource = MutableInteractionSource(),
    indication = null,
    onClick = onClick,
)

@Composable
fun Overline(text: String, modifier: Modifier = Modifier, color: Color = PulseColors.text, large: Boolean = false) {
    Text(text.uppercase(), style = if (large) PulseText.overlineLg else PulseText.overline, color = color, modifier = modifier)
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = PulseMetrics.sectionGap, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = PulseText.sectionTitle, color = PulseColors.text, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** Plain filled card (#2C3238, 13 dp corners). */
@Composable
fun PulseCard(
    modifier: Modifier = Modifier,
    color: Color = PulseColors.overlay,
    padding: Dp = PulseMetrics.cardPad,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(PulseMetrics.cardRadius)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(color)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(padding),
        content = content,
    )
}

/** Card with the lilac→violet gradient hairline and the dark lilac fill. */
@Composable
fun AccentCard(
    modifier: Modifier = Modifier,
    padding: Dp = 18.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(PulseMetrics.cardRadius)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .border(BorderStroke(1.5.dp, PulseColors.accentBrush), shape)
            .background(PulseColors.accentFill)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(padding),
        content = content,
    )
}

/** Card title row: overline on the left, a small chevron on the right. */
@Composable
fun CardHeader(title: String, showChevron: Boolean = true, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Overline(title, Modifier.weight(1f), large = true)
        when {
            trailing != null -> trailing()
            showChevron -> Icon(Icons.Outlined.ChevronRight, null, tint = PulseColors.textSecondary, modifier = Modifier.size(18.dp))
        }
    }
}

/** "EMPEZAR →" lilac call-to-action link. */
@Composable
fun CtaLink(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(modifier.pulseClick(onClick).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), style = PulseText.overline, color = PulseColors.accent)
        Spacer(Modifier.width(8.dp))
        Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = PulseColors.accent, modifier = Modifier.size(14.dp))
    }
}

/** 56 dp tappable list row with leading icon, uppercase label, optional value and chevron. */
@Composable
fun NavRow(
    label: String,
    icon: ImageVector? = null,
    value: String? = null,
    subtitle: String? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(PulseMetrics.cardRadius))
            .background(PulseColors.overlay)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = if (subtitle != null) 14.dp else 0.dp)
            .let { if (subtitle == null) it.height(56.dp) else it },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = PulseColors.textSecondary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(label.uppercase(), style = PulseText.overlineLg, color = PulseColors.text)
            if (subtitle != null) {
                Spacer(Modifier.height(4.dp))
                Text(subtitle, style = PulseText.body, color = PulseColors.textSecondary)
            }
        }
        if (value != null) {
            Text(value, style = PulseText.small, color = PulseColors.textSecondary)
            Spacer(Modifier.width(8.dp))
        }
        Icon(Icons.Outlined.ChevronRight, null, tint = PulseColors.textSecondary, modifier = Modifier.size(18.dp))
    }
}

/** Score ring: grey track plus a coloured arc for [fraction] (0–1), centred value text. */
@Composable
fun ScoreRing(
    fraction: Float?,
    color: Color,
    centre: String,
    modifier: Modifier = Modifier,
    size: Dp = 84.dp,
    stroke: Dp = 5.dp,
    large: Boolean = false,
    textStyle: TextStyle? = null,
) {
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val sw = stroke.toPx()
            val inset = sw / 2 + (size.toPx() * 2f / 84f)
            val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
            val tl = Offset(inset, inset)
            drawArc(PulseColors.track, 0f, 360f, false, tl, arcSize, style = Stroke(sw))
            if (fraction != null && fraction > 0f) {
                drawArc(color, -90f, 360f * fraction.coerceIn(0f, 1f), false, tl, arcSize, style = Stroke(sw, cap = StrokeCap.Round))
            }
        }
        Text(centre, style = textStyle ?: if (large) PulseText.ringNumberLg else PulseText.ringNumber, color = PulseColors.text, textAlign = TextAlign.Center)
    }
}

/** Segmented "N of M nights" progress, as in the calibration prompts. */
@Composable
fun SegmentProgress(done: Int, total: Int, modifier: Modifier = Modifier, segmentWidth: Dp? = null) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(total) { i ->
            val m = if (segmentWidth != null) Modifier.width(segmentWidth) else Modifier.weight(1f)
            Box(
                m.height(3.dp).clip(RoundedCornerShape(2.dp))
                    .background(if (i < done) PulseColors.accent else PulseColors.hairlineStrong),
            )
        }
    }
}

/** Inset "sleep N nights to unlock" panel. */
@Composable
fun UnlockPanel(text: String, done: Int, total: Int, fixedSegments: Boolean = false) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp)).background(PulseColors.inset).padding(16.dp),
    ) {
        Text(text, style = PulseText.body, color = PulseColors.text)
        Spacer(Modifier.height(8.dp))
        SegmentProgress(done, total, segmentWidth = if (fixedSegments) 38.dp else null)
    }
}

/** Pushed-screen top bar: back arrow, centred uppercase title, optional trailing slot. */
@Composable
fun PulseTopBar(title: String, onBack: (() -> Unit)?, trailing: (@Composable () -> Unit)? = null) {
    Box(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp).height(52.dp)) {
        if (onBack != null) {
            Box(
                Modifier.align(Alignment.CenterStart).size(44.dp).clip(RoundedCornerShape(22.dp)).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Atrás", tint = PulseColors.text, modifier = Modifier.size(22.dp)) }
        }
        Text(title.uppercase(), style = PulseText.tabTitle, color = PulseColors.text, modifier = Modifier.align(Alignment.Center))
        if (trailing != null) Box(Modifier.align(Alignment.CenterEnd).padding(end = 8.dp)) { trailing() }
    }
}

/** Centred uppercase title used by the root tabs (SALUD, COMUNIDAD, AJUSTES). */
@Composable
fun TabTitle(text: String) {
    Text(
        text.uppercase(),
        style = PulseText.tabTitle,
        color = PulseColors.text,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(top = 24.dp),
    )
}

/** A small grey pill ("PRÓXIMAMENTE", "ESTIMADO"…). */
@Composable
fun Pill(text: String, color: Color = PulseColors.hairlineStrong, textColor: Color = PulseColors.text) {
    Box(Modifier.clip(RoundedCornerShape(10.dp)).background(color).padding(horizontal = 8.dp, vertical = 4.dp)) {
        Text(text.uppercase(), style = PulseText.overline.copy(fontSize = 9.sp), color = textColor)
    }
}

/** Spanish wording for noop's ScoreConfidence tiers. */
fun confidenceLabel(c: com.noop.analytics.ScoreConfidence): String = when (c) {
    com.noop.analytics.ScoreConfidence.SOLID -> "Dato fiable"
    com.noop.analytics.ScoreConfidence.BUILDING -> "Estimación"
    com.noop.analytics.ScoreConfidence.CALIBRATING -> "Calibrando"
}

fun confidenceExplain(c: com.noop.analytics.ScoreConfidence, metric: String): String = when (c) {
    com.noop.analytics.ScoreConfidence.SOLID -> "$metric tiene todos sus datos detrás: puedes fiarte del número."
    com.noop.analytics.ScoreConfidence.BUILDING -> "$metric se ha calculado con datos incompletos (pocas noches de referencia, poco pulso registrado o una noche con poco movimiento captado). Tómalo como orientativo."
    com.noop.analytics.ScoreConfidence.CALIBRATING -> "Aún no hay datos suficientes para calcular $metric. No te mostramos un número inventado."
}

/** Discreet certainty marker: a small dot (filled = reliable, half = estimate, hollow = calibrating) + label. */
@Composable
fun ConfidenceTag(c: com.noop.analytics.ScoreConfidence, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(8.dp)) {
            val r = size.minDimension / 2
            when (c) {
                com.noop.analytics.ScoreConfidence.SOLID -> drawCircle(PulseColors.accent, r)
                com.noop.analytics.ScoreConfidence.BUILDING -> {
                    drawCircle(PulseColors.textSecondary, r - 0.75.dp.toPx(), style = Stroke(1.5.dp.toPx()))
                    drawArc(PulseColors.textSecondary, 90f, 180f, true)
                }
                com.noop.analytics.ScoreConfidence.CALIBRATING -> drawCircle(PulseColors.textTertiary, r - 0.75.dp.toPx(), style = Stroke(1.5.dp.toPx()))
            }
        }
        Spacer(Modifier.width(4.dp))
        Text(confidenceLabel(c).uppercase(), style = PulseText.chartAxis, color = PulseColors.textSecondary)
    }
}
