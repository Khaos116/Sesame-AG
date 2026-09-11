package io.github.aoguai.sesameag.util

import android.content.Context
import io.github.aoguai.sesameag.entity.RpcEntity
import io.github.aoguai.sesameag.hook.ApplicationHook
import io.github.aoguai.sesameag.util.maps.UserMap
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Calendar
import java.util.TimeZone

/**
 * 替换：
 *     JSONObject\(   ->    MyUtils.myJSONObject(
 *    new JSONObject\(    ->    MyUtils.myJSONObject(
 *    \.getString\(   ->    .optString(
 *    \.getInt\(      ->    .optInt(
 *    \.getLong\(     ->    .optLong(
 *    \.getString\(\"propName\"\)   ->    .optString("propName","")
 *    \.optString\(\"propName\"\)   ->    .optString("propName","")
 *    Calendar\.getInstance\(\)   ->    MyUtils.getInstance()
 * Author:Khaos116
 * Date:2026/1/20
 * Time:15:41
 */
object MyUtils {
  //为了方便快速找到处理本地修改的代码
  const val CHANGE_KT1 = "1_绿色经营单项屏蔽与封控冷却"
  const val CHANGE_KT2 = "2_当天异常不再执行_健康岛泡泡"
  const val CHANGE_KT3 = "3_支持免Root运行_FPA"
  const val CHANGE_KT4 = "4_同步步数限制在19853至21423"
  //const val CHANGE_KT5 = "5"
  //const val CHANGE_KT6 = "6"
  //const val CHANGE_KT7 = "7"
  //const val CHANGE_KT8 = "8"
  //const val CHANGE_KT9 = "9"
  //const val CHANGE_KT10 = "10"
  //const val CHANGE_KT11 = "11"
  //const val CHANGE_KT12 = "12"
  //const val CHANGE_KT13 = "13"
  //const val CHANGE_KT14 = "14"
  //const val CHANGE_KT15 = "15"
  //const val CHANGE_KT16 = "16"
  //const val CHANGE_KT17 = "17"
  //const val CHANGE_KT18 = "18"
  //const val CHANGE_KT19 = "19"
  //const val CHANGE_KT20 = "20"
  //const val CHANGE_KT21 = "21"
  //const val CHANGE_KT22 = "22"
  //const val CHANGE_KT23 = "23"
  //const val CHANGE_KT24 = "24"
  //const val CHANGE_KT25 = "25"
  //const val CHANGE_KT26 = "26"
  //const val CHANGE_KT27 = "27"
  //const val CHANGE_KT28 = "28"
  //const val CHANGE_KT29 = "29"
  //const val CHANGE_KT30 = "30"

  @JvmStatic
  fun getInstance(): Calendar {
    return Calendar.getInstance(TimeZone.getTimeZone("GMT+8"))
  }

  @JvmStatic
  fun myJSONObject(value: String?): JSONObject = JSONObject(jsonObjectSource(value))

  internal fun jsonObjectSource(value: String?): String = value?.takeUnless { it.isBlank() } ?: "{}"

  /** 自定义同步步数允许的最小目标。 */
  const val MIN_SYNC_STEP_COUNT = 19_853

  /** 自定义同步步数允许的最大目标。 */
  const val MAX_SYNC_STEP_COUNT = 21_423

  /**
   * 根据配置生成本次同步的目标步数。
   * 配置为 0 或负数时关闭同步；启用后结果始终位于 19853～21423。
   */
  fun randomSyncStepTarget(configuredStep: Int): Int {
    if (configuredStep <= 0) return 0
    val lowerBound = configuredStep.coerceIn(MIN_SYNC_STEP_COUNT, MAX_SYNC_STEP_COUNT)
    return RandomUtil.nextInt(lowerBound, MAX_SYNC_STEP_COUNT + 1)
  }

  /**
   * 合并真实步数与自定义目标，避免同步操作降低设备已有的真实步数。
   * 真实步数未超过上限时返回受限目标；超过上限时保留真实步数且不再增加。
   */
  fun resolveSyncStepTarget(originStep: Int, targetStep: Int): Int =
    maxOf(originStep.coerceAtLeast(0), targetStep.coerceIn(0, MAX_SYNC_STEP_COUNT))

  /** 绿色经营发生封控后的冷却分钟数，可按需调整。 */
  const val GREEN_FINANCE_COOLDOWN_MINUTES = 30L

  private const val GREEN_FINANCE_COOLDOWN_KEY = "GREEN_FINANCE_COOLDOWN_UNTIL"
  private const val GREEN_FINANCE_SUBMIT_TICK_METHOD =
    "com.alipay.mcaplatformunit.common.mobile.newservice.GreenFinanceTickService.submitTick"

  /** 返回从指定时刻开始计算的绿色经营冷却截止时间。 */
  internal fun greenFinanceCooldownUntil(nowMs: Long): Long =
    nowMs + GREEN_FINANCE_COOLDOWN_MINUTES * 60_000L

  /** 生成账号独立的绿色经营冷却存储键。 */
  internal fun greenFinanceCooldownKey(uid: String?): String =
    "${GREEN_FINANCE_COOLDOWN_KEY}_${uid.orEmpty()}"

  /** 返回剩余冷却分钟数；不足一分钟按一分钟显示，已到期返回 0。 */
  internal fun greenFinanceCooldownRemainingMinutes(untilMs: Long, nowMs: Long): Long {
    val remainingMs = (untilMs - nowMs).coerceAtLeast(0L)
    return if (remainingMs == 0L) 0L else (remainingMs + 59_999L) / 60_000L
  }

