package br.com.spoilershield.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import br.com.spoilershield.core.ShieldState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking

private val Context.shieldStore by preferencesDataStore(name = "shield_state")

/** Persistência do estado da proteção em DataStore (arquivo local; nada sai do aparelho). */
class ShieldRepository(context: Context, scope: CoroutineScope) {
    private val store = context.applicationContext.shieldStore

    private object K {
        val start = longPreferencesKey("start_at_ms")
        val manual = longPreferencesKey("manual_start_ms")
        val test = longPreferencesKey("test_until_ms")
        val ended = longPreferencesKey("ended_at_ms")
        val apps = stringSetPreferencesKey("enabled_apps")
        val web = booleanPreferencesKey("web_filter")
    }

    private val _state = MutableStateFlow(runBlocking { store.data.first().toState() })
    val state: StateFlow<ShieldState> = _state

    /** Leitura síncrona para serviços (sempre atualizada). */
    val current: ShieldState get() = _state.value

    init {
        store.data.map { it.toState() }.onEach { _state.value = it }.launchIn(scope)
    }

    suspend fun update(transform: (ShieldState) -> ShieldState) {
        val next = transform(_state.value)
        _state.value = next // visível imediatamente para os serviços
        store.edit { it.write(next) }
    }

    private fun Preferences.toState(): ShieldState {
        val d = ShieldState()
        return ShieldState(
            startAtMs = this[K.start] ?: d.startAtMs,
            manualStartMs = this[K.manual],
            testUntilMs = this[K.test],
            endedAtMs = this[K.ended],
            enabledApps = this[K.apps] ?: d.enabledApps,
            webFilter = this[K.web] ?: d.webFilter,
        )
    }

    private fun androidx.datastore.preferences.core.MutablePreferences.write(s: ShieldState) {
        this[K.start] = s.startAtMs
        if (s.manualStartMs != null) this[K.manual] = s.manualStartMs else remove(K.manual)
        if (s.testUntilMs != null) this[K.test] = s.testUntilMs else remove(K.test)
        if (s.endedAtMs != null) this[K.ended] = s.endedAtMs else remove(K.ended)
        this[K.apps] = s.enabledApps
        this[K.web] = s.webFilter
    }
}
