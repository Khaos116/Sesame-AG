//package io.github.aoguai.sesameag.task.AnswerAI
//
//import io.github.aoguai.sesameag.util.JsonUtil
//import io.github.aoguai.sesameag.util.Log
//import okhttp3.*
//import okhttp3.MediaType.Companion.toMediaType
//import okhttp3.RequestBody.Companion.toRequestBody
//import org.json.JSONArray
//import org.json.JSONObject
//
///**
// * GeminiAI帮助类，用于与Gemini接口交互以获取AI回答
// * 支持单条文本问题及带有候选答案列表的问题请求
// */
//class GeminiAI(token: String?) : AnswerAIInterface {
//  private val apiUrl = "https://api.genai.gd.edu.kg/google"
//  private val TAG = GeminiAI::class.java.simpleName
//  private val token: String = if (!token.isNullOrBlank()) token else ""
//  private val modelName = "gemini-2.5-flash"
//  private val client = OkHttpClient()
//
//  override fun getModelName(): String = modelName
//
//  override fun setModelName(modelName: String) {
//    Log.record(TAG, "不接受更改Gemini模型: $modelName")
//    Log.farm(TAG, "不接受更改Gemini模型: $modelName")
//    Log.runtime(TAG, "不接受更改Gemini模型: $modelName")
//    Log.summary(TAG, "不接受更改Gemini模型: $modelName")
//  }
//
//  override fun getAnswerStr(text: String, model: String): String {
//    setModelName(model)
//    return getAnswerStr(text)
//  }
//
//  override fun getAnswerStr(text: String): String {
//    var response: Response? = null
//    try {
//      val jsonReq = JSONObject()
//      val fullPrompt = "直接给出答案文字，严禁解释，不要标点符号。题目：$text"
//
//      val contents = JSONArray()
//      contents.put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", fullPrompt))))
//      jsonReq.put("contents", contents)
//
//      // 必须开启 google_search，否则无法回答最新的常识题（如蚂蚁庄园）
//      jsonReq.put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))
//
//      val body = jsonReq.toString().toRequestBody("application/json".toMediaType())
//      val finalUrl = "$apiUrl/v1beta/models/$modelName:generateContent?key=$token"
//      val request = Request.Builder().url(finalUrl).post(body).build()
//
//      response = client.newCall(request).execute()
//
//      val responseBody = response.body
//      if (responseBody != null) {
//        val jsonStr = responseBody.string()
//        val resObj = JSONObject(jsonStr)
//        val answer = JsonUtil.getValueByPath(resObj, "candidates.[0].content.parts.[0].text")
//        if (answer.isNotBlank()) {
//          // 清理所有可能干扰匹配的杂质（句号、逗号、感叹号、问号、空格、各类引号）
//          val cleanAnswer = answer.trim().replace(
//            Regex("[。，.！!？? \"\u2018\u2019\u201c\u201d]"), ""
//          )
//          Log.farm("Gemini回答: $cleanAnswer")
//          Log.summary("Gemini回答: $cleanAnswer")
//          Log.common("Gemini回答: $cleanAnswer")
//          Log.runtime("Gemini回答: $cleanAnswer")
//          return cleanAnswer
//        }
//      }
//    } catch (e: Exception) {
//      Log.printStackTrace(TAG, e)
//      Log.error("Gemini答题出错: $e")
//      Log.farm(TAG, "Gemini答题出错: $e")
//    } finally {
//      response?.close()
//    }
//    return ""
//  }
//
//  override fun getAnswer(title: String, answerList: List<String>): Int {
//    val answerStr = StringBuilder()
//    for (answer in answerList) {
//      answerStr.append("[").append(answer).append("]")
//    }
//    val answerResult = getAnswerStr("$title\n$answerStr")
//    if (answerResult.isNotBlank()) {
//      for (i in answerList.indices) {
//        if (answerResult.contains(answerList[i])) {
//          return i
//        }
//      }
//    }
//    return -1
//  }
//}
//
