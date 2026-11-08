package br.com.spoilershield.studio

import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File

/**
 * Cliente da Content Posting API OFICIAL do TikTok (https://developers.tiktok.com/doc/content-posting-api-get-started).
 * Somente publicação do PRÓPRIO usuário. Não existe nenhuma chamada de leitura de feed, busca ou vídeos de terceiros.
 *
 * Escopo necessário: video.publish (Direct Post). Enquanto o app não passar na auditoria do TikTok,
 * tudo que ele publica fica restrito ao modo privado (SELF_ONLY) – isso é regra da plataforma.
 */
class TikTokClient(
    private val http: OkHttpClient,
    private val base: String = "https://open.tiktokapis.com",
    private val sleep: (Long) -> Unit = { Thread.sleep(it) },
    private val pollIntervalMs: Long = 3000,
    private val maxPolls: Int = 60,
) {
    data class Tokens(
        val accessToken: String, val refreshToken: String, val openId: String,
        val expiresAtMs: Long, val refreshExpiresAtMs: Long, val scope: String,
    )

    data class CreatorInfo(
        val nickname: String,
        val privacyOptions: List<String>,
        val maxDurationSec: Int,
        val commentDisabled: Boolean,
        val duetDisabled: Boolean,
        val stitchDisabled: Boolean,
    )

    data class PostInfo(
        val title: String, val privacyLevel: String,
        val disableComment: Boolean, val disableDuet: Boolean, val disableStitch: Boolean,
        val coverTimestampMs: Long,
    )

    data class PublishStatus(val publishId: String, val status: String, val failReason: String?, val postIds: List<String>) {
        val complete get() = status == "PUBLISH_COMPLETE"
        val failed get() = status == "FAILED"
    }

    private val jsonType = "application/json; charset=UTF-8".toMediaType()

    // ---------------- OAuth ----------------

    fun exchangeCode(clientKey: String, clientSecret: String, code: String, redirectUri: String, codeVerifier: String,
                     now: Long = System.currentTimeMillis()): Tokens =
        tokenCall(
            FormBody.Builder().add("client_key", clientKey).add("client_secret", clientSecret).add("code", code)
                .add("grant_type", "authorization_code").add("redirect_uri", redirectUri).add("code_verifier", codeVerifier).build(),
            now,
        )

    fun refresh(clientKey: String, clientSecret: String, refreshToken: String, now: Long = System.currentTimeMillis()): Tokens =
        tokenCall(
            FormBody.Builder().add("client_key", clientKey).add("client_secret", clientSecret)
                .add("grant_type", "refresh_token").add("refresh_token", refreshToken).build(),
            now,
        )

    private fun tokenCall(body: FormBody, now: Long): Tokens {
        val req = Request.Builder().url("$base/v2/oauth/token/").post(body)
            .header("Content-Type", "application/x-www-form-urlencoded").build()
        http.newCall(req).execute().use { r ->
            val json = parse(r.body?.string().orEmpty(), r.code)
            if (!json.has("access_token")) {
                throw ApiException(json.optString("error", "token_error"), json.optString("error_description", "Falha ao obter token do TikTok."), r.code)
            }
            return Tokens(
                accessToken = json.getString("access_token"),
                refreshToken = json.optString("refresh_token"),
                openId = json.optString("open_id"),
                expiresAtMs = now + json.optLong("expires_in", 86_400) * 1000,
                refreshExpiresAtMs = now + json.optLong("refresh_expires_in", 31_536_000) * 1000,
                scope = json.optString("scope"),
            )
        }
    }

    // ---------------- Publicação ----------------

    /** Obrigatório antes de publicar: devolve as opções de privacidade e limites permitidos para o criador. */
    fun creatorInfo(token: String): CreatorInfo {
        val d = call(authed("/v2/post/publish/creator_info/query/", token, "{}")).optJSONObject("data")
            ?: throw ApiException("bad_response", "Resposta sem dados do criador.")
        return CreatorInfo(
            nickname = d.optString("creator_nickname"),
            privacyOptions = strings(d.optJSONArray("privacy_level_options")),
            maxDurationSec = d.optInt("max_video_post_duration_sec", 0),
            commentDisabled = d.optBoolean("comment_disabled"),
            duetDisabled = d.optBoolean("duet_disabled"),
            stitchDisabled = d.optBoolean("stitch_disabled"),
        )
    }

    /** Divisão em partes do upload (regras do Media Transfer Guide do TikTok: partes de 5–64 MB; a última pode chegar a 128 MB). */
    object Chunks {
        const val SINGLE_MAX = 64_000_000L
        const val CHUNK = 10_000_000L
        fun plan(size: Long): Pair<Long, Long> =
            if (size <= SINGLE_MAX) size to 1L else CHUNK to (size / CHUNK) // a última parte absorve o resto
    }

    fun publishVideo(
        token: String, file: File, mime: String, post: PostInfo,
        onProgress: (sent: Long, total: Long) -> Unit = { _, _ -> },
    ): PublishStatus {
        val size = file.length()
        require(size > 0) { "Arquivo vazio" }
        val (chunkSize, total) = Chunks.plan(size)

        val body = JSONObject()
            .put("post_info", JSONObject()
                .put("title", post.title)
                .put("privacy_level", post.privacyLevel)
                .put("disable_duet", post.disableDuet)
                .put("disable_comment", post.disableComment)
                .put("disable_stitch", post.disableStitch)
                .put("video_cover_timestamp_ms", post.coverTimestampMs)
                .put("brand_content_toggle", false)
                .put("brand_organic_toggle", false))
            .put("source_info", JSONObject()
                .put("source", "FILE_UPLOAD")
                .put("video_size", size)
                .put("chunk_size", chunkSize)
                .put("total_chunk_count", total))

        val init = call(authed("/v2/post/publish/video/init/", token, body.toString())).optJSONObject("data")
            ?: throw ApiException("bad_response", "Resposta sem dados de publicação.")
        val publishId = init.getString("publish_id")
        val uploadUrl = init.optString("upload_url").ifEmpty { throw ApiException("bad_response", "O TikTok não devolveu a URL de upload.") }

        val type = (if (mime in VIDEO_TYPES) mime else "video/mp4").toMediaType()
        var sent = 0L
        for (i in 0 until total) {
            val start = i * chunkSize
            val end = if (i == total - 1) size - 1 else start + chunkSize - 1
            val len = end - start + 1
            val req = Request.Builder().url(uploadUrl)
                .put(FileRangeBody(file, start, len, type) { sent += it; onProgress(sent, size) })
                .header("Content-Range", "bytes $start-$end/$size")
                .build()
            http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) throw ApiException("upload_failed", "Falha no envio do vídeo (HTTP ${r.code}).", r.code)
            }
        }

        var last = PublishStatus(publishId, "PROCESSING_UPLOAD", null, emptyList())
        repeat(maxPolls) {
            last = fetchStatus(token, publishId)
            if (last.complete || last.failed) return last
            sleep(pollIntervalMs)
        }
        return last // ainda processando: o chamador NÃO deve declarar sucesso
    }

    fun fetchStatus(token: String, publishId: String): PublishStatus {
        val d = call(authed("/v2/post/publish/status/fetch/", token, JSONObject().put("publish_id", publishId).toString()))
            .optJSONObject("data") ?: throw ApiException("bad_response", "Resposta sem status.")
        // A API grafa "publicaly_available_post_id" (sic); aceitamos as duas grafias.
        val ids = (d.optJSONArray("publicaly_available_post_id") ?: d.optJSONArray("publicly_available_post_id"))
            ?.let { a -> (0 until a.length()).map { a.get(it).toString() } } ?: emptyList()
        return PublishStatus(publishId, d.optString("status"), d.optString("fail_reason").ifEmpty { null }, ids)
    }

    // ---------------- util ----------------

    private fun authed(path: String, token: String, json: String) =
        Request.Builder().url("$base$path").post(json.toRequestBody(jsonType))
            .header("Authorization", "Bearer $token").build()

    private fun call(req: Request): JSONObject = http.newCall(req).execute().use { r ->
        val json = parse(r.body?.string().orEmpty(), r.code)
        val err = json.optJSONObject("error")
        val code = err?.optString("code").orEmpty()
        if (!r.isSuccessful || (code.isNotEmpty() && code != "ok")) {
            throw ApiException(code.ifEmpty { "http_${r.code}" }, err?.optString("message").orEmpty().ifEmpty { "Erro do TikTok (HTTP ${r.code})." }, r.code)
        }
        json
    }

    private fun parse(text: String, http: Int): JSONObject = try {
        JSONObject(text)
    } catch (e: JSONException) {
        throw ApiException("bad_response", "Resposta inesperada do TikTok (HTTP $http).", http)
    }

    private fun strings(a: JSONArray?) = if (a == null) emptyList() else (0 until a.length()).map { a.getString(it) }

    private companion object {
        val VIDEO_TYPES = setOf("video/mp4", "video/quicktime", "video/webm")
    }
}
