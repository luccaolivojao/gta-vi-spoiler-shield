package br.com.spoilershield.studio

import android.content.Context
import android.content.SharedPreferences
import br.com.spoilershield.data.SecureStore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Credenciais do APP (que você criou nos portais de desenvolvedor) e tokens das CONTAS, sempre cifrados. */
class AccountStore(private val s: SecureStore) {

    data class TikTokCreds(val clientKey: String, val clientSecret: String, val redirectUri: String)
    data class InstagramCreds(val appId: String, val appSecret: String, val redirectUri: String)
    data class TikTokAccount(val tokens: TikTokClient.Tokens, val nickname: String?)
    data class InstagramAccount(val token: String, val expiresAtMs: Long, val userId: String, val username: String, val accountType: String)
    data class Pending(val provider: String, val state: String, val verifier: String?)

    fun tiktokCreds(): TikTokCreds? = s.get("tt_creds")?.let {
        JSONObject(it).let { j -> TikTokCreds(j.getString("k"), j.getString("s"), j.getString("r")) }
    }
    fun setTikTokCreds(c: TikTokCreds?) = s.put("tt_creds", c?.let { JSONObject().put("k", it.clientKey).put("s", it.clientSecret).put("r", it.redirectUri).toString() })

    fun instagramCreds(): InstagramCreds? = s.get("ig_creds")?.let {
        JSONObject(it).let { j -> InstagramCreds(j.getString("a"), j.getString("s"), j.getString("r")) }
    }
    fun setInstagramCreds(c: InstagramCreds?) = s.put("ig_creds", c?.let { JSONObject().put("a", it.appId).put("s", it.appSecret).put("r", it.redirectUri).toString() })

    fun tiktok(): TikTokAccount? = s.get("tt_acc")?.let {
        val j = JSONObject(it)
        TikTokAccount(
            TikTokClient.Tokens(j.getString("at"), j.getString("rt"), j.getString("oid"), j.getLong("ae"), j.getLong("re"), j.optString("sc")),
            if (j.isNull("nn")) null else j.getString("nn"),
        )
    }
    fun setTikTok(a: TikTokAccount?) = s.put("tt_acc", a?.let {
        JSONObject().put("at", it.tokens.accessToken).put("rt", it.tokens.refreshToken).put("oid", it.tokens.openId)
            .put("ae", it.tokens.expiresAtMs).put("re", it.tokens.refreshExpiresAtMs).put("sc", it.tokens.scope)
            .put("nn", it.nickname ?: JSONObject.NULL).toString()
    })

    fun instagram(): InstagramAccount? = s.get("ig_acc")?.let {
        val j = JSONObject(it)
        InstagramAccount(j.getString("t"), j.getLong("e"), j.getString("u"), j.getString("n"), j.getString("a"))
    }
    fun setInstagram(a: InstagramAccount?) = s.put("ig_acc", a?.let {
        JSONObject().put("t", it.token).put("e", it.expiresAtMs).put("u", it.userId).put("n", it.username).put("a", it.accountType).toString()
    })

    fun pending(): Pending? = s.get("oauth_pending")?.let {
        val j = JSONObject(it)
        Pending(j.getString("p"), j.getString("s"), if (j.isNull("v")) null else j.getString("v"))
    }
    fun setPending(p: Pending?) = s.put("oauth_pending", p?.let {
        JSONObject().put("p", it.provider).put("s", it.state).put("v", it.verifier ?: JSONObject.NULL).toString()
    })
}

/** Fila/histórico de publicações (sem segredos). Atualizada pelo Worker e observada pela interface. */
class PostQueueStore(private val ctx: Context) {
    private val prefs: SharedPreferences = ctx.applicationContext.getSharedPreferences("post_queue", Context.MODE_PRIVATE)
    private val lock = Any()

    fun all(): List<QueuedPost> = synchronized(lock) {
        val arr = JSONArray(prefs.getString(KEY, "[]"))
        (0 until arr.length()).mapNotNull { runCatching { PostJson.fromJson(arr.getJSONObject(it)) }.getOrNull() }
            .sortedByDescending { it.request.createdAtMs }
    }

    fun get(id: String) = all().firstOrNull { it.request.id == id }

    fun put(post: QueuedPost) = synchronized(lock) {
        val list = all().filter { it.request.id != post.request.id } + post
        save(list)
    }

    fun remove(id: String) = synchronized(lock) {
        all().firstOrNull { it.request.id == id }?.request?.media?.let { File(it.path).delete() }
        save(all().filter { it.request.id != id })
    }

    private fun save(list: List<QueuedPost>) {
        prefs.edit().putString(KEY, JSONArray(list.map { PostJson.toJson(it) }).toString()).apply()
    }

    fun observe(): Flow<List<QueuedPost>> = callbackFlow {
        val l = SharedPreferences.OnSharedPreferenceChangeListener { _, k -> if (k == KEY) trySend(all()) }
        prefs.registerOnSharedPreferenceChangeListener(l)
        trySend(all())
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(l) }
    }

    fun mediaDir(): File = File(ctx.applicationContext.filesDir, "queue").apply { mkdirs() }

    private companion object { const val KEY = "items" }
}
