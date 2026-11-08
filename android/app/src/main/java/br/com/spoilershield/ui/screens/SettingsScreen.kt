package br.com.spoilershield.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.spoilershield.block.BlockActivity
import br.com.spoilershield.core.Phase
import br.com.spoilershield.core.ProtectionLogic
import br.com.spoilershield.ui.ShieldViewModel
import br.com.spoilershield.ui.theme.Neon
import br.com.spoilershield.ui.theme.NeonCard

@Composable
fun SettingsScreen(vm: ShieldViewModel) {
    val s by vm.state.collectAsState()
    val perm by vm.perm.collectAsState()
    val ctx = LocalContext.current
    val phase = ProtectionLogic.phase(s, rememberNow())
    var confirmEnd by remember { mutableStateOf(false) }
    var showReport by remember { mutableStateOf(false) }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {

        NeonCard(Modifier.fillMaxWidth(), glow = Neon.Cyan) {
            SectionTitle("TESTES")
            Muted("Antes do dia 18, confirme que tudo funciona sem esperar.")
            Spacer(Modifier.height(8.dp))
            GhostButton("TESTAR BLOQUEIO POR 5 MINUTOS", onClick = vm::startTest, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            GhostButton("VERIFICAR PERMISSÕES", onClick = { vm.refreshPermissions(); showReport = true }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            GhostButton("PRÉ-VISUALIZAR TELA DE BLOQUEIO", onClick = { BlockActivity.preview(ctx) }, modifier = Modifier.fillMaxWidth())
        }

        NeonCard(Modifier.fillMaxWidth()) {
            SectionTitle("ENCERRAR MODO ANTI-SPOILER")
            Muted("A decisão é sua. Ao encerrar, os bloqueios, o monitor e o filtro web são desligados na hora.")
            Spacer(Modifier.height(10.dp))
            when (phase) {
                Phase.ACTIVE, Phase.TESTING ->
                    NeonButton("ENCERRAR MODO ANTI-SPOILER", onClick = { confirmEnd = true }, modifier = Modifier.fillMaxWidth())
                Phase.ENDED ->
                    NeonButton("REATIVAR PROTEÇÃO", onClick = vm::rearm, modifier = Modifier.fillMaxWidth(), color = Neon.RedDeep)
                Phase.WAITING -> Muted("A proteção ainda não começou. Nada a encerrar.")
            }
        }

        NeonCard(Modifier.fillMaxWidth(), glow = Neon.Muted) {
            SectionTitle("PRIVACIDADE")
            Muted(
                "• Não lê mensagens privadas nem o conteúdo das telas.\n" +
                    "• Não acessa vídeos, feeds, comentários ou tendências de terceiros.\n" +
                    "• Não busca notícias sobre GTA VI nem usa trailers/imagens do jogo.\n" +
                    "• Não pede nem guarda senhas das redes.\n" +
                    "• Não faz login em nenhuma rede; as configurações ficam só neste aparelho.\n" +
                    "• Mensagens de erro são neutras e não exibem conteúdo externo.",
            )
        }

        NeonCard(Modifier.fillMaxWidth(), glow = Neon.Amber) {
            SectionTitle("LIMITES HONESTOS")
            Muted(
                "Este bloqueio é uma barreira de proteção pessoal, não uma trava impossível de contornar. " +
                    "Você pode desativá-lo, mudar o relógio do celular, desinstalar o app ou revogar permissões. " +
                    "DNS privado/DoH no navegador contorna o filtro web. " +
                    "Notificações das redes só deixam de aparecer se o app delas for bloqueado/silenciado no sistema — " +
                    "este app não lê notificações. Detalhes em docs/LIMITACOES.md.",
            )
        }
    }

    if (confirmEnd) {
        AlertDialog(
            onDismissRequest = { confirmEnd = false },
            title = { Text("Encerrar o modo anti-spoiler?") },
            text = { Text("As redes serão liberadas imediatamente. Você pode reativar depois.") },
            confirmButton = { TextButton({ confirmEnd = false; vm.endMode() }) { Text("ENCERRAR", color = Neon.Red, fontWeight = FontWeight.Black) } },
            dismissButton = { TextButton({ confirmEnd = false }) { Text("Continuar protegido") } },
        )
    }
    if (showReport) {
        AlertDialog(
            onDismissRequest = { showReport = false },
            title = { Text(perm.strength.label) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    perm.items.forEach {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatusDot(it.granted); Text(it.title, modifier = Modifier.weight(1f))
                            Text(if (it.granted) "OK" else "FALTA", color = if (it.granted) Neon.Green else Neon.Amber, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (perm.appBlocking) "Bloqueio de apps autorizado. Faça o teste de 5 minutos para confirmar na prática."
                        else "Falta autorizar um mecanismo de bloqueio de apps (Acessibilidade, ou Acesso ao uso + Sobreposição).",
                    )
                }
            },
            confirmButton = { TextButton({ showReport = false }) { Text("OK") } },
        )
    }
}
