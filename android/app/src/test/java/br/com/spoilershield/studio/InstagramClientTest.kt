package br.com.spoilershield.studio

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File

/** Servidor simulado: valida o formato das nossas chamadas, não a aceitação real da Meta. */
class InstagramClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: InstagramClient
    private lateinit var video: File

    @Before fun setUp() {
        server = MockWebServer().also { it.start() }
        val base = server.url("/").toString().trimEnd('/')
        client = InstagramClient(OkHttpClient(), base, base, base, "v27.0", sleep = {}, pollIntervalMs = 0, maxPolls = 3)
        video = File.createTempFile("v", ".mp4").apply { writeBytes(ByteArray(2048)) }
    }

    @After fun tearDown() { server.shutdown(); video.delete() }

    @Test fun reelResumableFlow() {
        server.enqueue(MockResponse().setBody("""{"id":"C1","uri":"${server.url("/ig-api-upload/v27.0/C1")}"}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"message":"Upload successful."}"""))
        server.enqueue(MockResponse().setBody("""{"status_code":"IN_PROGRESS"}"""))
        server.enqueue(MockResponse().setBody("""{"status_code":"FINISHED"}"""))
        server.enqueue(MockResponse().setBody("""{"id":"M1"}"""))
        server.enqueue(MockResponse().setBody("""{"permalink":"https://www.instagram.com/reel/abc/"}"""))

        val p = client.publishVideo("tok", "IG1", InstagramClient.Kind.REELS, video, "legenda #a", 1500)

        val create = server.takeRequest()
        assertEquals("/v27.0/IG1/media", create.path)
        val cb = create.body.readUtf8()
        assertTrue("media_type=REELS" in cb && "upload_type=resumable" in cb && "thumb_offset=1500" in cb)

        val up = server.takeRequest()
        assertEquals("OAuth tok", up.getHeader("Authorization"))
        assertEquals("0", up.getHeader("offset"))
        assertEquals("2048", up.getHeader("file_size"))
        assertEquals(2048L, up.bodySize)

        assertTrue(server.takeRequest().path!!.startsWith("/v27.0/C1?fields=status_code"))
        assertTrue(server.takeRequest().path!!.startsWith("/v27.0/C1?fields=status_code"))
        val pub = server.takeRequest()
        assertEquals("/v27.0/IG1/media_publish", pub.path)
        assertTrue("creation_id=C1" in pub.body.readUtf8())
        assertEquals("M1", p.mediaId)
    }

    @Test fun storyVideoHasNoCaption() {
        server.enqueue(MockResponse().setBody("""{"id":"C2"}"""))
        server.enqueue(MockResponse().setBody("""{"success":true}"""))
        server.enqueue(MockResponse().setBody("""{"status_code":"FINISHED"}"""))
        server.enqueue(MockResponse().setBody("""{"id":"M2"}"""))
        server.enqueue(MockResponse().setResponseCode(500)) // permalink opcional
        client.publishVideo("tok", "IG1", InstagramClient.Kind.STORIES, video, "ignorada", 0)
        val body = server.takeRequest().body.readUtf8()
        assertTrue("media_type=STORIES" in body)
        assertFalse("caption" in body)
    }

    @Test fun containerErrorIsNotReportedAsSuccess() {
        server.enqueue(MockResponse().setBody("""{"id":"C3"}"""))
        server.enqueue(MockResponse().setBody("""{"success":true}"""))
        server.enqueue(MockResponse().setBody("""{"status_code":"ERROR","status":"formato inválido"}"""))
        try {
            client.publishVideo("tok", "IG1", InstagramClient.Kind.REELS, video, "", 0)
            fail("deveria lançar")
        } catch (e: ApiException) { assertEquals("container_error", e.code) }
    }

    @Test fun timeoutWhileProcessingThrowsInsteadOfClaimingSuccess() {
        server.enqueue(MockResponse().setBody("""{"id":"C4"}"""))
        server.enqueue(MockResponse().setBody("""{"success":true}"""))
        repeat(3) { server.enqueue(MockResponse().setBody("""{"status_code":"IN_PROGRESS"}""")) }
        try { client.publishVideo("tok", "IG1", InstagramClient.Kind.REELS, video, "", 0); fail() }
        catch (e: ApiException) { assertEquals("processing_timeout", e.code) }
    }

    @Test fun imageNeedsPublicUrlAndUsesImageUrlParam() {
        server.enqueue(MockResponse().setBody("""{"id":"C5"}"""))
        server.enqueue(MockResponse().setBody("""{"status_code":"FINISHED"}"""))
        server.enqueue(MockResponse().setBody("""{"id":"M5"}"""))
        server.enqueue(MockResponse().setResponseCode(500))
        client.publishImage("tok", "IG1", InstagramClient.Kind.IMAGE, "https://cdn.exemplo.com/a.jpg", "oi")
        val b = server.takeRequest().body.readUtf8()
        assertTrue("image_url=https%3A%2F%2Fcdn.exemplo.com%2Fa.jpg" in b && "caption=oi" in b)
    }

    @Test fun profileAndAccountTypes() {
        server.enqueue(MockResponse().setBody("""{"user_id":"123","username":"eu","account_type":"MEDIA_CREATOR"}"""))
        server.enqueue(MockResponse().setBody("""{"user_id":"9","username":"p","account_type":"PERSONAL"}"""))
        assertTrue(client.profile("t").isProfessional)
        assertFalse(client.profile("t").isProfessional)
    }

    @Test fun apiErrorsAreParsed() {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":{"message":"Invalid OAuth access token","type":"OAuthException","code":190}}"""))
        try { client.profile("bad"); fail() } catch (e: ApiException) {
            assertEquals("190", e.code)
            assertTrue("Reconecte" in Friendly.message(br.com.spoilershield.core.Platform.INSTAGRAM, e))
        }
    }
}
