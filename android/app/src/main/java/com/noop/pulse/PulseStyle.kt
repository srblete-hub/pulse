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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.noop.ui.NoopType

/**
 * Pulse design tokens, measured from the Pulse mockups (390 pt wide artboards → dp 1:1).
 * Every Pulse screen reads colours, type and spacing from here and nowhere else.
 */
object PulseColors {
    val bgTop = Color(0xFF2A3036)
    val bgMid = Color(0xFF1B1F23)
    val base = Color(0xFF121518)
    val raised = Color(0xFF262C31)
    val overlay = Color(0xFF2C3238)
    val inset = Color(0xFF1E2328)
    val chip = Color(0xFF3D444B)
    val track = Color(0xFF30373D)
    val hairline = Color(0xFF3A424A)
    val hairlineStrong = Color(0xFF454D55)
    val text = Color(0xFFF2F4F5)
    val textSecondary = Color(0xFFA9B0B6)
    val textTertiary = Color(0xFF8E969D)
    val textQuiet = Color(0xFFC9CED3)
    val accent = Color(0xFFC78BF7)
    val accentHover = Color(0xFFDDB4FB)
    val violet = Color(0xFF8A5CF6)
    val accentFillTop = Color(0xFF2E2638)
    val accentFillBottom = Color(0xFF241E33)
    val gold = Color(0xFFE8B04A)
    val recoveryGreen = Color(0xFF3DDC84)
    val recoveryYellow = Color(0xFFF5C542)
    val recoveryRed = Color(0xFFFF5A5F)
    val strain = Color(0xFF3B9CFF)
    val sleep = Color(0xFF8C7CFF)
    val stressLow = Color(0xFF5DA8FF)
    val stressLowLabel = Color(0xFFA6CCFF)
    val stressMid = Color(0xFFC78BF7)
    val stressHigh = Color(0xFFF0A85A)
    val onAccent = Color(0xFF101315)

    val screenBrush = Brush.verticalGradient(0f to bgTop, 0.4f to bgMid, 1f to base)
    val accentBrush = Brush.linearGradient(listOf(accent, violet))
    val accentFill = Brush.linearGradient(listOf(accentFillTop, accentFillBottom))

    fun recovery(pct: Double?): Color = when {
        pct == null -> track
        pct >= 67 -> recoveryGreen
        pct >= 34 -> recoveryYellow
        else -> recoveryRed
    }
}

object PulseText {
    private fun label(size: TextUnit, weight: FontWeight = FontWeight.Bold, tracking: Float = 0.12f) =
        TextStyle(fontFamily = NoopType.label, fontWeight = weight, fontSize = size, letterSpacing = tracking.em)

    /** Uppercase labels: card overlines, ring captions, CTA links. */
    val overline = label(10.sp)
    val overlineLg = label(10.5.sp)
    val tabTitle = label(11.sp, tracking = 0.16f)
    val chartAxis = TextStyle(fontFamily = NoopType.label, fontWeight = FontWeight.Medium, fontSize = 9.sp)
    val ringNumber = TextStyle(fontFamily = NoopType.label, fontWeight = FontWeight.Bold, fontSize = 19.sp)
    val ringNumberLg = TextStyle(fontFamily = NoopType.label, fontWeight = FontWeight.Bold, fontSize = 22.sp)
    val bigNumber = TextStyle(fontFamily = NoopType.label, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
    val heroNumber = TextStyle(fontFamily = NoopType.label, fontWeight = FontWeight.Bold, fontSize = 44.sp)
    val wordmark = TextStyle(fontFamily = NoopType.label, fontWeight = FontWeight.Medium, fontSize = 17.sp, letterSpacing = 0.08.em)
    val small = TextStyle(fontFamily = NoopType.label, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)

    val sectionTitle = TextStyle(fontFamily = NoopType.sans, fontWeight = FontWeight.SemiBold, fontSize = 19.sp)
    val cardTitle = TextStyle(fontFamily = NoopType.sans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    val cardTitleLg = TextStyle(fontFamily = NoopType.sans, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    val body = TextStyle(fontFamily = NoopType.sans, fontWeight = FontWeight.Normal, fontSize = 12.5.sp, lineHeight = 17.5.sp)
    val bodyLg = TextStyle(fontFamily = NoopType.sans, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp)
    val headline = TextStyle(fontFamily = NoopType.sans, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp)
}

object PulseMetrics {
    val gutter = 15.dp
    val cardRadius = 13.dp
    val cardPad = 16.dp
    val sectionGap = 30.dp
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
        modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = PulseMetrics.sectionGap, bottom = 14.dp),
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
        Spacer(Modifier.width(6.dp))
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
        Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp)).background(PulseColors.inset).padding(14.dp),
    ) {
        Text(text, style = PulseText.body, color = PulseColors.text)
        Spacer(Modifier.height(10.dp))
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
        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(top = 26.dp),
    )
}

/** A small grey pill ("PRÓXIMAMENTE", "ESTIMADO"…). */
@Composable
fun Pill(text: String, color: Color = PulseColors.hairlineStrong, textColor: Color = PulseColors.text) {
    Box(Modifier.clip(RoundedCornerShape(10.dp)).background(color).padding(horizontal = 9.dp, vertical = 4.dp)) {
        Text(text.uppercase(), style = PulseText.overline.copy(fontSize = 9.sp), color = textColor)
    }
}
