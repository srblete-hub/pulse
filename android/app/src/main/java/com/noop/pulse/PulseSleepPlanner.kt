package com.noop.pulse

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noop.ui.AppViewModel
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

enum class WakeMode(val label: String) { Exact("HORA EXACTA"), Smart("VENTANA INTELIGENTE"), None("SIN ALARMA") }

/** One weekly wake schedule. [days] index 0 = Monday … 6 = Sunday. */
data class WakeSchedule(val days: Set<Int>, val minutes: Int, val mode: WakeMode)

/**
 * Pulse sleep schedules, stored locally and applied to noop's alarm engine:
 * exact → strap alarm (vibration) with per-weekday times; smart → phone window alarm in the 30 minutes
 * before the time, plus the strap buzz; none → suggestions only.
 */
object SleepSchedules {
    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("pulse_sleep", Context.MODE_PRIVATE)
    private val abbr = listOf("LUN", "MAR", "MIÉ", "JUE", "VIE", "SÁB", "DOM")
    const val smartWindow = 30

    fun load(c: Context): List<WakeSchedule> = (prefs(c).getString("schedules", "") ?: "").split(';').mapNotNull { e ->
        val p = e.split('|')
        if (p.size != 3) return@mapNotNull null
        val days = p[0].split(',').mapNotNull { it.toIntOrNull() }.filter { it in 0..6 }.toSet()
        val min = p[1].toIntOrNull() ?: return@mapNotNull null
        val mode = runCatching { WakeMode.valueOf(p[2]) }.getOrDefault(WakeMode.Exact)
        if (days.isEmpty()) null else WakeSchedule(days, min, mode)
    }

    fun save(c: Context, list: List<WakeSchedule>) =
        prefs(c).edit().putString("schedules", list.joinToString(";") { "${it.days.sorted().joinToString(",")}|${it.minutes}|${it.mode.name}" }).apply()

    fun enabled(c: Context) = prefs(c).getBoolean("on", true)
    fun setEnabled(c: Context, v: Boolean) = prefs(c).edit().putBoolean("on", v).apply()
    fun goal(c: Context) = prefs(c).getInt("goal", 0)
    fun setGoal(c: Context, v: Int) = prefs(c).edit().putInt("goal", v).apply()

    /** Pulse day index (Mon=0) → java.util.Calendar.DAY_OF_WEEK (Sun=1 … Sat=7). */
    fun calendarDow(i: Int) = if (i == 6) 1 else i + 2

    fun daysLabel(days: Set<Int>): String = when (days.sorted()) {
        listOf(0, 1, 2, 3, 4) -> "LUN – VIE"
        listOf(5, 6) -> "SÁB – DOM"
        (0..6).toList() -> "TODOS LOS DÍAS"
        else -> days.sorted().joinToString(" · ") { abbr[it] }
    }

    fun forDay(list: List<WakeSchedule>, dayIndex: Int) = list.lastOrNull { dayIndex in it.days }

    data class Tonight(val bedMin: Int, val wakeMin: Int, val mode: WakeMode, val on: Boolean)

    /** Tomorrow's wake time and tonight's recommended bedtime, or null when there is no schedule for tomorrow. */
    fun tonight(c: Context, needMinOverride: Double? = null): Tonight? {
        val list = load(c)
        val idx = LocalDate.now().plusDays(1).dayOfWeek.value - 1
        val s = forDay(list, idx) ?: return null
        val needMin = needMinOverride?.toInt() ?: ((PulseNeed.hours(c)) * 60).toInt()
        val inBed = needMin + listOf(0, 30, -60)[goal(c).coerceIn(0, 2)] + 15
        return Tonight(s.minutes - inBed, s.minutes, s.mode, enabled(c))
    }

    fun hm(min: Int) = "%02d:%02d".format(((min % 1440) + 1440) % 1440 / 60, ((min % 1440) + 1440) % 1440 % 60)

