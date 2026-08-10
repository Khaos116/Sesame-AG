package io.github.aoguai.sesameag.util

/**
 * 模块状态与 libxposed 运行时解析。
 *
 * 只使用框架通过 libxposed 提供的官方名称和 API 版本。
 */
object ModuleStatus {
    // This object also runs in the standalone settings process, where the compileOnly API jar is absent.
    const val MIN_SUPPORTED_LIBXPOSED_API = 102

    private const val UNKNOWN_FRAMEWORK = "Unknown"

    enum class FrameworkCategory {
        LSPOSED,
        PATCH_EMBEDDED,
        UNSUPPORTED,
    }

    data class FrameworkInfo(
        val displayName: String,
        val category: FrameworkCategory,
    )

    fun resolveFrameworkInfo(officialFrameworkName: String?): FrameworkInfo {
        val officialName = officialFrameworkName?.trim().orEmpty()
        val displayName = officialName.takeIf { it.isNotBlank() } ?: UNKNOWN_FRAMEWORK
        return FrameworkInfo(displayName, classifyFrameworkName(displayName))
    }

    fun classifyFrameworkName(frameworkName: String?): FrameworkCategory {
        return when (frameworkName?.trim()?.lowercase()) {
            "lsposed" -> FrameworkCategory.LSPOSED
            "fpa" -> {
                MyUtils.CHANGE_KT3
                FrameworkCategory.PATCH_EMBEDDED
            }
            else -> FrameworkCategory.UNSUPPORTED
        }
    }

    fun isSupportedLsposedFramework(frameworkName: String?, apiVersion: Int): Boolean {
        return apiVersion >= MIN_SUPPORTED_LIBXPOSED_API &&
            classifyFrameworkName(frameworkName) == FrameworkCategory.LSPOSED
    }

    /**
     * Runtime gate shared by hook installation and workflow execution.
     *
     * FPA 3.8 was inspected locally and provides the same API 102 interface used by LSPosed,
     * including the modern hook builder and package-ready callback. Unknown framework names and
     * either supported framework below API 102 remain rejected.
     */
    fun isSupportedHookRuntime(frameworkName: String?, apiVersion: Int): Boolean =
        when (classifyFrameworkName(frameworkName)) {
            FrameworkCategory.LSPOSED -> apiVersion >= MIN_SUPPORTED_LIBXPOSED_API
            FrameworkCategory.PATCH_EMBEDDED -> apiVersion >= MIN_SUPPORTED_LIBXPOSED_API
            FrameworkCategory.UNSUPPORTED -> false
        }

}
