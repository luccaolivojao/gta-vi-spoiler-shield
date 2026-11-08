package br.com.spoilershield.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.spoilershield.core.Capabilities
import br.com.spoilershield.core.MediaKind
import br.com.spoilershield.core.Platform
import br.com.spoilershield.core.PostFormat
import br.com.spoilershield.core.PostText
import br.com.spoilershield.core.Support
import br.com.spoilershield.studio.MediaFile
import br.com.spoilershield.studio.OutcomeState
import br.com.spoilershield.studio.PostRequest
import br.com.spoilershield.studio.QueuedPost
import br.com.spoilershield.studio.TikTokOptions
import br.com.spoilershield.ui.ShieldViewModel
import br.com.spoilershield.ui.StudioViewModel
import br.com.spoilershield.ui.theme.Neon
import br.com.spoilershield.ui.theme.NeonCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

private val shortFmt = DateTimeFormatter.ofPattern("dd/MM HH:mm", Locale("pt", "BR"))

private fun privacyLabel(p: String) = when (p) {
    "PUBLIC_TO_EVERYONE" -> "Todos"
    "MUTUAL_FOLLOW_FRIENDS" -> "Amigos (seguidores mútuos)"
    "FOLLOWER_OF_CREATOR" -> "Seguidores"
    "SELF_ONLY" -> "Somente eu"
    else -> p
}

