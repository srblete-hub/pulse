package com.noop.pulse

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noop.analytics.EffectRanker
import com.noop.data.JournalEntry
import com.noop.ui.AppViewModel
import com.noop.ui.JOURNAL_DEVICE_ID
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

data class Habit(val id: String, val name: String, val q: String, val follow: String, val cat: String)

/** Pulse journal habits. Answers are written to noop's journal table so its effect analysis keeps working. */
object PulseHabits {
    val catalog = listOf(
        Habit("cafe", "Café", "¿Has tomado café o bebidas con cafeína?", "¿A qué hora fue la última?", "Alimentación"),
        Habit("cena", "Cena tardía", "¿Has cenado poco antes de acostarte?", "¿A qué hora cenaste?", "Alimentación"),
        Habit("leer", "Leer en la cama", "¿Has leído en papel antes de dormir?", "¿A qué hora empezaste?", "Descanso"),
        Habit("alcohol", "Alcohol", "¿Has tomado alguna bebida con alcohol?", "¿A qué hora fue la última?", "Alimentación"),
        Habit("pantallas", "Pantallas en la cama", "¿Has usado el móvil o la tablet en la cama?", "¿Hasta qué hora?", "Descanso"),
        Habit("agua", "Hidratación", "¿Has bebido al menos 2 litros de agua?", "¿Cuándo bebiste más?", "Salud"),
        Habit("medita", "Meditación", "¿Has meditado hoy?", "¿Cuándo meditaste?", "Salud"),
        Habit("siesta", "Siesta", "¿Has dormido la siesta?", "¿A qué hora?", "Descanso"),
        Habit("estira", "Estiramientos", "¿Has hecho estiramientos?", "¿Cuándo?", "Salud"),
        Habit("azucar", "Azúcar añadido", "¿Has tomado dulces o azúcar añadido?", "¿A qué hora fue lo último?", "Alimentación"),
        Habit("sol", "Luz solar por la mañana", "¿Te ha dado el sol al levantarte?", "¿A qué hora?", "Salud"),
        Habit("viaje", "Viaje en avión", "¿Has viajado en avión?", "¿Cuándo aterrizaste?", "Salud"),
    )
    val categories = listOf("Todos", "Propios", "Alimentación", "Descanso", "Salud")
    const val maxPicked = 5

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("pulse_journal", Context.MODE_PRIVATE)

    fun custom(c: Context): List<Habit> = (prefs(c).getString("custom", "") ?: "").split('\n').filter { it.isNotBlank() }.mapIndexed { i, n ->
        Habit("own_$i", n, "¿$n hoy?".replace("¿¿", "¿").replace("??", "?"), "¿Cuándo?", "Propios")
    }
    fun addCustom(c: Context, name: String) {
        val list = custom(c).map { it.name } + name.trim()
        prefs(c).edit().putString("custom", list.joinToString("\n")).apply()
    }
    fun all(c: Context) = catalog + custom(c)
    fun picked(c: Context): List<String> = (prefs(c).getString("picked", null) ?: "cafe,cena,leer,alcohol").split(',').filter { it.isNotBlank() }
    fun setPicked(c: Context, ids: List<String>) = prefs(c).edit().putString("picked", ids.joinToString(",")).apply()
    fun notes(c: Context, day: String): String = prefs(c).getString("notes_$day", "") ?: ""
    fun setNotes(c: Context, day: String, v: String) = prefs(c).edit().putString("notes_$day", v).apply()
}

