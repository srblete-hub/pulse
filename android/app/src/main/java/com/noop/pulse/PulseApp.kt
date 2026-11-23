package com.noop.pulse

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.noop.ui.AppRoot
import com.noop.ui.AppViewModel
import com.noop.ui.BackupSyncScreen
import com.noop.ui.CoachOrb
import com.noop.pulse.coach.CoachAction
import com.noop.ui.DataSourcesScreen
import com.noop.ui.DevicesScreen
import com.noop.ui.HealthScreen
import com.noop.ui.InsightsScreen
import com.noop.ui.LiveScreen
import com.noop.ui.NoopLimitationsScreen
import com.noop.ui.NotificationsSettingsScreen
import com.noop.ui.SettingsScreen
import com.noop.ui.SleepScreen
import com.noop.ui.SmartAlarmScreen
import com.noop.ui.TrendsScreen
import com.noop.ui.WorkoutsScreen

/** Pulse routes. Root tabs show the floating bar; everything else is a pushed page. */
object PulseRoute {
    const val home = "home"
    const val health = "health"
    const val community = "community"
    const val more = "more"
    val tabs = setOf(home, health, community, more)

    const val sleep = "sleep"
    const val recovery = "recovery"
    const val effort = "effort"
    const val age = "age"
    const val profile = "profile"
    const val calibration = "calibration"
    const val goals = "goals"
    const val charging = "charging"
    const val help = "help"
    const val privacy = "privacy"
    const val about = "about"
    const val editProfile = "profile/edit"
    const val coachMemory = "coach/memory"
    const val welcome = "welcome"

    // noop engine screens hosted inside the Pulse shell (device link, import, backup, alarms…).
    const val strap = "noop/devices"
    const val dataImport = "noop/data"
    const val backup = "noop/backup"
    const val alarms = "noop/alarms"
    const val coach = "coach"
    const val live = "noop/live"
    const val trends = "noop/trends"
    const val monitor = "noop/health"
    const val stress = "stress"
    const val breatheRelax = "breathe/relax"
    const val breatheActivate = "breathe/activate"
    const val sleepDetail = "noop/sleep"
    const val workouts = "noop/workouts"
    const val journal = "noop/journal"
    const val notifications = "noop/notifications"
    const val advanced = "noop/settings"
    const val limitations = "noop/limitations"
    const val classic = "noop/classic"
}

