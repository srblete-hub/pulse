package com.noop.pulse

import android.Manifest
import android.app.DatePickerDialog
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noop.ble.WhoopModel
import com.noop.ingest.HealthConnectImporter
import com.noop.ui.AppViewModel
import com.noop.ui.CoachOrb
import com.noop.ui.ProfileStore
import com.noop.ui.blePermissions
import com.noop.ui.rememberRequestScan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** Pulse profile fields that noop's ProfileStore does not hold (name), in their own prefs file. */
object PulseProfile {
    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("pulse_profile", Context.MODE_PRIVATE)
    fun firstName(c: Context): String = prefs(c).getString("first_name", "") ?: ""
    fun lastName(c: Context): String = prefs(c).getString("last_name", "") ?: ""
    fun setName(c: Context, first: String, last: String) {
        prefs(c).edit().putString("first_name", first.trim()).putString("last_name", last.trim()).apply()
        // The coach greets by first name too.
        PrefsCoachMemory(c).set("name", first.trim().ifEmpty { null })
    }
    fun initials(c: Context): String {
        val f = firstName(c).firstOrNull()?.uppercaseChar()
        val l = lastName(c).firstOrNull()?.uppercaseChar()
        return listOfNotNull(f, l).joinToString("")
    }
    fun imperial(c: Context): Boolean = prefs(c).getBoolean("imperial", false)
    fun setImperial(c: Context, v: Boolean) = prefs(c).edit().putBoolean("imperial", v).apply()
    fun firstSyncAt(c: Context): Long? = prefs(c).getLong("first_sync", 0L).takeIf { it > 0 }
    fun markFirstSync(c: Context) { if (firstSyncAt(c) == null) prefs(c).edit().putLong("first_sync", System.currentTimeMillis()).apply() }
    fun joinedDay(c: Context): String? = prefs(c).getString("joined", null)
    fun markJoined(c: Context) {
        if (joinedDay(c) == null) prefs(c).edit().putString("joined", LocalDate.now().toString()).apply()
    }
}

private val welcomeBrush = androidx.compose.ui.graphics.SolidColor(PulseColors.raised)
private val fieldBg = PulseColors.base
private val titleStyle = TextStyle(fontFamily = PulseType.sans, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp)
private val leadStyle = TextStyle(fontFamily = PulseType.sans, fontSize = 14.sp, lineHeight = 20.sp)

private const val LAST_STEP = 8

/**
 * First-run setup ("Primera vez"): welcome, name, birth date + sex, height + weight, Health Connect,
 * strap pairing (noop's BLE connect), local-data acknowledgements, notifications, first week.
 * Replaces noop's terms gate, onboarding questionnaire and What's New sheet. [onFinished] records the
 * acknowledgements as the consent record.
 */