@Composable
fun PulseJournalIntro(onClose: () -> Unit, onDone: () -> Unit) {
    IntroFlow(
        lastLabel = "ABRIR DIARIO",
        steps = listOf(
            IntroStep(
                "Te damos la bienvenida a tu diario", listOf("Un minuto al día basta."), textTop = 600.dp,
                background = androidx.compose.ui.graphics.SolidColor(PulseColors.raised),
            ) {
                Box(Modifier.align(Alignment.TopCenter).padding(top = 200.dp).size(150.dp).clip(RoundedCornerShape(75.dp)).background(androidx.compose.ui.graphics.SolidColor(Color.Transparent)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Edit, null, tint = PulseColors.accent, modifier = Modifier.size(60.dp))
                }
            },
            IntroStep("Apunta tus hábitos y descubre cómo te afectan", emptyList()) {
                Column(
                    Modifier.align(Alignment.TopCenter).padding(start = 32.dp, end = 32.dp, top = 120.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(PulseColors.raised).padding(16.dp),
                ) {
                    Text("ÚLTIMOS 90 DÍAS", style = PulseText.overline, color = PulseColors.textSecondary)
                    Spacer(Modifier.height(8.dp))
                    Text("Qué afecta a tu recuperación", style = PulseText.cardTitleLg, color = PulseColors.text)
                    listOf("BEBER SUFICIENTE AGUA" to 11, "LEER ANTES DE DORMIR" to 8, "ESTIRAMIENTOS" to 4, "CENA TARDÍA" to -5, "ALCOHOL" to -12).forEach { (n, v) ->
                        EffectBar(n, v.toDouble(), "${if (v > 0) "+" else "−"}${kotlin.math.abs(v)}%")
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Ejemplo ilustrativo.", style = PulseText.body.copy(fontSize = 11.sp), color = PulseColors.textTertiary)
                }
            },
            IntroStep("Comprueba si tu nueva rutina de sueño funciona…", emptyList(), background = androidx.compose.ui.graphics.SolidColor(PulseColors.raised)) {
                Icon(androidx.compose.material.icons.Icons.Outlined.Check, null, tint = PulseColors.stressLow, modifier = Modifier.align(Alignment.TopCenter).padding(top = 240.dp).size(110.dp))
            },
            IntroStep("…o cómo te pasa factura el estrés del trabajo", emptyList(), background = androidx.compose.ui.graphics.SolidColor(PulseColors.raised)) {
                Icon(Icons.Outlined.Forum, null, tint = PulseColors.stressHigh, modifier = Modifier.align(Alignment.TopCenter).padding(top = 240.dp).size(110.dp))
            },
            IntroStep("Elige qué quieres registrar", listOf("Puedes cambiar tu lista cuando quieras. Todo se queda en tu teléfono.")) {
                Column(Modifier.align(Alignment.TopCenter).padding(start = 32.dp, end = 32.dp, top = 160.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Café", "Alcohol", "Meditación", "Pantallas en la cama", "Hidratación", "Siesta", "Cena tardía", "Estiramientos").chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { Text(it, style = PulseText.body.copy(fontWeight = FontWeight.SemiBold), color = PulseColors.text, modifier = Modifier.clip(RoundedCornerShape(18.dp)).background(PulseColors.overlay).padding(horizontal = 16.dp, vertical = 8.dp)) }
                        }
                    }
                }
            },
        ),
        onClose = onClose, onDone = onDone,
    )
}

@Composable
private fun EffectBar(name: String, value: Double, label: String, scale: Double = 15.0) {
    Column(Modifier.padding(top = 16.dp)) {
        Row {
            Text(name, style = PulseText.overline, color = PulseColors.text, modifier = Modifier.weight(1f))
            Text(label, style = PulseText.small.copy(fontWeight = FontWeight.Bold), color = if (value >= 0) PulseColors.accent else PulseColors.stressHigh)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().height(6.dp)) {
            val f = (kotlin.math.abs(value) / scale).toFloat().coerceIn(0.03f, 1f)
            Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
                if (value < 0) Box(Modifier.fillMaxWidth(f).fillMaxSize().clip(RoundedCornerShape(3.dp)).background(PulseColors.stressHigh))
            }
            Box(Modifier.width(2.dp).fillMaxSize().background(PulseColors.hairlineStrong))
            Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                if (value >= 0) Box(Modifier.fillMaxWidth(f).fillMaxSize().clip(RoundedCornerShape(3.dp)).background(PulseColors.accent))
            }
        }
    }
}

private val journalBg = androidx.compose.ui.graphics.SolidColor(PulseColors.raised)
private val es = Locale("es", "ES")

