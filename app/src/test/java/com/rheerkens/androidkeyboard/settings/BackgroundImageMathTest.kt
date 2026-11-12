package com.rheerkens.androidkeyboard.settings

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max

class BackgroundImageMathTest {
    @Test
    fun sourcesAreCheckedBeforeDecoding() {
        assertEquals(BackgroundImageError.UNSUPPORTED, checkSourceBounds(-1, -1))
        assertEquals(BackgroundImageError.UNSUPPORTED, checkSourceBounds(0, 100))
        assertEquals(BackgroundImageError.TOO_LARGE, checkSourceBounds(20_000, 10_000))
        assertNull(checkSourceBounds(12_000, 8_000))
    }

    @Test
    fun largePhotosAreDownsampledWhileDecoding() {
        for ((w, h) in listOf(4000 to 3000, 12_000 to 8_000, 8_000 to 12_000, 40_000 to 2_000, 3000 to 3000)) {
            val s = sampleSizeFor(w, h)
            assertEquals("power of two for $w×$h", 0, s and (s - 1))
            val dw = w / s
            val dh = h / s
            // Never more than four times the stored pixel budget...
            assertTrue("$w×$h sample $s", dw.toLong() * dh <= BackgroundLimits.MAX_PIXELS * 4)
            // ...and never smaller than the stored size needs, unless that budget forced it.
            if (s > 1) {
                val pw = w / (s / 2)
                val ph = h / (s / 2)
                assertTrue("$w×$h sample $s", max(dw, dh) >= BackgroundLimits.MAX_SIDE || pw.toLong() * ph > BackgroundLimits.MAX_PIXELS * 4)
            }
        }
        assertEquals(1, sampleSizeFor(1080, 720))
        assertEquals(1, sampleSizeFor(1, 1))
    }

    @Test
    fun storedImagesAreBoundedAndNeverUpscaled() {
        assertEquals(800 to 600, storedSize(800, 600))
        for ((w, h) in listOf(2880 to 2160, 6000 to 1000, 1000 to 6000, 1440 to 1440, 2000 to 2000)) {
            val (sw, sh) = storedSize(w, h)
            assertTrue("$w×$h -> $sw×$sh", max(sw, sh) <= BackgroundLimits.MAX_SIDE)
            assertTrue("$w×$h -> $sw×$sh", sw.toLong() * sh <= BackgroundLimits.MAX_PIXELS + max(sw, sh))
            assertTrue(sw <= w && sh <= h)
        }
        assertEquals(1 to 1, storedSize(1, 1))
    }

    @Test
    fun theContrastSampleIsSmall() {
        assertEquals(64 to 48, sampleSize(1440, 1080))
        assertEquals(10 to 5, sampleSize(10, 5))
        val (w, h) = sampleSize(1440, 1)
        assertEquals(64, w)
        assertEquals(1, h)
    }

    @Test
    fun exifOrientationTurnsImagesUpright() {
        assertEquals(Upright(0, false), Upright.fromExif(1))
        assertEquals(Upright(0, true), Upright.fromExif(2))
        assertEquals(Upright(180, false), Upright.fromExif(3))
        assertEquals(Upright(180, true), Upright.fromExif(4))
        assertEquals(Upright(90, true), Upright.fromExif(5))
        assertEquals(Upright(90, false), Upright.fromExif(6))
        assertEquals(Upright(270, true), Upright.fromExif(7))
        assertEquals(Upright(270, false), Upright.fromExif(8))
        assertEquals(Upright(0, false), Upright.fromExif(0))
        assertEquals(Upright(0, false), Upright.fromExif(99))
        // A portrait photo stored sideways (orientation 6) becomes portrait.
        assertEquals(3000 to 4000, Upright.fromExif(6).size(4000, 3000))
        assertEquals(4000 to 3000, Upright.fromExif(3).size(4000, 3000))
    }

    @Test
    fun theImageIsCroppedFromTheMiddle() {
        // A square image on a wide keyboard keeps its middle band.
        assertArrayEquals(intArrayOf(0, 250, 1000, 500), centerCrop(1000, 1000, 400f, 200f))
        // A wide image on a square area keeps its middle columns.
        assertArrayEquals(intArrayOf(500, 0, 1000, 1000), centerCrop(2000, 1000, 300f, 300f))
        // Same aspect: everything.
        assertArrayEquals(intArrayOf(0, 0, 800, 400), centerCrop(800, 400, 1600f, 800f))
        // Degenerate sizes do not crash.
        assertArrayEquals(intArrayOf(0, 0, 10, 10), centerCrop(10, 10, 0f, 0f))
    }
}
