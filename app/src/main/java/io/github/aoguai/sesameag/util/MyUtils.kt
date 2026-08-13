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
  const val CHANGE_KT1 = "1_当天异常不再执行_绿色经营"
  const val CHANGE_KT2 = "2_当天异常不再执行_健康岛泡泡"
  const val CHANGE_KT3 = "3_支持免Root运行_FPA"
  //const val CHANGE_KT4 = "4"
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
  private fun getMySp(): DailySharedPreferences? {
    val context: Context = ApplicationHook.appContext ?: return null
    mSpMap[UserMap.currentUid.orEmpty()]?.let { sp -> return sp }
    val sp = DailySharedPreferences(context, UserMap.currentUid.orEmpty())
    mSpMap[UserMap.currentUid.orEmpty()] = sp
    return sp
  }

  private fun rpcDailyKey(rpc: RpcEntity): String =
    MessageDigest.getInstance("SHA-256")
      .digest("${rpc.requestMethod}_${rpc.requestData}".toByteArray(Charsets.UTF_8))
      .joinToString("") { "%02x".format(it.toInt() and 0xff) }

  fun checkRpcTodayIsError(rpc: RpcEntity) {
    if (rpc.requestMethod.orEmpty().contains(".antfarm.")) {
      return //不能耽误喂鸡大业
    }
    rpc.responseString?.let { s ->
      when {
        s.contains("系统繁忙") ||
          s.contains("验证后继续") ||
          s.contains("已经签到") ||
          s.contains("操作存在异常") ||
          s.contains("系统出错") ||
          s.contains("\"error\":1009") -> {
          getMySp()?.let { sp ->
            sp.putBoolean(rpcDailyKey(rpc), true)
            Log.greenFinance("当日异常的请求，当日不再请求\n${rpc.requestMethod}")
          } ?: run {
            Log.greenFinance("当日异常的请求，当日不再请求,存储失败:${rpc.requestMethod}")
          }
        }
      }
    }
  }

  fun getRpcTodayIsError(rpc: RpcEntity): Boolean {
    if (rpc.requestMethod.orEmpty().contains(".antfarm.")) {
      return false //不能耽误喂鸡大业
    }
    return getMySp()?.getBoolean(rpcDailyKey(rpc)) ?: false
  }
}
