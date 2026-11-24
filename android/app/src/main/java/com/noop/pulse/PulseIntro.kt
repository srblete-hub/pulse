package com.noop.pulse

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One page of a first-visit introduction: artwork plus a title and one or two paragraphs. */
class IntroStep(
    val title: String,
    val body: List<String>,
    /** Distance of the text block from the top, as in the mockups (dp on a 844-tall screen). */
    val textTop: Dp = 640.dp,
    val background: Brush? = null,
    /** Small lilac label above the title ("ORGANIZA", "NUEVO"…). */
    val kicker: String? = null,
    val art: @Composable BoxScope.() -> Unit,
)

private val introBg = Brush.verticalGradient(0f to PulseColors.divider, 0.45f to PulseColors.overlay, 1f to PulseColors.raised)

/**
 * Multi-step introduction with the circular progress "next" button used by every Pulse intro
 * (Estrés, Horario de sueño, Diario, Comunidad). The first page shows a close button, the rest a back
 * arrow; the first and last pages have the filled lilac button.
 */
@Composable
fun IntroFlow(
    steps: List<IntroStep>,
    lastLabel: String = "VAMOS",
    onClose: () -> Unit,
    onDone: () -> Unit,
) {
    var i by rememberSaveable { mutableStateOf(0) }
    val step = steps[i]
    val last = i == steps.lastIndex
    BackHandler { if (i > 0) i-- else onClose() }
    Box(Modifier.fillMaxSize().background(step.background ?: introBg).statusBarsPadding().navigationBarsPadding()) {
        step.art(this)
        Column(Modifier.align(Alignment.TopStart).padding(start = 30.dp, end = 30.dp, top = step.textTop - 24.dp)) {
            step.kicker?.let {
                Text(it, style = PulseText.overlineLg, color = PulseColors.accent)
                Spacer(Modifier.height(8.dp))
            }
            Text(step.title, style = PulseText.headline.copy(fontSize = 22.sp, fontWeight = FontWeight.SemiBold), color = PulseColors.text)
            step.body.forEachIndexed { n, p ->
                Spacer(Modifier.height(if (n == 0) 8.dp else 12.dp))
                Text(p, style = PulseText.body.copy(fontSize = 13.5.sp, lineHeight = 19.sp), color = PulseColors.textQuiet)
            }
        }
        Box(
            Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 18.dp).size(44.dp).pulseClick { if (i > 0) i-- else onClose() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (i == 0) Icons.Outlined.Close else Icons.AutoMirrored.Outlined.ArrowBack, if (i == 0) "Cerrar" else "Atrás", tint = PulseColors.text, modifier = Modifier.size(22.dp))
        }
        Row(Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 36.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                when { i == 0 -> "EMPEZAR"; last -> lastLabel; else -> "SIGUIENTE" },
                style = PulseText.overlineLg, color = PulseColors.text,
            )
            Spacer(Modifier.width(16.dp))
            ProgressNextButton(fraction = if (i == 0 || last) null else i.toFloat() / (steps.size - 2).coerceAtLeast(1), done = last) {
                if (last) onDone() else i++
            }
        }
    }
}

/** 70 dp round button: filled lilac when [fraction] is null, otherwise a progress ring. */
@Composable
fun ProgressNextButton(fraction: Float?, done: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.size(70.dp).clip(RoundedCornerShape(35.dp))
            .background(if (fraction == null) PulseColors.accentBrush else SolidColor(Color.Transparent))
            .pulseClick { if (enabled) onClick() },
        contentAlignment = Alignment.Center,
    ) {
        if (fraction != null) {
            Canvas(Modifier.size(70.dp)) {
                val sw = 5.dp.toPx()
                val r = 32.dp.toPx()
                val tl = Offset(center.x - r, center.y - r)
                drawArc(PulseColors.overlay, 0f, 360f, false, tl, Size(r * 2, r * 2), style = Stroke(sw))
                drawArc(PulseColors.accent, -90f, 360f * fraction.coerceIn(0f, 1f), false, tl, Size(r * 2, r * 2), style = Stroke(sw, cap = StrokeCap.Round))
            }
        }
        Icon(
            if (done) Icons.Outlined.Check else Icons.AutoMirrored.Outlined.ArrowForward, null,
            tint = if (fraction == null) PulseColors.raised else if (enabled) PulseColors.text else PulseColors.textTertiary,
            modifier = Modifier.size(if (done) 26.dp else 24.dp),
        )
    }
}
