package io.github.aoguai.sesameag.hook.libxposed

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
        runtime.onModuleLoaded(this, param)
    }

    override fun onPackageReady(param: PackageReadyParam) {
        runtime.onPackageReady(this, param)
    }
}