@Composable
fun PulseWelcome(vm: AppViewModel, onFinished: () -> Unit) {
    val context = LocalContext.current
    val profile = remember { ProfileStore.from(context) }
    var step by rememberSaveable { mutableStateOf(0) }
    var first by rememberSaveable { mutableStateOf(PulseProfile.firstName(context)) }
    var last by rememberSaveable { mutableStateOf(PulseProfile.lastName(context)) }
    var dob by rememberSaveable { mutableStateOf<Long?>(null) }
    var sex by rememberSaveable { mutableStateOf("") }
    var imperial by rememberSaveable { mutableStateOf(false) }
    var height by rememberSaveable { mutableStateOf("") }
    var weight by rememberSaveable { mutableStateOf("") }
    val checks = remember { mutableStateListOf(false, false, false) }
    val live by vm.live.collectAsStateWithLifecycle()

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { step++ }
    val scope = rememberCoroutineScope()
    var hcStatus by remember { mutableStateOf<String?>(null) }
    val hcCategories = remember { HealthConnectImporter.selectedCategories(context) }
    val hcLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        if (granted.any { it in HealthConnectImporter.permissionsFor(hcCategories) }) {
            hcStatus = "Importando en segundo plano…"
            scope.launch {
                withContext(Dispatchers.IO) {
                    runCatching { HealthConnectImporter.import(context, vm.repo, ProfileStore.from(context).heightCm) }
                }
            }
        }
        step++
    }
    val requestScan = rememberRequestScan { vm.connect(promoteService = false) }

    fun saveProfile() {
        if (first.isNotBlank() || last.isNotBlank()) PulseProfile.setName(context, first, last)
        dob?.let { profile.dateOfBirthMillis = it }
        when (sex) {
            "female" -> profile.sex = "female"
            "male" -> profile.sex = "male"
            "nb", "none" -> profile.sex = "nonbinary"
        }
        PulseProfile.setImperial(context, imperial)
        height.replace(',', '.').toDoubleOrNull()?.let { h -> profile.heightCm = if (imperial) h * 30.48 else h }
        weight.replace(',', '.').toDoubleOrNull()?.let { w -> profile.weightKg = if (imperial) w * 0.453592 else w }
    }

    fun next() {
        when (step) {
            1, 2, 3 -> saveProfile()
            4 -> {
                val available = HealthConnectImporter.sdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
                if (available) {
                    HealthConnectImporter.markPermissionsAsked(context, hcCategories)
                    hcLauncher.launch(HealthConnectImporter.permissionsFor(hcCategories))
                    return
                }
                hcStatus = "Health Connect no está disponible en este teléfono."
            }
            5 -> {
                if (!live.bonded) {
                    requestScan()
                    return
                }
            }
            6 -> if (!checks.all { it }) return
            7 -> {
                val needs = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                if (needs) {
                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    return
                }
            }
            LAST_STEP -> {
                saveProfile()
                PulseProfile.markJoined(context)
                vm.promoteBackgroundConnectionIfActive()
                onFinished()
                return
            }
        }
        step++
    }

    // Pairing finished → move on by itself.
    LaunchedEffect(live.bonded) { if (live.bonded && step == 5) step = 6 }
    BackHandler(enabled = step > 0) { step-- }

    val labels = listOf("EMPEZAR", "SIGUIENTE", "SIGUIENTE", "SIGUIENTE", "CONECTAR", if (live.bonded) "SIGUIENTE" else "EMPAREJAR", "ACEPTAR", "ACTIVAR", "TERMINAR")
    val enabled = step != 6 || checks.all { it }

    Box(Modifier.fillMaxSize().background(welcomeBrush).statusBarsPadding().windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.ime.union(androidx.compose.foundation.layout.WindowInsets.navigationBars).only(androidx.compose.foundation.layout.WindowInsetsSides.Bottom))) {
        when (step) {
            0 -> WelcomeHero()
            1 -> StepBody(Icons.Outlined.Person, "¿Cómo te llamas?", "Lo usamos para saludarte y en tu perfil.") {
                FieldLabel("Nombre")
                PulseField(first, { first = it }, "Tu nombre")
                Spacer(Modifier.height(16.dp))
                FieldLabel("Apellido (opcional)")
                PulseField(last, { last = it }, "Tu apellido")
            }
            2 -> StepBody(Icons.Outlined.CalendarMonth, "Tu fecha de nacimiento", "Tu edad y tu sexo cambian los rangos de pulso, el VO₂ máx de referencia y la edad fisiológica.") {
                val fmt = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy") }
                val shown = dob?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().format(fmt) } ?: "--/--/----"
                Row(
                    Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(PulseMetrics.buttonRadius)).border(1.dp, PulseColors.hairline, RoundedCornerShape(PulseMetrics.buttonRadius))
                        .pulseClick {
                            val start = dob?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() } ?: LocalDate.now().minusYears(30)
                            DatePickerDialog(context, { _, y, m, d ->
                                dob = LocalDate.of(y, m + 1, d).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                            }, start.year, start.monthValue - 1, start.dayOfMonth).apply {
                                datePicker.maxDate = System.currentTimeMillis()
                            }.show()
                        }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(shown, style = leadStyle.copy(fontSize = 16.sp), color = PulseColors.text, modifier = Modifier.weight(1f))
                    Icon(Icons.Outlined.CalendarMonth, null, tint = PulseColors.textSecondary, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.height(20.dp))
                FieldLabel("Sexo para los cálculos")
                val opts = listOf("female" to "MUJER", "male" to "HOMBRE", "nb" to "NO BINARIO", "none" to "PREFIERO NO DECIRLO")
                opts.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { (k, label) ->
                            val on = sex == k
                            Box(
                                Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(10.dp))
                                    .border(1.5.dp, if (on) PulseColors.accent else PulseColors.hairline, RoundedCornerShape(10.dp))
                                    .background(if (on) PulseColors.accentFillTop else Color.Transparent).pulseClick { sex = k },
                                contentAlignment = Alignment.Center,
                            ) { Text(label, style = PulseText.overline.copy(letterSpacing = androidx.compose.ui.unit.TextUnit(0.06f, androidx.compose.ui.unit.TextUnitType.Em)), color = PulseColors.text) }
                        }
                    }
                }
            }
            3 -> StepBody(Icons.Outlined.Straighten, "Altura y peso", "Para estimar calorías y tu forma física. Puedes cambiarlos cuando quieras.") {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(fieldBg).padding(4.dp)) {
                    listOf(true to "IMPERIAL", false to "MÉTRICO").forEach { (imp, label) ->
                        Box(
                            Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(8.dp))
                                .background(if (imperial == imp) PulseColors.overlay else Color.Transparent).pulseClick { imperial = imp },
                            contentAlignment = Alignment.Center,
                        ) { Text(label, style = PulseText.overline, color = PulseColors.text) }
                    }
                }
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        FieldLabel("Altura (${if (imperial) "pies" else "cm"})")
                        PulseField(height, { height = it }, if (imperial) "5.9" else "175", KeyboardType.Decimal)
                    }
                    Column(Modifier.weight(1f)) {
                        FieldLabel("Peso (${if (imperial) "lb" else "kg"})")
                        PulseField(weight, { weight = it }, if (imperial) "160" else "72", KeyboardType.Decimal)
                    }
                }
            }
            4 -> {
                SkipButton("Omitir") { step++ }
                HealthConnectArt()
                BottomText("Conecta Health Connect", "Importa peso, composición corporal y entrenos de otras apps, y comparte con ellas lo que mide Pulse. Siempre dentro de tu teléfono.", hcStatus)
            }
            5 -> {
                SkipButton("Ahora no") { step++ }
                PairingArt(live.bonded)
                val status = when {
                    live.bonded -> "Pulsera emparejada${live.batteryPct?.let { " · batería ${it.roundToInt()} %" } ?: ""}"
                    live.connected -> "Conectada, emparejando…"
                    live.scanning -> "Buscando pulseras cercanas…"
                    else -> "Pulsa EMPAREJAR para buscar tu pulsera"
                }
                BottomText("Empareja tu pulsera", "Acércala al teléfono y activa el Bluetooth. Compatible con los modelos 4.0 y 5.0 / MG.", null) {
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(if (live.bonded) PulseColors.recoveryGreen else PulseColors.accent))
                        Spacer(Modifier.width(8.dp))
                        Text(status, style = leadStyle.copy(fontSize = 13.sp), color = PulseColors.accentHover)
                    }
                    if (!live.bonded) {
                        Spacer(Modifier.height(16.dp))
                        ModelPicker(vm) { requestScan() }
                    }
                }
            }
            6 -> StepBody(Icons.Outlined.Lock, "Tus datos, solo tuyos", "Pulse no tiene cuentas ni servidores. Antes de seguir, confirma que lo entiendes.") {
                listOf(
                    "Entiendo que mis datos de salud se guardan y procesan solo en este teléfono.",
                    "Entiendo que Pulse no es un dispositivo médico.",
                    "Haré copias de seguridad yo mismo, porque no hay nube.",
                ).forEachIndexed { i, t ->
                    Row(Modifier.fillMaxWidth().padding(bottom = 16.dp).pulseClick { checks[i] = !checks[i] }) {
                        Box(
                            Modifier.size(24.dp).clip(RoundedCornerShape(5.dp))
                                .border(2.dp, if (checks[i]) PulseColors.accent else PulseColors.text, RoundedCornerShape(5.dp))
                                .background(if (checks[i]) PulseColors.accent else Color.Transparent),
                            contentAlignment = Alignment.Center,
                        ) { if (checks[i]) Icon(Icons.Outlined.Check, null, tint = PulseColors.raised, modifier = Modifier.size(16.dp)) }
                        Spacer(Modifier.width(16.dp))
                        Text(t, style = leadStyle, color = PulseColors.text)
                    }
                }
            }
            7 -> {
                SkipButton("Ahora no") { step++ }
                NotificationsArt()
                BottomText("Activa las notificaciones", "Recordatorios para dormir, tu recuperación de cada mañana y avisos de la pulsera. Puedes cambiarlo en Ajustes cuando quieras.", null)
            }
            else -> {
                FirstWeekArt()
                BottomText("Tu primera semana", "Durante 7 noches Pulse aprenderá cómo es tu cuerpo. Llévala puesta día y noche y cada día se desbloquearán nuevas métricas.", null)
            }
        }

        if (step > 0) {
            Box(
                Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 16.dp).size(44.dp).pulseClick { step-- },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Atrás", tint = PulseColors.text, modifier = Modifier.size(22.dp)) }
        }
        Row(
            Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 36.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(labels[step], style = PulseText.overlineLg, color = if (enabled) PulseColors.text else PulseColors.textTertiary)
            Spacer(Modifier.width(16.dp))
            NextButton(step, enabled) { next() }
        }
    }
}

