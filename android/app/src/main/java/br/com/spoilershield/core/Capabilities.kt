package br.com.spoilershield.core

enum class Platform(val label: String) { TIKTOK("TikTok"), INSTAGRAM("Instagram") }

enum class PostFormat(val label: String) {
    VIDEO("Vídeo"), REEL("Reel"), STORY("Story"), PHOTO("Foto")
}

enum class MediaKind { VIDEO, IMAGE }

sealed interface Support {
    /** Funciona pela API oficial com o que o app já tem. */
    data object Yes : Support

    /** Funciona, mas com ressalvas que o usuário precisa saber. */
    data class Conditional(val note: String) : Support

    /** Não é possível pela API oficial (ou não implementado): o app NÃO tenta nem simula. */
    data class No(val reason: String) : Support

    val allowed: Boolean get() = this !is No
}

/**
 * Matriz de capacidades baseada na documentação oficial (pesquisada em nov/2026).
 * Esta é a única fonte de verdade sobre "o que dá para publicar": a UI e o publicador
 * consultam aqui antes de qualquer chamada de rede.
 */
object Capabilities {

    fun check(platform: Platform, format: PostFormat, media: MediaKind, hasPublicUrl: Boolean): Support =
        when (platform) {
            Platform.TIKTOK -> tiktok(format, media)
            Platform.INSTAGRAM -> instagram(format, media, hasPublicUrl)
        }

    private fun tiktok(format: PostFormat, media: MediaKind): Support = when {
        format == PostFormat.STORY ->
            Support.No("A API oficial do TikTok (Content Posting API) não oferece publicação de Stories.")
        format == PostFormat.PHOTO || media == MediaKind.IMAGE ->
            Support.No(
                "Fotos na API do TikTok só aceitam URLs públicas de domínio verificado (PULL_FROM_URL); " +
                    "upload direto da galeria não existe. Não implementado.",
            )
        else -> Support.Conditional(
            "Exige o escopo video.publish. Enquanto o app não passar na auditoria do TikTok, " +
                "todo vídeo fica PRIVADO (somente você vê).",
        )
    }

    private const val PRO_ONLY = "Somente contas Profissionais do Instagram (Criador ou Comercial); contas pessoais não publicam via API."

    private fun instagram(format: PostFormat, media: MediaKind, hasPublicUrl: Boolean): Support = when {
        format == PostFormat.VIDEO || format == PostFormat.REEL ->
            if (media == MediaKind.VIDEO) Support.Conditional("$PRO_ONLY Vídeos de feed são publicados como Reels.")
            else Support.No("Reels e vídeos precisam de um arquivo de vídeo.")
        format == PostFormat.STORY && media == MediaKind.VIDEO -> Support.Conditional(PRO_ONLY)
        // Imagens (feed ou Story): a API só aceita image_url pública em JPEG.
        media == MediaKind.IMAGE ->
            if (hasPublicUrl) Support.Conditional("$PRO_ONLY A imagem deve ser JPEG e estar em uma URL pública acessível.")
            else Support.No("A API do Instagram só publica imagens a partir de uma URL pública (JPEG). Informe a URL da imagem.")
        else -> Support.No("Para foto, escolha uma imagem; para vídeo, use Reel ou Story.")
    }
}
