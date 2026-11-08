package br.com.spoilershield.ui

import android.app.Application
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.com.spoilershield.core.MediaKind
import br.com.spoilershield.core.OAuthUrls
import br.com.spoilershield.core.Pkce
import br.com.spoilershield.core.Platform
import br.com.spoilershield.data.SecureStore
import br.com.spoilershield.studio.AccountStore
import br.com.spoilershield.studio.ApiException
import br.com.spoilershield.studio.Http
import br.com.spoilershield.studio.InstagramClient
import br.com.spoilershield.studio.MediaFile
import br.com.spoilershield.studio.OutcomeState
import br.com.spoilershield.studio.PlatformOutcome
import br.com.spoilershield.studio.PostQueueStore
import br.com.spoilershield.studio.PostRequest
import br.com.spoilershield.studio.PublishWorker
import br.com.spoilershield.studio.Publisher
import br.com.spoilershield.studio.QueuedPost
import br.com.spoilershield.studio.TikTokClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID

data class AccountsUi(
    val ttCreds: Boolean = false, val ttConnected: Boolean = false, val ttName: String? = null,
    val igCreds: Boolean = false, val igConnected: Boolean = false, val igUser: String? = null, val igType: String? = null,
)

class StudioViewModel(private val app: Application) : AndroidViewModel(app) {
    private val accounts = AccountStore(SecureStore(app))
    private val queueStore = PostQueueStore(app)
    private val tiktok = TikTokClient(Http.client)
    private val instagram = InstagramClient(Http.client)

    private val _acc = MutableStateFlow(readAccounts())
    val acc: StateFlow<AccountsUi> = _acc.asStateFlow()

    val queue: StateFlow<List<QueuedPost>> =
        queueStore.observe().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    fun consumeMessage() { _message.value = null }
    fun postMessage(msg: String) { _message.value = msg }

    private val _creator = MutableStateFlow<TikTokClient.CreatorInfo?>(null)
    val creator: StateFlow<TikTokClient.CreatorInfo?> = _creator.asStateFlow()

    private fun readAccounts(): AccountsUi {
        val tt = accounts.tiktok(); val ig = accounts.instagram()
        return AccountsUi(
            ttCreds = accounts.tiktokCreds() != null, ttConnected = tt != null, ttName = tt?.nickname,
            igCreds = accounts.instagramCreds() != null, igConnected = ig != null, igUser = ig?.username, igType = ig?.accountType,
        )
    }
    private fun refreshAccounts() { _acc.value = readAccounts() }

    // ---------------- credenciais e OAuth ----------------

    fun saveCreds(p: Platform, id: String, secret: String, redirect: String) {
        when (p) {
            Platform.TIKTOK -> {
                val keep = accounts.tiktokCreds()?.clientSecret.orEmpty()
                accounts.setTikTokCreds(AccountStore.TikTokCreds(id.trim(), secret.ifBlank { keep }.trim(), redirect.trim()))
            }
            Platform.INSTAGRAM -> {
                val keep = accounts.instagramCreds()?.appSecret.orEmpty()
                accounts.setInstagramCreds(AccountStore.InstagramCreds(id.trim(), secret.ifBlank { keep }.trim(), redirect.trim()))
            }
        }
        refreshAccounts()
    }

    fun currentCreds(p: Platform): Pair<String, String> = when (p) {
        Platform.TIKTOK -> accounts.tiktokCreds()?.let { it.clientKey to it.redirectUri }
        Platform.INSTAGRAM -> accounts.instagramCreds()?.let { it.appId to it.redirectUri }
    } ?: ("" to "")

    /** Prepara o OAuth e devolve a URL oficial de autorização (abre no navegador do sistema). */
    fun beginOAuth(p: Platform): String? {
        val state = Pkce.randomState()
        return when (p) {
            Platform.TIKTOK -> {
                val c = accounts.tiktokCreds()?.takeIf { it.clientKey.isNotBlank() && it.redirectUri.startsWith("https://") }
                    ?: return fail("Configure Client Key e a URL de redirecionamento (https) do TikTok primeiro.")
                val pkce = Pkce.generate()
                accounts.setPending(AccountStore.Pending("TIKTOK", state, pkce.verifier))
                OAuthUrls.tiktok(c.clientKey, c.redirectUri, state, pkce.challenge)
            }
            Platform.INSTAGRAM -> {
                val c = accounts.instagramCreds()?.takeIf { it.appId.isNotBlank() && it.redirectUri.startsWith("https://") }
                    ?: return fail("Configure App ID e a URL de redirecionamento (https) do Instagram primeiro.")
                accounts.setPending(AccountStore.Pending("INSTAGRAM", state, null))
                OAuthUrls.instagram(c.appId, c.redirectUri, state)
            }
        }
    }

    private fun fail(msg: String): String? { _message.value = msg; return null }

