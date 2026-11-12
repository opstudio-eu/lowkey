package com.rheerkens.androidkeyboard.settings

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackgroundImagesDecodeTest {
    @Test
    fun aCorruptOrUnreadableFileFallsBackToNoImage() {
        assertNull(decodeOrNull<String> { throw IllegalArgumentException("bad size") })
        assertNull(decodeOrNull<String> { throw IllegalStateException("recycled") })
        assertNull(decodeOrNull<String> { throw SecurityException("denied") })
        assertNull(decodeOrNull<String> { throw OutOfMemoryError() })
        assertEquals("ok", decodeOrNull { "ok" })
    }

    @Test(expected = CancellationException::class)
    fun cancellationIsNotSwallowed() {
        decodeOrNull<String> { throw CancellationException("cancelled") }
    }
}
