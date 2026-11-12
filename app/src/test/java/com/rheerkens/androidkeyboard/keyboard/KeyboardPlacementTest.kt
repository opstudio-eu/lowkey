package com.rheerkens.androidkeyboard.keyboard

import org.junit.Assert.*
import org.junit.Test

class KeyboardPlacementTest {
    @Test fun rotationDoesNotTurnAPhoneIntoATablet() {
        assertEquals(PlacementProfile.COMPACT_LANDSCAPE, PlacementProfile.forSize(840f, 400f))
        assertEquals(PlacementProfile.LARGE_PORTRAIT, PlacementProfile.forSize(700f, 900f))
    }

    @Test fun manualChoiceSurvivesFoldingAndDisablingAutomaticSplit() {
        val p = PlacementProfile.LARGE_PORTRAIT
        val enabled = PlacementPreferences().withAutomaticSplit(true)
        assertEquals(PlacementMode.SPLIT, enabled.get(p).mode)
        val manual = enabled.select(p, PlacementMode.NORMAL)
        assertEquals(PlacementMode.NORMAL, manual.get(p).mode)
        assertEquals(PlacementMode.SPLIT, manual.get(PlacementProfile.LARGE_LANDSCAPE).mode)
        val explicitSplit = enabled.select(p, PlacementMode.SPLIT).withAutomaticSplit(false)
        assertEquals(PlacementMode.SPLIT, explicitSplit.get(p).mode)
        assertEquals(PlacementMode.NORMAL, enabled.withAutomaticSplit(false).get(p).mode)
        assertEquals(PlacementMode.NORMAL, enabled.get(PlacementProfile.COMPACT_PORTRAIT).mode)
    }

    @Test fun legacyHeightAndManualProfilesRoundTrip() {
        val initial = PlacementPreferences(legacyRowHeight = 56f)
        val p = PlacementProfile.COMPACT_PORTRAIT
        assertEquals(56f, initial.get(p).rowHeight, 0f)
        val saved = initial.select(p, PlacementMode.FLOATING).update(p) {
            it.copy(floatingX = .8f, floatingY = .2f, floatingWidth = 310f)
        }
        assertEquals(saved, PlacementPreferences.decode(saved.encode(), 56f))
        assertEquals(PlacementMode.NORMAL, PlacementPreferences.decode("corrupt", 42f).get(p).mode)
        assertEquals(42f, PlacementPreferences.decode("corrupt", 42f).get(p).rowHeight, 0f)
    }

    @Test fun floatingFrameStaysOnScreenEvenWithInvalidSavedGeometry() {
        val value = KeyboardPlacement(mode = PlacementMode.FLOATING, floatingWidth = Float.NaN,
            floatingRowHeight = Float.POSITIVE_INFINITY, floatingX = 5f, floatingY = -4f)
        val frame = value.frame(360f, 600f, 48f)
        assertTrue(frame.left >= 0f && frame.top >= 0f)
        assertTrue(frame.left + frame.width <= 360.01f)
        assertTrue(frame.top + frame.height <= 600.01f)
        assertTrue(frame.rowHeight.isFinite())
    }

    @Test fun changingDockedGeometryDoesNotChangeFloatingSize() {
        val value = KeyboardPlacement().copy(rowHeight = 64f, bottomSpace = 30f)
        val normal = value.frame(400f, 800f)
        val floating = value.copy(mode = PlacementMode.FLOATING).frame(400f, 800f)
        assertEquals(64f, normal.rowHeight, 0f)
        assertEquals(48f, floating.rowHeight, 0f)
        assertEquals(30f, 800f - normal.top - normal.height, .01f)
    }

    @Test fun resettingGeometrySurvivesRestartAfterMigratingTallKeys() {
        val reset = PlacementPreferences(legacyRowHeight = 56f).resetGeometry()
        val restored = PlacementPreferences.decode(reset.encode(), 56f)
        assertEquals(48f, restored.get(PlacementProfile.LARGE_LANDSCAPE).rowHeight, 0f)
    }

    @Test fun switchingToVoiceKeepsTheFloatingTopPosition() {
        val value = KeyboardPlacement(mode = PlacementMode.FLOATING)
        assertEquals(value.frame(400f, 800f).top, value.frame(400f, 800f, 57f).top, .01f)
    }

    @Test fun allRowsFitEvenInAShortWindow() {
        val frame = KeyboardPlacement().frame(400f, 160f)
        assertTrue(49f + frame.rowHeight * 4f <= frame.height + .01f)
    }

    @Test fun floatingWidthResizeKeepsItsLeftEdge() {
        val value = KeyboardPlacement(mode = PlacementMode.FLOATING, floatingWidth = 300f, floatingX = .5f)
        val before = value.frame(720f, 1000f)
        val after = value.resizeWidth(720f, 1000f, 80f).frame(720f, 1000f)
        assertEquals(before.left, after.left, .01f)
        assertEquals(before.width + 80f, after.width, .01f)
        val limited = value.resizeWidth(720f, 1000f, 1000f).frame(720f, 1000f)
        assertEquals(before.left, limited.left, .01f)
        assertEquals(720f, limited.left + limited.width, .01f)
    }

    @Test fun bottomResizeKeepsTheTopEvenAtHeightLimits() {
        for (mode in listOf(PlacementMode.NORMAL, PlacementMode.FLOATING)) {
            val value = KeyboardPlacement(mode = mode, bottomSpace = 100f)
            val before = value.frame(720f, 1000f)
            for (delta in listOf(-1000f, 1000f)) {
                val after = value.resizeVertical(720f, 1000f, delta, bottom = true).frame(720f, 1000f)
                assertEquals(before.top, after.top, .01f)
                assertTrue(after.top + after.height <= 1000.01f)
            }
        }
    }

    @Test fun topResizeKeepsTheBottomEvenAtHeightLimits() {
        for (mode in listOf(PlacementMode.NORMAL, PlacementMode.FLOATING)) {
            val value = KeyboardPlacement(mode = mode, bottomSpace = 100f, floatingY = .1f)
            val before = value.frame(720f, 1000f)
            for (delta in listOf(-1000f, 1000f)) {
                val after = value.resizeVertical(720f, 1000f, delta, bottom = false).frame(720f, 1000f)
                assertEquals(before.top + before.height, after.top + after.height, .01f)
                assertTrue(after.top >= 0f)
            }
        }
    }

    @Test fun fiveRowsFitInEveryModeWithoutChangingSavedKeySize() {
        for (mode in PlacementMode.entries) {
            val value = KeyboardPlacement(mode = mode)
            val four = value.frame(720f, 1000f)
            val five = value.frame(720f, 1000f, rows = 5)
            assertEquals(four.rowHeight, five.rowHeight, .01f)
            assertEquals(four.height + four.rowHeight, five.height, .01f)
            val small = value.frame(720f, 300f, rows = 5)
            assertTrue(49f + small.rowHeight * 5 + (if (mode == PlacementMode.FLOATING) 24f else 0f) <= small.height + .01f)
        }
    }

    @Test fun resizingFiveRowsKeepsTheOppositeEdgeFixed() {
        for (mode in listOf(PlacementMode.NORMAL, PlacementMode.FLOATING)) {
            val value = KeyboardPlacement(mode = mode, bottomSpace = 100f)
            val before = value.frame(720f, 1000f, rows = 5)
            val after = value.resizeVertical(720f, 1000f, -80f, bottom = true, rows = 5).frame(720f, 1000f, rows = 5)
            assertEquals(before.top, after.top, .01f)
            assertEquals(before.height - 80f, after.height, .01f)
        }
    }
}
