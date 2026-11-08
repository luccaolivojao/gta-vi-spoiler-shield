package br.com.spoilershield.block

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import br.com.spoilershield.ui.screens.BlockOverlayScreen
import br.com.spoilershield.ui.theme.ShieldTheme

/**
 * Tela exibida ao tentar abrir uma rede bloqueada. Mensagem estática: não mostra o nome do app,
 * notificações, thumbnails nem qualquer conteúdo externo.
 */
class BlockActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // "Voltar" também leva à tela inicial, para nunca devolver o usuário ao app bloqueado.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goHome()
        })
        val preview = intent.getBooleanExtra(EXTRA_PREVIEW, false)
        setContent {
            ShieldTheme { BlockOverlayScreen(preview = preview, onBack = ::goHome) }
        }
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        finishAndRemoveTask()
    }

    companion object {
        const val EXTRA_PREVIEW = "preview"
        fun preview(ctx: Context) =
            ctx.startActivity(Intent(ctx, BlockActivity::class.java).putExtra(EXTRA_PREVIEW, true))
    }
}
