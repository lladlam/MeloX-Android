package com.lladlam.melox.ui.glass

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class MeloXSettingsMenuHeightTest {
    @Test
    fun shortMenusKeepTheirNaturalHeight() {
        assertEquals(108.dp, meloXSettingsMenuHeight(2))
    }

    @Test
    fun longMenusLeaveRoomForAScrollingViewport() {
        assertEquals(328.dp, meloXSettingsMenuHeight(7))
        assertEquals(360.dp, meloXSettingsMenuHeight(50))
    }
}
