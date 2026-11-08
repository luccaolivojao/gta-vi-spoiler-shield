package br.com.spoilershield.core

/**
 * Redes sociais que o Spoiler Shield sabe bloquear.
 *
 * - [packages]: nomes de pacote Android (usados pelo bloqueio de apps).
 * - [domains]: domínios usados pelo filtro DNS local (melhor esforço; a lista é editável).
 *
 * Nenhum conteúdo é carregado destas redes: só os identificadores são usados para BLOQUEAR.
 */
enum class SocialApp(
    val id: String,
    val label: String,
    val packages: List<String>,
    val domains: List<String>,
    val defaultEnabled: Boolean = true,
) {
    TIKTOK(
        "tiktok", "TikTok",
        listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill"),
        listOf("tiktok.com", "tiktokcdn.com", "tiktokcdn-us.com", "tiktokv.com", "tiktokv.us"),
    ),
    INSTAGRAM(
        "instagram", "Instagram",
        listOf("com.instagram.android"),
        listOf("instagram.com", "cdninstagram.com", "instagr.am"),
    ),
    YOUTUBE(
        "youtube", "YouTube",
        listOf("com.google.android.youtube"),
        listOf("youtube.com", "youtu.be", "ytimg.com", "googlevideo.com", "youtube-nocookie.com"),
    ),
    X(
        "x", "X / Twitter",
        listOf("com.twitter.android"),
        listOf("x.com", "twitter.com", "twimg.com", "t.co"),
    ),
    FACEBOOK(
        "facebook", "Facebook",
        listOf("com.facebook.katana", "com.facebook.lite"),
        listOf("facebook.com", "fb.com", "fbcdn.net", "fb.watch"),
    ),
    THREADS(
        "threads", "Threads",
        listOf("com.instagram.barcelona"),
        listOf("threads.net", "threads.com"),
    ),
    REDDIT(
        "reddit", "Reddit",
        listOf("com.reddit.frontpage"),
        listOf("reddit.com", "redd.it", "redditmedia.com", "redditstatic.com"),
    ),
    SNAPCHAT(
        "snapchat", "Snapchat",
        listOf("com.snapchat.android"),
        listOf("snapchat.com", "sc-cdn.net"),
    ),
    TWITCH(
        "twitch", "Twitch",
        listOf("tv.twitch.android.app"),
        listOf("twitch.tv", "ttvnw.net", "jtvnw.net"),
    ),
    DISCORD(
        "discord", "Discord (opcional)",
        listOf("com.discord"),
        listOf("discord.com", "discord.gg", "discordapp.com", "discordapp.net"),
        defaultEnabled = false,
    );

    companion object {
        val defaultIds: Set<String> = entries.filter { it.defaultEnabled }.map { it.id }.toSet()
        fun fromId(id: String): SocialApp? = entries.firstOrNull { it.id == id }

        fun packagesFor(enabledIds: Set<String>): Set<String> =
            entries.filter { it.id in enabledIds }.flatMap { it.packages }.toSet()

        fun domainsFor(enabledIds: Set<String>): List<String> =
            entries.filter { it.id in enabledIds }.flatMap { it.domains }
    }
}

object DomainMatcher {
    /** Verdadeiro se [host] pertence a um domínio bloqueado. */
    fun isBlocked(host: String, blockedDomains: Collection<String>): Boolean {
        val h = host.trim().trimEnd('.').lowercase()
        if (h.isEmpty()) return false
        return blockedDomains.any { h == it || h.endsWith(".$it") }
    }
}