    /** Pushes the schedules into noop's alarms. Returns false when the smart window needs the exact-alarm permission. */
    fun apply(vm: AppViewModel, list: List<WakeSchedule>, on: Boolean): Boolean {
        val active = if (on) list.filter { it.mode != WakeMode.None } else emptyList()
        val strap = active
        if (strap.isEmpty()) {
            vm.setSmartAlarmEnabled(false)
        } else {
            val first = strap.first()
            vm.setSmartAlarmMinutes(first.minutes)
            (0..6).forEach { i -> vm.setSmartAlarmDayOverride(calendarDow(i), forDay(strap, i)?.minutes?.takeIf { it != first.minutes }) }
            vm.setSmartAlarmWeekdays(strap.flatMap { it.days }.map(::calendarDow).toSet())
            vm.setSmartAlarmEnabled(true)
        }
        val smart = active.filter { it.mode == WakeMode.Smart }
        if (smart.isEmpty()) {
            vm.setPhoneAlarmEnabled(false)
            return true
        }
        val first = smart.first()
        vm.setPhoneAlarmWindowMinutes(smartWindow)
        vm.setPhoneAlarmTargetMinutes(first.minutes - smartWindow)
        (0..6).forEach { i -> vm.setPhoneAlarmDayOverride(calendarDow(i), forDay(smart, i)?.minutes?.minus(smartWindow)?.takeIf { it != first.minutes - smartWindow }) }
        vm.setPhoneAlarmWeekdays(smart.flatMap { it.days }.map(::calendarDow).toSet())
        return vm.setPhoneAlarmEnabled(true)
    }
}

@Composable
fun PulseScheduleIntro(onClose: () -> Unit, onDone: () -> Unit) {
    IntroFlow(
        lastLabel = "CREAR HORARIO",
        steps = listOf(
            IntroStep("Planifica tus alarmas con el horario de sueño", emptyList(), textTop = 600.dp, kicker = "NUEVO") { ScheduleCards() },
            IntroStep("Crea horarios distintos para cada parte de la semana", emptyList(), kicker = "ORGANIZA") { ScheduleCards() },
            IntroStep("Elige qué días se repite cada alarma", emptyList(), kicker = "PERSONALIZA") {
                Row(Modifier.align(Alignment.TopCenter).padding(top = 300.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("L", "M", "X", "J", "V", "S", "D").forEachIndexed { i, d ->
                        Box(
                            Modifier.size(36.dp).clip(RoundedCornerShape(6.dp)).background(if (i < 5) Color.White else PulseColors.hairline),
                            contentAlignment = Alignment.Center,
                        ) { Text(d, style = PulseText.small.copy(fontWeight = FontWeight.Bold), color = if (i < 5) PulseColors.base else PulseColors.text) }
                    }
                }
            },
            IntroStep("Ajusta la hora de despertar a las horas que quieres dormir", emptyList(), kicker = "PLANIFICA") {
                Column(Modifier.align(Alignment.TopCenter).padding(top = 200.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(180.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize()) {
                            drawCircle(PulseColors.overlay, size.minDimension / 2 - 8.dp.toPx(), style = Stroke(12.dp.toPx()))
                            drawArc(PulseColors.sleep, -150f, 240f, false, Offset(8.dp.toPx(), 8.dp.toPx()), androidx.compose.ui.geometry.Size(size.width - 16.dp.toPx(), size.height - 16.dp.toPx()), style = Stroke(12.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
                        }
                        Text("8 h", style = PulseText.bigNumber.copy(fontSize = 34.sp), color = PulseColors.text)
                    }
                    Spacer(Modifier.height(20.dp))
                    Text("Te acuestas a las 23:00", style = PulseText.bodyLg, color = PulseColors.textQuiet)
                    Text("Te despiertas a las 07:00", style = PulseText.bodyLg, color = PulseColors.textQuiet)
                }
            },
            IntroStep("La pulsera vibra en tu muñeca para despertarte sin molestar a nadie", emptyList(), kicker = "DESPIERTA") {
                val t = rememberInfiniteTransition(label = "buzz")
                val a by t.animateFloat(-4f, 4f, infiniteRepeatable(tween(90), RepeatMode.Reverse), label = "a")
                Box(Modifier.align(Alignment.TopCenter).padding(top = 232.dp).rotate(a), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(width = 90.dp, height = 130.dp).clip(RoundedCornerShape(26.dp)).background(PulseColors.raised).border(2.dp, PulseColors.accent, RoundedCornerShape(26.dp)))
                    Icon(Icons.Outlined.Vibration, null, tint = PulseColors.accent, modifier = Modifier.size(40.dp))
                }
            },
            IntroStep("Consulta tu próxima alarma en el planificador de sueño", emptyList(), kicker = "GESTIONA") {
                Column(
                    Modifier.align(Alignment.TopCenter).padding(start = 40.dp, end = 40.dp, top = 260.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(PulseColors.divider).padding(16.dp),
                ) {
                    Text("ALARMA", style = PulseText.overline.copy(fontSize = 11.sp), color = PulseColors.text, modifier = Modifier.align(Alignment.CenterHorizontally))
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MiniCell("MODO", "HORA EXACTA", Modifier.weight(1f))
                        MiniCell("DESPERTAR", "07:00", Modifier.weight(1f))
                    }
                }
            },
            IntroStep("Desactiva el horario y la alarma con un solo interruptor", emptyList(), kicker = "PAUSA") {
                Box(Modifier.align(Alignment.TopCenter).padding(top = 300.dp)) { BigSwitch(true) { } }
            },
            IntroStep("Duerme con más regularidad", listOf("Con un horario fijo, Pulse aprende tu rutina y te sugiere mejor cuándo acostarte."), textTop = 600.dp) {
                Icon(Icons.Outlined.Bedtime, null, tint = PulseColors.sleep, modifier = Modifier.align(Alignment.TopCenter).padding(top = 232.dp).size(120.dp))
            },
        ),
        onClose = onClose, onDone = onDone,
    )
}

@Composable
private fun BoxScope.ScheduleCards() {
    Column(Modifier.align(Alignment.TopCenter).padding(start = 40.dp, end = 40.dp, top = 220.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        listOf("LUN – VIE" to "07:00", "SÁB – DOM" to "09:30").forEach { (d, t) ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(PulseMetrics.cardRadius)).background(PulseColors.hairlineStrong).padding(16.dp)) {
                Text(d, style = PulseText.overlineLg, color = PulseColors.textSecondary)
                Spacer(Modifier.height(8.dp))
                Text(t, style = PulseText.bigNumber.copy(fontSize = 26.sp), color = PulseColors.text)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Vibration, null, tint = PulseColors.textQuiet, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Vibración en la pulsera", style = PulseText.body.copy(fontSize = 12.sp), color = PulseColors.textQuiet)
                }
            }
        }
    }
}

