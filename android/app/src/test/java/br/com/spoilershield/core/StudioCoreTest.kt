package br.com.spoilershield.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudioCoreTest {

    @Test
    fun pkceMatchesRfc7636Example() {
        // Exemplo do Apêndice B da RFC 7636
        assertEquals(
            "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            Pkce.challengeFor("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"),
        )
    }

    @Test
    fun generatedPkceIsValidAndConsistent() {
        val p = Pkce.generate()
        assertTrue(p.verifier.length in 43..128)
        assertTrue(p.verifier.all { it.isLetterOrDigit() || it == '-' || it == '_' })
        assertEquals(Pkce.challengeFor(p.verifier), p.challenge)
        assertFalse(Pkce.randomState() == Pkce.randomState())
    }

    @Test
    fun oauthUrlsAreEncodedAndRequestOnlyPublishingScopes() {
        val tt = OAuthUrls.tiktok("key123", "https://ex.com/r.html", "st", "ch")
        assertTrue(tt.startsWith("https://www.tiktok.com/v2/auth/authorize/?client_key=key123"))
        assertTrue("scope=user.info.basic%2Cvideo.publish" in tt)
        assertTrue("redirect_uri=https%3A%2F%2Fex.com%2Fr.html" in tt)
        assertTrue("code_challenge_method=S256" in tt)
        val ig = OAuthUrls.instagram("app1", "https://ex.com/r.html", "st")
        assertTrue(ig.startsWith("https://www.instagram.com/oauth/authorize?client_id=app1"))
        assertTrue("instagram_business_content_publish" in ig)
        // nada de escopos de leitura de feed/mensagens
        assertFalse("messages" in ig)
        assertFalse("manage_comments" in ig)
    }

    @Test
    fun hashtagsAreNormalized() {
        assertEquals(listOf("gta", "Rockstar_2", "ação"), PostText.normalizeHashtags("#gta, ##Rockstar_2  ação ;#GTA !!"))
        assertEquals(emptyList<String>(), PostText.normalizeHashtags("   # , "))
    }

    @Test
    fun composeAndValidate() {
        assertEquals("Olá\n\n#a #b", PostText.compose(" Olá ", listOf("a", "b")))
        assertEquals("#a", PostText.compose("", listOf("a")))
        assertEquals("só legenda", PostText.compose("só legenda", emptyList()))
        assertNull(PostText.validate(Platform.TIKTOK, "ok", listOf("a")))
        assertTrue(PostText.validate(Platform.INSTAGRAM, "x", List(31) { "t$it" }) != null)
        assertTrue(PostText.validate(Platform.TIKTOK, "x".repeat(2300), emptyList()) != null)
    }
}
