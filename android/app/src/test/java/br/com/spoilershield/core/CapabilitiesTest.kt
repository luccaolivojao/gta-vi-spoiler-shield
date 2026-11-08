package br.com.spoilershield.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilitiesTest {
    private fun ok(p: Platform, f: PostFormat, m: MediaKind, url: Boolean = false) =
        Capabilities.check(p, f, m, url).allowed

    @Test
    fun tiktokStoriesAndPhotosAreNotSupported() {
        assertFalse(ok(Platform.TIKTOK, PostFormat.STORY, MediaKind.VIDEO))
        assertFalse(ok(Platform.TIKTOK, PostFormat.PHOTO, MediaKind.IMAGE, url = true))
        assertTrue(ok(Platform.TIKTOK, PostFormat.VIDEO, MediaKind.VIDEO))
    }

    @Test
    fun tiktokVideoIsConditionalOnAudit() {
        val s = Capabilities.check(Platform.TIKTOK, PostFormat.VIDEO, MediaKind.VIDEO, false)
        assertTrue(s is Support.Conditional && "PRIVADO" in s.note)
    }

    @Test
    fun instagramVideoFormats() {
        assertTrue(ok(Platform.INSTAGRAM, PostFormat.REEL, MediaKind.VIDEO))
        assertTrue(ok(Platform.INSTAGRAM, PostFormat.STORY, MediaKind.VIDEO))
        assertTrue(ok(Platform.INSTAGRAM, PostFormat.VIDEO, MediaKind.VIDEO))
        assertFalse(ok(Platform.INSTAGRAM, PostFormat.REEL, MediaKind.IMAGE, url = true))
    }

    @Test
    fun instagramImagesNeedPublicUrl() {
        assertFalse(ok(Platform.INSTAGRAM, PostFormat.PHOTO, MediaKind.IMAGE, url = false))
        assertTrue(ok(Platform.INSTAGRAM, PostFormat.PHOTO, MediaKind.IMAGE, url = true))
        assertFalse(ok(Platform.INSTAGRAM, PostFormat.STORY, MediaKind.IMAGE, url = false))
        assertTrue(ok(Platform.INSTAGRAM, PostFormat.STORY, MediaKind.IMAGE, url = true))
    }
}
