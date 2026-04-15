package io.github.aoguai.sesameag.task.AnswerAI

import io.github.aoguai.sesameag.util.JsonUtil
import io.github.aoguai.sesameag.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * GeminiAI帮助类，用于与Gemini接口交互以获取AI回答
 * 支持单条文本问题及带有候选答案列表的问题请求
 */
class GeminiAI(token: String?) : AnswerAIInterface {
  private val mGeminiBaseUrl = "https://api.genai.gd.edu.kg/google"
  private val TAG = GeminiAI::class.java.simpleName
  private val token: String = if (!token.isNullOrEmpty()) token else ""
  private val modelNameInternal: String = "gemini-2.5-flash"
  private val mOkHttpClient = OkHttpClient()

  override fun getModelName(): String = modelNameInternal
  override fun setModelName(modelName: String) {
    //固定gemini-2.5-flash不做修改
  }

  override fun getAnswerStr(text: String, model: String): String {
    setModelName(model)
    return getAnswerStr(text)
  }

  override fun getAnswerStr(text: String): String {
    var response: Response? = null
    try {
      val jsonReq = JSONObject()
      // 针对选择题优化的 Prompt
      val fullPrompt = "直接给出答案文字，严禁解释，不要标点符号。题目：$text"

      val contents = JSONArray()
      contents.put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", fullPrompt))))
      jsonReq.put("contents", contents)

      // 必须开启 google_search，否则无法回答最新的常识题（如蚂蚁庄园）
      jsonReq.put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))

      val body = jsonReq.toString().toRequestBody("application/json".toMediaType())

      val finalUrl = "$mGeminiBaseUrl/v1beta/models/$modelNameInternal:generateContent?key=$token"

      val request = Request.Builder().url(finalUrl).post(body).build()
      response = mOkHttpClient.newCall(request).execute()

      response.body.let { responseBody ->
        val jsonStr = responseBody.string()
        // 调试建议：Log.i("Gemini Raw: $jsonStr")
        val resObj = JSONObject(jsonStr)
        val answer = JsonUtil.getValueByPath(resObj, "candidates.[0].content.parts.[0].text")
        return answer.trim { it <= ' ' }.replace("[。，.！!？? \"'“”]".toRegex(), "").also { a ->
          Log.farm("Gemini回答: $a")
          Log.summary("Gemini回答: $a")
          Log.common("Gemini回答: $a")
          Log.runtime("Gemini回答: $a")
        }
      }
    } catch (e: Exception) {
      Log.printStackTrace(TAG, e)
      Log.farm("Gemini答题出错: ${e.message}")
      Log.error("Gemini答题出错: ${e.message}")
      Log.common("Gemini答题出错: ${e.message}")
      Log.runtime("Gemini答题出错: ${e.message}")
    } finally {
      response?.close()
    }
    return ""
  }

  override fun getAnswer(title: String, answerList: List<String>): Int {
    val answerStr = StringBuilder()
    for (answer in answerList) {
      answerStr.append("[").append(answer).append("]")
    }
    val answerResult = getAnswerStr("$title\n$answerStr")
    if (answerResult.isNotBlank()) {
      for (i in answerList.indices) {
        if (answerResult.contains(answerList[i])) {
          return i
        }
      }
    }
    return -1
  }
}

