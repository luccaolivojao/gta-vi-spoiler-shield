package br.com.spoilershield.studio

import android.content.Context
import br.com.spoilershield.core.Capabilities
import br.com.spoilershield.core.MediaKind
import br.com.spoilershield.core.Platform
import br.com.spoilershield.core.PostFormat
import br.com.spoilershield.core.PostText
import br.com.spoilershield.core.Support
import br.com.spoilershield.data.SecureStore
import br.com.spoilershield.shieldApp
import java.io.File
import java.io.IOException

/**
 * Orquestra a publicação. Regra de ouro: NUNCA devolver "Publicado" sem confirmação da API.
 * Quando a plataforma ainda processa, o estado é PROCESSING; quando não há suporte oficial,
 * NOT_SUPPORTED com o motivo; erros viram FAILED com mensagem neutra.
 */
class Publisher(
    private val ctx: Context,
    private val tiktok: TikTokClient = TikTokClient(Http.client),
    private val instagram: InstagramClient = InstagramClient(Http.client),
) {
    private val accounts = AccountStore(SecureStore(ctx))
    private val queue = PostQueueStore(ctx)

    fun run(postId: String, onStage: (String) -> Unit = {}) {
        var post = queue.get(postId) ?: return
        for (platform in post.request.platforms.sortedBy { it.ordinal }) {
            if (post.outcomes[platform]?.state?.terminal == true) continue
            onStage("Enviando para ${platform.label}…")
            post = post.with(PlatformOutcome(platform, OutcomeState.SENDING)); queue.put(post)
            val outcome = runOne(post.request, platform)
            post = post.with(outcome); queue.put(post)
        }
        // A cópia local da mídia só é apagada quando nada mais depende dela.
        if (post.allTerminal && post.outcomes.values.none { it.state == OutcomeState.FAILED || it.state == OutcomeState.PROCESSING }) {
            post.request.media?.let { File(it.path).delete() }
        }
    }

    fun runOne(req: PostRequest, platform: Platform): PlatformOutcome {
        val kind = req.media?.kind ?: if (!req.publicImageUrl.isNullOrBlank()) MediaKind.IMAGE else MediaKind.VIDEO
        val support = Capabilities.check(platform, req.format, kind, !req.publicImageUrl.isNullOrBlank())
        if (support is Support.No) return PlatformOutcome(platform, OutcomeState.NOT_SUPPORTED, support.reason)
        PostText.validate(platform, req.caption, req.hashtags)?.let { return PlatformOutcome(platform, OutcomeState.FAILED, it) }
        return try {
            when (platform) {
                Platform.TIKTOK -> publishTikTok(req)
                Platform.INSTAGRAM -> publishInstagram(req)
            }
        } catch (e: ApiException) {
            PlatformOutcome(platform, OutcomeState.FAILED, Friendly.message(platform, e))
        } catch (e: IOException) {
            PlatformOutcome(platform, OutcomeState.FAILED, "Sem conexão ou conexão interrompida. Nada foi confirmado; toque em \"Tentar de novo\".")
        } catch (e: Exception) {
            PlatformOutcome(platform, OutcomeState.FAILED, "Erro inesperado ao publicar (${e.javaClass.simpleName}).")
        }
    }

    // ---------------- TikTok ----------------

    private fun publishTikTok(req: PostRequest): PlatformOutcome {
        val media = req.media ?: return PlatformOutcome(Platform.TIKTOK, OutcomeState.FAILED, "Escolha um vídeo.")
        val creds = accounts.tiktokCreds() ?: return needsSetup(Platform.TIKTOK)
        var acc = accounts.tiktok() ?: return needsConnect(Platform.TIKTOK)

        val now = System.currentTimeMillis()
        if (acc.tokens.expiresAtMs - now < 5 * 60_000) {
            if (acc.tokens.refreshExpiresAtMs <= now) return needsConnect(Platform.TIKTOK)
            acc = acc.copy(tokens = tiktok.refresh(creds.clientKey, creds.clientSecret, acc.tokens.refreshToken))
            accounts.setTikTok(acc)
        }
        val token = acc.tokens.accessToken

        val info = tiktok.creatorInfo(token)
        val audited = ctx.shieldApp.repo.current.tiktokAudited
        // App não auditado: o TikTok só aceita SELF_ONLY. Pedimos exatamente isso, sem fingir outra coisa.
        val privacy = if (audited) req.tiktok.privacy else "SELF_ONLY"
        if (privacy == null || privacy !in info.privacyOptions) {
            return PlatformOutcome(Platform.TIKTOK, OutcomeState.FAILED, "A opção de privacidade escolhida não está disponível para esta conta TikTok.")
        }
        val maxMs = info.maxDurationSec * 1000L
        if (maxMs > 0 && (media.durationMs ?: 0) > maxMs) {
            return PlatformOutcome(Platform.TIKTOK, OutcomeState.FAILED, "O vídeo passa do limite desta conta (${info.maxDurationSec}s).")
        }

        val result = tiktok.publishVideo(
            token, File(media.path), media.mime,
            TikTokClient.PostInfo(
                title = PostText.compose(req.caption, req.hashtags),
                privacyLevel = privacy,
                disableComment = info.commentDisabled || !req.tiktok.allowComment,
                disableDuet = info.duetDisabled || !req.tiktok.allowDuet,
                disableStitch = info.stitchDisabled || !req.tiktok.allowStitch,
                coverTimestampMs = req.coverOffsetMs,
            ),
        )
        return tiktokOutcome(result, privacy, audited)
    }

    fun tiktokOutcome(r: TikTokClient.PublishStatus, privacy: String, audited: Boolean): PlatformOutcome = when {
        r.complete && privacy == "SELF_ONLY" -> PlatformOutcome(
            Platform.TIKTOK, OutcomeState.PUBLISHED_PRIVATE,
            if (audited) "Publicado com visibilidade \"somente eu\"."
            else "Publicado como PRIVADO: o app ainda não passou na auditoria do TikTok, que mantém tudo em modo privado.",
            r.publishId)
        r.complete -> PlatformOutcome(Platform.TIKTOK, OutcomeState.PUBLISHED, "Publicado.", r.publishId)
        r.failed -> PlatformOutcome(Platform.TIKTOK, OutcomeState.FAILED, Friendly.tiktokFail(r.failReason), r.publishId)
        else -> PlatformOutcome(Platform.TIKTOK, OutcomeState.PROCESSING,
            "O TikTok ainda está processando (${r.status}). Toque em \"Atualizar status\" depois; não é sucesso confirmado.", r.publishId)
    }

    /** Reconsulta o status de uma publicação TikTok que ficou em processamento. */
    fun refreshTikTok(postId: String) {
        val post = queue.get(postId) ?: return
        val o = post.outcomes[Platform.TIKTOK]?.takeIf { it.state == OutcomeState.PROCESSING && it.remoteId != null } ?: return
        val acc = accounts.tiktok() ?: return
        val updated = try {
            val privacy = if (ctx.shieldApp.repo.current.tiktokAudited) post.request.tiktok.privacy ?: "SELF_ONLY" else "SELF_ONLY"
            tiktokOutcome(tiktok.fetchStatus(acc.tokens.accessToken, o.remoteId!!), privacy, ctx.shieldApp.repo.current.tiktokAudited)
        } catch (e: Exception) { o.copy(detail = "Não foi possível consultar o status agora.") }
        queue.put(post.with(updated))
    }

    // ---------------- Instagram ----------------

    private fun publishInstagram(req: PostRequest): PlatformOutcome {
        val creds = accounts.instagramCreds() ?: return needsSetup(Platform.INSTAGRAM)
        var acc = accounts.instagram() ?: return needsConnect(Platform.INSTAGRAM)
        val now = System.currentTimeMillis()
        if (acc.expiresAtMs <= now) return needsConnect(Platform.INSTAGRAM)
        if (acc.expiresAtMs - now < 10L * 86_400_000) { // renova quando faltam < 10 dias
            runCatching { instagram.refresh(acc.token) }.getOrNull()?.let {
                acc = acc.copy(token = it.accessToken, expiresAtMs = it.expiresAtMs); accounts.setInstagram(acc)
            }
        }
        if (acc.accountType != "BUSINESS" && acc.accountType != "MEDIA_CREATOR") {
            return PlatformOutcome(Platform.INSTAGRAM, OutcomeState.NOT_SUPPORTED,
                "Esta conta não é Profissional (Criador/Comercial). A API oficial não publica em contas pessoais.")
        }
        instagram.quota(acc.token, acc.userId)?.let { (used, total) ->
            if (used >= total) return PlatformOutcome(Platform.INSTAGRAM, OutcomeState.FAILED, "Limite de publicações via API nas últimas 24h atingido ($used/$total).")
        }

        val caption = PostText.compose(req.caption, req.hashtags)
        val media = req.media
        val published = when {
            media != null && media.kind == MediaKind.VIDEO -> {
                val kind = if (req.format == PostFormat.STORY) InstagramClient.Kind.STORIES else InstagramClient.Kind.REELS
                instagram.publishVideo(acc.token, acc.userId, kind, File(media.path), caption, req.coverOffsetMs)
            }
            !req.publicImageUrl.isNullOrBlank() -> {
                val kind = if (req.format == PostFormat.STORY) InstagramClient.Kind.STORIES else InstagramClient.Kind.IMAGE
                instagram.publishImage(acc.token, acc.userId, kind, req.publicImageUrl, caption)
            }
            else -> return PlatformOutcome(Platform.INSTAGRAM, OutcomeState.FAILED, "Escolha um vídeo ou informe a URL pública da imagem.")
        }
        return PlatformOutcome(Platform.INSTAGRAM, OutcomeState.PUBLISHED, "Publicado e confirmado pelo Instagram.", published.mediaId, published.permalink)
    }

    private fun needsSetup(p: Platform) = PlatformOutcome(p, OutcomeState.FAILED,
        "Faltam as credenciais do app de desenvolvedor (${p.label}). Configure em ESTÚDIO → Contas.")
    private fun needsConnect(p: Platform) = PlatformOutcome(p, OutcomeState.FAILED,
        "Conta ${p.label} não conectada ou sessão expirada. Reconecte em ESTÚDIO → Contas. Se a tela de login não abrir durante o bloqueio, desligue temporariamente o filtro web em BLOQUEIO e religue depois.")
}

