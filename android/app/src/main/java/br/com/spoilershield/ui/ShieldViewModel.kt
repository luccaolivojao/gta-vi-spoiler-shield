package br.com.spoilershield.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.com.spoilershield.block.PermissionReport
import br.com.spoilershield.block.Permissions
import br.com.spoilershield.core.ProtectionLogic
import br.com.spoilershield.core.ShieldState
import br.com.spoilershield.schedule.ShieldController
import br.com.spoilershield.shieldApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ShieldViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = app.shieldApp.repo
    val state: StateFlow<ShieldState> = repo.state

    private val _perm = MutableStateFlow(Permissions.report(app))
    val perm: StateFlow<PermissionReport> = _perm.asStateFlow()

    fun refreshPermissions() {
        _perm.value = Permissions.report(getApplication())
        ShieldController.sync(getApplication()) // ex.: o usuário acabou de conceder a VPN/alarme
    }

    private fun mutate(f: (ShieldState) -> ShieldState) = viewModelScope.launch {
        repo.update(f)
        ShieldController.sync(getApplication())
    }

    fun panic() = mutate { ProtectionLogic.panic(it, System.currentTimeMillis()) }
    fun startTest() = mutate { ProtectionLogic.startTest(it, System.currentTimeMillis()) }
    fun endMode() = mutate { ProtectionLogic.end(it, System.currentTimeMillis()) }
    fun rearm() = mutate { ProtectionLogic.rearm(it, System.currentTimeMillis()) }
    fun setStart(ms: Long) = mutate { ProtectionLogic.reschedule(it, ms) }

    fun setApp(id: String, on: Boolean) = mutate {
        it.copy(enabledApps = if (on) it.enabledApps + id else it.enabledApps - id)
    }

    fun setWebFilter(on: Boolean) = mutate { it.copy(webFilter = on) }
}
