package com.rheerkens.androidkeyboard.keyboard

import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandFanTest {
    private val sectors = CommandFan.sectors()

    /** The command under a finger [r] dp from the button's centre at [deg] degrees (90 = up, 180 = left). */
    private fun at(deg: Double, r: Double, sectors: List<FanSector> = this.sectors): FanCommand? {
        val rad = Math.toRadians(deg)
        return CommandFan.hit(sectors, (r * cos(rad)).toFloat(), (-r * sin(rad)).toFloat())?.command
    }

    @Test
    fun theOriginalLayoutWithTheKeyboardSplitOverBothColumnsOfTheOuterRing() {
        assertEquals(FanCommand.WORDS, at(90.0, 80.0))
        assertEquals(FanCommand.SPACE, at(118.0, 60.0))
        assertEquals(FanCommand.CLEAR_CLIPBOARD, at(118.0, 130.0))
        assertEquals(FanCommand.ACTION, at(160.0, 60.0))
        assertEquals(FanCommand.NEW_LINE, at(160.0, 130.0))
        assertEquals(FanCommand.NUMBERS_ONCE, at(118.0, 185.0))
        assertEquals(FanCommand.KEYBOARD_ONCE, at(148.0, 185.0))
        assertEquals(FanCommand.KEYBOARD_ONCE, at(174.0, 185.0))
    }

    @Test
    fun wordsReachesTheOuterEdgeOfTheFan() {
        val words = sectors.single { it.command == FanCommand.WORDS }
        assertEquals(CommandFan.THIRD, words.outer)
        // Every wedge that ends on the fan's edge (and so catches an overshoot) ends on the same arc.
        assertEquals(setOf(CommandFan.THIRD), sectors.filter { it.reach > it.outer }.map { it.outer }.toSet())
        assertEquals(FanCommand.WORDS, at(90.0, 200.0))
        assertEquals(FanCommand.WORDS, at(82.0, 205.0))
        assertEquals(FanCommand.WORDS, at(98.0, 225.0))
        assertNull(at(90.0, 240.0))
    }

    @Test
    fun wordsMeetsTheUpLeftColumnInEveryRing() {
        for ((r, upLeft) in listOf(60.0 to FanCommand.SPACE, 130.0 to FanCommand.CLEAR_CLIPBOARD, 185.0 to FanCommand.NUMBERS_ONCE)) {
            assertEquals(FanCommand.WORDS, at(99.0, r))
            assertEquals(upLeft, at(101.0, r))
            // Its right edge stays just past vertical.
            assertEquals(FanCommand.WORDS, at(81.0, r))
            assertNull(at(79.0, r))
        }
    }

    @Test
    fun wordsCatchesTheSameOvershootAsTheOuterRing() {
        assertEquals(FanCommand.WORDS, CommandFan.hit(sectors, 0f, -233.9f)?.command)
        assertNull(CommandFan.hit(sectors, 0f, -234f))
        assertEquals(FanCommand.WORDS, at(81.0, 220.0))
        assertNull(at(79.0, 220.0))
    }

    @Test
    fun wordsIsTheSameAfterAClear() {
        val words = sectors.single { it.command == FanCommand.WORDS }
        assertEquals(words, CommandFan.sectors(undoClear = true).single { it.command == FanCommand.WORDS })
    }

    @Test
    fun newLineIsAlwaysThere() {
        assertTrue(sectors.any { it.command == FanCommand.NEW_LINE })
        assertTrue(CommandFan.sectors(undoClear = true).any { it.command == FanCommand.NEW_LINE })
    }

    @Test
    fun releasingOnTheButtonCancelsAndTheWedgesStartAtItsEdge() {
        assertNull(at(160.0, 15.0))
        assertNull(at(90.0, 22.0))
        assertEquals(FanCommand.ACTION, at(160.0, 24.0))
        assertEquals(CommandFan.BUTTON + 2f, CommandFan.DEAD_ZONE)
    }

    @Test
    fun aPressNearTheButtonsEdgeWidensTheCancelZone() {
        assertEquals(CommandFan.DEAD_ZONE, CommandFan.deadZoneFor(5f))
        assertEquals(26f, CommandFan.deadZoneFor(20f))
        val rad = Math.toRadians(160.0)
        assertNull(CommandFan.hit(sectors, (24 * cos(rad)).toFloat(), (-24 * sin(rad)).toFloat(), CommandFan.deadZoneFor(20f)))
    }

    @Test
    fun overshootingTheOuterRingStillCounts() {
        assertEquals(FanCommand.KEYBOARD_ONCE, at(170.0, 225.0))
        assertEquals(FanCommand.NUMBERS_ONCE, at(110.0, 225.0))
        assertNull(at(170.0, 260.0))
    }

    @Test
    fun aLeftwardDragThatDipsBelowTheBarStillRunsTheAction() {
        assertEquals(FanCommand.ACTION, at(184.0, 70.0))
        assertNull(at(200.0, 70.0))
    }

    @Test
    fun afterAClearUndoClearTakesClearClipsPlace() {
        val undo = CommandFan.sectors(undoClear = true)
        assertEquals(FanCommand.UNDO_CLEAR, at(118.0, 130.0, undo))
        assertTrue(undo.none { it.command == FanCommand.CLEAR_CLIPBOARD })
        assertTrue(sectors.none { it.command == FanCommand.UNDO_CLEAR })
    }

    @Test
    fun angles() {
        assertEquals(90f, CommandFan.angleOf(0f, -10f), 0.01f)
        assertEquals(180f, CommandFan.angleOf(-10f, 0f), 0.01f)
        assertEquals(270f, CommandFan.angleOf(0f, 10f), 0.01f)
    }

    @Test
    fun aSteepSlideUpStartsTheWordSliderDirectly() {
        assertTrue(CommandFan.isSliderStart(0f, -10f, 8f))
        assertTrue(CommandFan.isSliderStart(-3f, -10f, 8f))
        assertFalse(CommandFan.isSliderStart(-10f, -10f, 8f))
        assertFalse(CommandFan.isSliderStart(0f, -6f, 8f))
        assertFalse(CommandFan.isSliderStart(0f, 10f, 8f))
    }
}
