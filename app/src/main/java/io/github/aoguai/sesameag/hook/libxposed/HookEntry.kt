package io.github.aoguai.sesameag.hook.libxposed

import android.util.Log
import io.github.aoguai.sesameag.hook.ApplicationHook
import io.github.aoguai.sesameag.util.MyUtils
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam

/**
 * Modern libxposed API 101 entry shared by FPA 3.8 and backward-compatible LSPosed runtimes.
 *
 * Both frameworks discover this class through `META-INF/xposed/java_init.list`; API 101 attaches
 * the framework before [onModuleLoaded] and supplies the target class loader in [onPackageReady].
 */
class HookEntry : XposedModule() {
    private val runtime = LibXposedRuntime(ApplicationHook())

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        MyUtils.CHANGE_KT3
        try {
            runtime.onModuleLoaded(this, param)
        } catch (t: Throwable) {
            log(Log.ERROR, "HookEntry", "onModuleLoaded failed: ${t.javaClass.simpleName}", t)
        }
    }

    override fun onPackageReady(param: PackageReadyParam) {
        try {
            runtime.onPackageReady(this, param)
        } catch (t: Throwable) {
            log(Log.ERROR, "HookEntry", "onPackageReady failed: ${t.javaClass.simpleName}", t)
        }
    }
}