/** Mensagens neutras e úteis para erros conhecidos das APIs. Nunca expõem conteúdo externo. */
object Friendly {
    fun message(p: Platform, e: ApiException): String = when {
        e.code == "unaudited_client_can_only_post_to_private_accounts" ->
            "O app TikTok ainda não foi auditado e só pode publicar em conta privada. Deixe a conta TikTok privada temporariamente ou conclua a auditoria."
        e.code == "scope_not_authorized" || e.code == "access_token_invalid" ->
            "O TikTok não autorizou esta ação (escopo video.publish não aprovado ou sessão expirada). Reconecte a conta."
        e.code == "spam_risk_too_many_posts" -> "Limite diário de publicações via API do TikTok atingido."
        e.code == "reached_active_user_cap" -> "Limite diário de usuários ativos do app TikTok atingido."
        e.code == "rate_limit_exceeded" || e.http == 429 -> "Muitas requisições. Aguarde um minuto e tente de novo."
        e.code == "url_ownership_unverified" -> "O TikTok exige domínio verificado para este tipo de mídia."
        e.code == "privacy_level_option_mismatch" -> "A privacidade escolhida não é permitida para esta conta."
        e.code.startsWith("container_") -> e.message ?: "Mídia recusada pelo Instagram."
        e.http == 401 || e.code.startsWith("190") -> "Sessão ${p.label} expirada. Reconecte a conta."
        else -> "${p.label}: ${e.message ?: "erro ao publicar"} (${e.code})"
    }

    fun tiktokFail(reason: String?) = when (reason) {
        null, "" -> "O TikTok recusou a publicação."
        else -> "O TikTok recusou a publicação ($reason)."
    }
}