@Composable
fun PulseJournalScreen(vm: AppViewModel, onClose: () -> Unit, onCoach: () -> Unit) {
    val context = LocalContext.current
    var introDone by remember { mutableStateOf(PulseTips.seen(context, "journal_intro")) }
    if (!introDone) {
        PulseJournalIntro(onClose = onClose, onDone = { PulseTips.markSeen(context, "journal_intro"); introDone = true })
        return
    }
    val scope = rememberCoroutineScope()
    var offset by rememberSaveable { mutableStateOf(0) }
    val day = PulseData.dayKey(offset)
    val picked = remember { mutableStateListOf<String>().apply { addAll(PulseHabits.picked(context)) } }
    val answers = remember { mutableStateMapOf<String, Boolean>() }
    val times = remember { mutableStateMapOf<String, Int>() }
    var answeredDays by remember { mutableStateOf<Set<String>>(emptySet()) }
    var notes by remember(day) { mutableStateOf(PulseHabits.notes(context, day)) }
    var selecting by rememberSaveable { mutableStateOf(false) }
    var reload by remember { mutableStateOf(0) }
    val habits = PulseHabits.all(context)

    LaunchedEffect(day, reload) {
        answers.clear(); times.clear()
        val rows = runCatching { vm.repo.journal(JOURNAL_DEVICE_ID, PulseData.dayKey(-6), PulseData.dayKey(0)) }.getOrDefault(emptyList())
        answeredDays = rows.map { it.day }.toSet()
        rows.filter { it.day == day }.forEach { r ->
            habits.firstOrNull { it.q == r.question }?.let { h ->
                answers[h.id] = r.answeredYes
                r.notes?.let { n -> listOf("Mañana", "Tarde", "Noche").indexOf(n).takeIf { it >= 0 }?.let { times[h.id] = it } }
            }
        }
    }
    fun save(h: Habit) {
        val v = answers[h.id]
        scope.launch {
            runCatching {
                if (v == null) vm.repo.deleteJournalEntry(JOURNAL_DEVICE_ID, day, h.q)
                else vm.repo.upsertJournal(listOf(JournalEntry(JOURNAL_DEVICE_ID, day, h.q, v, notes = if (v) times[h.id]?.let { listOf("Mañana", "Tarde", "Noche")[it] } else null)))
            }
        }
    }
    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { notes = listOf(notes, it).filter { s -> s.isNotBlank() }.joinToString(" ") }
        }
    }
    val date = LocalDate.now().plusDays(offset.toLong())

    Box(Modifier.fillMaxSize().background(journalBg).imePadding()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(top = 72.dp, bottom = 120.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.ChevronLeft, "Día anterior", tint = PulseColors.text, modifier = Modifier.size(20.dp).pulseClick { if (offset > -6) offset-- })
                Spacer(Modifier.width(56.dp))
                Text(PulseData.dayLabel(offset), style = PulseText.overline.copy(fontSize = 11.sp), color = PulseColors.text)
                Spacer(Modifier.width(56.dp))
                Icon(Icons.Outlined.ChevronRight, "Día siguiente", tint = if (offset < 0) PulseColors.text else PulseColors.textTertiary, modifier = Modifier.size(20.dp).pulseClick { if (offset < 0) offset++ })
            }
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (-6..0).forEach { o ->
                    val d = LocalDate.now().plusDays(o.toLong())
                    val selected = o == offset
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(22.dp)).background(Color(0x1AFFFFFF))
                            .border(2.dp, if (selected) PulseColors.text else Color.Transparent, RoundedCornerShape(22.dp)).pulseClick { offset = o }
                            .padding(top = 8.dp, bottom = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(d.dayOfWeek.getDisplayName(TextStyle.SHORT, es).trimEnd('.').replaceFirstChar { it.uppercase() }, style = PulseText.body.copy(fontSize = 11.sp), color = PulseColors.textQuiet)
                        Text("${d.dayOfMonth}", style = PulseText.small.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold), color = PulseColors.text)
                        Spacer(Modifier.height(8.dp))
                        Box(Modifier.size(12.dp).clip(RoundedCornerShape(6.dp)).background(if (d.toString() in answeredDays) PulseColors.accent else Color(0x59FFFFFF)))
                    }
                }
            }
            Text(
                "¿Qué tal ha ido ${if (offset == 0) "hoy" else "el día"}, ${date.dayOfMonth} de ${date.month.getDisplayName(TextStyle.FULL, es)}?",
                style = PulseText.headline.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold), color = PulseColors.text,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp),
            )
            Column(
                Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp).fillMaxWidth().clip(RoundedCornerShape(PulseMetrics.cardRadius))
                    .background(androidx.compose.ui.graphics.SolidColor(PulseColors.overlay)).padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Forum, null, tint = PulseColors.stressLowLabel, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Cuéntaselo al coach", style = PulseText.cardTitle, color = PulseColors.text)
                }
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x1FFFFFFF)).pulseClick(onCoach), contentAlignment = Alignment.Center) {
                        Text("ESCRIBIR", style = PulseText.overlineLg, color = PulseColors.text)
                    }
                    Box(
                        Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(10.dp)).background(PulseColors.violet.copy(alpha = 0.35f)).pulseClick {
                            runCatching {
                                speech.launch(
                                    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
                                        .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true),
                                )
                            }
                        },
                        contentAlignment = Alignment.Center,
                    ) { Text("HABLAR", style = PulseText.overlineLg, color = PulseColors.text) }
                }
            }
            Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("HÁBITOS", style = PulseText.overline, color = PulseColors.textSecondary)
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f).height(1.dp).background(PulseColors.hairline))
            }
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                habits.filter { it.id in picked }.forEach { h ->
                    val a = answers[h.id]
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(PulseMetrics.cardRadius)).background(Color(0xB33A424A)).padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(h.q, style = PulseText.bodyLg.copy(lineHeight = 19.sp), color = PulseColors.text, modifier = Modifier.weight(1f))
                            Spacer(Modifier.width(8.dp))
                            AnswerButton(Icons.Outlined.Remove, a == false, PulseColors.textTertiary, PulseColors.text) { if (a == false) answers.remove(h.id) else answers[h.id] = false; save(h) }
                            Spacer(Modifier.width(8.dp))
                            AnswerButton(Icons.Outlined.Check, a == true, PulseColors.accent, PulseColors.raised) { if (a == true) { answers.remove(h.id); save(h) } else { answers[h.id] = true; save(h) } }
                        }
                        if (a == true) {
                            Text(h.follow, style = PulseText.body.copy(fontSize = 13.5.sp), color = PulseColors.text, modifier = Modifier.padding(top = 16.dp))
                            TimeSlider(times[h.id] ?: 0) { times[h.id] = it; save(h) }
                        }
                    }
                }
            }
            Text("NOTAS", style = PulseText.overline, color = PulseColors.textSecondary, modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp))
            Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(90.dp).clip(RoundedCornerShape(12.dp)).background(Color(0x0FFFFFFF)).border(1.dp, PulseColors.hairline, RoundedCornerShape(12.dp)).padding(12.dp)) {
                if (notes.isEmpty()) Text("Algo más que quieras recordar de hoy…", style = PulseText.body, color = PulseColors.textTertiary)
                BasicTextField(notes, { notes = it }, textStyle = PulseText.body.copy(color = PulseColors.text), cursorBrush = SolidColor(PulseColors.accent), modifier = Modifier.fillMaxSize())
            }
        }
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 8.dp, end = 12.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).pulseClick(onClose), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Close, "Cerrar", tint = PulseColors.text, modifier = Modifier.size(20.dp)) }
            Text("DIARIO", style = PulseText.tabTitle, color = PulseColors.text, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            Box(Modifier.size(44.dp).pulseClick { selecting = true }, contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Edit, "Editar hábitos", tint = PulseColors.text, modifier = Modifier.size(20.dp)) }
        }
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(androidx.compose.ui.graphics.SolidColor(PulseColors.base))
                .navigationBarsPadding().padding(start = 24.dp, end = 24.dp, top = 32.dp, bottom = 24.dp),
        ) {
            Box(
                Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(25.dp)).background(Color.White).pulseClick {
                    PulseHabits.setNotes(context, day, notes); onClose()
                },
                contentAlignment = Alignment.Center,
            ) { Text("LISTO", style = PulseText.overline.copy(fontSize = 12.sp, letterSpacing = PulseText.tabTitle.letterSpacing * 0.875f), color = PulseColors.raised) }
        }
        if (selecting) {
            HabitPicker(picked.toList(), onClose = { selecting = false }) { ids ->
                picked.clear(); picked.addAll(ids); PulseHabits.setPicked(context, ids); selecting = false; reload++
            }
        }
    }
}

