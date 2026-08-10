package io.github.aoguai.sesameag.hook

import org.junit.Assert.assertEquals
import org.junit.Test

class AccountSlotRegistryTest {
    @Test
    fun `five accounts can occupy executable slots`() {
        // This limit is the shared contract used by account admission and the migration UI.
        // Keeping it under test prevents a future upstream merge from silently restoring two slots.
        assertEquals(5, MAX_EXECUTABLE_ACCOUNT_SLOTS)
    }
}
