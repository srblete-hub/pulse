package com.noop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Secondary end of the Pulse accent gradient (lilac → violet). */
internal val PulseViolet = Color(0xFF8A5CF6)

/** Brand gradient used by highlighted borders, the coach orb and primary filled buttons. */
internal val pulseAccentBrush: Brush
    get() = Brush.linearGradient(listOf(Palette.accent, PulseViolet))

/**
 * The Pulse mark: an open ring with a heartbeat trace through it. Drawn on a Canvas so it scales
 * crisply at any size and needs no bitmap asset.
 */
@Composable
fun PulseMark(
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    ringColor: Color = Palette.accent,
    traceColor: Color = Palette.textPrimary,
) {
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension / 32f
        val ring = Stroke(width = 3f * s, cap = StrokeCap.Round)
        // Open ring: a 300° arc leaving a gap at the upper right.
        drawArc(
            color = ringColor,
            startAngle = -40f,
            sweepAngle = 300f,
            useCenter = false,
            topLeft = Offset(5f * s, 5f * s),
            size = Size(22f * s, 22f * s),
            style = ring,
        )
        val trace = Path().apply {
            moveTo(8f * s, 17f * s)
            lineTo(12f * s, 17f * s)
            lineTo(14f * s, 12f * s)
            lineTo(17f * s, 21f * s)
            lineTo(19f * s, 15f * s)
            lineTo(20.5f * s, 17f * s)
            lineTo(25f * s, 17f * s)
        }
        drawPath(trace, traceColor, style = Stroke(width = 2.4f * s, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** The coach avatar: the Pulse mark inside a circle with a glowing lilac→violet rim. */
@Composable
fun CoachOrb(modifier: Modifier = Modifier, diameter: Dp = 40.dp) {
    Box(
        modifier = modifier
            .size(diameter)
            .clip(CircleShape)
            .background(pulseAccentBrush)
            .padding(1.5.dp)
            .clip(CircleShape)
            .background(Color(0xFF161B24)),
        contentAlignment = Alignment.Center,
    ) {
        PulseMark(size = diameter * 0.5f)
    }
}

/**
 * The Community tab. Sharing with friends needs a server, which this app deliberately does not have,
 * so for now the tab explains what is coming and keeps its place in the bar.
 */
@Composable
fun CommunityScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(22.dp))
        Text(
            "COMUNIDAD",
            style = NoopType.overline.copy(fontSize = 11.sp, letterSpacing = 1.8.sp),
            color = Palette.textPrimary,
        )
        Spacer(Modifier.height(22.dp))
        CommunityEmptyCard(
            icon = { Icon(Icons.Outlined.Groups, null, tint = Palette.textTertiary, modifier = Modifier.size(56.dp)) },
            title = "Comparte tu progreso",
            body = "Sigue las métricas diarias de las personas que te importan y animaos a mantener buenos hábitos.",
            button = "PRÓXIMAMENTE",
        )
        Spacer(Modifier.height(30.dp))
        Text(
            "Mis grupos",
            style = NoopType.title2.copy(fontSize = 19.sp),
            color = Palette.textPrimary,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(14.dp))
        CommunityEmptyCard(
            icon = { Icon(Icons.Outlined.Shield, null, tint = Palette.textTertiary, modifier = Modifier.size(56.dp)) },
            title = "¿Entrenas en grupo?",
            body = "Pronto podrás crear grupos con tu equipo o tus amigos y comparar vuestra semana.",
            button = "VER GRUPOS",
        )
        Spacer(Modifier.height(140.dp))
    }
}

@Composable
private fun CommunityEmptyCard(
    icon: @Composable () -> Unit,
    title: String,
    body: String,
    button: String,
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Palette.hairline, shape)
            .padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        icon()
        Text(title, style = NoopType.headline.copy(fontSize = 16.sp), color = Palette.textPrimary)
        Text(body, style = NoopType.subhead, color = Palette.textSecondary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Palette.surfaceOverlay),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                button,
                style = NoopType.overline.copy(fontSize = 10.5.sp, letterSpacing = 1.3.sp, fontWeight = FontWeight.Bold),
                color = Palette.textSecondary,
            )
        }
    }
}
