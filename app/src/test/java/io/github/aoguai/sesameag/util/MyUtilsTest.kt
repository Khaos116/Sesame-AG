package io.github.aoguai.sesameag.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MyUtilsTest {
    @Test
    fun `blank response uses an empty JSON object source`() {
        assertEquals("{}", MyUtils.jsonObjectSource(null))
        assertEquals("{}", MyUtils.jsonObjectSource("   "))
        assertEquals("{\"success\":true}", MyUtils.jsonObjectSource("{\"success\":true}"))
    }

    @Test
    fun `custom sync step stays in configured safe range`() {
        assertEquals(0, MyUtils.randomSyncStepTarget(0))
        repeat(1_000) {
            val target = MyUtils.randomSyncStepTarget(MyUtils.MIN_SYNC_STEP_COUNT)
            assertTrue(target in MyUtils.MIN_SYNC_STEP_COUNT..MyUtils.MAX_SYNC_STEP_COUNT)
        }
        assertEquals(
            30_000,
            MyUtils.resolveSyncStepTarget(30_000, MyUtils.MAX_SYNC_STEP_COUNT)
        )
    }

    @Test
    fun `green finance cooldown is account scoped and expires after configured duration`() {
        val now = 1_000_000L
        val until = MyUtils.greenFinanceCooldownUntil(now)

        assertEquals(30 * 60_000L, until - now)
        assertEquals(30L, MyUtils.greenFinanceCooldownRemainingMinutes(until, now))
        assertEquals(1L, MyUtils.greenFinanceCooldownRemainingMinutes(until, until - 1))
        assertEquals(0L, MyUtils.greenFinanceCooldownRemainingMinutes(until, until))
        assertNotEquals(
            MyUtils.greenFinanceCooldownKey("account-a"),
            MyUtils.greenFinanceCooldownKey("account-b")
        )
    }

    @Test
    fun `only local daily block response is treated as skipped behavior`() {
        assertTrue(MyUtils.isGreenFinanceDailyBlockedError(9999))
        assertFalse(MyUtils.isGreenFinanceDailyBlockedError(1009))
        assertFalse(MyUtils.isGreenFinanceDailyBlockedError(0))
    }
}
