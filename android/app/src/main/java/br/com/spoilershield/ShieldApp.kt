package br.com.spoilershield

import android.app.Application
import android.content.Context
import br.com.spoilershield.data.ShieldRepository
import br.com.spoilershield.schedule.Notifications
import br.com.spoilershield.schedule.ShieldController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class ShieldApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var repo: ShieldRepository
        private set

    override fun onCreate() {
        super.onCreate()
        // O repositório lê o estado de forma síncrona uma única vez para que serviços acordados
        // pelo sistema (alarme, boot, acessibilidade) nunca enxerguem um estado "em branco".
        repo = ShieldRepository(this, appScope)
        Notifications.createChannels(this)
        ShieldController.sync(this)
    }
}

val Context.shieldApp: ShieldApp get() = applicationContext as ShieldApp
