package com.noop.pulse

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noop.analytics.PulseAgeEngine
import com.noop.data.DailyMetric
import com.noop.ui.AppViewModel
import com.noop.ui.ProfileStore
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
private fun DetailScaffold(title: String, onBack: () -> Unit, info: Pair<String, String>?, content: @Composable () -> Unit) {
    var showInfo by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(PulseColors.screenBrush)) {
        PulseTopBar(title, onBack, trailing = info?.let {
            {
                Box(
                    Modifier.size(22.dp).border(1.2.dp, PulseColors.textSecondary, RoundedCornerShape(11.dp)).pulseClick { showInfo = true },
                    contentAlignment = Alignment.Center,
                ) { Text("i", style = PulseText.overline, color = PulseColors.textSecondary) }
            }
        })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            content()
            Spacer(Modifier.height(40.dp))
        }
    }
    if (showInfo && info != null) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            containerColor = PulseColors.overlay,
            title = { Text(info.first, style = PulseText.headline.copy(fontSize = 20.sp), color = PulseColors.text) },
            text = { Text(info.second, style = PulseText.bodyLg, color = PulseColors.textSecondary) },
            confirmButton = { TextButton(onClick = { showInfo = false }) { Text("ENTENDIDO", style = PulseText.overline, color = PulseColors.accent) } },
        )
    }
}

/** Simple informational page (charging tips, goals, help). */
@Composable
fun PulseInfoScreen(title: String, heading: String, paragraphs: List<String>, onBack: () -> Unit, cta: Pair<String, () -> Unit>? = null) {
    DetailScaffold(title, onBack, null) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(heading, style = PulseText.headline, color = PulseColors.text)
            paragraphs.forEach {
                Spacer(Modifier.height(14.dp))
                Text(it, style = PulseText.bodyLg, color = PulseColors.textSecondary)
            }
            if (cta != null) {
                Spacer(Modifier.height(24.dp))
                Box(
                    Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(25.dp)).background(PulseColors.accent).pulseClick(cta.second),
                    contentAlignment = Alignment.Center,
                ) { Text(cta.first.uppercase(), style = PulseText.overlineLg, color = PulseColors.onAccent) }
            }
        }
    }
}
