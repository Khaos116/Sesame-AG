package io.github.aoguai.sesameag.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModuleStatusTest {
    @Test
    fun `supported hook runtime accepts LSPosed and FPA`() {
        assertTrue(ModuleStatus.isSupportedHookRuntime("LSPosed", 101))
        assertTrue(ModuleStatus.isSupportedHookRuntime("LSPosed", 102))
        assertTrue(ModuleStatus.isSupportedHookRuntime("FPA", 101))
        assertTrue(ModuleStatus.isSupportedHookRuntime("FPA", 102))
    }

    @Test
    fun `supported hook runtime rejects unknown and obsolete runtimes`() {
        assertFalse(ModuleStatus.isSupportedHookRuntime("Unknown", 102))
        assertFalse(ModuleStatus.isSupportedHookRuntime("FPA", 100))
        assertFalse(ModuleStatus.isSupportedHookRuntime("LSPosed", 100))
        assertFalse(ModuleStatus.isSupportedHookRuntime("LSPatch", 102))
        assertFalse(ModuleStatus.isSupportedLsposedFramework("FPA", 101))
    }
}
