package io.github.aoguai.sesameag.util

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.*

class DailySharedPreferences(private val context: Context, private val uid: String) {

  // 动态生成文件名，如：DailyCachePrefs_UID_10086
  private val prefsName = "DailyCachePrefs_UID_$uid"

  private val prefs: SharedPreferences by lazy {
    context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
  }

  companion object {
    // 用于记录该账号全局最后一天的 Key
    private const val KEY_GLOBAL_DATE = "global_last_saved_date"
  }

  init {
    // 初始化时，主动检查当前账号是否跨天
    checkAndClearIfCrossedDay()
  }

  /**
   * 获取东八区（GMT+8）的当前日期字符串，例如 "20260530"
   */
  private fun getTodayDateString(): String {
    val sdf = SimpleDateFormat("yyyyMMdd", Locale.US)
    sdf.timeZone = TimeZone.getTimeZone("GMT+8")
    return sdf.format(Date())
  }

  /**
   * 检查该账号是否跨天，如果超过24点则清空该账号的所有数据
   */
  private fun checkAndClearIfCrossedDay() {
    val today = getTodayDateString()
    val lastSavedDate = prefs.getString(KEY_GLOBAL_DATE, null)

    // 如果上次保存的日期存在，且不等于今天的日期，说明该账号已经过了半夜 24 点
    if (lastSavedDate != null && lastSavedDate != today) {
      prefs.edit().clear().apply() // 一键清空当前 UID 的所有 key
    }
  }

  /**
   * 存入数据
   */
  fun putString(key: String, value: String) {
    checkAndClearIfCrossedDay() // 存之前检查

    val today = getTodayDateString()
    prefs.edit().apply {
      putString(key, value)
      putString(KEY_GLOBAL_DATE, today) // 更新该账号的日期戳
      apply()
    }
  }

  fun putBoolean(key: String, value: Boolean) {
    checkAndClearIfCrossedDay() // 存之前检查

    val today = getTodayDateString()
    prefs.edit().apply {
      putBoolean(key, value)
      putString(KEY_GLOBAL_DATE, today) // 更新该账号的日期戳
      apply()
    }
  }

  fun putInt(key: String, value: Int) {
    checkAndClearIfCrossedDay() // 存之前检查

    val today = getTodayDateString()
    prefs.edit().apply {
      putInt(key, value)
      putString(KEY_GLOBAL_DATE, today) // 更新该账号的日期戳
      apply()
    }
  }

  /**
   * 获取数据
   */
  fun getString(key: String): String? {
    checkAndClearIfCrossedDay() // 取之前检查
    return prefs.getString(key, null)
  }

  fun getBoolean(key: String): Boolean {
    checkAndClearIfCrossedDay() // 取之前检查
    return prefs.getBoolean(key, false)
  }

  fun getInt(key: String): Int {
    checkAndClearIfCrossedDay() // 取之前检查
    return prefs.getInt(key, 0)
  }

  /**
   * 清空当前账号的所有数据
   */
  fun clearAll() {
    prefs.edit().clear().apply()
  }
}