package io.github.aoguai.sesameag.hook.libxposed

import android.util.Log
import io.github.aoguai.sesameag.data.General
import io.github.aoguai.sesameag.hook.ApplicationHook
import io.github.aoguai.sesameag.hook.RuntimeIdentityGuard
import io.github.aoguai.sesameag.hook.XposedEnv
import io.github.aoguai.sesameag.util.ModuleStatus
import io.github.aoguai.sesameag.util.MyUtils
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam

/**
 * Bridges the API 101 lifecycle to the existing application hook runtime.
 *
 * FPA 3.8 implements the API 101 entry, hook builder and package-ready callbacks used here.
 * Runtime admission remains centralized in [ModuleStatus.isSupportedHookRuntime] so unknown
 * frameworks cannot gain workflow permission merely by exposing similarly named classes.
 * API 101 has no `detach()` method; package and process checks provide the lifecycle boundary.
 */
internal class LibXposedRuntime(
    private val applicationHook: ApplicationHook,
) {
    private var processName: String? = null
    private var active = false
    private var packageReady = false

    fun onModuleLoaded(module: XposedModule, param: ModuleLoadedParam) {
        if (processName != null) {
            module.log(Log.WARN, TAG, "Ignoring duplicate onModuleLoaded callback")
            return
        }

        val identityDecision = RuntimeIdentityGuard.verifyModuleLoaded(module.moduleApplicationInfo)
        if (!identityDecision.accepted) {
            module.log(Log.ERROR, TAG, "instance_rejected: ${identityDecision.reasonCode}")
            return
        }

        processName = param.processName
        val frameworkName = runCatching { module.frameworkName }.getOrDefault("Unknown")
        val apiVersion = runCatching { module.apiVersion }.getOrDefault(0)
        if (!ModuleStatus.isSupportedHookRuntime(frameworkName, apiVersion)) {
            module.log(
                Log.ERROR,
                TAG,
                "Unsupported runtime: $frameworkName API $apiVersion; requires LSPosed or FPA API ${ModuleStatus.MIN_SUPPORTED_LIBXPOSED_API}+",
            )
            return
        }

        MyUtils.CHANGE_KT3
        active = true
        applicationHook.attachLibXposedRuntime(module)
        val frameworkVersion = runCatching { module.frameworkVersion }.getOrDefault("unknown")
        val frameworkVersionCode = runCatching { module.frameworkVersionCode }.getOrDefault(-1L)
        val moduleProcess = runCatching { module.moduleApplicationInfo.processName }.getOrDefault("unknown")
        module.log(
            Log.INFO,
            TAG,
            "Initialized for process ${param.processName}; framework=$frameworkName $frameworkVersion $frameworkVersionCode api=$apiVersion module_process=$moduleProcess",
        )
    }

    fun onPackageReady(module: XposedModule, param: PackageReadyParam) {
        if (!active || packageReady || param.packageName != General.PACKAGE_NAME) return

        val targetProcessName = processName ?: run {
            module.log(Log.ERROR, TAG, "Package callback arrived before module runtime initialization")
            return
        }
        val identityDecision =
            RuntimeIdentityGuard.verifyPackageReady(
                applicationInfo = param.applicationInfo,
                packageName = param.packageName,
                processName = targetProcessName,
            )
        if (!identityDecision.accepted) {
            module.log(Log.ERROR, TAG, "instance_rejected: ${identityDecision.reasonCode}")
            return
        }
        packageReady = true

        try {
            XposedEnv.classLoader = param.classLoader
            XposedEnv.appInfo = param.applicationInfo
            XposedEnv.packageName = param.packageName
            XposedEnv.processName = targetProcessName
            applicationHook.loadPackage(param)
            module.log(Log.INFO, TAG, "Hooked ${param.packageName} in process $targetProcessName via onPackageReady")
        } catch (t: Throwable) {
            module.log(Log.ERROR, TAG, "Hook failed - ${t.message}", t)
        }
    }

    private companion object {
        const val TAG = "LibXposedRuntime"
    }
}
