package com.noop.pulse

import android.content.Context
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noop.ble.WhoopModel
import com.noop.ui.AppViewModel
import com.noop.ui.DevicesScreen
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** "Pulso en directo · mostrarlo en la app" preference. */
object PulseLivePref {
    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("pulse_profile", Context.MODE_PRIVATE)
    fun enabled(c: Context) = prefs(c).getBoolean("show_live_hr", false)
    fun set(c: Context, v: Boolean) = prefs(c).edit().putBoolean("show_live_hr", v).apply()
}

private val strapBg = Brush.verticalGradient(0f to Color(0xFF2A3036), 0.4f to Color(0xFF181C20), 1f to Color(0xFF0E1013))

@Composable
fun PulseStrapScreen(vm: AppViewModel, onClose: () -> Unit, onCharging: () -> Unit, onImport: () -> Unit, liveEnabled: Boolean, onLiveChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    val live by vm.live.collectAsStateWithLifecycle()
    val model by vm.selectedModel.collectAsStateWithLifecycle()
    val name by vm.activeDeviceName.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(0) }
    var tip by remember { mutableStateOf(!PulseTips.seen(context, "strap")) }
    val tail = (live.advertisingName ?: name ?: vm.activeStrapId).filter { it.isLetterOrDigit() }.takeLast(4).uppercase()
    val sync = live.lastSyncAt?.let { Instant.ofEpochSecond(it).atZone(ZoneId.systemDefault()).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")) } ?: "--:--"
    val state = when {
        live.bonded && live.connected -> "CONECTADA"
        live.connected -> "EMPAREJANDO"
        live.scanning -> "BUSCANDO"
        else -> "DESCONECTADA"
    }

    Box(Modifier.fillMaxSize().background(strapBg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 12.dp, top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).pulseClick(onClose), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Close, "Cerrar", tint = PulseColors.text, modifier = Modifier.size(20.dp)) }
                Text("PULSERA", style = PulseText.tabTitle.copy(letterSpacing = PulseText.overline.letterSpacing * 1.16f), color = PulseColors.text, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                Box(
                    Modifier.size(34.dp).border(1.5.dp, PulseColors.text, RoundedCornerShape(17.dp)).pulseClick(onCharging),
                    contentAlignment = Alignment.Center,
                ) { Text("i", style = PulseText.ringNumber.copy(fontSize = 14.sp), color = PulseColors.text) }
            }
            Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 20.dp, top = 24.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(state, style = PulseText.overline.copy(letterSpacing = PulseText.tabTitle.letterSpacing * 0.875f), color = if (live.connected) PulseColors.accent else PulseColors.textSecondary)
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (tail.isNotEmpty()) "MI PULSERA · $tail" else "MI PULSERA", style = PulseText.small.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = PulseText.overline.letterSpacing * 0.5f), color = PulseColors.text)
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Outlined.Edit, null, tint = PulseColors.textSecondary, modifier = Modifier.size(16.dp).pulseClick { tab = 1 })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text("ÚLTIMA SINCRONIZACIÓN", style = PulseText.overline, color = PulseColors.textSecondary)
                        Spacer(Modifier.height(4.dp))
                        Text(sync, style = PulseText.small.copy(fontWeight = FontWeight.Bold), color = PulseColors.text)
                    }
                    Spacer(Modifier.width(10.dp))
                    Icon(
                        if (live.connected) Icons.Outlined.Sync else Icons.Outlined.Bluetooth, "Sincronizar", tint = PulseColors.text,
                        modifier = Modifier.size(26.dp).pulseClick { vm.connect() },
                    )
                }
            }
            Row(Modifier.padding(start = 22.dp, end = 22.dp, top = 26.dp), horizontalArrangement = Arrangement.spacedBy(30.dp)) {
                listOf("ESTADO", "AVANZADO").forEachIndexed { i, t ->
                    Column(Modifier.pulseClick { tab = i }) {
                        Text(t, style = PulseText.overlineLg.copy(letterSpacing = PulseText.overline.letterSpacing * 0.66f), color = if (tab == i) PulseColors.text else PulseColors.textTertiary)
                        Spacer(Modifier.height(8.dp))
                        Box(Modifier.width(if (t == "ESTADO") 52.dp else 72.dp).height(2.dp).background(if (tab == i) PulseColors.text else Color.Transparent))
                    }
                }
            }
            if (tab == 1) {
                Box(Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp)) { DevicesScreen(vm, onUseFileImport = onImport) }
            } else {
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    StrapArt(Modifier.align(Alignment.TopStart).offset(x = (-10).dp, y = 40.dp), live.connected)
                    val pct = live.batteryPct
                    Row(Modifier.align(Alignment.TopEnd).padding(end = 24.dp, top = 260.dp), verticalAlignment = Alignment.Bottom) {
                        Column(horizontalAlignment = Alignment.End) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(pct?.roundToInt()?.toString() ?: "--", style = PulseText.bigNumber.copy(fontSize = 34.sp), color = PulseColors.text)
                                Text(" %", style = PulseText.small.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold), color = PulseColors.text, modifier = Modifier.padding(bottom = 4.dp))
                            }
                            Text(if (model == WhoopModel.WHOOP4) "Modelo 4.0" else "Modelo 5.0 / MG", style = PulseText.body.copy(fontSize = 12.sp), color = PulseColors.textSecondary)
                        }
                        Spacer(Modifier.width(14.dp))
                        Box(Modifier.width(5.dp).height(100.dp).clip(RoundedCornerShape(3.dp)).background(PulseColors.overlay), contentAlignment = Alignment.BottomCenter) {
                            Box(Modifier.fillMaxWidth().fillMaxHeight(((pct ?: 0.0) / 100).toFloat().coerceIn(0f, 1f)).clip(RoundedCornerShape(3.dp)).background(Brush.verticalGradient(listOf(PulseColors.accent, PulseColors.violet))))
                        }
                    }
                    Row(
                        Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 10.dp, end = 10.dp, bottom = 24.dp).fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp)).background(PulseColors.raised).padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(38.dp).clip(RoundedCornerShape(19.dp)).background(Color(0xFF353C43)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Favorite, null, tint = PulseColors.recoveryRed, modifier = Modifier.size(18.dp, 16.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("PULSO EN DIRECTO" + (if (liveEnabled) live.heartRate?.let { " · $it LPM" } ?: "" else ""), style = PulseText.overline.copy(fontSize = 11.sp, letterSpacing = PulseText.overline.letterSpacing * 0.83f), color = PulseColors.text)
                            Spacer(Modifier.height(3.dp))
                            Text("MOSTRARLO EN LA APP", style = PulseText.overline.copy(fontSize = 9.sp, letterSpacing = PulseText.overline.letterSpacing * 0.83f), color = PulseColors.textSecondary)
                        }
                        PulseSwitch(liveEnabled) { onLiveChange(it) }
                    }
                }
            }
        }
        if (tip) {
            Box(Modifier.fillMaxSize().background(Color(0xD9060709)).pulseClick { }) {
                Box(
                    Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 12.dp, end = 6.dp).size(52.dp)
                        .background(Brush.radialGradient(listOf(Color(0x59FFFFFF), Color.Transparent)), RoundedCornerShape(26.dp)),
                )
                Column(Modifier.align(Alignment.TopStart).statusBarsPadding().padding(start = 30.dp, end = 30.dp, top = 316.dp)) {
                    Text("Que no se te acabe la batería", style = PulseText.headline.copy(fontSize = 22.sp, fontWeight = FontWeight.SemiBold), color = PulseColors.text)
                    Spacer(Modifier.height(14.dp))
                    Text("Cárgala cuando estés quieto un rato, por ejemplo en la ducha o en el sofá, para no perder datos ni de día ni de noche.", style = PulseText.bodyLg.copy(lineHeight = 21.sp), color = Color(0xFFE2E5E8))
                    Spacer(Modifier.height(12.dp))
                    Text("Toca la «i» de arriba para ver cómo cargarla.", style = PulseText.bodyLg.copy(lineHeight = 21.sp), color = Color(0xFFE2E5E8))
                    Spacer(Modifier.height(22.dp))
                    Box(
                        Modifier.height(42.dp).clip(RoundedCornerShape(21.dp)).border(2.dp, PulseColors.accentBrush, RoundedCornerShape(21.dp))
                            .background(Color(0xFF0E1013)).pulseClick { PulseTips.markSeen(context, "strap"); tip = false }.padding(horizontal = 30.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("ENTENDIDO", style = PulseText.overline.copy(fontSize = 11.sp), color = PulseColors.accentHover) }
                }
            }
        }
    }
}