@Composable
private fun AnswerButton(icon: androidx.compose.ui.graphics.vector.ImageVector, on: Boolean, onBg: Color, onFg: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(if (on) onBg else Color(0x1FFFFFFF)).pulseClick(onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = if (on) onFg else PulseColors.text, modifier = Modifier.size(18.dp)) }
}

@Composable
private fun TimeSlider(value: Int, onChange: (Int) -> Unit) {
    Column(Modifier.padding(top = 8.dp)) {
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().height(22.dp)) {
            val w = maxWidth
            Box(Modifier.align(Alignment.CenterStart).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(PulseColors.textTertiary))
            Box(Modifier.align(Alignment.CenterStart).width((w - 22.dp) * (value / 2f) + 11.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(PulseColors.accent))
            Box(Modifier.offset(x = (w - 22.dp) * (value / 2f)).size(22.dp).clip(RoundedCornerShape(11.dp)).background(Color.White))
            Row(Modifier.fillMaxSize()) { repeat(3) { i -> Box(Modifier.weight(1f).fillMaxSize().pulseClick { onChange(i) }) } }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            listOf("Mañana" to TextAlign.Start, "Tarde" to TextAlign.Center, "Noche" to TextAlign.End).forEachIndexed { i, (t, a) ->
                Text(t, style = PulseText.body.copy(fontSize = 10.5.sp), color = if (i == value) PulseColors.text else PulseColors.textSecondary, textAlign = a, modifier = Modifier.weight(1f).pulseClick { onChange(i) })
            }
        }
    }
}