    /** Chamado quando a página de redirecionamento devolve spoilershield://oauth?code=...&state=... */
    fun handleRedirect(uri: Uri) {
        val pending = accounts.pending() ?: run { _message.value = "Nenhuma conexão em andamento."; return }
        if (uri.getQueryParameter("state") != pending.state) { _message.value = "Resposta de autorização inválida. Tente conectar de novo."; return }
        accounts.setPending(null)
        val code = uri.getQueryParameter("code")
        if (uri.getQueryParameter("error") != null || code.isNullOrBlank()) { _message.value = "Autorização cancelada ou negada."; return }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                when (pending.provider) {
                    "TIKTOK" -> connectTikTok(code, pending.verifier.orEmpty())
                    "INSTAGRAM" -> connectInstagram(code)
                }
                refreshAccounts()
            } catch (e: ApiException) {
                _message.value = "Não foi possível conectar: ${e.message}"
            } catch (e: IOException) {
                _message.value = "Sem conexão durante a autorização. Tente de novo."
            }
        }
    }

    private fun connectTikTok(code: String, verifier: String) {
        val c = accounts.tiktokCreds() ?: throw ApiException("no_creds", "Credenciais do TikTok ausentes.")
        val tokens = tiktok.exchangeCode(c.clientKey, c.clientSecret, code, c.redirectUri, verifier)
        val name = runCatching { tiktok.creatorInfo(tokens.accessToken).nickname }.getOrNull()
        accounts.setTikTok(AccountStore.TikTokAccount(tokens, name))
        _message.value = "TikTok conectado."
    }

    private fun connectInstagram(code: String) {
        val c = accounts.instagramCreds() ?: throw ApiException("no_creds", "Credenciais do Instagram ausentes.")
        val (short, _) = instagram.exchangeCode(c.appId, c.appSecret, code, c.redirectUri)
        val long = instagram.longLived(c.appSecret, short)
        val p = instagram.profile(long.accessToken)
        accounts.setInstagram(AccountStore.InstagramAccount(long.accessToken, long.expiresAtMs, p.userId, p.username, p.accountType))
        _message.value = if (p.isProfessional) "Instagram conectado (@${p.username})."
        else "Instagram conectado, mas a conta não é Profissional: a API oficial não publica em contas pessoais."
    }

    fun disconnect(p: Platform) {
        if (p == Platform.TIKTOK) accounts.setTikTok(null) else accounts.setInstagram(null)
        refreshAccounts()
    }

    /** Busca as opções de privacidade/limites do criador (exigido pelas regras de UX do TikTok). */
    fun loadCreatorInfo() {
        val tokens = accounts.tiktok()?.tokens ?: run { _message.value = "Conecte o TikTok primeiro."; return }
        viewModelScope.launch(Dispatchers.IO) {
            try { _creator.value = tiktok.creatorInfo(tokens.accessToken) }
            catch (e: ApiException) { _message.value = "TikTok: ${e.message}" }
            catch (e: IOException) { _message.value = "Sem conexão para consultar o TikTok." }
        }
    }

    // ---------------- mídia e publicação ----------------

    /** Copia a mídia ESCOLHIDA PELO USUÁRIO para o armazenamento privado (necessário para agendar). */
    suspend fun importMedia(uri: Uri): MediaFile? = withContext(Dispatchers.IO) {
        val cr = app.contentResolver
        val mime = cr.getType(uri) ?: return@withContext null
        val kind = when {
            mime.startsWith("video/") -> MediaKind.VIDEO
            mime.startsWith("image/") -> MediaKind.IMAGE
            else -> return@withContext null
        }
        val ext = when (mime) {
            "video/mp4" -> "mp4"; "video/quicktime" -> "mov"; "video/webm" -> "webm"
            "image/jpeg" -> "jpg"; "image/png" -> "png"; else -> "bin"
        }
        val dest = File(queueStore.mediaDir(), "${UUID.randomUUID()}.$ext")
        cr.openInputStream(uri)?.use { i -> dest.outputStream().use { o -> i.copyTo(o) } } ?: return@withContext null
        var duration: Long? = null
        if (kind == MediaKind.VIDEO) {
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(dest.path)
                duration = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            } catch (_: Exception) { } finally { r.release() }
        }
        MediaFile(dest.path, mime, dest.length(), kind, duration)
    }

    fun discardMedia(m: MediaFile?) { m?.let { File(it.path).delete() } }

    fun submit(req: PostRequest) {
        queueStore.put(QueuedPost(req, req.platforms.associateWith { PlatformOutcome(it, OutcomeState.QUEUED) }))
        PublishWorker.enqueue(app, req.id, req.scheduledAtMs)
        _message.value = if (req.scheduledAtMs != null) "Publicação agendada. O aparelho precisa estar ligado e com internet na hora."
        else "Publicação enviada. O resultado aparece no histórico e em uma notificação."
    }

    fun retry(id: String) {
        val post = queueStore.get(id) ?: return
        val reset = post.outcomes.mapValues { (p, o) ->
            if (o.state == OutcomeState.FAILED) PlatformOutcome(p, OutcomeState.QUEUED) else o
        }
        queueStore.put(post.copy(outcomes = reset))
        PublishWorker.enqueue(app, id, null)
    }

    fun refreshStatus(id: String) {
        viewModelScope.launch(Dispatchers.IO) { Publisher(app).refreshTikTok(id) }
    }

    fun delete(id: String) {
        PublishWorker.cancel(app, id)
        queueStore.remove(id)
    }
}