@Composable
private fun NextButton(step: Int, enabled: Boolean, onClick: () -> Unit) {
    val filled = step == 0 || step == LAST_STEP
    Box(
        Modifier.size(70.dp).clip(RoundedCornerShape(35.dp))
            .background(if (filled) PulseColors.accentBrush else SolidColor(Color.Transparent))
            .pulseClick { if (enabled) onClick() },
        contentAlignment = Alignment.Center,
    ) {
        if (!filled) {
            Canvas(Modifier.size(70.dp)) {
                val sw = 3.dp.toPx()
                val r = size.minDimension / 2 - 3.dp.toPx()
                val tl = Offset(center.x - r, center.y - r)
                drawArc(PulseColors.hairlineStrong, 0f, 360f, false, tl, Size(r * 2, r * 2), style = Stroke(sw))
                drawArc(PulseColors.accent, -90f, 360f * step / LAST_STEP, false, tl, Size(r * 2, r * 2), style = Stroke(sw, cap = StrokeCap.Round))
            }
        }
        Icon(
            Icons.AutoMirrored.Outlined.ArrowForward, null,
            tint = if (filled) PulseColors.onAccent else if (enabled) PulseColors.text else PulseColors.textTertiary,
            modifier = Modifier.size(26.dp),
        )
    }
}

