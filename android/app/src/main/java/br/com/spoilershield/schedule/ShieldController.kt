package br.com.spoilershield.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import br.com.spoilershield.block.DnsShieldVpnService
import br.com.spoilershield.block.ShieldMonitorService
import br.com.spoilershield.core.ProtectionLogic
import br.com.spoilershield.shieldApp
import java.util.concurrent.TimeUnit

/**
 * Ponto único que "põe o sistema no estado certo": agenda o alarme do próximo marco,
 * garante o watchdog e liga/desliga monitor e filtro DNS conforme [ProtectionLogic].
 * É idempotente: pode ser chamado a qualquer momento (boot, alarme, abertura do app...).
 *
 * Importante: o bloqueio NÃO depende do alarme ter disparado na hora exata. O serviço de
 * acessibilidade e o monitor sempre consultam o relógio real; o alarme só acorda o processo.
 */
object ShieldController {

    fun sync(ctx: Context) {
        val app = ctx.shieldApp
        val s = app.repo.current
        val now = System.currentTimeMillis()

        ShieldScheduler.scheduleBoundary(ctx, ProtectionLogic.nextBoundaryMs(s, now))
        ShieldScheduler.ensureWatchdog(ctx)

        if (ProtectionLogic.isEnforcing(s, now)) {
            ShieldMonitorService.start(ctx)
            val vpnReady = try { VpnService.prepare(ctx) == null } catch (_: Exception) { false }
            if (s.webFilter && vpnReady) DnsShieldVpnService.start(ctx) else DnsShieldVpnService.stop(ctx)
        } else {
            ShieldMonitorService.stop(ctx)
            DnsShieldVpnService.stop(ctx)
        }
    }
}

object ShieldScheduler {
    const val ACTION_ALARM = "br.com.spoilershield.ALARM"
    private const val REQ = 4401
    private const val WATCHDOG = "spoilershield_watchdog"

    private fun pending(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
        ctx, REQ,
        Intent(ctx, ShieldReceiver::class.java).setAction(ACTION_ALARM),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun canScheduleExact(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    fun scheduleBoundary(ctx: Context, atMs: Long?) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = pending(ctx)
        am.cancel(pi)
        if (atMs == null) return
        if (canScheduleExact(ctx)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pi) // sem permissão: pode atrasar alguns minutos
    }

    /** Reforço periódico (mín. 15 min do WorkManager) caso o sistema tenha matado serviços. */
    fun ensureWatchdog(ctx: Context) {
        val req = PeriodicWorkRequestBuilder<WatchdogWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.NONE).build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(WATCHDOG, ExistingPeriodicWorkPolicy.KEEP, req)
    }
}

class ShieldReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Alarme, boot, atualização do app, mudança de relógio/fuso: tudo resolve em "sincronizar".
        ShieldController.sync(context)
    }
}

class WatchdogWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        ShieldController.sync(applicationContext)
        return Result.success()
    }
}
