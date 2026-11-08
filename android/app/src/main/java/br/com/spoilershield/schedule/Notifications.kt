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
    const val CH_PUBLISH = "publish"
    const val ID_SHIELD = 1001
    const val ID_PUBLISH_FG = 1002
    const val ID_PUBLISH_DONE = 1003

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_SHIELD, "Proteção anti-spoiler", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "Indica que o modo anti-spoiler está ativo." },
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_PUBLISH, "Publicações", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "Progresso e resultado das publicações do Estúdio Seguro." },
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

    fun publishProgress(ctx: Context, text: String): Notification =
        NotificationCompat.Builder(ctx, CH_PUBLISH)
            .setSmallIcon(R.drawable.ic_stat_shield)
            .setContentTitle("Estúdio Seguro")
            .setContentText(text)
            .setOngoing(true)
            .setProgress(0, 0, true)
            .build()

    fun publishDone(ctx: Context, summary: String) {
        val n = NotificationCompat.Builder(ctx, CH_PUBLISH)
            .setSmallIcon(R.drawable.ic_stat_shield)
            .setContentTitle("Estúdio Seguro")
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary))
            .setAutoCancel(true)
            .build()
        try {
            ctx.getSystemService(NotificationManager::class.java).notify(ID_PUBLISH_DONE, n)
        } catch (_: SecurityException) { /* sem permissão de notificação: o resultado fica no histórico do app */ }
    }
}