@Composable
private fun BoxScope.HabitPicker(initial: List<String>, onClose: () -> Unit, onSave: (List<String>) -> Unit) {
    val context = LocalContext.current
    val sel = remember { mutableStateListOf<String>().apply { addAll(initial) } }
    var cat by remember { mutableStateOf("Todos") }
    var creating by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var all by remember { mutableStateOf(PulseHabits.all(context)) }
    Box(Modifier.matchParentSize().background(Color(0x8C000000)).pulseClick(onClose))
    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(top = 72.dp).clip(RoundedCornerShape(topStart = PulseMetrics.sheetRadius, topEnd = PulseMetrics.sheetRadius))
            .background(androidx.compose.ui.graphics.SolidColor(PulseColors.raised)).pulseClick { },
    ) {
        Box(Modifier.align(Alignment.CenterHorizontally).padding(top = 12.dp).width(60.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(PulseColors.textTertiary))
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 12.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).pulseClick(onClose), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Close, "Cerrar", tint = PulseColors.text, modifier = Modifier.size(18.dp)) }
            Text("ELIGE TUS HÁBITOS", style = PulseText.overline.copy(fontSize = 11.5.sp, letterSpacing = PulseText.tabTitle.letterSpacing * 0.875f), color = PulseColors.text, textAlign = TextAlign.Center, modifier = Modifier.weight(1f).padding(end = 40.dp))
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(start = 16.dp, top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PulseHabits.categories.forEach { c ->
                Text(
                    c, style = PulseText.body.copy(fontWeight = FontWeight.SemiBold), color = if (c == cat) PulseColors.raised else PulseColors.text,
                    modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(if (c == cat) Color.White else Color(0x1FFFFFFF)).pulseClick { cat = c }.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Column(Modifier.padding(top = 16.dp).fillMaxWidth().clip(RoundedCornerShape(PulseMetrics.cardRadius)).background(Color(0x14FFFFFF)).padding(16.dp)) {
                Text("Crea un hábito propio", style = PulseText.cardTitle, color = PulseColors.text)
                if (creating) {
                    Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(44.dp).clip(RoundedCornerShape(10.dp)).background(PulseColors.base).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                        if (newName.isEmpty()) Text("Por ejemplo: Ducha fría", style = PulseText.body, color = PulseColors.textTertiary)
                        BasicTextField(newName, { newName = it }, singleLine = true, textStyle = PulseText.body.copy(color = PulseColors.text), cursorBrush = SolidColor(PulseColors.accent), modifier = Modifier.fillMaxWidth())
                    }
                    Text("AÑADIR", style = PulseText.overlineLg, color = PulseColors.accent, modifier = Modifier.padding(top = 8.dp).pulseClick {
                        if (newName.isNotBlank()) { PulseHabits.addCustom(context, newName); all = PulseHabits.all(context); newName = ""; creating = false; cat = "Propios" }
                    })
                } else {
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("ESCRIBIR", style = PulseText.overline, color = PulseColors.text, modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0x1FFFFFFF)).pulseClick { creating = true }.padding(horizontal = 16.dp, vertical = 8.dp))
                    }
                }
            }
            Text("ELEGIDOS · ${sel.size}/${PulseHabits.maxPicked}", style = PulseText.overline, color = PulseColors.textSecondary, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
            all.filter { it.id in sel }.forEach { h -> HabitRow(h, true) { sel.remove(h.id) } }
            Text("SIN ELEGIR", style = PulseText.overline, color = PulseColors.textSecondary, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
            all.filter { it.id !in sel && (cat == "Todos" || it.cat == cat) }.forEach { h -> HabitRow(h, false) { if (sel.size < PulseHabits.maxPicked) sel.add(h.id) } }
            Spacer(Modifier.height(100.dp))
        }
        Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 24.dp, end = 24.dp, bottom = 20.dp, top = 8.dp)) {
            Box(Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(25.dp)).background(Color.White).pulseClick { onSave(sel.toList()) }, contentAlignment = Alignment.Center) {
                Text("GUARDAR HÁBITOS", style = PulseText.overline.copy(fontSize = 12.sp), color = PulseColors.raised)
            }
        }
    }
}

