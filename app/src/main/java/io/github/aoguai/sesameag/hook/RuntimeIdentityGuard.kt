package io.github.aoguai.sesameag.hook

import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import io.github.aoguai.sesameag.data.General
import io.github.aoguai.sesameag.util.MyUtils

/**
 * Verifies that the module is running against the one supported Android user and target process.
 * Main-process execution is trusted separately from capture-only Alipay lite processes.
 */
data class RuntimeIdentity(
    val moduleUid: Int,
    val targetUid: Int,
    val targetSourceDir: String,
    val targetProcessName: String,
)

data class RuntimeIdentityDecision(
    val accepted: Boolean,
    val reasonCode: String? = null,
)

object RuntimeIdentityGuard {
    private data class ModuleSnapshot(
        val uid: Int,
        val sourceDir: String,
    )

    private data class TargetSnapshot(
        val uid: Int,
        val sourceDir: String,
        val processName: String,
    )

    private const val PRIMARY_ANDROID_USER_ID = 0
    private const val ANDROID_PER_USER_RANGE = 100_000
    private const val UNASSIGNED_ARCHIVE_UID = -1

    @Volatile
    private var moduleSnapshot: ModuleSnapshot? = null

    @Volatile
    private var targetSnapshot: TargetSnapshot? = null

    @Volatile
    private var attachedIdentity: RuntimeIdentity? = null

    @Volatile
    private var lastDecision = RuntimeIdentityDecision(false, "identity_not_verified")

    @Synchronized
    fun verifyModuleLoaded(applicationInfo: ApplicationInfo): RuntimeIdentityDecision {
        val packageName = applicationInfo.packageName.orEmpty()
        val sourceDir = applicationInfo.sourceDir.orEmpty()
        val decision = when {
            packageName != General.MODULE_PACKAGE_NAME -> reject("module_package_mismatch")
            !isPrimaryOrUnassignedModuleUid(applicationInfo.uid) -> reject("module_non_primary_user")
            sourceDir.isBlank() -> reject("module_source_missing")
            else -> {
                moduleSnapshot = ModuleSnapshot(applicationInfo.uid, sourceDir)
                accept()
            }
        }
        lastDecision = decision
        return decision
    }

    @Synchronized
    fun verifyPackageReady(
        applicationInfo: ApplicationInfo,
        packageName: String?,
        processName: String?,
    ): RuntimeIdentityDecision {
        val module = moduleSnapshot ?: return rejectAndStore("module_identity_missing")
        val appPackageName = applicationInfo.packageName.orEmpty()
        val appProcessName = applicationInfo.processName.orEmpty()
        val targetProcessName = processName ?: return rejectAndStore("target_process_missing")
        val sourceDir = applicationInfo.sourceDir.orEmpty()
        val userId = androidUserId(applicationInfo.uid)
        val decision = when {
            packageName != General.PACKAGE_NAME -> reject("target_package_mismatch")
            appPackageName != General.PACKAGE_NAME -> reject("target_application_package_mismatch")
            !isSupportedTargetProcess(targetProcessName) -> reject("target_unsupported_process")
            targetProcessName == General.PACKAGE_NAME && appProcessName != General.PACKAGE_NAME ->
                reject("target_application_process_mismatch")
            userId != PRIMARY_ANDROID_USER_ID -> reject("target_non_primary_user")
            sourceDir.isBlank() -> reject("target_source_missing")
            !isPrimaryOrUnassignedModuleUid(module.uid) -> reject("module_non_primary_user")
            else -> {
                targetSnapshot = TargetSnapshot(applicationInfo.uid, sourceDir, targetProcessName)
                attachedIdentity = null
                accept()
            }
        }
        lastDecision = decision
        return decision
    }