@Composable
fun PulseSwitch(on: Boolean, onChange: (Boolean) -> Unit) {
    Box(
        Modifier.width(46.dp).height(26.dp).clip(RoundedCornerShape(13.dp)).background(if (on) Color(0xFF4A3A66) else PulseColors.hairline).pulseClick { onChange(!on) },
    ) {
        Box(
            Modifier.offset(x = if (on) 23.dp else 3.dp, y = 3.dp).size(20.dp).clip(RoundedCornerShape(10.dp))
                .background(if (on) PulseColors.accent else PulseColors.textQuiet),
        )
    }
}

/** Line drawing of the strap seen from the side, with a soft glow on the sensor when connected. */
@Composable
private fun StrapArt(modifier: Modifier, connected: Boolean) {
    Canvas(modifier.size(300.dp, 320.dp)) {
        val s = 1.dp.toPx()
        val stroke = Stroke(2.dp.toPx())
        drawRoundRect(PulseColors.hairlineStrong, Offset(110 * s, 70 * s), Size(100 * s, 110 * s), CornerRadius(26 * s), style = stroke)
        drawRoundRect(PulseColors.textTertiary, Offset(122 * s, 82 * s), Size(76 * s, 86 * s), CornerRadius(18 * s), style = Stroke(1.5.dp.toPx()))
        // band
        drawArc(PulseColors.hairlineStrong, 200f, 140f, false, Offset(30 * s, 0f), Size(260 * s, 250 * s), style = stroke)
        drawArc(PulseColors.hairlineStrong, 20f, 140f, false, Offset(30 * s, 0f), Size(260 * s, 250 * s), style = stroke)
        drawCircle(PulseColors.accent.copy(alpha = if (connected) 0.18f else 0.06f), 34 * s, Offset(160 * s, 125 * s))
        drawCircle(if (connected) PulseColors.accent else PulseColors.textTertiary, 4 * s, Offset(160 * s, 125 * s))
    }
}