@Composable
private fun MiniCell(label: String, value: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(10.dp)).background(PulseColors.hairlineStrong).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = PulseText.overline.copy(fontSize = 9.sp), color = PulseColors.textSecondary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(value, style = PulseText.small.copy(fontWeight = FontWeight.Bold), color = PulseColors.text, textAlign = TextAlign.Center)
    }
}

@Composable
private fun BigSwitch(on: Boolean, onToggle: () -> Unit) {
    Box(Modifier.width(56.dp).height(30.dp).clip(RoundedCornerShape(15.dp)).background(if (on) PulseColors.hairlineStrong else PulseColors.hairline).pulseClick(onToggle)) {
        Box(Modifier.offset(x = if (on) 29.dp else 3.dp, y = 3.dp).size(24.dp).clip(RoundedCornerShape(12.dp)).background(if (on) PulseColors.accent else PulseColors.textTertiary))
    }
}

private enum class PlannerView { Planner, Schedule, Sheet, Saving, Done }
private enum class SheetStep { Days, Mode, Time }

@Composable
fun PulseSleepPlannerScreen(vm: AppViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    var introDone by remember { mutableStateOf(PulseTips.seen(context, "schedule_intro")) }
    var view by remember { mutableStateOf(PlannerView.Planner) }
    if (!introDone) {
        PulseScheduleIntro(onClose = onBack, onDone = {
            PulseTips.markSeen(context, "schedule_intro"); introDone = true
            if (SleepSchedules.load(context).isEmpty()) view = PlannerView.Sheet
        })
        return
    }
    val days by com.noop.pulse.engine.pulseDays(vm)
    val live by vm.live.collectAsStateWithLifecycle()
    val schedules = remember { mutableStateListOf<WakeSchedule>().apply { addAll(SleepSchedules.load(context)) } }
    var on by remember { mutableStateOf(SleepSchedules.enabled(context)) }
    var goal by remember { mutableStateOf(SleepSchedules.goal(context)) }
    var sheet by remember { mutableStateOf(SheetStep.Days) }
    val sel = remember { mutableStateListOf(false, false, false, false, false, false, false) }
    var mode by remember { mutableStateOf(WakeMode.Exact) }
    var h by remember { mutableStateOf(7) }
    var m by remember { mutableStateOf(0) }
    var needsPermission by remember { mutableStateOf(false) }

    fun persist() {
        SleepSchedules.save(context, schedules)
        SleepSchedules.setEnabled(context, on)
        needsPermission = !SleepSchedules.apply(vm, schedules, on)
    }

    BackHandler {
        when (view) {
            PlannerView.Planner -> onBack()
            PlannerView.Sheet -> if (sheet == SheetStep.Days) view = PlannerView.Schedule else sheet = if (sheet == SheetStep.Time) SheetStep.Mode else SheetStep.Days
            else -> view = PlannerView.Planner
        }
    }

    val needMin = ((PulseNeed.hours(context)) * 60).toInt()
    val goalShift = listOf(0, 30, -60)[goal]
    val tomorrow = LocalDate.now().plusDays(1)
    val tIdx = tomorrow.dayOfWeek.value - 1
    val tomorrowSched = SleepSchedules.forDay(schedules, tIdx)
    val wake = tomorrowSched?.minutes ?: 7 * 60
    val inBed = needMin + goalShift + 15
    val bed = wake - inBed
    val nights = PulseData.nightsWithSleep(days)

    Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.SolidColor(PulseColors.raised)).statusBarsPadding()) {
        when (view) {
            PlannerView.Planner, PlannerView.Sheet -> {
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).pulseClick(onBack), contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver", tint = PulseColors.text, modifier = Modifier.size(22.dp)) }
                        Text("PLANIFICADOR DE SUEÑO", style = PulseText.tabTitle.copy(letterSpacing = PulseText.overline.letterSpacing * 1.16f), color = PulseColors.text, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                        Box(Modifier.size(44.dp).border(1.5.dp, PulseColors.text, RoundedCornerShape(22.dp)).pulseClick { view = PlannerView.Schedule }, contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.CalendarMonth, "Mi horario", tint = PulseColors.text, modifier = Modifier.size(20.dp))
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(end = 16.dp, top = 8.dp), horizontalArrangement = Arrangement.End) {
                        Text(
                            if (on) "ACTIVO" else "APAGADO", style = PulseText.overline.copy(fontSize = 9.5.sp),
                            color = if (on) PulseColors.accentHover else PulseColors.textSecondary,
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(if (on) PulseColors.hairlineStrong else PulseColors.overlay).padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                    Box(Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp).size(40.dp).border(1.5.dp, PulseColors.text, RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Bedtime, null, tint = PulseColors.text, modifier = Modifier.size(20.dp))
                    }
                    Text(
                        "Si te acuestas hoy a las ${SleepSchedules.hm(bed)}, mañana ${listOf("mantendrás tu horario habitual", "llegarás a tope de energía", "dormirás lo mínimo recomendable")[goal]}.",
                        style = PulseText.bodyLg.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium), color = PulseColors.text, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(start = 40.dp, end = 40.dp, top = 16.dp).fillMaxWidth(),
                    )
                    Text("MAÑANA QUIERO", style = PulseText.overline, color = PulseColors.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 24.dp))
                    Box(
                        Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp).height(44.dp).clip(RoundedCornerShape(22.dp)).border(1.5.dp, PulseColors.text, RoundedCornerShape(22.dp))
                            .pulseClick { goal = (goal + 1) % 3; SleepSchedules.setGoal(context, goal) }.padding(horizontal = 32.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(listOf("DORMIR LO NECESARIO", "RENDIR AL MÁXIMO", "DORMIR LO MÍNIMO")[goal], style = PulseText.overlineLg, color = PulseColors.text) }
                    Column(Modifier.padding(top = 24.dp).fillMaxWidth().weight(1f).background(PulseColors.raised).padding(horizontal = 24.dp, vertical = 20.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            TimeLabel(SleepSchedules.hm(bed), "HORA DE\nACOSTARTE")
                            TimeLabel(SleepSchedules.hm(wake), "HORA DE\nDESPERTAR")
                        }
                        Text("EN LA CAMA", style = PulseText.overline, color = PulseColors.text, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 16.dp))
                        Box(Modifier.fillMaxWidth().padding(top = 8.dp, start = 24.dp, end = 24.dp).height(36.dp), contentAlignment = Alignment.Center) {
                            Canvas(Modifier.fillMaxWidth().height(26.dp)) {
                                var x = 0f
                                while (x < size.width + size.height) {
                                    drawLine(PulseColors.hairline, Offset(x, 0f), Offset(x - size.height, size.height), 2.dp.toPx()); x += 8.dp.toPx()
                                }
                                drawLine(PulseColors.text, Offset(0f, 0f), Offset(0f, size.height), 2.dp.toPx())
                                drawLine(PulseColors.accent, Offset(size.width, 0f), Offset(size.width, size.height), 2.dp.toPx())
                            }
                            Text(
                                SleepSchedules.hm(inBed), style = PulseText.small.copy(fontWeight = FontWeight.Bold), color = PulseColors.text,
                                modifier = Modifier.clip(RoundedCornerShape(18.dp)).border(2.dp, PulseColors.text, RoundedCornerShape(18.dp)).background(PulseColors.base).padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                        Text(if (nights >= 3) "ÓPTIMO" else "ÓPTIMO", style = PulseText.overlineLg.copy(letterSpacing = PulseText.tabTitle.letterSpacing * 0.875f), color = PulseColors.stressLowLabel, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 16.dp))
                        Text(
                            if (nights >= 3) "Basado en tu necesidad de sueño (${SleepSchedules.hm(needMin)} h) y tus últimas noches." else "Necesitamos tres noches seguidas para calcular tus horas óptimas.",
                            style = PulseText.body.copy(fontSize = 11.5.sp), color = PulseColors.textTertiary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        )
                    }
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = PulseMetrics.sheetRadius, topEnd = PulseMetrics.sheetRadius)).background(PulseColors.divider).navigationBarsPadding().padding(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 24.dp)) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Alarm, null, tint = PulseColors.text, modifier = Modifier.size(28.dp))
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("ALARMA", style = PulseText.overline.copy(fontSize = 11.sp), color = PulseColors.text)
                                Text("PARA EL ${tomorrow.dayOfWeek.getDisplayName(TextStyle.FULL, Locale("es", "ES")).uppercase()}", style = PulseText.overline.copy(fontSize = 9.sp), color = PulseColors.textSecondary)
                            }
                            BigSwitch(on) { on = !on; persist() }
                        }
                        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MiniCell("MODO DE ALARMA", if (!on) "APAGADA" else tomorrowSched?.mode?.label ?: "SIN HORARIO", Modifier.weight(1f))
                            MiniCell("DESPERTAR A LAS", SleepSchedules.hm(wake), Modifier.weight(1f))
                        }
                        if (needsPermission) {
                            Text(
                                "Para la ventana inteligente, permite las alarmas exactas. Toca aquí.",
                                style = PulseText.body.copy(fontSize = 12.sp), color = PulseColors.stressHigh,
                                modifier = Modifier.padding(top = 8.dp).pulseClick {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                        runCatching { context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).setData(android.net.Uri.parse("package:${context.packageName}"))) }
                                    }
                                },
                            )
                        }
                        if (!live.connected && on && schedules.isNotEmpty()) {
                            Text("La pulsera no está conectada: la alarma se guardará en cuanto se conecte.", style = PulseText.body.copy(fontSize = 12.sp), color = PulseColors.textSecondary, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }
            }
            PlannerView.Schedule -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).pulseClick { view = PlannerView.Planner }, contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver", tint = PulseColors.text, modifier = Modifier.size(22.dp)) }
                    Text("MI HORARIO", style = PulseText.tabTitle.copy(letterSpacing = PulseText.overline.letterSpacing * 1.16f), color = PulseColors.text, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                    PulseSwitch(on) { on = it; persist() }
                }
                schedules.forEachIndexed { i, s ->
                    Column(Modifier.padding(start = 18.dp, end = 18.dp, top = if (i == 0) 30.dp else 12.dp).fillMaxWidth().clip(RoundedCornerShape(PulseMetrics.cardRadius)).background(PulseColors.hairlineStrong).padding(16.dp)) {
                        Text(SleepSchedules.daysLabel(s.days), style = PulseText.overlineLg.copy(letterSpacing = PulseText.overline.letterSpacing * 0.83f), color = PulseColors.textSecondary)
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(SleepSchedules.hm(s.minutes), style = PulseText.bigNumber.copy(fontSize = 26.sp), color = PulseColors.text, modifier = Modifier.weight(1f))
                            Icon(Icons.Outlined.Delete, "Borrar", tint = PulseColors.textSecondary, modifier = Modifier.size(20.dp).pulseClick { schedules.removeAt(i); persist() })
                        }
                        Row(
                            Modifier.padding(top = 8.dp).clip(RoundedCornerShape(6.dp)).background(PulseColors.hairlineStrong).padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(if (s.mode == WakeMode.None) Icons.Outlined.NotificationsOff else Icons.Outlined.Vibration, null, tint = PulseColors.text, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(s.mode.label, style = PulseText.overline, color = PulseColors.text)
                        }
                    }
                }
                val free = (0..6).filter { d -> schedules.none { d in it.days } }
                Box(
                    Modifier.align(Alignment.CenterHorizontally).padding(top = 32.dp).height(48.dp).clip(RoundedCornerShape(24.dp)).border(2.dp, PulseColors.text, RoundedCornerShape(24.dp))
                        .pulseClick {
                            val preset = if (free.size in 1..6) free.toSet() else setOf(0, 1, 2, 3, 4)
                            (0..6).forEach { sel[it] = it in preset }
                            mode = WakeMode.Exact; h = 7; m = 0; sheet = SheetStep.Days; view = PlannerView.Sheet
                        }.padding(horizontal = 40.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("CREAR HORARIO", style = PulseText.overlineLg, color = PulseColors.text) }
                if (schedules.isNotEmpty() && free.isNotEmpty()) {
                    Row(Modifier.align(Alignment.CenterHorizontally).padding(top = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(24.dp).clip(RoundedCornerShape(12.dp)).background(PulseColors.stressHigh), contentAlignment = Alignment.Center) { Text("!", style = PulseText.small.copy(fontWeight = FontWeight.ExtraBold), color = PulseColors.base) }
                        Spacer(Modifier.width(8.dp))
                        Text("Hay días sin horario", style = PulseText.body.copy(fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold), color = PulseColors.stressHigh)
                    }
                    Text("Esos días se usa tu última hora de despertar y la alarma queda apagada.", style = PulseText.body, color = PulseColors.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.padding(start = 40.dp, end = 40.dp, top = 16.dp))
                }
                if (schedules.isEmpty()) {
                    Text("Crea un horario para elegir a qué hora despertarte cada día de la semana.", style = PulseText.body, color = PulseColors.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.padding(start = 40.dp, end = 40.dp, top = 20.dp))
                }
            }
            PlannerView.Saving -> SavingView(done = false) { }
            PlannerView.Done -> SavingView(done = true) { view = PlannerView.Schedule }
        }

        if (view == PlannerView.Sheet) {
            Box(Modifier.fillMaxSize().background(Color(0xB3000000)).pulseClick { view = PlannerView.Schedule })
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(520.dp).clip(RoundedCornerShape(topStart = PulseMetrics.sheetRadius, topEnd = PulseMetrics.sheetRadius))
                    .background(androidx.compose.ui.graphics.SolidColor(PulseColors.raised)).pulseClick { }.navigationBarsPadding(),
            ) {
                Box(Modifier.align(Alignment.CenterHorizontally).padding(top = 12.dp).width(60.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(PulseColors.textTertiary))
                Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 16.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).pulseClick {
                        when (sheet) { SheetStep.Days -> view = PlannerView.Schedule; SheetStep.Mode -> sheet = SheetStep.Days; SheetStep.Time -> sheet = SheetStep.Mode }
                    }, contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Atrás", tint = PulseColors.text, modifier = Modifier.size(20.dp)) }
                    Text(
                        when (sheet) { SheetStep.Days -> "ELIGE LOS DÍAS QUE SE REPITE"; SheetStep.Mode -> "ELIGE EL MODO DE ALARMA"; SheetStep.Time -> "HORA DE DESPERTAR" },
                        style = PulseText.overline.copy(fontSize = 11.5.sp, lineHeight = 17.sp), color = PulseColors.text, textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                    )
                    Box(Modifier.size(28.dp).border(1.4.dp, PulseColors.textSecondary, RoundedCornerShape(PulseMetrics.cardRadius)), contentAlignment = Alignment.Center) { Text("i", style = PulseText.overline, color = PulseColors.textSecondary) }
                }
                Box(Modifier.fillMaxSize()) {
                    when (sheet) {
                        SheetStep.Days -> {
                            Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.size(34.dp).border(1.4.dp, PulseColors.text, RoundedCornerShape(17.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Bedtime, null, tint = PulseColors.text, modifier = Modifier.size(18.dp)) }
                                Text("Acostarte y despertarte siempre a la misma hora mejora cómo descansas.", style = PulseText.body.copy(fontSize = 13.sp), color = PulseColors.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.padding(start = 40.dp, end = 40.dp, top = 16.dp))
                                Row(Modifier.padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf("L", "M", "X", "J", "V", "S", "D").forEachIndexed { i, d ->
                                        Box(
                                            Modifier.size(32.dp).clip(RoundedCornerShape(6.dp)).background(if (sel[i]) Color.White else PulseColors.hairline).pulseClick { sel[i] = !sel[i] },
                                            contentAlignment = Alignment.Center,
                                        ) { Text(d, style = PulseText.small.copy(fontWeight = FontWeight.Bold), color = if (sel[i]) PulseColors.raised else PulseColors.text) }
                                    }
                                }
                            }
                            SheetButton("SIGUIENTE", sel.any { it }) { sheet = SheetStep.Mode }
                        }
                        SheetStep.Mode -> Column(Modifier.fillMaxWidth().padding(start = 40.dp, end = 40.dp, top = 36.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            ModeOption("HORA EXACTA", "Te despierta a una hora concreta.") { mode = WakeMode.Exact; sheet = SheetStep.Time }
                            ModeOption("VENTANA INTELIGENTE", "Te despierta en sueño ligero, poco antes de tu hora.") { mode = WakeMode.Smart; sheet = SheetStep.Time }
                            Row(Modifier.align(Alignment.CenterHorizontally).padding(top = 16.dp).pulseClick { mode = WakeMode.None; sheet = SheetStep.Time }, verticalAlignment = Alignment.CenterVertically) {
                                Text("SIN ALARMA", style = PulseText.overlineLg, color = PulseColors.text)
                                Spacer(Modifier.width(8.dp))
                                Icon(Icons.Outlined.NotificationsOff, null, tint = PulseColors.text, modifier = Modifier.size(16.dp))
                            }
                            Text("Recibe sugerencias de cuándo acostarte y levantarte, sin que suene nada.", style = PulseText.body.copy(fontSize = 12.sp), color = PulseColors.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 8.dp))
                        }
                        SheetStep.Time -> {
                            Row(Modifier.fillMaxWidth().padding(top = 60.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                Wheel(h, 24, { "$it" }) { h = it }
                                Text(":", style = PulseText.ringNumber.copy(fontSize = 20.sp), color = PulseColors.textSecondary, modifier = Modifier.padding(horizontal = 16.dp))
                                Wheel(m, 60, { "%02d".format(it) }, step = 5) { m = it }
                            }
                            SheetButton("GUARDAR", true) {
                                val chosen = (0..6).filter { sel[it] }.toSet()
                                val kept = schedules.map { it.copy(days = it.days - chosen) }.filter { it.days.isNotEmpty() }
                                schedules.clear(); schedules.addAll(kept + WakeSchedule(chosen, h * 60 + m, mode))
                                view = PlannerView.Saving
                            }
                        }
                    }
                }
            }
        }
        if (view == PlannerView.Saving) {
            LaunchedEffect(Unit) {
                persist()
                delay(1500)
                view = PlannerView.Done
            }
        }
    }
}

