package com.noop.pulse

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.animateFloat
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noop.data.DailyMetric
import com.noop.pulse.coach.CoachAction
import com.noop.pulse.coach.CoachEngine
import com.noop.pulse.coach.CoachMemory
import com.noop.pulse.coach.CoachReply
import com.noop.pulse.coach.CoachSnapshot
import com.noop.ui.AppViewModel
import com.noop.ui.CoachOrb
import com.noop.ui.ProfileStore
import com.noop.widget.StressWidgetProducer
import java.time.LocalTime
import kotlinx.coroutines.launch

/** A suggestion tapped in a screen's coach bar: shown as the coach's opening message in the chat. */
object CoachLaunch {
    var pending: Pair<String, String>? = null
    fun offer(line: String, query: String) { pending = line to query }
}

/** Coach memory in its own SharedPreferences file; never leaves the phone. */
class PrefsCoachMemory(context: Context) : CoachMemory {
    private val prefs = context.applicationContext.getSharedPreferences("pulse_coach", Context.MODE_PRIVATE)
    override fun get(key: String): String? = prefs.getString(key, null)
    override fun set(key: String, value: String?) {
        prefs.edit().apply { if (value == null) remove(key) else putString(key, value) }.apply()
    }
}

private data class ChatLine(val fromUser: Boolean, val text: String, val reply: CoachReply? = null)

private val sheetBrush = Brush.verticalGradient(
    0f to Color(0xFF171A24), 0.6f to Color(0xFF0C0E12), 1f to Color(0xFF08090B),
)
private val inputBorder = Brush.horizontalGradient(listOf(PulseColors.violet, PulseColors.stressLow))

fun buildCoachSnapshot(
    context: Context,
    today: DailyMetric?,
    days: List<DailyMetric>,
    stress: Double?,
    vo2: Double?,
    battery: Double?,
    connected: Boolean,
    streak: Int,
    pulseAge: Double? = null,
): CoachSnapshot {
    val d = today ?: days.lastOrNull { it.day == PulseData.dayKey(0) }
    val yesterday = days.lastOrNull { it.day == PulseData.dayKey(-1) }
    val last30 = days.takeLast(30)
    fun avg(f: (DailyMetric) -> Double?) = last30.mapNotNull(f).takeIf { it.isNotEmpty() }?.average()
    val byDay = days.associateBy { it.day }
    val week = (-6..0).map { byDay[PulseData.dayKey(it)] }
    val profile = ProfileStore.from(context)
    return CoachSnapshot(
        recovery = d?.recovery,
        sleepPct = PulseData.sleepPct(d),
        sleepMin = d?.totalSleepMin?.takeIf { it > 0 },
        deepMin = d?.deepMin,
        remMin = d?.remMin,
        efficiency = d?.efficiency,
        disturbances = d?.disturbances,
        hrv = d?.avgHrv,
        hrvAvg = avg { it.avgHrv },
        rhr = d?.restingHr?.toDouble(),
        rhrAvg = avg { it.restingHr?.toDouble() },
        resp = d?.respRateBpm,
        respAvg = avg { it.respRateBpm },
        skinTempDev = d?.skinTempDevC,
        effort = PulseData.effort21(d),
        effortAvg = avg { PulseData.effort21(it) },
        yesterdayEffort = PulseData.effort21(yesterday),
        steps = d?.steps,
        stepsAvg = avg { it.steps?.toDouble() },
        kcal = d?.activeKcalEst,
        stress = stress,
        nights = PulseData.nightsWithSleep(days),
        streak = streak,
        age = profile.age,
        sex = profile.sex,
        pulseAge = pulseAge,
        weekRecovery = week.map { it?.recovery },
        weekSleepMin = week.map { it?.totalSleepMin?.takeIf { m -> m > 0 } },
        weekEffort = week.map { PulseData.effort21(it) },
        hour = LocalTime.now().hour,
        batteryPct = battery,
        connected = connected,
    )
}