@Composable
private fun HabitRow(h: Habit, chosen: Boolean, onToggle: () -> Unit) {
    Row(Modifier.padding(bottom = 8.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0x14FFFFFF)).pulseClick(onToggle).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(h.name, style = PulseText.cardTitle, color = PulseColors.text)
            Text(h.q, style = PulseText.body.copy(fontSize = 12.sp), color = PulseColors.textSecondary)
        }
        Box(
            Modifier.size(28.dp).clip(RoundedCornerShape(PulseMetrics.cardRadius)).background(if (chosen) PulseColors.accent else Color(0x1FFFFFFF)),
            contentAlignment = Alignment.Center,
        ) { Icon(if (chosen) Icons.Outlined.Check else Icons.Outlined.Add, null, tint = if (chosen) PulseColors.raised else PulseColors.text, modifier = Modifier.size(16.dp)) }
    }
}

/** "Hábitos que influyen": the user's own effects once there is enough data, general guidance before. */
@Composable
fun PulseHabitsScreen(vm: AppViewModel, onBack: () -> Unit, onJournal: () -> Unit) {
    val context = LocalContext.current
    val days by com.noop.pulse.engine.pulseDays(vm)
    var effects by remember { mutableStateOf<List<Pair<String, Double>>>(emptyList()) }
    LaunchedEffect(days.size) {
        val rows = runCatching { vm.repo.journal(JOURNAL_DEVICE_ID, "0000-01-01", "9999-12-31") }.getOrDefault(emptyList())
        val recNext = days.associate { it.day to it.recovery }.let { byDay ->
            days.mapNotNull { d -> runCatching { LocalDate.parse(d.day).minusDays(1).toString() }.getOrNull()?.let { prev -> byDay[d.day]?.let { prev to it } } }.toMap()
        }
        val names = PulseHabits.all(context).associateBy { it.q }
        val yes = rows.filter { it.answeredYes }.groupBy { it.question }.mapValues { e -> e.value.map { it.day }.toSet() }
        val no = rows.filter { !it.answeredYes }.groupBy { it.question }.mapValues { e -> e.value.map { it.day }.toSet() }
        effects = runCatching { EffectRanker.rankNoLag(yes, no, recNext, "recovery") }.getOrDefault(emptyList())
            .filter { it.nWith >= 3 && it.nWithout >= 3 }
            .map { (names[it.behavior]?.name ?: it.behavior) to (it.pctChange ?: 0.0) }
    }
    Column(Modifier.fillMaxSize().background(PulseColors.screenBrush).verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).pulseClick(onBack), contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver", tint = PulseColors.text, modifier = Modifier.size(22.dp)) }
            Text("HÁBITOS QUE INFLUYEN", style = PulseText.tabTitle, color = PulseColors.text, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(44.dp))
        }
        Text("Apunta tus hábitos y descubre qué te sienta bien", style = PulseText.headline.copy(fontSize = 22.sp, fontWeight = FontWeight.SemiBold), color = PulseColors.text, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp))
        Text("Prueba pequeños cambios en tu día a día, en comida, descanso o trabajo, y Pulse irá viendo cómo afectan a tu recuperación.", style = PulseText.bodyLg, color = PulseColors.textSecondary, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp))
        AccentCard(Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp), onClick = onJournal) {
            Text(if (effects.isEmpty()) "¿Con ganas de empezar?" else "Sigue apuntando", style = PulseText.cardTitleLg, color = PulseColors.text)
            Spacer(Modifier.height(8.dp))
            Text(
                if (effects.isEmpty()) "Vuelve mañana, después de tu primera noche con la pulsera, y apunta tus hábitos en el diario." else "Cuantos más días apuntes, más fiables serán estos efectos.",
                style = PulseText.body, color = PulseColors.textSecondary,
            )
            Spacer(Modifier.height(8.dp))
            CtaLink("Abrir diario", onClick = onJournal)
        }
        Text(
            if (effects.isEmpty()) "HÁBITOS PARA EMPEZAR" else "TUS HÁBITOS", style = PulseText.overlineLg, color = PulseColors.textSecondary,
            modifier = Modifier.padding(start = 20.dp, top = 32.dp),
        )
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(PulseMetrics.cardRadius)).background(PulseColors.raised).padding(16.dp)) {
            Text(if (effects.isEmpty()) "Lo que dice la investigación sobre el sueño" else "Efecto en tu recuperación de la mañana siguiente", style = PulseText.cardTitle, color = PulseColors.text)
            Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Text("▼ PERJUDICA", style = PulseText.overline.copy(fontSize = 9.sp), color = PulseColors.stressHigh, modifier = Modifier.weight(1f))
                Text("EFECTO", style = PulseText.overline.copy(fontSize = 9.sp), color = PulseColors.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                Text("AYUDA ▲", style = PulseText.overline.copy(fontSize = 9.sp), color = PulseColors.accent, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
            }
            if (effects.isEmpty()) {
                listOf(
                    Triple("Rutina fija de sueño", "Acuéstate y levántate a la misma hora", 10.0),
                    Triple("Leer en la cama", "En papel, sin pantallas", 6.0),
                    Triple("Café por la tarde", "Evita la cafeína 8 horas antes de dormir", -6.0),
                    Triple("Cena tardía", "Deja 2 horas entre la cena y la cama", -4.5),
                    Triple("Alcohol", "Sobre todo cerca de la hora de dormir", -10.0),
                ).forEach { (n, tip, v) ->
                    EffectBar(n.uppercase(es), v, "")
                    Text(tip, style = PulseText.body.copy(fontSize = 11.5.sp), color = PulseColors.textSecondary, modifier = Modifier.padding(top = 4.dp))
                }
                Text("Orientación general, no datos tuyos. Tus efectos reales aparecerán aquí cuando lleves unas semanas apuntando hábitos.", style = PulseText.body.copy(fontSize = 11.sp), color = PulseColors.textTertiary, modifier = Modifier.padding(top = 16.dp))
            } else {
                effects.forEach { (n, pct) -> EffectBar(n.uppercase(es), pct, "${if (pct >= 0) "+" else "−"}${kotlin.math.abs(pct).roundToInt()}%", scale = 20.0) }
                Text("Comparación de tu recuperación los días después de hacerlo y de no hacerlo. Es una asociación, no una prueba de causa.", style = PulseText.body.copy(fontSize = 11.sp), color = PulseColors.textTertiary, modifier = Modifier.padding(top = 16.dp))
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}
