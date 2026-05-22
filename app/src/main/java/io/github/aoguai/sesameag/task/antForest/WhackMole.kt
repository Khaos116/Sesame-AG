package io.github.aoguai.sesameag.task.antForest

import io.github.aoguai.sesameag.data.Status
import io.github.aoguai.sesameag.data.StatusFlags
import io.github.aoguai.sesameag.util.Log
import io.github.aoguai.sesameag.util.ResChecker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.math.max

/**
 * 6秒拼手速打地鼠。
 */
object WhackMole {
    private const val TAG = "WhackMole"
    private const val SOURCE = "senlinguangchangdadishu"
    private val globalScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var isRunning = false

    suspend fun startSuspend(): Boolean = withContext(Dispatchers.IO) {
        if (isRunning) {
            Log.forest("⏭️ 打地鼠游戏正在运行中，跳过重复启动")
            return@withContext false
        }
        isRunning = true

        try {
            val executed = runWhackMole()
            if (executed) {
                Status.setFlagToday(StatusFlags.FLAG_ANTFOREST_WHACK_MOLE_EXECUTED)
            }
            return@withContext executed
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.printStackTrace(TAG, "打地鼠异常: ", e)
            return@withContext false
        } finally {
            isRunning = false
            Log.forest("🎮 打地鼠运行状态已重置")
        }
    }

    fun start() {
        globalScope.launch {
            startSuspend()
        }
    }

    private suspend fun runWhackMole(): Boolean {
        try {
            val startTs = System.currentTimeMillis()
            val response = JSONObject(AntForestRpcCall.startWhackMole(SOURCE))
            if (!ResChecker.checkRes(TAG, response)) {
                Log.forest(response.optString("resultDesc", "开始失败"))
                return false
            }
            if (!response.optBoolean("canPlayToday", true)) {
                Status.setFlagToday(StatusFlags.FLAG_ANTFOREST_WHACK_MOLE_EXECUTED)
                logTodaySettleInfo(response)
                return false
            }

            val moleInfoArray = response.optJSONArray("moleInfo")
            val token = response.optString("token")
            if (moleInfoArray == null || moleInfoArray.length() == 0 || token.isEmpty()) {
                Log.forest("🎮 拼手速未返回可结算地鼠信息，跳过")
                return false
            }

            val allMoleIds = mutableListOf<Long>()
            val bubbleMoleIds = mutableListOf<Long>()
            for (i in 0 until moleInfoArray.length()) {
                val mole = moleInfoArray.getJSONObject(i)
                val moleId = mole.getLong("id")
                allMoleIds.add(moleId)
                if (mole.has("bubbleId")) {
                    bubbleMoleIds.add(moleId)
                }
            }

            var hitCount = 0
            bubbleMoleIds.forEach { moleId ->
                try {
                    val whackResp = JSONObject(AntForestRpcCall.whackMole(moleId, token, SOURCE))
                    if (whackResp.optBoolean("success")) {
                        val energy = whackResp.optInt("energyAmount", 0)
                        hitCount++
                        Log.forest("森林能量⚡️[打地鼠:$moleId +${energy}g]")
                        if (hitCount < bubbleMoleIds.size) {
                            delay(100 + (0..200).random().toLong())
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Throwable) {
                }
            }

            val settlementIds = allMoleIds.map { it.toString() }
            val elapsedTime = System.currentTimeMillis() - startTs
            delay(max(0L, 6000L - elapsedTime - 200L))

            val settleResp = JSONObject(AntForestRpcCall.settlementWhackMole(token, settlementIds, SOURCE))
            if (ResChecker.checkRes(TAG, settleResp)) {
                val total = settleResp.optInt("totalEnergy", 0)
                Log.forest("森林能量⚡️[拼手速完成(打${settlementIds.size}个) 总能量+${total}g]")
                return true
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Log.forest("拼手速出错: ${t.message}")
        }
        return false
    }

    private fun logTodaySettleInfo(response: JSONObject) {
        val settleInfo = response.optJSONObject("currentDaySettleInfoVO")
        val totalEnergy = settleInfo?.optInt("totalEnergy", 0) ?: 0
        val totalFriendNums = settleInfo?.optInt("totalFriendNums", 0) ?: 0
        if (totalEnergy > 0 || totalFriendNums > 0) {
            Log.forest("🎮 拼手速今日次数已用尽，今日已结算${totalFriendNums}个好友，总能量+${totalEnergy}g")
        } else {
            Log.forest("🎮 拼手速今日次数已用尽，跳过")
        }
    }
}
