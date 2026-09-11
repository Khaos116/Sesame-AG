package io.github.aoguai.sesameag.hook

import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import io.github.aoguai.sesameag.data.General
import io.github.aoguai.sesameag.entity.RpcEntity
import io.github.aoguai.sesameag.util.DailySharedPreferences
import io.github.aoguai.sesameag.util.Files
import io.github.aoguai.sesameag.util.MyUtils
import io.github.aoguai.sesameag.util.TimeUtil
import io.github.aoguai.sesameag.util.WorkflowRootGuard
import io.github.aoguai.sesameag.util.maps.UserMap
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf
import java.io.File
import java.time.Instant
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE, application = Application::class)
class MyFixRegressionTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Before
    fun resetSandboxAccounts() {
        // Files caches its directory across Robolectric tests; clean only that temporary sandbox.
        val directory = Files.CONFIG_DIR.canonicalFile
        check(directory.toPath().startsWith(File(requireNotNull(System.getProperty("java.io.tmpdir"))).canonicalFile.toPath()))
        check(directory.name == "config" && directory.parentFile?.name == "sesame-AG")
        directory.listFiles()?.forEach { check(it.deleteRecursively()) }
        MyUtils::class.java.getDeclaredField("mSpMap").apply { isAccessible = true }
            .get(null).let { (it as MutableMap<*, *>).clear() }
        UserMap.setCurrentUserId(null)
    }

    private fun account(uid: String) {
        File(Files.CONFIG_DIR, uid).apply { mkdirs() }.also {
            File(it, "config_v2.json").writeText("{}")
            File(it, "self.json").writeText("""{"userId":"$uid"}""")
        }
    }

    private fun field(owner: Class<*>, name: String, value: Any?) {
        owner.getDeclaredField(name).apply { isAccessible = true }.set(null, value)
    }

    @Test
    fun `first runtime slot is confirmed only after its account files are persisted`() {
        val admission = AccountSlotRegistry.admitRuntimeUser("new-account")
        assertTrue(admission is AccountSlotAdmission.Allowed && admission.requiresConfirmation)
        assertFalse(AccountSlotRegistry.isExecutableUser("new-account"))
        assertEquals(AccountSlotRuntimeConfirmation.PendingPersistence, AccountSlotRegistry.confirmRuntimeUser("new-account"))
        account("new-account")
        assertEquals(AccountSlotRuntimeConfirmation.Confirmed, AccountSlotRegistry.confirmRuntimeUser("new-account"))
        assertTrue(AccountSlotRegistry.isExecutableUser("new-account"))
    }

    @Test
    fun `five historical accounts execute and a sixth is refused`() {
        (1..6).forEach { account("account-$it") }
        assertFalse(AccountSlotRegistry.snapshot().isReady)
        (1..5).forEach { index ->
            assertTrue(AccountSlotRegistry.addExecutableSlot(null, "account-$index").replaced)
        }
        repeat(2) {
            (1..5).forEach { index ->
                val uid = "account-$index"
                UserMap.setCurrentUserId(uid)
                assertTrue(AccountSlotRegistry.admitRuntimeUser(uid) is AccountSlotAdmission.Allowed)
                assertTrue(AccountSlotRegistry.isExecutableUser(uid))
            }
        }
        assertEquals("account_slot_full", AccountSlotRegistry.addExecutableSlot(null, "account-6").reasonCode)
        assertEquals(AccountSlotAdmission.Denied("account_slot_full"), AccountSlotRegistry.admitRuntimeUser("account-6"))
        assertFalse(AccountSlotRegistry.isExecutableUser("account-6"))
    }

    @Test
    fun `daily RPC blocks are stable account scoped and expire in Beijing time`() {
        ApplicationHook.appContext = context
        UserMap.setCurrentUserId("account-1")
        val method = "example.task.query"
        val request = RpcEntity(method, "[]")
        assertFalse(MyUtils.getRpcTodayIsError(request))
        request.responseString = """{"success":false,"errorTip":"1009"}"""
        MyUtils.checkRpcTodayIsError(request)
        assertTrue(MyUtils.getRpcTodayIsError(RpcEntity(method, "[]")))
        (2..5).forEach {
            UserMap.setCurrentUserId("account-$it")
            assertFalse(MyUtils.getRpcTodayIsError(RpcEntity(method, "[]")))
        }
        UserMap.setCurrentUserId("account-1")
        val farm = RpcEntity("com.alipay.antfarm.query", "[]").apply {
            responseString = """{"error":1009,"errorMessage":"系统繁忙"}"""
        }
        MyUtils.checkRpcTodayIsError(farm)
        assertFalse(MyUtils.getRpcTodayIsError(farm))
        val prefs = context.getSharedPreferences("DailyCachePrefs_UID_account-1", Context.MODE_PRIVATE)
        prefs.edit().putString("global_last_saved_date", "20000101").commit()
        assertFalse(MyUtils.getRpcTodayIsError(request))
        val today = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).apply {
            timeZone = MyUtils.getInstance().timeZone
        }.format(java.util.Date())
        assertEquals(today, prefs.getString("global_last_saved_date", null))
        DailySharedPreferences(context, "account-1").putInt("retry", 3)
        assertEquals(3, DailySharedPreferences(context, "account-1").getInt("retry"))
        assertEquals(0, DailySharedPreferences(context, "account-2").getInt("retry"))

        // The response arrives after the current account changed, but must retain its original owner.
        UserMap.setCurrentUserId("account-2")
        MyUtils.checkRpcTodayIsError(request, "account-1")
        assertFalse(MyUtils.getRpcTodayIsError(request))
        assertTrue(MyUtils.getRpcTodayIsError(request, "account-1"))
    }

    @Test
    fun `FPA executes without module service only after identity hook and slot checks`() {
        (1..5).forEach { account("account-$it") }
        ApplicationHook.appContext = context
        ApplicationHook.classLoader = javaClass.classLoader
        UserMap.setCurrentUserId("account-1")
        val runtimeClass = Class.forName("${ApplicationHook::class.java.name}\$Companion\$FrameworkRuntimeInfo")
        val runtime = runtimeClass.declaredConstructors.single { it.parameterCount == 5 }.apply {
            isAccessible = true
        }.newInstance("FPA", "3.8", 38L, 101, 0L)
        field(ApplicationHook::class.java, "frameworkRuntimeInfo", runtime)
        field(ApplicationHook::class.java, "isHooked", true)
        val target = context.applicationInfo.apply {
            packageName = General.PACKAGE_NAME
            processName = General.PACKAGE_NAME
            uid = 10_123
            sourceDir = "/data/app/alipay/base.apk"
        }
        val targetContext = object : android.content.ContextWrapper(context) {
            override fun getPackageName() = General.PACKAGE_NAME
            override fun getApplicationInfo() = target
        }
        shadowOf(context.packageManager).installPackage(android.content.pm.PackageInfo().apply {
            packageName = General.PACKAGE_NAME
            applicationInfo = target
        })
        org.robolectric.shadows.ShadowApplication.setProcessName(General.PACKAGE_NAME)
        assertTrue(RuntimeIdentityGuard.verifyModuleLoaded(ApplicationInfo().apply {
            packageName = General.MODULE_PACKAGE_NAME
            uid = -1
            sourceDir = "/data/module.apk"
        }).accepted)
        assertTrue(RuntimeIdentityGuard.verifyPackageReady(target, General.PACKAGE_NAME, General.PACKAGE_NAME).accepted)
        val attached = RuntimeIdentityGuard.verifyApplicationAttach(targetContext, false)
        assertTrue(attached.reasonCode, attached.accepted)
        (1..5).forEach {
            val uid = "account-$it"
            UserMap.setCurrentUserId(uid)
            assertTrue(WorkflowRootGuard.isExecutionAllowed())
            val session = AccountSessionCoordinator.applySession(
                context = null,
                userId = uid,
                activeUserSnapshot = io.github.aoguai.sesameag.entity.UserEntity.UserDto(userId = uid).toEntity(),
                legalAccepted = true,
                workflowAllowed = true,
                reason = "myfix_regression",
            )
            assertEquals(uid, session.userId)
            assertTrue(session.workflowAllowed)
        }
        UserMap.setCurrentUserId("account-1")
        val rpc = RpcEntity("example.task.query", "[]").apply {
            responseString = """{"error":1009}"""
        }
        MyUtils.checkRpcTodayIsError(rpc)
        val blocked = io.github.aoguai.sesameag.hook.rpc.bridge.AriverRpcBridge()
            .requestString(RpcEntity("example.task.query", "[]"), 1, 0)
        assertEquals(9999, org.json.JSONObject(blocked!!).getInt("error"))
        UserMap.setCurrentUserId("unselected")
        assertFalse(WorkflowRootGuard.isExecutionAllowed())
        UserMap.setCurrentUserId("account-1")
        field(ApplicationHook::class.java, "isHooked", false)
        assertFalse(WorkflowRootGuard.isExecutionAllowed())
        field(ApplicationHook::class.java, "isHooked", true)
        assertFalse(RuntimeIdentityGuard.verifyPackageReady(
            ApplicationInfo(target).apply { uid += 100_000 }, General.PACKAGE_NAME, General.PACKAGE_NAME,
        ).accepted)
        assertFalse(WorkflowRootGuard.isExecutionAllowed())
        assertFalse(RuntimeIdentityGuard.verifyPackageReady(target, "other.package", General.PACKAGE_NAME).accepted)
        assertFalse(RuntimeIdentityGuard.verifyPackageReady(target, General.PACKAGE_NAME, "${General.PACKAGE_NAME}:other").accepted)
    }

    @Test
    fun `business date formatting ignores device timezone`() {
        val previous = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
            val midnight = Instant.parse("2026-09-11T16:00:00Z").toEpochMilli()
            assertEquals("2026-09-12 00:00", TimeUtil.getFormatTime(midnight, "yyyy-MM-dd HH:mm"))
            assertEquals("12日00:00:00", TimeUtil.getCommonDate(midnight))
        } finally {
            TimeZone.setDefault(previous)
        }
    }
}
