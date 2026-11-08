package br.com.spoilershield.block

import android.content.Context
import android.content.Intent
import br.com.spoilershield.core.ProtectionLogic
import br.com.spoilershield.core.SocialApp
import br.com.spoilershield.shieldApp

/** Decisão compartilhada pelo serviço de acessibilidade e pelo monitor de uso. */
object BlockEnforcer {
    private var lastPkg = ""
    private var lastAt = 0L

    fun shouldBlock(ctx: Context, pkg: String, now: Long = System.currentTimeMillis()): Boolean {
        if (pkg == ctx.packageName) return false
        val s = ctx.shieldApp.repo.current
        if (!ProtectionLogic.isEnforcing(s, now)) return false
        return pkg in SocialApp.packagesFor(s.enabledApps)
    }

    /** Mostra a tela de bloqueio. Limita a repetição para não entrar em laço. */
    fun block(ctx: Context, pkg: String) {
        val now = System.currentTimeMillis()
        if (pkg == lastPkg && now - lastAt < 1200) return
        lastPkg = pkg; lastAt = now
        try {
            ctx.startActivity(
                Intent(ctx, BlockActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION),
            )
        } catch (_: Exception) {
            // Sem permissão para abrir telas em segundo plano (falta Sobreposição). O monitor
            // continuará tentando; a tela de Permissões mostra o que falta.
        }
    }
}
