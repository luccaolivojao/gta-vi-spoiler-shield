package br.com.spoilershield.studio

import br.com.spoilershield.core.MediaKind
import br.com.spoilershield.core.Platform
import br.com.spoilershield.core.PostFormat
import org.json.JSONArray
import org.json.JSONObject

/** Mídia do USUÁRIO, copiada para o armazenamento privado do app. */
data class MediaFile(
    val path: String,
    val mime: String,
    val sizeBytes: Long,
    val kind: MediaKind,
    val durationMs: Long?,
)

/** Opções exigidas pelas diretrizes de UX do TikTok (nenhuma vem marcada por padrão). */
data class TikTokOptions(
    val privacy: String? = null,
    val allowComment: Boolean = false,
    val allowDuet: Boolean = false,
    val allowStitch: Boolean = false,
)

data class PostRequest(
    val id: String,
    val platforms: Set<Platform>,
    val format: PostFormat,
    val media: MediaFile?,
    val publicImageUrl: String?,
    val caption: String,
    val hashtags: List<String>,
    val coverOffsetMs: Long,
    val scheduledAtMs: Long?,
    val tiktok: TikTokOptions,
    val createdAtMs: Long,
)

enum class OutcomeState(val label: String, val terminal: Boolean) {
    QUEUED("Na fila", false),
    SENDING("Enviando", false),
    PROCESSING("Processando na plataforma", false),
    PUBLISHED("Publicado", true),
    PUBLISHED_PRIVATE("Publicado como PRIVADO", true),
    FAILED("Falhou", true),
    NOT_SUPPORTED("Sem suporte oficial", true),
}

data class PlatformOutcome(
    val platform: Platform,
    val state: OutcomeState,
    val detail: String = "",
    val remoteId: String? = null,
    val link: String? = null,
)

data class QueuedPost(val request: PostRequest, val outcomes: Map<Platform, PlatformOutcome>) {
    val allTerminal: Boolean get() = request.platforms.all { outcomes[it]?.state?.terminal == true }
    fun with(o: PlatformOutcome) = copy(outcomes = outcomes + (o.platform to o))
}

// ---------- JSON ----------

object PostJson {
    fun toJson(q: QueuedPost): JSONObject {
        val r = q.request
        return JSONObject().apply {
            put("id", r.id)
            put("platforms", JSONArray(r.platforms.map { it.name }))
            put("format", r.format.name)
            r.media?.let {
                put("media", JSONObject().put("path", it.path).put("mime", it.mime).put("size", it.sizeBytes)
                    .put("kind", it.kind.name).put("duration", it.durationMs ?: JSONObject.NULL))
            }
            put("publicImageUrl", r.publicImageUrl ?: JSONObject.NULL)
            put("caption", r.caption)
            put("hashtags", JSONArray(r.hashtags))
            put("cover", r.coverOffsetMs)
            put("scheduledAt", r.scheduledAtMs ?: JSONObject.NULL)
            put("createdAt", r.createdAtMs)
            put("tt", JSONObject().put("privacy", r.tiktok.privacy ?: JSONObject.NULL)
                .put("comment", r.tiktok.allowComment).put("duet", r.tiktok.allowDuet).put("stitch", r.tiktok.allowStitch))
            put("outcomes", JSONArray(q.outcomes.values.map {
                JSONObject().put("p", it.platform.name).put("s", it.state.name).put("d", it.detail)
                    .put("id", it.remoteId ?: JSONObject.NULL).put("l", it.link ?: JSONObject.NULL)
            }))
        }
    }

    fun fromJson(j: JSONObject): QueuedPost {
        fun optStr(o: JSONObject, k: String) = if (o.isNull(k)) null else o.getString(k)
        val media = j.optJSONObject("media")?.let {
            MediaFile(it.getString("path"), it.getString("mime"), it.getLong("size"), MediaKind.valueOf(it.getString("kind")),
                if (it.isNull("duration")) null else it.getLong("duration"))
        }
        val tt = j.getJSONObject("tt")
        val platforms = j.getJSONArray("platforms").let { a -> (0 until a.length()).map { Platform.valueOf(a.getString(it)) }.toSet() }
        val tags = j.getJSONArray("hashtags").let { a -> (0 until a.length()).map { a.getString(it) } }
        val req = PostRequest(
            id = j.getString("id"), platforms = platforms, format = PostFormat.valueOf(j.getString("format")),
            media = media, publicImageUrl = optStr(j, "publicImageUrl"), caption = j.getString("caption"),
            hashtags = tags, coverOffsetMs = j.getLong("cover"),
            scheduledAtMs = if (j.isNull("scheduledAt")) null else j.getLong("scheduledAt"),
            tiktok = TikTokOptions(optStr(tt, "privacy"), tt.getBoolean("comment"), tt.getBoolean("duet"), tt.getBoolean("stitch")),
            createdAtMs = j.getLong("createdAt"),
        )
        val outs = j.getJSONArray("outcomes").let { a ->
            (0 until a.length()).map { a.getJSONObject(it) }.map {
                PlatformOutcome(Platform.valueOf(it.getString("p")), OutcomeState.valueOf(it.getString("s")),
                    it.getString("d"), optStr(it, "id"), optStr(it, "l"))
            }
        }
        return QueuedPost(req, outs.associateBy { it.platform })
    }
}

class ApiException(val code: String, message: String, val http: Int = 0) : Exception(message)
