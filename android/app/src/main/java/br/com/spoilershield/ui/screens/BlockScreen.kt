package br.com.spoilershield.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.spoilershield.block.PermissionItem
import br.com.spoilershield.core.ShieldDefaults
import br.com.spoilershield.core.SocialApp
import br.com.spoilershield.ui.ShieldViewModel
import br.com.spoilershield.ui.theme.Neon
import br.com.spoilershield.ui.theme.NeonCard
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm", Locale("pt", "BR"))

@Composable
fun BlockScreen(vm: ShieldViewModel) {
    val s by vm.state.collectAsState()
    val perm by vm.perm.collectAsState()
    val ctx = LocalContext.current

    val vpnLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { vm.refreshPermissions() }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refreshPermissions() }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        NeonCard(Modifier.fillMaxWidth()) {
            SectionTitle("AGENDAMENTO")
            val start = ZonedDateTime.ofInstant(Instant.ofEpochMilli(s.startAtMs), ShieldDefaults.ZONE)
            Text("Início: ${fmt.format(start)}", color = Neon.Text, fontWeight = FontWeight.Bold)
            Muted("Fuso: America/Sao_Paulo. O instante é absoluto: mudar o fuso do celular não desloca o início. Duração: até você encerrar.")
            Spacer(Modifier.height(8.dp))
            ScheduleEditor(current = s.startAtMs, onPick = vm::setStart)
        }

        NeonCard(Modifier.fillMaxWidth()) {
            SectionTitle("REDES BLOQUEADAS")
            SocialApp.entries.forEach { app ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(app.label, color = Neon.Text, modifier = Modifier.weight(1f))
                    Switch(
                        checked = app.id in s.enabledApps, onCheckedChange = { vm.setApp(app.id, it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = Neon.Red),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Proteger também sites no navegador", color = Neon.Text)
                    Muted("Filtro DNS local (VPN oficial do Android). Não envia dados a servidor algum.")
                }
                Switch(checked = s.webFilter, onCheckedChange = vm::setWebFilter, colors = SwitchDefaults.colors(checkedTrackColor = Neon.Red))
            }
        }

        NeonCard(Modifier.fillMaxWidth(), glow = Neon.Cyan) {
            SectionTitle("PERMISSÕES")
            Muted("Você controla cada uma. Nada é ativado sem a sua autorização no sistema.")
            Spacer(Modifier.height(8.dp))
            perm.items.forEach { item ->
                PermissionRow(item) {
                    when (item.id) {
                        "accessibility" -> ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        "usage" -> ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                        "overlay" -> ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")))
                        "alarm" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                            ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}")))
                        "notifications" -> if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
                        "battery" -> ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")))
                        "vpn" -> VpnService.prepare(ctx)?.let { vpnLauncher.launch(it) } ?: vm.refreshPermissions()
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            GhostButton("ABRIR INFORMAÇÕES DO APP (configurações restritas)", onClick = { openAppInfo(ctx) }, modifier = Modifier.fillMaxWidth())
            Muted("Android 13+: se a Acessibilidade aparecer em cinza, abra Informações do app → ⋮ → \"Permitir configurações restritas\" e tente de novo.", Modifier.padding(top = 6.dp))
        }
    }
}

private fun openAppInfo(ctx: Context) =
    ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")))

@Composable
private fun PermissionRow(item: PermissionItem, onFix: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusDot(item.granted)
            Text(item.title, color = Neon.Text, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (!item.granted) TextButton(onClick = onFix) { Text("AJUSTAR", color = Neon.Cyan, fontWeight = FontWeight.Black) }
            else Text("OK", color = Neon.Green, fontWeight = FontWeight.Black)
        }
        Muted(item.why)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleEditor(current: Long, onPick: (Long) -> Unit) {
    var step by remember { mutableStateOf(0) } // 0 fechado, 1 data, 2 hora
    var pickedDate by remember { mutableStateOf<LocalDate?>(null) }
    val cur = ZonedDateTime.ofInstant(Instant.ofEpochMilli(current), ShieldDefaults.ZONE)

    GhostButton("ALTERAR DATA E HORA", onClick = { step = 1 }, modifier = Modifier.fillMaxWidth())

    if (step == 1) {
        val st = rememberDatePickerState(initialSelectedDateMillis = cur.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { step = 0 },
            confirmButton = {
                TextButton({
                    st.selectedDateMillis?.let { pickedDate = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate(); step = 2 }
                }) { Text("PRÓXIMO") }
            },
            dismissButton = { TextButton({ step = 0 }) { Text("Cancelar") } },
        ) { DatePicker(state = st) }
    }
    if (step == 2) {
        val ts = rememberTimePickerState(initialHour = cur.hour, initialMinute = cur.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { step = 0 },
            text = { TimePicker(state = ts) },
            confirmButton = {
                TextButton({
                    val d = pickedDate
                    if (d != null) onPick(ZonedDateTime.of(d, LocalTime.of(ts.hour, ts.minute), ShieldDefaults.ZONE).toInstant().toEpochMilli())
                    step = 0
                }) { Text("SALVAR", color = Neon.Red, fontWeight = FontWeight.Black) }
            },
            dismissButton = { TextButton({ step = 0 }) { Text("Cancelar") } },
        )
    }
}
