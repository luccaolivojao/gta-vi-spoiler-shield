package br.com.spoilershield.studio

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Testam o NOSSO cliente contra um servidor simulado (formato descrito na documentação oficial).
 * Isto NÃO prova que o TikTok real aceitará a chamada: só uma conta de desenvolvedor aprovada prova.
 */
class TikTokClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: TikTokClient
    private lateinit var video: File

    @Before fun setUp() {
        server = MockWebServer().also { it.start() }
        client = TikTokClient(OkHttpClient(), server.url("/").toString().trimEnd('/'), sleep = {}, pollIntervalMs = 0, maxPolls = 2)
        video = File.createTempFile("video", ".mp4").apply { writeBytes(ByteArray(1000) { it.toByte() }) }
    }

    @After fun tearDown() { server.shutdown(); video.delete() }

    private val post = TikTokClient.PostInfo("legenda #a", "SELF_ONLY", true, true, true, 0)

    @Test fun directPostFlowSingleChunk() {
        val upload = server.url("/upload/abc").toString()
        server.enqueue(MockResponse().setBody("""{"data":{"publish_id":"p1","upload_url":"$upload"},"error":{"code":"ok","message":""}}"""))
        server.enqueue(MockResponse().setResponseCode(201))
        server.enqueue(MockResponse().setBody("""{"data":{"status":"PUBLISH_COMPLETE","publicaly_available_post_id":[123]},"error":{"code":"ok"}}"""))

        val r = client.publishVideo("tok", video, "video/mp4", post)

        val init = server.takeRequest()
        assertEquals("/v2/post/publish/video/init/", init.path)
        assertEquals("Bearer tok", init.getHeader("Authorization"))
        val body = JSONObject(init.body.readUtf8())
        assertEquals("FILE_UPLOAD", body.getJSONObject("source_info").getString("source"))
        assertEquals(1000, body.getJSONObject("source_info").getInt("video_size"))
        assertEquals(1, body.getJSONObject("source_info").getInt("total_chunk_count"))
        assertEquals("SELF_ONLY", body.getJSONObject("post_info").getString("privacy_level"))

        val put = server.takeRequest()
        assertEquals("PUT", put.method)
        assertEquals("bytes 0-999/1000", put.getHeader("Content-Range"))
        assertEquals(1000L, put.bodySize)

        assertEquals("/v2/post/publish/status/fetch/", server.takeRequest().path)
        assertTrue(r.complete)
        assertEquals(listOf("123"), r.postIds)
    }

    @Test fun stillProcessingIsNeverReportedAsComplete() {
        server.enqueue(MockResponse().setBody("""{"data":{"publish_id":"p1","upload_url":"${server.url("/u")}"},"error":{"code":"ok"}}"""))
        server.enqueue(MockResponse().setResponseCode(201))
        repeat(2) { server.enqueue(MockResponse().setBody("""{"data":{"status":"PROCESSING_UPLOAD"},"error":{"code":"ok"}}""")) }
        val r = client.publishVideo("tok", video, "video/mp4", post)
        assertFalse(r.complete); assertFalse(r.failed)
        assertEquals("PROCESSING_UPLOAD", r.status)
    }

    @Test fun unauditedClientErrorIsSurfaced() {
        server.enqueue(MockResponse().setResponseCode(403).setBody(
            """{"error":{"code":"unaudited_client_can_only_post_to_private_accounts","message":"x","log_id":"1"}}"""))
        try {
            client.publishVideo("tok", video, "video/mp4", post.copy(privacyLevel = "PUBLIC_TO_EVERYONE"))
            fail("deveria lançar")
        } catch (e: ApiException) {
            assertEquals("unaudited_client_can_only_post_to_private_accounts", e.code)
            assertTrue("PRIVADO" in Friendly.message(br.com.spoilershield.core.Platform.TIKTOK, e).uppercase() || "privada" in Friendly.message(br.com.spoilershield.core.Platform.TIKTOK, e))
        }
    }

    @Test fun creatorInfoParsed() {
        server.enqueue(MockResponse().setBody("""{"data":{"creator_nickname":"nick","privacy_level_options":["SELF_ONLY","PUBLIC_TO_EVERYONE"],"comment_disabled":false,"duet_disabled":true,"stitch_disabled":false,"max_video_post_duration_sec":600},"error":{"code":"ok"}}"""))
        val c = client.creatorInfo("tok")
        assertEquals("nick", c.nickname); assertEquals(600, c.maxDurationSec); assertTrue(c.duetDisabled)
        assertEquals(listOf("SELF_ONLY", "PUBLIC_TO_EVERYONE"), c.privacyOptions)
    }

    @Test fun tokenExchangeSendsPkceVerifier() {
        server.enqueue(MockResponse().setBody("""{"access_token":"a","expires_in":86400,"open_id":"o","refresh_token":"r","refresh_expires_in":31536000,"scope":"video.publish","token_type":"Bearer"}"""))
        val t = client.exchangeCode("ck", "cs", "code", "https://r", "ver", now = 0)
        val req = server.takeRequest().body.readUtf8()
        assertTrue("code_verifier=ver" in req && "grant_type=authorization_code" in req)
        assertEquals(86_400_000L, t.expiresAtMs)
    }

    @Test fun chunkPlanCoversWholeFile() {
        val (c1, n1) = TikTokClient.Chunks.plan(50_000_000)
        assertEquals(1L, n1); assertEquals(50_000_000L, c1)
        val size = 123_456_789L
        val (c, n) = TikTokClient.Chunks.plan(size)
        assertTrue(n >= 2)
        val lastStart = (n - 1) * c
        val lastLen = size - lastStart
        assertTrue(lastLen >= c && lastLen < 2 * c) // última parte absorve o resto
    }
}
