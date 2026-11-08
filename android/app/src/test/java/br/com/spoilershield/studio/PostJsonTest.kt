package br.com.spoilershield.studio

import br.com.spoilershield.core.MediaKind
import br.com.spoilershield.core.Platform
import br.com.spoilershield.core.PostFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PostJsonTest {
    private val req = PostRequest(
        id = "1", platforms = setOf(Platform.TIKTOK, Platform.INSTAGRAM), format = PostFormat.REEL,
        media = MediaFile("/p/a.mp4", "video/mp4", 10, MediaKind.VIDEO, 5000), publicImageUrl = null,
        caption = "olá \"mundo\"\nlinha 2", hashtags = listOf("a", "b"), coverOffsetMs = 1200, scheduledAtMs = 99,
        tiktok = TikTokOptions("SELF_ONLY", allowComment = true), createdAtMs = 7,
    )

    @Test fun roundTrip() {
        val q = QueuedPost(req, mapOf(
            Platform.TIKTOK to PlatformOutcome(Platform.TIKTOK, OutcomeState.PUBLISHED_PRIVATE, "ok", "p1"),
            Platform.INSTAGRAM to PlatformOutcome(Platform.INSTAGRAM, OutcomeState.FAILED, "x"),
        ))
        val back = PostJson.fromJson(PostJson.toJson(q))
        assertEquals(q, back)
    }

    @Test fun allTerminalOnlyWhenEveryPlatformFinished() {
        val q = QueuedPost(req, mapOf(Platform.TIKTOK to PlatformOutcome(Platform.TIKTOK, OutcomeState.PUBLISHED)))
        assertFalse(q.allTerminal)
        assertTrue(q.with(PlatformOutcome(Platform.INSTAGRAM, OutcomeState.NOT_SUPPORTED, "r")).allTerminal)
    }
}
