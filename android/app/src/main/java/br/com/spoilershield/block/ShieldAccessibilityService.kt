package br.com.spoilershield.block

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import br.com.spoilershield.ui.screens.BlockOverlayScreen
import br.com.spoilershield.ui.theme.ShieldTheme

/**
 * Mecanismo mais confiável de bloqueio. Usa SOMENTE o nome do pacote da janela que abriu
 * (canRetrieveWindowContent=false): não lê textos, mensagens, senhas nem o conteúdo da tela.
 *
 * A tela vermelha é desenhada como janela de acessibilidade (TYPE_ACCESSIBILITY_OVERLAY), que
 * não depende da permissão de Sobreposição nem da regra de "atividade em segundo plano".
 *
 * Limitações reais (veja docs/LIMITACOES.md): no Android 13+ apps instalados por APK exigem
 * "Permitir configurações restritas"; e no Android 17, com o Modo de Proteção Avançada ligado,
 * o sistema pode impedir serviços de acessibilidade que não sejam ferramentas de acessibilidade.
 */
class ShieldAccessibilityService : AccessibilityService() {

    private var overlay: ComposeView? = null
    private var owner: OverlayOwner? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (BlockEnforcer.shouldBlock(this, pkg)) {
            if (!showOverlay()) BlockEnforcer.block(this, pkg) // plano B: atividade
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    private fun showOverlay(): Boolean {
        if (overlay != null) return true
        return try {
            val o = OverlayOwner().also { it.start() }
            val view = ComposeView(this).apply {
                setViewTreeLifecycleOwner(o)
                setViewTreeSavedStateRegistryOwner(o)
                setViewTreeViewModelStoreOwner(o)
                setContent {
                    ShieldTheme { BlockOverlayScreen(preview = false, onBack = { hideOverlay(goHome = true) }) }
                }
            }
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.OPAQUE,
            )
            (getSystemService(WINDOW_SERVICE) as WindowManager).addView(view, lp)
            overlay = view; owner = o
            true
        } catch (e: Exception) {
            android.util.Log.w("SpoilerShield", "Overlay falhou: ${e.message}")
            overlay = null; owner = null
            false
        }
    }

    private fun hideOverlay(goHome: Boolean) {
        val v = overlay ?: return
        overlay = null
        try { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(v) } catch (_: Exception) {}
        owner?.stop(); owner = null
        if (goHome) performGlobalAction(GLOBAL_ACTION_HOME)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        hideOverlay(goHome = false)
        super.onDestroy()
    }
}

private class OverlayOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val registry = LifecycleRegistry(this)
    private val ssc = SavedStateRegistryController.create(this)
    override val viewModelStore = ViewModelStore()
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = ssc.savedStateRegistry

    fun start() {
        ssc.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun stop() {
        registry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
    }
}
