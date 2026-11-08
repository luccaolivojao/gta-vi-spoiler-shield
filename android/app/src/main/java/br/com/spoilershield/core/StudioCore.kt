package br.com.spoilershield.core

import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** PKCE (RFC 7636) e utilitários de OAuth, puros e testáveis. */
object Pkce {
    data class Pair(val verifier: String, val challenge: String)

    fun challengeFor(verifier: String): String =
        Base64.getUrlEncoder().withoutPadding()
            .encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))

    fun generate(random: SecureRandom = SecureRandom()): Pair {
        val bytes = ByteArray(48).also(random::nextBytes)
        val v = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes) // 64 caracteres
        return Pair(v, challengeFor(v))
    }

    fun randomState(random: SecureRandom = SecureRandom()): String =
        ByteArray(16).also(random::nextBytes).joinToString("") { "%02x".format(it) }
}

object OAuthUrls {
    const val TIKTOK_SCOPES = "user.info.basic,video.publish"
    const val INSTAGRAM_SCOPES = "instagram_business_basic,instagram_business_content_publish"

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    fun tiktok(clientKey: String, redirectUri: String, state: String, challenge: String) =
        "https://www.tiktok.com/v2/auth/authorize/" +
            "?client_key=${enc(clientKey)}&scope=${enc(TIKTOK_SCOPES)}&response_type=code" +
            "&redirect_uri=${enc(redirectUri)}&state=${enc(state)}" +
            "&code_challenge=${enc(challenge)}&code_challenge_method=S256"

    fun instagram(appId: String, redirectUri: String, state: String) =
        "https://www.instagram.com/oauth/authorize" +
            "?client_id=${enc(appId)}&redirect_uri=${enc(redirectUri)}&response_type=code" +
            "&scope=${enc(INSTAGRAM_SCOPES)}&state=${enc(state)}"
}

/** Montagem e validação de legenda/hashtags. */
object PostText {
    const val MAX_CHARS = 2200
    const val MAX_INSTAGRAM_HASHTAGS = 30

    fun normalizeHashtags(raw: String): List<String> =
        raw.split(Regex("[\\s,;]+"))
            .map { t -> t.trimStart('#').filter { it.isLetterOrDigit() || it == '_' } }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }

    fun compose(caption: String, tags: List<String>): String {
        val c = caption.trim()
        if (tags.isEmpty()) return c
        val t = tags.joinToString(" ") { "#$it" }
        return if (c.isEmpty()) t else "$c\n\n$t"
    }

    /** Retorna a mensagem de erro, ou null se estiver ok. */
    fun validate(platform: Platform, caption: String, tags: List<String>): String? {
        val text = compose(caption, tags)
        if (text.length > MAX_CHARS) return "${platform.label}: texto com ${text.length} caracteres (máximo $MAX_CHARS)."
        if (platform == Platform.INSTAGRAM && tags.size > MAX_INSTAGRAM_HASHTAGS)
            return "Instagram: no máximo $MAX_INSTAGRAM_HASHTAGS hashtags."
        return null
    }
}