@Composable
private fun BoxScope.WelcomeHero() {
    Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.SolidColor(Color.Transparent)))
    Box(Modifier.align(Alignment.TopCenter).padding(top = 200.dp)) { CoachOrb(diameter = 120.dp) }
    Column(Modifier.align(Alignment.TopStart).padding(start = 24.dp, end = 24.dp, top = 472.dp)) {
        Text("Te damos la bienvenida a Pulse", style = titleStyle, color = PulseColors.text)
        Spacer(Modifier.height(8.dp))
        Text("Sin cuentas ni servidores: todo lo que mide tu pulsera se queda en este teléfono. Necesitamos unos datos para que los cálculos sean correctos.", style = leadStyle, color = PulseColors.textQuiet)
    }
}

@Composable
private fun StepBody(icon: ImageVector, title: String, lead: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = 152.dp, bottom = 140.dp),
    ) {
        Icon(icon, null, tint = PulseColors.textTertiary, modifier = Modifier.size(80.dp))
        Spacer(Modifier.height(32.dp))
        Text(title, style = titleStyle, color = PulseColors.text)
        Spacer(Modifier.height(8.dp))
        Text(lead, style = leadStyle, color = PulseColors.textQuiet)
        Spacer(Modifier.height(24.dp))
        content()
    }
}

