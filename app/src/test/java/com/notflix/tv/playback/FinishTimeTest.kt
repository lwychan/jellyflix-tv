package com.notflix.tv.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FinishTimeTest {
    private val minute = 60_000L

    @Test
    fun finishesWhenTheRemainingTimeHasPassed() {
        // 40 min in, 90 min long: 50 min left.
        assertEquals(1_000_000L + 50 * minute, finishTimeMs(1_000_000L, 40 * minute, 90 * minute))
    }

    @Test
    fun unknownLengthGivesNoTime() {
        assertNull(finishTimeMs(1_000_000L, 0, 0))
        assertNull(finishTimeMs(1_000_000L, 5 * minute, -1))
    }

    @Test
    fun pastTheEndCountsAsNow() {
        assertEquals(1_000_000L, finishTimeMs(1_000_000L, 95 * minute, 90 * minute))
    }
}