@Composable
fun StudioScreen(studio: StudioViewModel, shield: ShieldViewModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val acc by studio.acc.collectAsState()
    val queue by studio.queue.collectAsState()
    val message by studio.message.collectAsState()
    val creator by studio.creator.collectAsState()
    val state by shield.state.collectAsState()

    // ----- rascunho -----
    var media by remember { mutableStateOf<MediaFile?>(null) }
    var publicUrl by remember { mutableStateOf("") }
    var ttSel by remember { mutableStateOf(false) }
    var igSel by remember { mutableStateOf(false) }
    var format by remember { mutableStateOf(PostFormat.REEL) }
    var caption by remember { mutableStateOf("") }
    var tagsRaw by remember { mutableStateOf("") }
    var coverLive by remember { mutableLongStateOf(0L) }
    var coverCommitted by remember { mutableLongStateOf(0L) }
    var ttPrivacy by remember { mutableStateOf<String?>(null) }
    var allowComment by remember { mutableStateOf(false) }
    var allowDuet by remember { mutableStateOf(false) }
    var allowStitch by remember { mutableStateOf(false) }
    var scheduleOn by remember { mutableStateOf(false) }
    var scheduledAt by remember { mutableLongStateOf(System.currentTimeMillis() + 60 * 60_000L) }
    var credsFor by remember { mutableStateOf<Platform?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) scope.launch {
            val m = studio.importMedia(uri)
            if (m == null) {
                studio.postMessage("Não foi possível usar esse arquivo. Escolha um vídeo ou foto.")
            } else {
                studio.discardMedia(media); media = m; coverLive = 0; coverCommitted = 0
                if (m.kind == MediaKind.IMAGE && format == PostFormat.REEL) format = PostFormat.PHOTO
                if (m.kind == MediaKind.VIDEO && format == PostFormat.PHOTO) format = PostFormat.REEL
            }
        }
    }

    val urlOk = publicUrl.trim().startsWith("https://")
    val kind: MediaKind? = media?.kind ?: if (urlOk) MediaKind.IMAGE else null
    val platforms = listOfNotNull(Platform.TIKTOK.takeIf { ttSel }, Platform.INSTAGRAM.takeIf { igSel })
    val supports = platforms.associateWith { Capabilities.check(it, format, kind ?: MediaKind.VIDEO, urlOk) }
    val tags = PostText.normalizeHashtags(tagsRaw)
    val textErrors = platforms.mapNotNull { PostText.validate(it, caption, tags) }
    val connectedOk = platforms.all { if (it == Platform.TIKTOK) acc.ttConnected else acc.igConnected }
    val ttPrivacyOk = !ttSel || !state.tiktokAudited || ttPrivacy != null
    val canPublish = kind != null && platforms.isNotEmpty() && supports.values.all { it.allowed } &&
        textErrors.isEmpty() && connectedOk && ttPrivacyOk

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("📤 ESTÚDIO SEGURO", color = Neon.Red, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
        Muted("Publica pelas APIs oficiais, sem abrir TikTok/Instagram e sem carregar feed, comentários, tendências ou vídeos de terceiros.")

        message?.let {
            NeonCard(Modifier.fillMaxWidth(), glow = Neon.Cyan) {
                Text(it, color = Neon.Text)
                TextButton(onClick = studio::consumeMessage) { Text("OK", color = Neon.Cyan, fontWeight = FontWeight.Black) }
            }
        }

        // ---------------- CONTAS ----------------
        NeonCard(Modifier.fillMaxWidth(), glow = Neon.Cyan) {
            SectionTitle("CONTAS")
            AccountRow(
                "TikTok", connected = acc.ttConnected,
                detail = if (acc.ttConnected) "Conectado${acc.ttName?.let { " como $it" } ?: ""}" else if (acc.ttCreds) "Credenciais salvas • não conectado" else "Credenciais do app ausentes",
                onConfig = { credsFor = Platform.TIKTOK },
                onConnect = { studio.beginOAuth(Platform.TIKTOK)?.let { openUrl(ctx, it) } },
                onDisconnect = { studio.disconnect(Platform.TIKTOK) },
            )
            Spacer(Modifier.height(8.dp))
            AccountRow(
                "Instagram", connected = acc.igConnected,
                detail = if (acc.igConnected) "@${acc.igUser} • ${acc.igType}" else if (acc.igCreds) "Credenciais salvas • não conectado" else "Credenciais do app ausentes",
                onConfig = { credsFor = Platform.INSTAGRAM },
                onConnect = { studio.beginOAuth(Platform.INSTAGRAM)?.let { openUrl(ctx, it) } },
                onDisconnect = { studio.disconnect(Platform.INSTAGRAM) },
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Meu app TikTok já foi auditado", color = Neon.Text)
                    Muted("Sem auditoria, o TikTok força toda publicação a ser PRIVADA. Marque só se a auditoria foi aprovada.")
                }
                Switch(checked = state.tiktokAudited, onCheckedChange = shield::setTikTokAudited, colors = SwitchDefaults.colors(checkedTrackColor = Neon.Red))
            }
            Spacer(Modifier.height(6.dp))
            Muted("Conecte as contas ANTES do dia 18: a tela de login é do site da rede e pode ser barrada pelo filtro web.")
        }

        // ---------------- NOVA PUBLICAÇÃO ----------------
        NeonCard(Modifier.fillMaxWidth()) {
            SectionTitle("NOVA PUBLICAÇÃO")

            GhostButton("ESCOLHER VÍDEO OU FOTO DA GALERIA", modifier = Modifier.fillMaxWidth(), onClick = {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
            })
            media?.let { m ->
                Spacer(Modifier.height(8.dp))
                MediaPreview(m, coverCommitted)
                Muted("${m.kind.name.lowercase()} • ${m.sizeBytes / 1024 / 1024} MB" + (m.durationMs?.let { " • ${it / 1000}s" } ?: ""))
                if (m.kind == MediaKind.VIDEO && (m.durationMs ?: 0) > 1000) {
                    Text("Capa: ${coverLive / 1000.0}s", color = Neon.Text)
                    Slider(
                        value = coverLive.toFloat(), onValueChange = { coverLive = it.toLong() },
                        onValueChangeFinished = { coverCommitted = coverLive },
                        valueRange = 0f..((m.durationMs ?: 1000L) - 500).coerceAtLeast(500).toFloat(),
                    )
                    Muted("Capa escolhida por posição do próprio vídeo (frame).")
                }
            }

            Spacer(Modifier.height(10.dp))
            Text("Onde publicar", color = Neon.Text, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = ttSel, onClick = { ttSel = !ttSel }, label = { Text("TikTok") })
                FilterChip(selected = igSel, onClick = { igSel = !igSel }, label = { Text("Instagram") })
            }
            Spacer(Modifier.height(6.dp))
            Text("Formato", color = Neon.Text, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PostFormat.entries.forEach { f -> FilterChip(selected = format == f, onClick = { format = f }, label = { Text(f.label) }) }
            }

            if (kind == MediaKind.IMAGE || media == null) {
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = publicUrl, onValueChange = { publicUrl = it }, singleLine = true,
                    label = { Text("URL pública da imagem JPEG (só para foto no Instagram)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri),
                )
                Muted("A API do Instagram não aceita upload de foto: precisa de um link https público para um JPEG que você controla.")
            }

            supports.forEach { (p, s) ->
                when (s) {
                    is Support.No -> Text("✖ ${p.label}: ${s.reason}", color = Neon.Red, modifier = Modifier.padding(top = 6.dp))
                    is Support.Conditional -> Text("ℹ ${p.label}: ${s.note}", color = Neon.Amber, modifier = Modifier.padding(top = 6.dp))
                    Support.Yes -> Unit
                }
            }

            Spacer(Modifier.height(10.dp))
            val capEnabled = !(format == PostFormat.STORY)
            OutlinedTextField(
                value = caption, onValueChange = { caption = it }, label = { Text(if (capEnabled) "Legenda" else "Legenda (Stories não têm legenda)") },
                modifier = Modifier.fillMaxWidth(), minLines = 2, enabled = capEnabled,
            )
            OutlinedTextField(
                value = tagsRaw, onValueChange = { tagsRaw = it }, label = { Text("Hashtags (separe por espaço)") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
            )
            textErrors.forEach { Text(it, color = Neon.Red, modifier = Modifier.padding(top = 4.dp)) }

            if (ttSel) {
                Spacer(Modifier.height(10.dp))
                Text("TikTok — configurações da publicação", color = Neon.Text, fontWeight = FontWeight.Bold)
                if (!state.tiktokAudited) {
                    Text("Visibilidade: SOMENTE EU (forçado pelo TikTok enquanto o app não for auditado)", color = Neon.Amber)
                } else {
                    PrivacyPicker(ttPrivacy, creator?.privacyOptions.orEmpty(), onLoad = studio::loadCreatorInfo) { ttPrivacy = it }
                }
                SwitchRow("Permitir comentários", allowComment) { allowComment = it }
                SwitchRow("Permitir Dueto", allowDuet) { allowDuet = it }
                SwitchRow("Permitir Stitch", allowStitch) { allowStitch = it }
                Muted("Ao publicar, você concorda com a Confirmação de Uso de Música do TikTok.")
            }

            Spacer(Modifier.height(10.dp))
            SwitchRow("Agendar publicação", scheduleOn) { scheduleOn = it }
            if (scheduleOn) {
                Text("Para: ${shortFmt.format(Instant.ofEpochMilli(scheduledAt).atZone(ZoneId.of("America/Sao_Paulo")))}", color = Neon.Text)
                ScheduleEditor(current = scheduledAt, onPick = { scheduledAt = it })
                Muted("O app envia na hora marcada (o aparelho precisa estar ligado e com internet; pode haver atraso de alguns minutos). As redes não têm agendamento nativo via API.")
            }

            Spacer(Modifier.height(12.dp))
            if (!connectedOk && platforms.isNotEmpty()) Text("Conecte as contas selecionadas acima.", color = Neon.Amber)
            if (ttSel && state.tiktokAudited && ttPrivacy == null) Text("Escolha a visibilidade do TikTok.", color = Neon.Amber)
            NeonButton(
                if (scheduleOn) "AGENDAR PUBLICAÇÃO" else "PUBLICAR AGORA",
                enabled = canPublish, modifier = Modifier.fillMaxWidth().height(54.dp),
                onClick = {
                    val req = PostRequest(
                        id = UUID.randomUUID().toString(), platforms = platforms.toSet(), format = format,
                        media = media, publicImageUrl = publicUrl.trim().takeIf { urlOk },
                        caption = if (format == PostFormat.STORY) "" else caption, hashtags = if (format == PostFormat.STORY) emptyList() else tags,
                        coverOffsetMs = coverCommitted, scheduledAtMs = scheduledAt.takeIf { scheduleOn },
                        tiktok = TikTokOptions(ttPrivacy, allowComment, allowDuet, allowStitch),
                        createdAtMs = System.currentTimeMillis(),
                    )
                    studio.submit(req)
                    media = null; caption = ""; tagsRaw = ""; publicUrl = ""; scheduleOn = false; coverLive = 0; coverCommitted = 0
                },
            )
        }

        // ---------------- HISTÓRICO ----------------
        NeonCard(Modifier.fillMaxWidth(), glow = Neon.Muted) {
            SectionTitle("FILA E HISTÓRICO")
            if (queue.isEmpty()) Muted("Nenhuma publicação ainda.")
            queue.forEach { q -> QueueItem(q, studio) }
        }
    }

    credsFor?.let { p -> CredsDialog(p, studio) { credsFor = null } }
}

