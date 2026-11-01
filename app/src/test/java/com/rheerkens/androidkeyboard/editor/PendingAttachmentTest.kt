package com.rheerkens.androidkeyboard.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PendingAttachmentTest {
    private fun offer(key: String = "chat", at: Long = 1_000L) =
        Attachment("content://x/1.png", "image/png", "caption", key, at).also(PendingAttachment::offer)

    @Test
    fun deliversOnceToTheFieldItCameFrom() {
        val a = offer()
        assertNull(PendingAttachment.take("other field", 2_000L))
        assertEquals(a, PendingAttachment.take("chat", 2_000L))
        assertNull(PendingAttachment.take("chat", 2_000L))
    }

    @Test
    fun expires() {
        offer()
        assertNull(PendingAttachment.take("chat", 1_000L + PendingAttachment.TTL_MS + 1))
        assertNull(PendingAttachment.take("chat", 1_000L))
    }
}