@Composable
private fun BoxScope.BottomText(title: String, lead: String, status: String?, extra: @Composable () -> Unit = {}) {
    Column(Modifier.align(Alignment.BottomStart).padding(start = 24.dp, end = 24.dp, bottom = 128.dp)) {
        Text(title, style = titleStyle, color = PulseColors.text)
        Spacer(Modifier.height(8.dp))
        Text(lead, style = leadStyle, color = PulseColors.textQuiet)
        if (status != null) {
            Spacer(Modifier.height(8.dp))
            Text(status, style = leadStyle.copy(fontSize = 13.sp), color = PulseColors.accentHover)
        }
        extra()
    }
}

@Composable
private fun BoxScope.SkipButton(text: String, onClick: () -> Unit) {
    Text(
        text.uppercase(), style = PulseText.overline.copy(fontSize = 11.sp), color = PulseColors.text,
        modifier = Modifier.align(Alignment.TopEnd).padding(top = 32.dp, end = 16.dp).pulseClick(onClick),
    )
}

@Composable
internal fun FieldLabel(text: String) {
    Text(text.uppercase(), style = PulseText.overlineLg, color = PulseColors.textSecondary)
    Spacer(Modifier.height(8.dp))
}

@Composable
internal fun PulseField(value: String, onChange: (String) -> Unit, placeholder: String, type: KeyboardType = KeyboardType.Text) {
    Box(
        Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, PulseColors.overlay, RoundedCornerShape(12.dp))
            .background(fieldBg).padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) Text(placeholder, style = leadStyle.copy(fontSize = 16.sp), color = PulseColors.textTertiary)
        BasicTextField(
            value = value, onValueChange = onChange, singleLine = true,
            textStyle = leadStyle.copy(fontSize = 16.sp, color = PulseColors.text),
            cursorBrush = SolidColor(PulseColors.accent),
            keyboardOptions = KeyboardOptions(keyboardType = type),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ModelPicker(vm: AppViewModel, rescan: () -> Unit) {
    val selected by vm.selectedModel.collectAsStateWithLifecycle()
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(fieldBg).padding(4.dp)) {
        WhoopModel.entries.forEach { m ->
            Box(
                Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(8.dp))
                    .background(if (m == selected) PulseColors.overlay else Color.Transparent)
                    .pulseClick {
                        if (m != selected) {
                            vm.setSelectedModel(m)
                            vm.disconnect()
                            rescan()
                        }
                    },
                contentAlignment = Alignment.Center,
            ) { Text(if (m == WhoopModel.WHOOP4) "MODELO 4.0" else "MODELO 5.0 / MG", style = PulseText.overline, color = PulseColors.text) }
        }
    }
}

@Composable
private fun BoxScope.HealthConnectArt() {
    Box(Modifier.align(Alignment.TopCenter).padding(top = 128.dp).size(width = 390.dp, height = 360.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val hub = Offset(size.width * 0.38f, size.height / 2)
            val nodes = listOf(Offset(size.width * 0.85f, size.height * 0.22f), Offset(size.width * 0.88f, size.height * 0.49f), Offset(size.width * 0.82f, size.height * 0.75f))
            nodes.forEach { drawLine(PulseColors.hairlineStrong, hub, it, 2.dp.toPx()) }
            nodes.forEach {
                drawCircle(PulseColors.raised, 26.dp.toPx(), it)
                drawCircle(PulseColors.hairlineStrong, 26.dp.toPx(), it, style = Stroke(1.dp.toPx()))
            }
            drawCircle(androidx.compose.ui.graphics.SolidColor(PulseColors.accent), 56.dp.toPx(), hub)
        }
        Icon(Icons.Outlined.Favorite, null, tint = PulseColors.onAccent, modifier = Modifier.align(Alignment.CenterStart).padding(start = 128.dp).size(40.dp))
    }
}