    @Suppress("DEPRECATION")
    @Synchronized
    fun verifyApplicationAttach(
        context: Context,
        verifyInstalledModulePackage: Boolean = true,
    ): RuntimeIdentityDecision {
        val module = moduleSnapshot ?: return rejectAndStore("module_identity_missing")
        // FPA 3.8 supplies moduleApplicationInfo from the selected module APK, so that snapshot is
        // still mandatory. Only the PackageManager lookup of a separately installed module is
        // skipped, because a patched target may not have one installed under the module package.
        if (!verifyInstalledModulePackage) MyUtils.CHANGE_KT3
        val target = targetSnapshot ?: return rejectAndStore("target_identity_missing")
        val contextInfo = context.applicationInfo ?: return rejectAndStore("target_context_missing")
        val decision = runCatching {
            val targetInfo = context.packageManager.getApplicationInfo(General.PACKAGE_NAME, 0)
            val moduleInfo =
                if (verifyInstalledModulePackage) {
                    context.packageManager.getApplicationInfo(General.MODULE_PACKAGE_NAME, 0)
                } else {
                    null
                }
            when {
                context.packageName != General.PACKAGE_NAME -> reject("target_context_package_mismatch")
                !matchesTargetApplication(contextInfo, target) -> reject("target_context_mismatch")
                Application.getProcessName() != target.processName -> reject("target_runtime_process_mismatch")
                !matchesTargetApplication(targetInfo, target) -> reject("target_package_manager_mismatch")
                verifyInstalledModulePackage && moduleInfo?.packageName != General.MODULE_PACKAGE_NAME -> reject("module_package_manager_mismatch")
                verifyInstalledModulePackage && moduleInfo?.uid != module.uid -> reject("module_uid_mismatch")
                verifyInstalledModulePackage && androidUserId(moduleInfo?.uid ?: -1) != PRIMARY_ANDROID_USER_ID -> reject("module_non_primary_user")
                verifyInstalledModulePackage && moduleInfo?.sourceDir.orEmpty() != module.sourceDir -> reject("module_source_mismatch")
                else -> {
                    attachedIdentity = RuntimeIdentity(
                        moduleUid = module.uid,
                        targetUid = target.uid,
                        targetSourceDir = target.sourceDir,
                        targetProcessName = target.processName,
                    )
                    accept()
                }
            }
        }.getOrElse { reject("package_metadata_unavailable") }
        lastDecision = decision
        return decision
    }

    fun isPackageReady(): Boolean =
        lastDecision.accepted && targetSnapshot != null

    fun isTrustedForExecution(): Boolean =
        lastDecision.accepted && attachedIdentity?.targetProcessName == General.PACKAGE_NAME

    fun isMainProcess(): Boolean =
        targetSnapshot?.processName == General.PACKAGE_NAME

    fun isCaptureOnlyProcess(): Boolean =
        targetSnapshot?.processName?.let(::isCaptureOnlyProcessName) == true

    fun lastReasonCode(): String? = lastDecision.reasonCode

    fun trustedIdentity(): RuntimeIdentity? = attachedIdentity

    private fun isSupportedTargetProcess(processName: String?): Boolean =
        processName == General.PACKAGE_NAME || isCaptureOnlyProcessName(processName)

    private fun isCaptureOnlyProcessName(processName: String?): Boolean {
        val prefix = "${General.PACKAGE_NAME}:lite"
        if (processName.isNullOrBlank() || !processName.startsWith(prefix)) {
            return false
        }
        val suffix = processName.removePrefix(prefix)
        return suffix.isNotEmpty() && suffix.all { it in '0'..'9' }
    }

    private fun matchesTargetApplication(info: ApplicationInfo, target: TargetSnapshot): Boolean =
        info.packageName == General.PACKAGE_NAME &&
            info.uid == target.uid &&
            androidUserId(info.uid) == PRIMARY_ANDROID_USER_ID &&
            info.sourceDir.orEmpty() == target.sourceDir

    /** UserHandle.getUserId is hidden from this module's compile SDK; Android reserves 100000 UIDs per user. */
    private fun androidUserId(uid: Int): Int =
        if (uid >= 0) uid / ANDROID_PER_USER_RANGE else -1

    /**
     * FPA loads a module as an APK archive and supplies the resulting [ApplicationInfo]. Android
     * assigns `-1` to that archive because it is not installed inside the patched target. Accept
     * only that one unassigned value; installed modules must still belong to Android's main user,
     * while secondary-user UIDs and every other negative value remain rejected.
     */
    internal fun isPrimaryOrUnassignedModuleUid(uid: Int): Boolean {
        if (uid == UNASSIGNED_ARCHIVE_UID) {
            MyUtils.CHANGE_KT3
            return true
        }
        return androidUserId(uid) == PRIMARY_ANDROID_USER_ID
    }

    private fun accept(): RuntimeIdentityDecision = RuntimeIdentityDecision(true)

    private fun reject(reasonCode: String): RuntimeIdentityDecision {
        attachedIdentity = null
        return RuntimeIdentityDecision(false, reasonCode)
    }

    private fun rejectAndStore(reasonCode: String): RuntimeIdentityDecision =
        reject(reasonCode).also { lastDecision = it }
}
