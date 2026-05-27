package io.github.aoguai.sesameag.hook

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import io.github.aoguai.sesameag.SesameApplication
import io.github.aoguai.sesameag.util.Log
import io.github.aoguai.sesameag.util.maps.UserMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 复用好友中心的目标应用就绪判定。
 * 优先使用当前进程状态；若不可判定，则通过 HOOK_READY 广播握手做跨进程确认。
 */
object HookReadyChecker {
    private const val TAG = "HookReadyChecker"
    private const val DEFAULT_TIMEOUT_MS = 1_200L

    @JvmStatic
    fun isTargetAppReadyForRpc(
        expectedUserId: String? = UserMap.currentUid,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): Boolean {
        val normalizedExpectedUserId = expectedUserId?.trim().orEmpty()
        if (isReadyInCurrentProcess(normalizedExpectedUserId)) {
            return true
        }
        val context = SesameApplication.appContext ?: ApplicationHook.appContext ?: return false
        return isReadyByHookReadyBroadcast(context, normalizedExpectedUserId, timeoutMs)
    }

    @JvmStatic
    fun isCurrentProcessReadyForRpc(expectedUserId: String? = UserMap.currentUid): Boolean {
        return isReadyInCurrentProcess(expectedUserId?.trim().orEmpty())
    }

    private fun isReadyInCurrentProcess(expectedUserId: String): Boolean {
        if (ApplicationHookConstants.shouldBlockRpc() || ApplicationHook.rpcBridge == null) {
            return false
        }
        val loader = ApplicationHook.classLoader ?: return false
        val currentUserId = HookUtil.getUserId(loader)?.trim().orEmpty()
        if (currentUserId.isEmpty()) {
            return false
        }
        return expectedUserId.isEmpty() || expectedUserId == currentUserId
    }

    private fun isReadyByHookReadyBroadcast(context: Context, expectedUserId: String, timeoutMs: Long): Boolean {
        val appContext = context.applicationContext
        val ready = AtomicBoolean(false)
        val received = AtomicBoolean(false)
        val latch = CountDownLatch(1)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action != ApplicationHookConstants.BroadcastActions.HOOK_READY_RESULT) {
                    return
                }
                val resultUserId = intent.getStringExtra("userId").orEmpty().trim()
                if (expectedUserId.isNotEmpty() && resultUserId.isNotEmpty() && expectedUserId != resultUserId) {
                    return
                }
                ready.set(intent.getBooleanExtra("ready", false))
                received.set(true)
                latch.countDown()
            }
        }
        return try {
            ContextCompat.registerReceiver(
                appContext,
                receiver,
                IntentFilter(ApplicationHookConstants.BroadcastActions.HOOK_READY_RESULT),
                ContextCompat.RECEIVER_EXPORTED
            )
            appContext.sendBroadcast(Intent(ApplicationHookConstants.BroadcastActions.HOOK_READY).apply {
                putExtra("userId", expectedUserId)
            })
            latch.await(timeoutMs.coerceAtLeast(100L), TimeUnit.MILLISECONDS)
            received.get() && ready.get()
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "isReadyByHookReadyBroadcast err:", t)
            false
        } finally {
            runCatching { appContext.unregisterReceiver(receiver) }
        }
    }
}