@Composable
fun PulseApp(vm: AppViewModel) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: PulseRoute.home
    val back: () -> Unit = { nav.popBackStack() }
    fun go(r: String): () -> Unit = { nav.navigate(r) { launchSingleTop = true } }

    Box(Modifier.fillMaxSize().background(PulseColors.base)) {
        NavHost(nav, startDestination = PulseRoute.home, modifier = Modifier.fillMaxSize()) {
            composable(PulseRoute.home) {
                PulseHomeScreen(
                    vm,
                    HomeActions(
                        openProfile = go(PulseRoute.profile),
                        openStrap = go(PulseRoute.strap),
                        openSleep = go(PulseRoute.sleep),
                        openRecovery = go(PulseRoute.recovery),
                        openEffort = go(PulseRoute.effort),
                        openStress = go(PulseRoute.stress),
                        openCalibration = go(PulseRoute.calibration),
                        openGoals = go(PulseRoute.goals),
                        openCharging = go(PulseRoute.charging),
                        openSleepSchedule = go(PulseRoute.alarms),
                        openStartActivity = go(PulseRoute.live),
                        openHrv = go(PulseRoute.recovery),
                        openSteps = go(PulseRoute.effort),
                        openCalories = go(PulseRoute.effort),
                    ),
                )
            }
            composable(PulseRoute.health) {
                PulseHealthScreen(
                    vm,
                    HealthActions(
                        openAge = go(PulseRoute.age),
                        openTrends = go(PulseRoute.trends),
                        openMonitor = go(PulseRoute.monitor),
                        openStress = go(PulseRoute.stress),
                    ),
                )
            }
            composable(PulseRoute.community) { PulseCommunityScreen() }
            composable(PulseRoute.more) {
                PulseSettingsScreen(vm, settingsGroups(::go), onCalibration = go(PulseRoute.calibration))
            }

            composable(PulseRoute.sleep) { PulseSleepScreen(vm, back, onCoach = go(PulseRoute.coach)) }
            composable(PulseRoute.recovery) { PulseRecoveryScreen(vm, back, onHabits = go(PulseRoute.journal), onCoach = go(PulseRoute.coach)) }
            composable(PulseRoute.effort) { PulseEffortScreen(vm, back, onCoach = go(PulseRoute.coach)) }
            composable(PulseRoute.age) { PulseAgeScreen(vm, back, onCoach = go(PulseRoute.coach)) }
            composable(PulseRoute.profile) {
                PulseProfileScreen(
                    vm, back,
                    onSettings = go(PulseRoute.more),
                    onEdit = go(PulseRoute.editProfile),
                    onAge = go(PulseRoute.age),
                    onCoachMemory = go(PulseRoute.coachMemory),
                    onCalibration = go(PulseRoute.calibration),
                )
            }
            composable(PulseRoute.editProfile) { PulseEditProfileScreen(back) }
            composable(PulseRoute.coachMemory) { PulseCoachMemoryScreen(back) }
            composable(PulseRoute.welcome) { PulseWelcome(vm, onFinished = back) }
            composable(PulseRoute.calibration) { PulseCalibrationScreen(vm, back) }
            composable(PulseRoute.goals) {
                PulseInfoScreen(
                    "Objetivos", "¿Qué quieres mejorar?",
                    listOf(
                        "Dormir mejor: fíjate en el rendimiento del sueño y en la regularidad de tus horarios.",
                        "Rendir más: equilibra el esfuerzo del día con tu recuperación de la mañana.",
                        "Envejecer mejor: tu edad fisiológica resume sueño, actividad y forma física en una sola cifra.",
                        "El coach puede proponerte un plan con pasos concretos para cualquiera de estos objetivos.",
                    ),
                    back, cta = "Hablar con el coach" to go(PulseRoute.coach),
                )
            }
            composable(PulseRoute.charging) {
                PulseInfoScreen(
                    "Cargar la pulsera", "Cómo cargar la pulsera",
                    listOf(
                        "Carga la pulsera antes de dormir para que registre la noche completa.",
                        "Con la batería por debajo del 20 % puede saltarse lecturas nocturnas; Pulse te avisará.",
                        "El porcentaje de batería aparece arriba a la derecha en Inicio. Tócalo para ver el estado de la conexión.",
                    ),
                    back, cta = "Ver mi pulsera" to go(PulseRoute.strap),
                )
            }
            composable(PulseRoute.help) {
                PulseInfoScreen(
                    "Cómo funciona", "Todo se calcula en tu teléfono",
                    listOf(
                        "Pulse se conecta por Bluetooth a tu pulsera, descarga los datos y calcula sueño, recuperación, esfuerzo y estrés directamente en el teléfono.",
                        "No hay cuenta ni servidor: tus datos no salen del dispositivo salvo que tú los exportes.",
                        "Los primeros días son de calibración: Pulse aprende tus valores normales antes de puntuar.",
                    ),
                    back, cta = "Límites y detalles técnicos" to go(PulseRoute.limitations),
                )
            }
            composable(PulseRoute.privacy) {
                PulseInfoScreen(
                    "Privacidad", "Tus datos son tuyos",
                    listOf(
                        "Pulse guarda todo en una base de datos local del teléfono.",
                        "No envía métricas a ningún servidor. Las copias de seguridad las guardas tú donde quieras.",
                        "El coach funciona con reglas dentro del teléfono: no usa inteligencia artificial ni internet.",
                    ),
                    back,
                )
            }
            composable(PulseRoute.about) {
                PulseInfoScreen(
                    "Acerca de Pulse", "Pulse",
                    listOf(
                        "Interfaz Pulse construida sobre el motor de código abierto NOOP (licencia PolyForm Noncommercial 1.0.0), que gestiona la conexión Bluetooth con la pulsera y los cálculos.",
                        "Uso personal, no comercial. No es un dispositivo médico.",
                    ),
                    back, cta = "Interfaz clásica de NOOP" to go(PulseRoute.classic),
                )
            }

            // Hosted noop engine screens.
            legacy(PulseRoute.strap, "Pulsera", back) { DevicesScreen(vm, onUseFileImport = { nav.navigate(PulseRoute.dataImport) }) }
            legacy(PulseRoute.dataImport, "Importar datos", back) { DataSourcesScreen(vm) }
            legacy(PulseRoute.backup, "Copia y restauración", back) { BackupSyncScreen() }
            legacy(PulseRoute.alarms, "Horario de sueño", back) { SmartAlarmScreen(vm) }
            composable(PulseRoute.coach) {
                PulseCoachScreen(vm, back) { action ->
                    nav.navigate(
                        when (action) {
                            CoachAction.Sleep -> PulseRoute.sleep
                            CoachAction.Recovery -> PulseRoute.recovery
                            CoachAction.Effort -> PulseRoute.effort
                            CoachAction.Stress -> PulseRoute.stress
                            CoachAction.Age -> PulseRoute.age
                            CoachAction.Alarms -> PulseRoute.alarms
                            CoachAction.Live -> PulseRoute.live
                            CoachAction.Strap -> PulseRoute.strap
                            CoachAction.Import -> PulseRoute.dataImport
                            CoachAction.Backup -> PulseRoute.backup
                            CoachAction.Journal -> PulseRoute.journal
                            CoachAction.Calibration -> PulseRoute.calibration
                            CoachAction.Trends -> PulseRoute.trends
                            CoachAction.Profile -> PulseRoute.profile
                        },
                    ) { launchSingleTop = true }
                }
            }
            legacy(PulseRoute.live, "Actividad", back) { LiveScreen(vm, onManageDevices = { nav.navigate(PulseRoute.strap) }) }
            legacy(PulseRoute.trends, "Tendencias", back) { TrendsScreen(vm) }
            legacy(PulseRoute.monitor, "Monitor de salud", back) { HealthScreen(vm, onOpenSettings = { nav.navigate(PulseRoute.advanced) }) }
            composable(PulseRoute.stress) {
                PulseStressScreen(vm, back, onCoach = go(PulseRoute.coach), onBreathe = { relax -> nav.navigate(if (relax) PulseRoute.breatheRelax else PulseRoute.breatheActivate) }, onSettings = go(PulseRoute.notifications))
            }
            composable(PulseRoute.breatheRelax) { PulseBreatheScreen(true, back) }
            composable(PulseRoute.breatheActivate) { PulseBreatheScreen(false, back) }
            legacy(PulseRoute.sleepDetail, "Sueño", back) { SleepScreen(vm = vm, onOpenAlarms = { nav.navigate(PulseRoute.alarms) }) }
            legacy(PulseRoute.workouts, "Entrenamientos", back) { WorkoutsScreen(vm) }
            legacy(PulseRoute.journal, "Diario", back) { InsightsScreen(vm) }
            legacy(PulseRoute.notifications, "Notificaciones", back) { NotificationsSettingsScreen(vm) }
            legacy(PulseRoute.advanced, "Ajustes avanzados", back) {
                SettingsScreen(vm, onOpenBackupSync = { nav.navigate(PulseRoute.backup) })
            }
            legacy(PulseRoute.limitations, "Detalles técnicos", back) { NoopLimitationsScreen() }
            composable(PulseRoute.classic) { AppRoot(viewModel = vm) }
        }

        if (route in PulseRoute.tabs) {
            PulseBottomBar(
                current = route,
                onSelect = { r ->
                    nav.navigate(r) {
                        popUpTo(PulseRoute.home) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onCoach = go(PulseRoute.coach),
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

private fun settingsGroups(go: (String) -> () -> Unit): List<Pair<String?, List<SettingsItem>>> = listOf(
    "Preferencias" to listOf(
        SettingsItem("Perfil", onClick = go(PulseRoute.profile)),
        SettingsItem("Pulsera", onClick = go(PulseRoute.strap)),
        SettingsItem("Entrenamientos", onClick = go(PulseRoute.workouts)),
        SettingsItem("Diario", onClick = go(PulseRoute.journal)),
        SettingsItem("Horario de sueño y alarma", onClick = go(PulseRoute.alarms)),
        SettingsItem("Health Connect", onClick = go(PulseRoute.dataImport)),
        SettingsItem("Notificaciones", onClick = go(PulseRoute.notifications)),
        SettingsItem("Ciclo menstrual, idioma y unidades", onClick = go(PulseRoute.advanced)),
    ),
    "Tus datos" to listOf(
        SettingsItem("Importar datos", "Exportación de tu historial, Apple Health, Health Connect, nutrición (CSV)", go(PulseRoute.dataImport)),
        SettingsItem("Copia y restauración", "Guarda o recupera una copia completa de Pulse (.noopbak)", go(PulseRoute.backup)),
    ),
    "Más" to listOf(
        SettingsItem("Coach", "Sin inteligencia artificial, todo en tu teléfono", go(PulseRoute.coach)),
        SettingsItem("Privacidad", onClick = go(PulseRoute.privacy)),
        SettingsItem("Cómo funciona", onClick = go(PulseRoute.help)),
        SettingsItem("Configuración inicial", "Repite la bienvenida: perfil, pulsera y permisos", go(PulseRoute.welcome)),
        SettingsItem("Ajustes avanzados", "Todas las opciones del motor NOOP", go(PulseRoute.advanced)),
        SettingsItem("Acerca de Pulse", onClick = go(PulseRoute.about)),
    ),
)

private fun androidx.navigation.NavGraphBuilder.legacy(
    route: String,
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    composable(route) {
        Column(Modifier.fillMaxSize().background(PulseColors.base)) {
            PulseTopBar(title, onBack)
            Box(Modifier.fillMaxWidth().weight(1f)) { content() }
        }
    }
}

@Composable
private fun PulseBottomBar(current: String, onSelect: (String) -> Unit, onCoach: () -> Unit, modifier: Modifier = Modifier) {
    val barColor = Color(0xF0282E34)
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier.fillMaxWidth().navigationBarsPadding().padding(start = 12.dp, end = 10.dp, bottom = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            Modifier.weight(1f).height(66.dp).shadow(18.dp, shape).clip(shape).background(barColor).border(1.dp, PulseColors.hairline, shape),
        ) {
            BarItem("Inicio", Icons.Outlined.Home, current == PulseRoute.home, Modifier.weight(1f)) { onSelect(PulseRoute.home) }
            BarItem("Salud", Icons.Outlined.MonitorHeart, current == PulseRoute.health, Modifier.weight(1f)) { onSelect(PulseRoute.health) }
            BarItem("Comunidad", Icons.Outlined.Groups, current == PulseRoute.community, Modifier.weight(1f)) { onSelect(PulseRoute.community) }
            BarItem("Más", Icons.Outlined.Menu, current == PulseRoute.more, Modifier.weight(1f)) { onSelect(PulseRoute.more) }
        }
        Box(
            Modifier.size(66.dp).shadow(18.dp, shape).clip(shape).background(barColor).border(1.dp, PulseColors.hairline, shape).pulseClick(onCoach),
            contentAlignment = Alignment.Center,
        ) { CoachOrb(diameter = 40.dp) }
    }
}

@Composable
private fun BarItem(label: String, icon: ImageVector, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val tint = if (active) PulseColors.text else PulseColors.textSecondary
    Column(
        modifier.fillMaxHeight().pulseClick(onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = PulseText.body.copy(fontSize = 10.5.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium, lineHeight = 12.sp),
            color = tint,
        )
    }
}
