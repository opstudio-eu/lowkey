package com.rheerkens.androidkeyboard.keyboard.ui

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Test

class AbovePopupTest {
    @Test fun menuExtendsAboveTheShortImeWindowInsteadOfCoveringNavigation() {
        val position = AboveAnchor(gap = 6, endInset = null, margin = 8).calculatePosition(
            IntRect(0, 0, 360, 48), IntSize(360, 72), LayoutDirection.Ltr, IntSize(280, 156),
        )
        assertEquals(IntOffset(40, -162), position)
    }

    @Test fun menuRemainsInsideTheHorizontalWindowEdges() {
        val position = AboveAnchor(gap = 6, endInset = 0, margin = 8).calculatePosition(
            IntRect(0, 0, 58, 48), IntSize(320, 72), LayoutDirection.Ltr, IntSize(280, 156),
        )
        assertEquals(8, position.x)
    }
}