@Composable
private fun BoxScope.PairingArt(bonded: Boolean) {
    val t = rememberInfiniteTransition(label = "scan")
    val pulse by t.animateFloat(0.85f, 1.1f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "p")
    Box(Modifier.align(Alignment.TopCenter).padding(top = 140.dp).size(300.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            listOf(120f to 0.15f, 85f to 0.3f, 50f to 0.5f).forEach { (r, a) ->
                drawCircle(PulseColors.accent.copy(alpha = a), r.dp.toPx() * (if (bonded) 1f else pulse), style = Stroke(2.dp.toPx()))
            }
        }
        Box(
            Modifier.size(width = 64.dp, height = 96.dp).clip(RoundedCornerShape(20.dp)).background(PulseColors.raised)
                .border(2.dp, if (bonded) PulseColors.recoveryGreen else PulseColors.hairlineStrong, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (bonded) Icon(Icons.Outlined.CheckCircle, null, tint = PulseColors.recoveryGreen, modifier = Modifier.size(30.dp))
            else Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(PulseColors.accent))
        }
    }
}

@Composable
private fun BoxScope.NotificationsArt() {
    Box(
        Modifier.align(Alignment.TopStart).padding(start = 112.dp, top = 152.dp).size(170.dp, 320.dp).rotate(8f)
            .clip(RoundedCornerShape(28.dp)).border(2.dp, PulseColors.hairlineStrong, RoundedCornerShape(28.dp)).background(PulseColors.overlay),
    )
    Column(Modifier.align(Alignment.TopStart).padding(start = 48.dp, top = 248.dp).width(250.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        NotifCard(Icons.Outlined.NightsStay, "Hora de desconectar", "Acuéstate a las 22:30 para dormir 8 h.", 1f, 0.dp)
        NotifCard(Icons.Outlined.Favorite, "Tu recuperación: 78 %", "Buen día para entrenar fuerte.", 0.8f, 20.dp)
    }
}

@Composable
private fun NotifCard(icon: ImageVector, title: String, body: String, alpha: Float, indent: androidx.compose.ui.unit.Dp) {
    Row(
        Modifier.padding(start = indent).fillMaxWidth().clip(RoundedCornerShape(PulseMetrics.cardRadius))
            .background((if (alpha == 1f) PulseColors.overlay else PulseColors.raised).copy(alpha = alpha)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(34.dp).border(1.5.dp, PulseColors.accent, RoundedCornerShape(17.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = PulseColors.accent, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(8.dp))
        Column {
            Text(title, style = leadStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold), color = PulseColors.text)
            Text(body, style = leadStyle.copy(fontSize = 12.sp), color = PulseColors.textSecondary)
        }
    }
}

@Composable
private fun BoxScope.FirstWeekArt() {
    Box(Modifier.align(Alignment.TopCenter).padding(top = 140.dp).size(320.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val r = 120.dp.toPx()
            val tl = Offset(center.x - r, center.y - r)
            val seg = 360f / 7
            for (i in 0 until 7) {
                drawArc(
                    androidx.compose.ui.graphics.SolidColor(PulseColors.accent),
                    -90f + i * seg + 4f, seg - 8f, false, tl, Size(r * 2, r * 2),
                    alpha = 0.35f + 0.65f * (i + 1) / 7f, style = Stroke(10.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("7", style = PulseText.heroNumber.copy(fontSize = 64.sp), color = PulseColors.text)
            Text("NOCHES", style = PulseText.overlineLg, color = PulseColors.textQuiet)
        }
    }
}
