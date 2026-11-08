package br.com.spoilershield.schedule

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import br.com.spoilershield.R

/** Todas as notificações do app têm texto fixo e neutro. Nada de conteúdo externo. */
object Notifications {
    const val CH_SHIELD = "shield"
    const val ID_SHIELD = 1001

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_SHIELD, "Proteção anti-spoiler", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "Indica que o modo anti-spoiler está ativo." },
        )
    }

    fun shield(ctx: Context): Notification =
        NotificationCompat.Builder(ctx, CH_SHIELD)
            .setSmallIcon(R.drawable.ic_stat_shield)
            .setContentTitle("🛡️ Modo anti-spoiler ativado")
            .setContentText("Redes bloqueadas. Publique pelo Estúdio Seguro.")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
}