  /** 为当前账号开始或刷新绿色经营封控冷却。 */
  fun startGreenFinanceCooldown(nowMs: Long = System.currentTimeMillis(), uid: String? = UserMap.currentUid) {
    getMySp(uid)?.putString(
      greenFinanceCooldownKey(uid),
      greenFinanceCooldownUntil(nowMs).toString()
    )
  }

  /** 返回当前账号剩余的绿色经营冷却分钟数。 */
  fun greenFinanceCooldownRemainingMinutes(nowMs: Long = System.currentTimeMillis()): Long {
    val untilMs = getMySp()
      ?.getString(greenFinanceCooldownKey(UserMap.currentUid))
      ?.toLongOrNull()
      ?: 0L
    return greenFinanceCooldownRemainingMinutes(untilMs, nowMs)
  }

  /** 判断错误码是否为 RPC 层生成的“今日已屏蔽”本地错误码。 */
  internal fun isGreenFinanceDailyBlockedError(error: Int): Boolean = error == 9999

  /** 判断响应是否为 RPC 层生成的“今日已屏蔽”本地响应。 */
  fun isGreenFinanceDailyBlockedResponse(response: JSONObject): Boolean =
    isGreenFinanceDailyBlockedError(response.optInt("error"))

  fun 自动同意LICENSE() = true

  fun 是否开启绿色绿色经营(): Boolean {
    //176 2088702045701743
    //158 2088122949590991
    //765 2088152366474514
    return UserMap.currentUid == "2088702045701743"
  }

  fun getSp当天是否执行(key: String): Boolean {
    val sp = getMySp() ?: return true
    return sp.getBoolean(key + "_" + (UserMap.currentUid ?: ""))
  }

  fun setSp当天是否执行(key: String, jo: JSONObject?) {
    if (jo == null) return
    //{"error":1009,"errorMessage":"为了保障您的操作安全，请进行验证后继续。","errorNo":3,"errorTip":"1009"}
    val errorMessage = jo.optString("errorMessage", "")
    val error = jo.optLong("error", 0)
    val errorTip = jo.optString("errorTip", "")
    var isError = false
    if (error == 1009L || errorTip == "1009") {
      isError = true
    } else if (errorMessage.contains("验证后继续")) {
      isError = true
    } else if (errorMessage.contains("系统繁忙")) {
      isError = true
    } else if (errorMessage.contains("已经签到")) {
      isError = true
    } else if (errorMessage.contains("操作存在异常")) {
      isError = true
    } else if (errorMessage.contains("系统出错")) {
      isError = true
    }
    if (isError) {
      getMySp()?.putBoolean(key + "_" + (UserMap.currentUid ?: ""), true)
    }
  }

  private const val DO_FARM_TASK_COUNT = "DO_FARM_TASK_COUNT"
  fun updateDoFarmTaskCount() {
    getMySp()?.let { sp ->
      val key = DO_FARM_TASK_COUNT + "_" + (UserMap.currentUid ?: "")
      val count = sp.getInt(key) + 1
      sp.putInt(key, count)
    }
  }

  fun getDoFarmTaskCount(): Int {
    return getMySp()?.getInt(DO_FARM_TASK_COUNT + "_" + (UserMap.currentUid ?: "")) ?: 0
  }

  private val mSpMap = hashMapOf<String, DailySharedPreferences>()
  @Synchronized
  private fun getMySp(uid: String? = UserMap.currentUid): DailySharedPreferences? {
    val userId = uid?.takeIf { it.isNotBlank() } ?: return null
    val context: Context = ApplicationHook.appContext ?: return null
    return mSpMap.getOrPut(userId) { DailySharedPreferences(context, userId) }
  }

  private fun rpcDailyKey(rpc: RpcEntity): String =
    MessageDigest.getInstance("SHA-256")
      .digest("${rpc.requestMethod}_${rpc.requestData}".toByteArray(Charsets.UTF_8))
      .joinToString("") { "%02x".format(it.toInt() and 0xff) }

  fun checkRpcTodayIsError(rpc: RpcEntity, uid: String? = UserMap.currentUid) {
    if (rpc.requestMethod.orEmpty().contains(".antfarm.")) {
      return //不能耽误喂鸡大业
    }
    rpc.responseString?.let { s ->
      val response = runCatching { JSONObject(s) }.getOrNull()
      when {
        s.contains("系统繁忙") ||
          s.contains("验证后继续") ||
          s.contains("已经签到") ||
          s.contains("操作存在异常") ||
          s.contains("系统出错") ||
          response?.optString("error") == "1009" ||
          response?.optString("errorTip") == "1009" -> {
          getMySp(uid)?.let { sp ->
            sp.putBoolean(rpcDailyKey(rpc), true)
            if (rpc.requestMethod == GREEN_FINANCE_SUBMIT_TICK_METHOD) {
              startGreenFinanceCooldown(uid = uid)
              Log.greenFinance("检测到封控，绿色经营冷却${GREEN_FINANCE_COOLDOWN_MINUTES}分钟")
            }
            Log.greenFinance("当日异常的请求，当日不再请求\n${rpc.requestMethod}")
          } ?: run {
            Log.greenFinance("当日异常的请求，当日不再请求,存储失败:${rpc.requestMethod}")
          }
        }
      }
    }
  }

  fun getRpcTodayIsError(rpc: RpcEntity, uid: String? = UserMap.currentUid): Boolean {
    if (rpc.requestMethod.orEmpty().contains(".antfarm.")) {
      return false //不能耽误喂鸡大业
    }
    return getMySp(uid)?.getBoolean(rpcDailyKey(rpc)) ?: false
  }
}
