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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.spoilershield.core.CountdownFormat
import br.com.spoilershield.core.Phase
import br.com.spoilershield.core.ProtectionLogic
import br.com.spoilershield.ui.ShieldViewModel
import br.com.spoilershield.ui.theme.Neon
import br.com.spoilershield.ui.theme.NeonCard
import kotlinx.coroutines.delay
import java.time.Duration

@Composable
fun rememberNow(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    return now
}

@Composable
fun HomeScreen(vm: ShieldViewModel, goToBlock: () -> Unit) {
    val s by vm.state.collectAsState()
    val perm by vm.perm.collectAsState()
    val now = rememberNow()
    val phase = ProtectionLogic.phase(s, now)
    var confirmPanic by remember { mutableStateOf(false) }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("GTA VI SPOILER SHIELD", color = Neon.Red, fontWeight = FontWeight.Black, fontSize = 15.sp, letterSpacing = 3.sp)
        Spacer(Modifier.height(14.dp))
        ShieldMark(size = 120.dp, color = if (phase == Phase.ACTIVE) Neon.Green else Neon.Red)
        Spacer(Modifier.height(14.dp))

        NeonCard(Modifier.fillMaxWidth(), glow = if (phase == Phase.ACTIVE) Neon.Green else Neon.Red) {
            when (phase) {
                Phase.WAITING -> {
                    val remaining = Duration.ofMillis(s.startAtMs - now)
                    val p = CountdownFormat.parts(remaining)
                    Text(
                        CountdownFormat.countdownSentence(remaining),
                        color = Neon.Text, fontWeight = FontWeight.Bold, fontSize = 16.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        DigitTile(p.days, "DIAS"); DigitTile(p.hours, "HORAS")
                        DigitTile(p.minutes, "MIN"); DigitTile(p.seconds, "SEG")
                    }
                }
                Phase.ACTIVE -> {
                    Text("🛡️ MODO ANTI-SPOILER ATIVADO", color = Neon.Green, fontWeight = FontWeight.Black, fontSize = 19.sp,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    val since = ProtectionLogic.protectedSinceMs(s, now) ?: now
                    Text(CountdownFormat.protectedSentence(Duration.ofMillis(now - since)), color = Neon.Text,
                        fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
                Phase.TESTING -> {
                    val left = Duration.ofMillis((s.testUntilMs ?: now) - now)
                    val p = CountdownFormat.parts(left)
                    Text("🧪 TESTE DE BLOQUEIO ATIVO", color = Neon.Amber, fontWeight = FontWeight.Black, fontSize = 18.sp,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text("Termina em %02d:%02d — tente abrir uma rede bloqueada.".format(p.minutes, p.seconds),
                        color = Neon.Text, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
                Phase.ENDED -> {
                    Text("MODO ANTI-SPOILER ENCERRADO", color = Neon.Muted, fontWeight = FontWeight.Black, fontSize = 18.sp,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    Muted("Você decidiu encerrar a proteção. Reative em Configurações.", Modifier.fillMaxWidth())
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        NeonCard(Modifier.fillMaxWidth(), glow = Neon.Cyan) {
            SectionTitle("SITUAÇÃO DA PROTEÇÃO")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusDot(perm.appBlocking)
                Text(perm.strength.label, color = Neon.Text, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(4.dp))
            Muted(
                if (perm.appBlocking) "Redes selecionadas: ${s.enabledApps.size}. Filtro web: ${if (s.webFilter) "ligado" else "desligado"}."
                else "Nenhum mecanismo de bloqueio de apps está autorizado ainda. Ative a Acessibilidade (ou Acesso ao uso + Sobreposição) na aba BLOQUEIO.",
            )
            if (perm.missingRequired.isNotEmpty() || !perm.appBlocking) {
                Spacer(Modifier.height(8.dp))
                GhostButton("AJUSTAR PERMISSÕES", onClick = goToBlock)
            }
        }

        if (phase != Phase.ACTIVE) {
            Spacer(Modifier.height(18.dp))
            NeonButton("ATIVAR BLOQUEIO TOTAL AGORA", onClick = { confirmPanic = true }, modifier = Modifier.fillMaxWidth().height(60.dp))
            Spacer(Modifier.height(8.dp))
            Muted("Inicia imediatamente todas as restrições disponíveis. Você encerra quando quiser em Configurações.")
        }
        if (phase == Phase.WAITING || phase == Phase.ENDED) {
            Spacer(Modifier.height(10.dp))
            GhostButton("TESTAR POR 5 MINUTOS", onClick = vm::startTest, modifier = Modifier.fillMaxWidth())
        }
    }

    if (confirmPanic) {
        AlertDialog(
            onDismissRequest = { confirmPanic = false },
            title = { Text("Ativar bloqueio total agora?") },
            text = { Text("As redes selecionadas serão bloqueadas imediatamente e continuarão assim até você encerrar o modo anti-spoiler em Configurações.") },
            confirmButton = { TextButton({ confirmPanic = false; vm.panic() }) { Text("ATIVAR", color = Neon.Red, fontWeight = FontWeight.Black) } },
            dismissButton = { TextButton({ confirmPanic = false }) { Text("Cancelar") } },
        )
    }
}
