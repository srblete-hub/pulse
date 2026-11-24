package com.noop.pulse

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.BluetoothDisabled
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Watch
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noop.ble.WhoopModel
import com.noop.data.PairedDeviceRow
import com.noop.ingest.HealthConnectImporter
import com.noop.ingest.WhoopCsvImporter
import com.noop.ui.AppViewModel
import com.noop.ui.ProfileStore
import com.noop.ui.rememberRequestScan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** WHOOP-only import: the WHOOP data export and Health Connect. */
@Composable
fun PulseImportScreen(vm: AppViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    fun run(block: suspend () -> com.noop.data.ImportSummary) {
        busy = true
        status = "Importando…"
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { block() } }
            busy = false
            status = r.fold(
                { s -> if (s.counts.values.sum() > 0) "Importación completada: ${s.counts.values.sum()} registros${s.firstDay?.let { f -> " (del $f al ${s.lastDay})" } ?: ""}." else s.message },
                { "No se ha podido importar: ${it.message ?: "error desconocido"}" },
            )
        }
    }
    val whoopLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) run { WhoopCsvImporter.importZip(context, uri, vm.repo) }
    }
    val hcCategories = remember { HealthConnectImporter.selectedCategories(context) }
    val hcLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        if (granted.any { it in HealthConnectImporter.permissionsFor(hcCategories) }) {
            run { HealthConnectImporter.import(context, vm.repo, ProfileStore.from(context).heightCm) }
        } else status = "Sin permiso de Health Connect."
    }
    val hcAvailable = remember { HealthConnectImporter.sdkStatus(context) == HealthConnectClient.SDK_AVAILABLE }

    Column(Modifier.fillMaxSize().background(PulseColors.screenBrush)) {
        PulseTopBar("Importar datos", onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = PulseMetrics.gutter).navigationBarsPadding()) {
            Text("Trae tu historial de WHOOP para que Pulse tenga tu línea base desde el primer día.", style = PulseText.bodyLg, color = PulseColors.textSecondary, modifier = Modifier.padding(top = 8.dp, bottom = 16.dp))
            SourceCard(
                Icons.Outlined.FolderZip, "Exportación de WHOOP",
                "En la app de WHOOP: Más → Ajustes de la app → Exportar mis datos. Te llega un .zip por correo; ábrelo aquí tal cual.",
                "ELEGIR ARCHIVO", enabled = !busy,
            ) { whoopLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "text/csv", "text/comma-separated-values", "*/*")) }
            Spacer(Modifier.height(12.dp))
            SourceCard(
                Icons.Outlined.FavoriteBorder, "Health Connect",
                if (hcAvailable) "Importa peso, composición corporal y entrenos guardados en Health Connect." else "Health Connect no está disponible en este teléfono.",
                "CONECTAR", enabled = hcAvailable && !busy,
            ) {
                HealthConnectImporter.markPermissionsAsked(context, hcCategories)
                hcLauncher.launch(HealthConnectImporter.permissionsFor(hcCategories))
            }
            status?.let {
                Spacer(Modifier.height(16.dp))
                Text(it, style = PulseText.body, color = if (busy) PulseColors.accentHover else PulseColors.text)
            }
            Spacer(Modifier.height(16.dp))
            Text("Todo se guarda solo en este teléfono.", style = PulseText.body.copy(fontSize = PulseText.chartAxis.fontSize), color = PulseColors.textTertiary)
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SourceCard(icon: ImageVector, title: String, body: String, cta: String, enabled: Boolean, onClick: () -> Unit) {
    PulseCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = PulseColors.accent, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Text(title, style = PulseText.cardTitleLg, color = PulseColors.text)
        }
        Spacer(Modifier.height(8.dp))
        Text(body, style = PulseText.body, color = PulseColors.textSecondary)
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(10.dp))
                .border(1.5.dp, if (enabled) PulseColors.text else PulseColors.hairlineStrong, RoundedCornerShape(10.dp))
                .pulseClick { if (enabled) onClick() },
            contentAlignment = Alignment.Center,
        ) { Text(cta, style = PulseText.overlineLg, color = if (enabled) PulseColors.text else PulseColors.textTertiary) }
    }
}

