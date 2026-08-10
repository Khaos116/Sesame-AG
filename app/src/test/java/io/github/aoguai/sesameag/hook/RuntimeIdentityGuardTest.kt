package io.github.aoguai.sesameag.hook

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeIdentityGuardTest {
    @Test
    fun `module uid accepts installed primary user and unassigned archive`() {
        assertTrue(RuntimeIdentityGuard.isPrimaryOrUnassignedModuleUid(10_519))
        assertTrue(RuntimeIdentityGuard.isPrimaryOrUnassignedModuleUid(-1))
    }

    @Test
    fun `module uid rejects secondary Android users and invalid negative values`() {
        assertFalse(RuntimeIdentityGuard.isPrimaryOrUnassignedModuleUid(1_010_519))
        assertFalse(RuntimeIdentityGuard.isPrimaryOrUnassignedModuleUid(-2))
    }
}
