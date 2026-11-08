package br.com.spoilershield.studio

import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import java.io.File

/**
 * Cliente da "Instagram API with Instagram Login" (Business Login for Instagram) – oficial da Meta.
 * Somente publicação no PRÓPRIO perfil, que precisa ser conta Profissional (Criador ou Comercial).
 * Permissões: instagram_business_basic e instagram_business_content_publish.
 *
 * Mídia:
 *  - Vídeo (Reel / Story em vídeo): upload resumível direto do aparelho (rupload.facebook.com).
 *  - Imagem (feed / Story em foto): a API só aceita image_url pública em JPEG.
 */
class InstagramClient(
    private val http: OkHttpClient,
    private val authBase: String = "https://api.instagram.com",
    private val graphBase: String = "https://graph.instagram.com",
    private val ruploadBase: String = "https://rupload.facebook.com",
    val version: String = "v27.0",
    private val sleep: (Long) -> Unit = { Thread.sleep(it) },
    private val pollIntervalMs: Long = 5000,
    private val maxPolls: Int = 120,
) {
    data class Token(val accessToken: String, val expiresAtMs: Long)
    data class Profile(val userId: String, val username: String, val accountType: String) {
        val isProfessional get() = accountType == "BUSINESS" || accountType == "MEDIA_CREATOR"
    }
    data class Published(val mediaId: String, val permalink: String?)

    enum class Kind(val apiName: String) { REELS("REELS"), STORIES("STORIES"), IMAGE("IMAGE") }

    // ---------------- OAuth ----------------

    /** Troca o código por um token de curta duração. */
    fun exchangeCode(appId: String, appSecret: String, code: String, redirectUri: String): Pair<String, String> {
        val body = FormBody.Builder().add("client_id", appId).add("client_secret", appSecret)
            .add("grant_type", "authorization_code").add("redirect_uri", redirectUri)
            .add("code", code.removeSuffix("#_")).build()
        http.newCall(Request.Builder().url("$authBase/oauth/access_token").post(body).build()).execute().use { r ->
            val j = parse(r.body?.string().orEmpty(), r.code)
            if (!r.isSuccessful) throw apiError(j, r.code)
            // Formato novo: {"data":[{...}]}; formato antigo: objeto direto.
            val o = j.optJSONArray("data")?.optJSONObject(0) ?: j
            val token = o.optString("access_token").ifEmpty { throw ApiException("bad_response", "Instagram não devolveu token.", r.code) }
            return token to o.opt("user_id").toString()
        }
    }

    fun longLived(appSecret: String, shortToken: String, now: Long = System.currentTimeMillis()): Token {
        val url = "$graphBase/access_token".toHttpUrl().newBuilder()
            .addQueryParameter("grant_type", "ig_exchange_token")
            .addQueryParameter("client_secret", appSecret)
            .addQueryParameter("access_token", shortToken).build()
        return tokenFrom(url, now)
    }

    fun refresh(token: String, now: Long = System.currentTimeMillis()): Token {
        val url = "$graphBase/refresh_access_token".toHttpUrl().newBuilder()
            .addQueryParameter("grant_type", "ig_refresh_token")
            .addQueryParameter("access_token", token).build()
        return tokenFrom(url, now)
    }

    private fun tokenFrom(url: okhttp3.HttpUrl, now: Long): Token = get(url).let {
        Token(it.getString("access_token"), now + it.optLong("expires_in", 5_184_000) * 1000)
    }

    fun profile(token: String): Profile {
        val url = "$graphBase/$version/me".toHttpUrl().newBuilder()
            .addQueryParameter("fields", "user_id,username,account_type")
            .addQueryParameter("access_token", token).build()
        val j = get(url)
        return Profile(j.opt("user_id")?.toString() ?: j.optString("id"), j.optString("username"), j.optString("account_type"))
    }

    /** (usado, total) das publicações pela API nas últimas 24h; null se a consulta falhar. */
    fun quota(token: String, igUserId: String): Pair<Int, Int>? = try {
        val url = "$graphBase/$version/$igUserId/content_publishing_limit".toHttpUrl().newBuilder()
            .addQueryParameter("fields", "quota_usage,config")
            .addQueryParameter("access_token", token).build()
        val d = get(url).getJSONArray("data").getJSONObject(0)
        d.getInt("quota_usage") to d.optJSONObject("config")?.optInt("quota_total", 100).let { it ?: 100 }
    } catch (_: Exception) { null }

    // ---------------- Publicação ----------------

    fun publishVideo(
        token: String, igUserId: String, kind: Kind, file: File,
        caption: String, coverOffsetMs: Long, shareToFeed: Boolean = true,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ): Published {
        require(kind != Kind.IMAGE) { "Use publishImage para imagens" }
        val form = FormBody.Builder()
            .add("media_type", kind.apiName)
            .add("upload_type", "resumable")
            .add("access_token", token)
        if (kind == Kind.REELS) {
            if (caption.isNotBlank()) form.add("caption", caption)
            form.add("thumb_offset", coverOffsetMs.toString())
            form.add("share_to_feed", shareToFeed.toString())
        }
        val created = post("$graphBase/$version/$igUserId/media", form.build())
        val containerId = created.getString("id")
        val uploadUrl = created.optString("uri").ifEmpty { "$ruploadBase/ig-api-upload/$version/$containerId" }

        val size = file.length()
        require(size > 0) { "Arquivo vazio" }
        val upload = Request.Builder().url(uploadUrl)
            .post(FileRangeBody(file, 0, size, null) { onProgress(it, size) })
            .header("Authorization", "OAuth $token")
            .header("offset", "0")
            .header("file_size", size.toString())
            .build()
        http.newCall(upload).execute().use { r ->
            val j = parse(r.body?.string().orEmpty(), r.code)
            if (!r.isSuccessful || !j.optBoolean("success", false)) throw apiError(j, r.code, "upload_failed")
        }
        waitFinished(token, containerId)
        return publish(token, igUserId, containerId)
    }

    /** Imagem: [imageUrl] precisa ser pública (https), JPEG, acessível no momento da publicação. */
    fun publishImage(token: String, igUserId: String, kind: Kind, imageUrl: String, caption: String): Published {
        require(kind == Kind.IMAGE || kind == Kind.STORIES)
        val form = FormBody.Builder().add("image_url", imageUrl).add("access_token", token)
        if (kind == Kind.STORIES) form.add("media_type", "STORIES")
        else if (caption.isNotBlank()) form.add("caption", caption)
        val containerId = post("$graphBase/$version/$igUserId/media", form.build()).getString("id")
        waitFinished(token, containerId)
        return publish(token, igUserId, containerId)
    }

    private fun waitFinished(token: String, containerId: String) {
        val url = "$graphBase/$version/$containerId".toHttpUrl().newBuilder()
            .addQueryParameter("fields", "status_code,status")
            .addQueryParameter("access_token", token).build()
        repeat(maxPolls) {
            val j = get(url)
            when (j.optString("status_code")) {
                "FINISHED", "PUBLISHED" -> return
                "ERROR", "EXPIRED" -> throw ApiException("container_${j.optString("status_code").lowercase()}",
                    "O Instagram recusou a mídia: ${j.optString("status").ifEmpty { "sem detalhes" }}")
            }
            sleep(pollIntervalMs)
        }
        throw ApiException("processing_timeout", "O Instagram ainda está processando a mídia. Confira o perfil mais tarde; nada foi publicado por este app.")
    }

    private fun publish(token: String, igUserId: String, containerId: String): Published {
        val id = post(
            "$graphBase/$version/$igUserId/media_publish",
            FormBody.Builder().add("creation_id", containerId).add("access_token", token).build(),
        ).getString("id")
        val link = try {
            get("$graphBase/$version/$id".toHttpUrl().newBuilder()
                .addQueryParameter("fields", "permalink").addQueryParameter("access_token", token).build())
                .optString("permalink").ifEmpty { null }
        } catch (_: Exception) { null }
        return Published(id, link)
    }

    // ---------------- util ----------------

    private fun post(url: String, body: FormBody): JSONObject =
        http.newCall(Request.Builder().url(url).post(body).build()).execute().use { r ->
            val j = parse(r.body?.string().orEmpty(), r.code)
            if (!r.isSuccessful) throw apiError(j, r.code)
            j
        }

    private fun get(url: okhttp3.HttpUrl): JSONObject =
        http.newCall(Request.Builder().url(url).get().build()).execute().use { r ->
            val j = parse(r.body?.string().orEmpty(), r.code)
            if (!r.isSuccessful) throw apiError(j, r.code)
            j
        }

    private fun apiError(j: JSONObject, http: Int, fallback: String = "http_$http"): ApiException {
        val e = j.optJSONObject("error")
        val msg = e?.optString("message").orEmpty().ifEmpty { j.optString("error_message") }.ifEmpty { "Erro do Instagram (HTTP $http)." }
        val code = e?.let { "${it.optInt("code")}" + (if (it.has("error_subcode")) "/${it.optInt("error_subcode")}" else "") }
            ?: j.optString("error_type").ifEmpty { fallback }
        return ApiException(code, msg, http)
    }

    private fun parse(text: String, http: Int): JSONObject = try {
        JSONObject(text)
    } catch (e: JSONException) {
        throw ApiException("bad_response", "Resposta inesperada do Instagram (HTTP $http).", http)
    }
}