@Composable
private fun TimeLabel(time: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(time, style = PulseText.bigNumber.copy(fontSize = 28.sp), color = PulseColors.text)
        Spacer(Modifier.height(4.dp))
        Text(label, style = PulseText.overline.copy(fontSize = 9.5.sp, lineHeight = 13.sp), color = PulseColors.textSecondary, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ModeOption(title: String, body: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(PulseColors.hairline).pulseClick(onClick).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = PulseText.overlineLg, color = PulseColors.text)
        Spacer(Modifier.height(8.dp))
        Text(body, style = PulseText.body.copy(fontSize = 12.sp), color = PulseColors.textSecondary, textAlign = TextAlign.Center)
    }
}

@Composable
private fun BoxScope.SheetButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.align(Alignment.BottomCenter).padding(start = 24.dp, end = 24.dp, bottom = 40.dp).fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp))
            .border(2.dp, if (enabled) PulseColors.text else PulseColors.hairlineStrong, RoundedCornerShape(24.dp)).pulseClick { if (enabled) onClick() },
        contentAlignment = Alignment.Center,
    ) { Text(label, style = PulseText.overlineLg, color = if (enabled) PulseColors.text else PulseColors.textTertiary) }
}

@Composable
private fun Wheel(value: Int, modulo: Int, fmt: (Int) -> String, step: Int = 1, onChange: (Int) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(fmt((value - step + modulo) % modulo), style = PulseText.small.copy(fontSize = 16.sp), color = PulseColors.textTertiary, modifier = Modifier.pulseClick { onChange((value - step + modulo) % modulo) }.padding(8.dp))
        Spacer(Modifier.height(8.dp))
        Box(Modifier.width(70.dp)) {
            Column {
                Box(Modifier.fillMaxWidth().height(2.dp).background(PulseColors.text))
                Text(fmt(value), style = PulseText.ringNumberLg, color = PulseColors.text, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                Box(Modifier.fillMaxWidth().height(2.dp).background(PulseColors.text))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(fmt((value + step) % modulo), style = PulseText.small.copy(fontSize = 16.sp), color = PulseColors.textTertiary, modifier = Modifier.pulseClick { onChange((value + step) % modulo) }.padding(8.dp))
    }
}

@Composable
private fun SavingView(done: Boolean, onTap: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(androidx.compose.ui.graphics.SolidColor(PulseColors.raised)).pulseClick(onTap),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        if (done) {
            Box(Modifier.size(86.dp).border(3.dp, PulseColors.accent, RoundedCornerShape(43.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Check, null, tint = PulseColors.accent, modifier = Modifier.size(36.dp))
            }
        } else {
            val t = rememberInfiniteTransition(label = "spin")
            val a by t.animateFloat(0f, 360f, infiniteRepeatable(tween(1200)), label = "a")
            Box(Modifier.size(90.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize().rotate(a)) {
                    drawArc(PulseColors.accent, 0f, 270f, false, style = Stroke(3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
                }
                Icon(Icons.Outlined.Alarm, null, tint = PulseColors.text, modifier = Modifier.size(34.dp))
            }
        }
        Text(if (done) "¡LISTO!" else "GUARDANDO TU HORARIO…", style = PulseText.overline.copy(fontSize = 13.sp, letterSpacing = PulseText.tabTitle.letterSpacing * 0.875f), color = PulseColors.text, modifier = Modifier.padding(top = 28.dp))
        Text(if (done) "Tu horario se ha guardado." else "Mantén la pulsera cerca y conectada.", style = PulseText.body.copy(fontSize = 13.5.sp), color = PulseColors.textSecondary, modifier = Modifier.padding(top = 8.dp))
        if (done) Text("(toca para continuar)", style = PulseText.body.copy(fontSize = 11.sp), color = PulseColors.textTertiary, modifier = Modifier.padding(top = 40.dp))
    }
}