@Composable
fun PulseCoachScreen(vm: AppViewModel, onBack: () -> Unit, onAction: (CoachAction) -> Unit) {
    val context = LocalContext.current
    val today by com.noop.pulse.engine.pulseToday(vm)
    val days by com.noop.pulse.engine.pulseDays(vm)
    val live by vm.live.collectAsStateWithLifecycle()
    val streaks by vm.streaks.collectAsStateWithLifecycle()
    var stress by remember { mutableStateOf<Double?>(null) }
    var vo2 by remember { mutableStateOf<Double?>(null) }
    LaunchedEffect(Unit) {
        stress = runCatching { StressWidgetProducer.todayCurve(vm.repo, vm.activeStrapId)?.points?.lastOrNull { it.level != null }?.level }.getOrNull()
        vo2 = runCatching { vm.repo.latestMetricComputedUnion(vm.activeStrapId, "vo2max_est")?.value }.getOrNull()
    }
    val engine = remember { CoachEngine(PrefsCoachMemory(context)) }
    var pulseAge by remember { mutableStateOf<Double?>(null) }
    val workouts by vm.workouts.collectAsStateWithLifecycle()
    LaunchedEffect(days.size, vo2) {
        if (PulseData.dataDays(days) >= PulseData.pulseAgeDays) pulseAge = runCatching { com.noop.pulse.engine.PulseAge.compute(context, vm, days, workouts, vo2)?.result?.pulseAge }.getOrNull()
    }
    fun snapshot() = buildCoachSnapshot(context, today, days, stress, vo2, live.batteryPct, live.connected, streaks.current, pulseAge)

    val lines = remember { mutableStateListOf<ChatLine>() }
    var started by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    var thinking by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    fun send(text: String) {
        val t = text.trim()
        if (t.isEmpty() || thinking) return
        started = true
        lines += ChatLine(true, t)
        input = ""
        val r = engine.reply(t, snapshot())
        thinking = true
        scope.launch {
            // Feels like the coach is reading and thinking: 1.2–2.8 s, a bit longer for longer answers.
            kotlinx.coroutines.delay((1200L + r.text.length * 4L).coerceAtMost(2800L) + kotlin.random.Random.nextLong(0, 400))
            lines += ChatLine(false, r.text, r)
            thinking = false
        }
    }
    fun start() {
        started = true
        val r = engine.welcome(snapshot())
        lines += ChatLine(false, r.text, r)
    }
    LaunchedEffect(Unit) {
        CoachLaunch.pending?.let { (line, query) ->
            CoachLaunch.pending = null
            started = true
            engine.offer(query)
            lines += ChatLine(false, line, CoachReply(line, listOf("Sí, vamos", "No, gracias"), intent = "oferta"))
        }
    }
    LaunchedEffect(lines.size, thinking) { if (lines.isNotEmpty()) listState.animateScrollToItem(lines.lastIndex + if (thinking) 1 else 0) }

    val chips = lines.lastOrNull { !it.fromUser }?.reply?.quickReplies?.takeIf { it.isNotEmpty() }
        ?: listOf("¿Cómo dormí anoche?", "¿Entreno fuerte hoy?", "Mi semana", "¿Cómo estoy hoy?")

    Column(
        Modifier.fillMaxSize().background(PulseColors.inset).statusBarsPadding()
            .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars).only(WindowInsetsSides.Bottom)),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).pulseClick(onBack), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Atrás", tint = PulseColors.text, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.clip(RoundedCornerShape(16.dp)).background(PulseColors.overlay).padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Lock, null, tint = PulseColors.textQuiet, modifier = Modifier.size(14.dp))
                Spacer(Modifier.size(8.dp))
                Text("Tus datos no salen del teléfono", style = PulseText.body, color = PulseColors.textQuiet)
            }
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(44.dp).pulseClick { lines.clear(); started = false }, contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Refresh, "Nueva conversación", tint = PulseColors.text, modifier = Modifier.size(22.dp))
            }
        }
        Column(
            Modifier.fillMaxWidth().weight(1f).padding(top = 12.dp)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(sheetBrush),
        ) {
            Row(Modifier.padding(start = 14.dp, top = 14.dp)) {
                Row(
                    Modifier.clip(RoundedCornerShape(16.dp)).background(Color(0x0FFFFFFF)).padding(start = 3.dp, end = 12.dp, top = 3.dp, bottom = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CoachOrb(diameter = 26.dp)
                    Spacer(Modifier.size(8.dp))
                    Text("Coach", style = PulseText.body.copy(fontWeight = FontWeight.SemiBold), color = PulseColors.textQuiet)
                }
            }
            if (!started) {
                Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 30.dp), verticalArrangement = Arrangement.Center) {
                    CoachOrb(diameter = 54.dp)
                    Spacer(Modifier.height(22.dp))
                    Text(
                        "Hola. Leo tu sueño, tu recuperación y tus entrenos para explicarte qué significan y ayudarte a mejorar día a día.",
                        style = PulseText.bodyLg.copy(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium), color = PulseColors.text,
                    )
                    Spacer(Modifier.height(22.dp))
                    Box(
                        Modifier.height(36.dp).clip(RoundedCornerShape(9.dp)).background(PulseColors.text).pulseClick { start() }.padding(horizontal = 44.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("EMPEZAR", style = PulseText.overline, color = PulseColors.onAccent) }
                }
            } else {
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    state = listState,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(lines) { line -> Bubble(line, onAction) }
                    if (thinking) item { TypingBubble() }
                }
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                chips.forEach { chip ->
                    Box(
                        Modifier.height(32.dp).clip(RoundedCornerShape(8.dp)).background(PulseColors.text).pulseClick { send(chip) }.padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(chip, style = PulseText.body.copy(fontWeight = FontWeight.SemiBold), color = PulseColors.onAccent) }
                }
            }
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(14.dp)).border(1.5.dp, inputBorder, RoundedCornerShape(14.dp))
                        .background(Color(0xFF15161F)).padding(start = 14.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) {
                        if (input.isEmpty()) Text("Pregunta lo que quieras…", style = PulseText.body.copy(fontSize = 13.5.sp), color = PulseColors.textTertiary)
                        BasicTextField(
                            value = input,
                            onValueChange = { input = it },
                            singleLine = true,
                            textStyle = PulseText.body.copy(fontSize = 13.5.sp, color = PulseColors.text),
                            cursorBrush = SolidColor(PulseColors.accent),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { send(input) }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Box(Modifier.size(36.dp).pulseClick { send(input) }, contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Outlined.Send, "Enviar", tint = if (input.isBlank() || thinking) PulseColors.textTertiary else PulseColors.accent, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun Bubble(line: ChatLine, onAction: (CoachAction) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (line.fromUser) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier.widthIn(max = 300.dp)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = if (line.fromUser) 16.dp else 4.dp, bottomEnd = if (line.fromUser) 4.dp else 16.dp))
                .background(if (line.fromUser) PulseColors.accentFillTop else PulseColors.overlay)
                .padding(horizontal = 14.dp, vertical = 11.dp),
        ) {
            Text(line.text, style = PulseText.bodyLg, color = PulseColors.text)
            line.reply?.action?.let { a ->
                Spacer(Modifier.height(8.dp))
                CtaLink(a.label) { onAction(a) }
            }
        }
    }
}

/** Three softly pulsing dots while the coach "thinks". */
@Composable
private fun TypingBubble() {
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "typing")
    val phase by t.animateFloat(
        0f, 3f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(900, easing = androidx.compose.animation.core.LinearEasing)),
        label = "phase",
    )
    Row(Modifier.fillMaxWidth()) {
        Row(
            Modifier.clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp))
                .background(PulseColors.overlay).padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            repeat(3) { i ->
                val on = phase.toInt() == i
                Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(if (on) PulseColors.accent else PulseColors.textTertiary))
            }
        }
    }
}