private fun openUrl(ctx: android.content.Context, url: String) =
    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Neon.Text, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = Neon.Red))
    }
}

@Composable
private fun AccountRow(name: String, connected: Boolean, detail: String, onConfig: () -> Unit, onConnect: () -> Unit, onDisconnect: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusDot(connected)
            Column(Modifier.weight(1f)) {
                Text(name, color = Neon.Text, fontWeight = FontWeight.Bold)
                Muted(detail)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onConfig) { Text("CREDENCIAIS", color = Neon.Cyan) }
            if (connected) TextButton(onClick = onDisconnect) { Text("DESCONECTAR", color = Neon.Red) }
            else TextButton(onClick = onConnect) { Text("CONECTAR", color = Neon.Green, fontWeight = FontWeight.Black) }
        }
    }
}

@Composable
private fun PrivacyPicker(selected: String?, options: List<String>, onLoad: () -> Unit, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (options.isEmpty()) onLoad() }
    Box {
        GhostButton(selected?.let { "Visibilidade: ${privacyLabel(it)}" } ?: "ESCOLHER VISIBILIDADE", onClick = { open = true }, modifier = Modifier.fillMaxWidth())
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (options.isEmpty()) DropdownMenuItem(text = { Text("Carregando opções do TikTok…") }, onClick = { onLoad(); open = false })
            options.forEach { o -> DropdownMenuItem(text = { Text(privacyLabel(o)) }, onClick = { onSelect(o); open = false }) }
        }
    }
}

