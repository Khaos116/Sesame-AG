package io.github.aoguai.sesameag.util

import org.junit.Assert.assertEquals
import org.junit.Test

class MyUtilsTest {
    @Test
    fun `blank response uses an empty JSON object source`() {
        assertEquals("{}", MyUtils.jsonObjectSource(null))
        assertEquals("{}", MyUtils.jsonObjectSource("   "))
        assertEquals("{\"success\":true}", MyUtils.jsonObjectSource("{\"success\":true}"))
    }
}
