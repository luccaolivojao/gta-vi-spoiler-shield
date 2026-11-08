package br.com.spoilershield.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.spoilershield.ui.theme.Neon

/** Texto exatamente como especificado. Estático: nenhuma informação externa é exibida. */
@Composable
fun BlockOverlayScreen(preview: Boolean, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF1A0006), Neon.Black)))
            .verticalScroll(rememberScrollState())
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ShieldMark(size = 110.dp)
        Spacer(Modifier.height(22.dp))
        Text(
            "🛑 CUIDADO! ÁREA COM RISCO DE SPOILERS DO GTA VI!",
            color = Neon.Red, fontSize = 24.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, lineHeight = 30.sp,
        )
        Spacer(Modifier.height(18.dp))
        Text("Seu modo anti-spoiler está ativado.", color = Neon.Text, fontSize = 17.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(
            "Você chegou até aqui sem spoilers. Não estrague a surpresa!",
            color = Neon.Text, fontSize = 17.sp, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text("🔒 Acesso temporariamente bloqueado.", color = Neon.Cyan, fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))
        NeonButton("VOLTAR À SEGURANÇA", onClick = onBack, modifier = Modifier.fillMaxWidth().height(56.dp))
        if (preview) {
            Spacer(Modifier.height(14.dp))
            Muted("(pré-visualização — esta tela aparece quando você abre uma rede bloqueada)")
        }
    }
}