/** Pré-visualização da PRÓPRIA mídia escolhida (nunca de conteúdo externo). */
@Composable
private fun MediaPreview(m: MediaFile, offsetMs: Long) {
    val bmp by produceState<Bitmap?>(null, m.path, offsetMs) {
        value = withContext(Dispatchers.IO) {
            try {
                if (m.kind == MediaKind.IMAGE) {
                    BitmapFactory.decodeFile(m.path, BitmapFactory.Options().apply { inSampleSize = 4 })
                } else {
                    val r = MediaMetadataRetriever()
                    try { r.setDataSource(m.path); r.getFrameAtTime(offsetMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) } finally { r.release() }
                }
            } catch (_: Exception) { null }
        }
    }
    bmp?.let {
        Image(it.asImageBitmap(), contentDescription = "Prévia da sua mídia", modifier = Modifier.fillMaxWidth().height(180.dp), contentScale = ContentScale.Fit)
    }
}

@Composable
private fun QueueItem(q: QueuedPost, studio: StudioViewModel) {
    val r = q.request
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        val whenText = (r.scheduledAtMs ?: r.createdAtMs).let { shortFmt.format(Instant.ofEpochMilli(it).atZone(ZoneId.of("America/Sao_Paulo"))) }
        Text("${r.format.label} • ${if (r.scheduledAtMs != null) "agendado p/ " else ""}$whenText", color = Neon.Text, fontWeight = FontWeight.Bold)
        r.platforms.sortedBy { it.ordinal }.forEach { p ->
            val o = q.outcomes[p]
            val c = when (o?.state) {
                OutcomeState.PUBLISHED -> Neon.Green
                OutcomeState.PUBLISHED_PRIVATE, OutcomeState.PROCESSING, OutcomeState.QUEUED, OutcomeState.SENDING -> Neon.Amber
                else -> Neon.Red
            }
            Text("${p.label}: ${o?.state?.label ?: "—"}", color = c, fontWeight = FontWeight.Bold)
            if (!o?.detail.isNullOrBlank()) Muted(o!!.detail)
        }
        Row {
            if (q.outcomes.values.any { it.state == OutcomeState.FAILED }) TextButton({ studio.retry(r.id) }) { Text("TENTAR DE NOVO", color = Neon.Cyan) }
            if (q.outcomes.values.any { it.state == OutcomeState.PROCESSING }) TextButton({ studio.refreshStatus(r.id) }) { Text("ATUALIZAR STATUS", color = Neon.Cyan) }
            TextButton({ studio.delete(r.id) }) { Text("REMOVER", color = Neon.Muted) }
        }
    }
}

@Composable
private fun CredsDialog(p: Platform, studio: StudioViewModel, onClose: () -> Unit) {
    val (savedId, savedRedirect) = remember(p) { studio.currentCreds(p) }
    var id by remember { mutableStateOf(savedId) }
    var secret by remember { mutableStateOf("") }
    var redirect by remember { mutableStateOf(savedRedirect) }
    val idLabel = if (p == Platform.TIKTOK) "Client Key" else "App ID do Instagram"
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Credenciais — ${p.label}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Muted("Use as credenciais do SEU app criado no portal de desenvolvedores. Ficam cifradas no Android Keystore e nunca saem do aparelho. Veja docs/CONFIGURAR_APIS.md.")
                OutlinedTextField(id, { id = it }, label = { Text(idLabel) }, singleLine = true)
                OutlinedTextField(secret, { secret = it }, label = { Text("Secret (deixe vazio para manter)") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                OutlinedTextField(redirect, { redirect = it }, label = { Text("URL https de redirecionamento") }, singleLine = true)
            }
        },
        confirmButton = { TextButton({ studio.saveCreds(p, id, secret, redirect); onClose() }) { Text("SALVAR", color = Neon.Red, fontWeight = FontWeight.Black) } },
        dismissButton = { TextButton(onClose) { Text("Cancelar") } },
    )
}