/** "AVANZADO" tab of the strap page: WHOOP model, connection controls and saved WHOOP straps. */
@Composable
fun StrapAdvancedPanel(vm: AppViewModel) {
    val live by vm.live.collectAsStateWithLifecycle()
    val model by vm.selectedModel.collectAsStateWithLifecycle()
    val activeId by vm.activeStrapIdFlow.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var straps by remember { mutableStateOf<List<PairedDeviceRow>>(emptyList()) }
    var reload by remember { mutableStateOf(0) }
    var confirmForget by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(reload) {
        straps = runCatching { vm.pairedDevices() }.getOrDefault(emptyList()).filter { it.brand.contains("whoop", ignoreCase = true) }
    }
    val scan = rememberRequestScan { vm.connect() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 16.dp).navigationBarsPadding()) {
        Text("MODELO", style = PulseText.overline, color = PulseColors.textSecondary)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(PulseColors.base).padding(4.dp)) {
            WhoopModel.entries.forEach { m ->
                Box(
                    Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(8.dp)).background(if (m == model) PulseColors.overlay else Color.Transparent)
                        .pulseClick { if (m != model) { vm.setSelectedModel(m); vm.disconnect(); scan() } },
                    contentAlignment = Alignment.Center,
                ) { Text(if (m == WhoopModel.WHOOP4) "4.0" else "5.0 / MG", style = PulseText.overlineLg, color = PulseColors.text) }
            }
        }
        Spacer(Modifier.height(20.dp))
        Text("CONEXIÓN", style = PulseText.overline, color = PulseColors.textSecondary)
        Spacer(Modifier.height(8.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(PulseColors.overlay)) {
            ActionRow(Icons.Outlined.Bluetooth, if (live.connected) "Reconectar" else "Conectar") { scan() }
            Box(Modifier.padding(start = 16.dp).fillMaxWidth().height(1.dp).background(PulseColors.hairline))
            ActionRow(Icons.Outlined.Search, "Emparejar otra pulsera WHOOP") { vm.disconnect(); scan() }
            Box(Modifier.padding(start = 16.dp).fillMaxWidth().height(1.dp).background(PulseColors.hairline))
            ActionRow(Icons.Outlined.BluetoothDisabled, "Desconectar") { vm.disconnect() }
        }
        Spacer(Modifier.height(20.dp))
        Text("MIS PULSERAS", style = PulseText.overline, color = PulseColors.textSecondary)
        Spacer(Modifier.height(8.dp))
        if (straps.isEmpty()) {
            Text("Todavía no hay pulseras guardadas.", style = PulseText.body, color = PulseColors.textSecondary)
        }
        straps.forEach { d ->
            val isActive = d.id == activeId
            Row(
                Modifier.padding(bottom = 8.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(PulseColors.overlay).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Watch, null, tint = if (isActive) PulseColors.accent else PulseColors.textSecondary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(d.nickname ?: "WHOOP ${d.model}", style = PulseText.cardTitle, color = PulseColors.text)
                    Text(if (isActive) "En uso" else "Guardada", style = PulseText.body.copy(fontSize = PulseText.chartAxis.fontSize), color = if (isActive) PulseColors.accent else PulseColors.textSecondary)
                }
                if (!isActive) {
                    if (confirmForget == d.id) {
                        Text("¿OLVIDAR Y BORRAR SUS DATOS?", style = PulseText.overline, color = PulseColors.recoveryRed, modifier = Modifier.pulseClick {
                            scope.launch { runCatching { vm.forgetPairedDevice(d.id) }; confirmForget = null; reload++ }
                        })
                    } else {
                        Icon(Icons.Outlined.Delete, "Olvidar", tint = PulseColors.textSecondary, modifier = Modifier.size(20.dp).pulseClick { confirmForget = d.id })
                    }
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun ActionRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().pulseClick(onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = PulseColors.textQuiet, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, style = PulseText.cardTitle.copy(fontWeight = FontWeight.Medium), color = PulseColors.text)
    }
}
