package br.com.spoilershield.block

import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.net.VpnService
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import br.com.spoilershield.schedule.ShieldScheduler

enum class Strength(val label: String) {
    NONE("SEM PROTEÇÃO DE APPS"),
    STRONG("PROTEÇÃO FORTE"),
}

data class PermissionItem(
    val id: String,
    val title: String,
    val why: String,
    val granted: Boolean,
    val required: Boolean,
)

data class PermissionReport(val items: List<PermissionItem>) {
    private fun ok(id: String) = items.firstOrNull { it.id == id }?.granted == true

    /** Bloqueio de apps é confiável com Acessibilidade OU (Acesso ao uso + Sobreposição). */
    val appBlocking: Boolean get() = ok("accessibility") || (ok("usage") && ok("overlay"))
    val strength: Strength get() = if (appBlocking) Strength.STRONG else Strength.NONE
    val missingRequired: List<PermissionItem> get() = items.filter { it.required && !it.granted }
}

object Permissions {

    fun accessibilityEnabled(ctx: Context): Boolean {
        val enabled = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val me = ComponentName(ctx, ShieldAccessibilityService::class.java).flattenToString()
        return enabled.split(':').any { it.equals(me, ignoreCase = true) }
    }

    fun usageAccess(ctx: Context): Boolean {
        val ops = ctx.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun overlay(ctx: Context) = Settings.canDrawOverlays(ctx)
    fun exactAlarm(ctx: Context) = ShieldScheduler.canScheduleExact(ctx)
    fun notifications(ctx: Context) = NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    fun batteryUnrestricted(ctx: Context) =
        ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)
    fun vpnAuthorized(ctx: Context) = try { VpnService.prepare(ctx) == null } catch (_: Exception) { false }

    fun report(ctx: Context) = PermissionReport(
        listOf(
            PermissionItem("accessibility", "Acessibilidade (detector de apps)",
                "Mecanismo mais confiável: detecta qual app abriu (só o nome do pacote). Em APK instalado por fora da Play Store, ative antes \"Permitir configurações restritas\" em Informações do app.",
                accessibilityEnabled(ctx), required = false),
            PermissionItem("usage", "Acesso ao uso",
                "Alternativa/reforço: permite saber qual app está em primeiro plano.",
                usageAccess(ctx), required = false),
            PermissionItem("overlay", "Exibir sobre outros apps",
                "Necessário para o modo de Acesso ao uso abrir a tela de proteção em segundo plano.",
                overlay(ctx), required = false),
            PermissionItem("alarm", "Alarmes exatos",
                "Inicia o bloqueio às 06h30 em ponto. Sem isso o início pode atrasar alguns minutos (o bloqueio ainda vale pelo relógio).",
                exactAlarm(ctx), required = true),
            PermissionItem("notifications", "Notificações",
                "Mostra o aviso fixo de proteção ativa e o resultado das publicações.",
                notifications(ctx), required = false),
            PermissionItem("battery", "Sem restrição de bateria",
                "Evita que o sistema encerre o monitor em segundo plano.",
                batteryUnrestricted(ctx), required = false),
            PermissionItem("vpn", "Filtro web (VPN local de DNS)",
                "Bloqueia sites das redes no navegador. Não envia tráfego a nenhum servidor.",
                vpnAuthorized(ctx), required = false),
        ),
    )
}
