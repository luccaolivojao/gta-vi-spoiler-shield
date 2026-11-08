package br.com.spoilershield.block

import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import br.com.spoilershield.core.ProtectionLogic
import br.com.spoilershield.schedule.Notifications
import br.com.spoilershield.schedule.ShieldController
import br.com.spoilershield.shieldApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Serviço em primeiro plano ativo SOMENTE enquanto a proteção está valendo.
 * - Mantém o processo vivo (a notificação é obrigatória).
 * - Fallback ao serviço de acessibilidade: consulta o UsageStatsManager (acesso ao uso) para
 *   saber qual app está em primeiro plano. Para abrir a tela de bloqueio em segundo plano,
 *   precisa da permissão de Sobreposição (SYSTEM_ALERT_WINDOW).
 * - Encerra a si mesmo e o filtro DNS quando a proteção termina (teste de 5 min, encerramento).
 */
class ShieldMonitorService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val n = Notifications.shield(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(this, Notifications.ID_SHIELD, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(Notifications.ID_SHIELD, n)
        }
        if (job?.isActive != true) job = scope.launch { loop() }
        return START_STICKY
    }

    private suspend fun CoroutineScope.loop() {
        val usm = getSystemService(UsageStatsManager::class.java)
        var current: String? = null
        var cursor = System.currentTimeMillis() - 10 * 60_000L // olha 10 min atrás na 1ª vez
        while (isActive) {
            val now = System.currentTimeMillis()
            if (!ProtectionLogic.isEnforcing(shieldApp.repo.current, now)) {
                ShieldController.sync(this@ShieldMonitorService) // para tudo e agenda o que vier
                stopSelf()
                return
            }
            try {
                val events = usm.queryEvents(cursor, now)
                val e = UsageEvents.Event()
                while (events.hasNextEvent()) {
                    events.getNextEvent(e)
                    @Suppress("DEPRECATION")
                    when (e.eventType) {
                        UsageEvents.Event.MOVE_TO_FOREGROUND -> current = e.packageName
                        UsageEvents.Event.MOVE_TO_BACKGROUND -> if (e.packageName == current) current = null
                    }
                }
                cursor = now
                current?.let { pkg ->
                    if (BlockEnforcer.shouldBlock(this@ShieldMonitorService, pkg, now)) {
                        BlockEnforcer.block(this@ShieldMonitorService, pkg)
                    }
                }
            } catch (_: SecurityException) {
                // Sem "Acesso ao uso": o serviço de acessibilidade continua sendo o mecanismo principal.
            }
            delay(700)
        }
    }

    override fun onDestroy() {
        job?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        fun start(ctx: Context) {
            try {
                ctx.startForegroundService(Intent(ctx, ShieldMonitorService::class.java))
            } catch (_: Exception) {
                // Android 12+ pode negar início em segundo plano sem isenção; o watchdog tenta de novo.
            }
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, ShieldMonitorService::class.java))
        }
    }
}
