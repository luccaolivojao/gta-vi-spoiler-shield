package br.com.spoilershield.block

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * Mecanismo mais confiável de bloqueio. Usa SOMENTE o nome do pacote da janela que abriu
 * (canRetrieveWindowContent=false): não lê textos, mensagens, senhas nem o conteúdo da tela.
 *
 * Limitações reais (veja docs/LIMITACOES.md): no Android 13+ apps instalados por APK exigem
 * "Permitir configurações restritas"; e no Android 17, com o Modo de Proteção Avançada ligado,
 * o sistema pode impedir serviços de acessibilidade que não sejam ferramentas de acessibilidade.
 */
class ShieldAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (BlockEnforcer.shouldBlock(this, pkg)) {
            performGlobalAction(GLOBAL_ACTION_HOME)
            BlockEnforcer.block(this, pkg)
        }
    }

    override fun onInterrupt() = Unit
}
