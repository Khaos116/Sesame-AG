package io.github.aoguai.sesameag.task.antFishPond

import io.github.aoguai.sesameag.data.Status
import io.github.aoguai.sesameag.data.StatusFlags
import io.github.aoguai.sesameag.model.ModelFields
import io.github.aoguai.sesameag.model.ModelGroup
import io.github.aoguai.sesameag.model.modelFieldExt.BooleanModelField
import io.github.aoguai.sesameag.model.modelFieldExt.IntegerModelField
import io.github.aoguai.sesameag.model.withDesc
import io.github.aoguai.sesameag.task.ModelTask
import io.github.aoguai.sesameag.util.GlobalThreadPools
import io.github.aoguai.sesameag.util.Log
import io.github.aoguai.sesameag.util.ResChecker
import io.github.aoguai.sesameag.util.TaskBlacklist
import io.github.aoguai.sesameag.util.maps.IdMapManager
import io.github.aoguai.sesameag.util.maps.UserMap
import io.github.aoguai.sesameag.util.maps.VipDataIdMap
import org.json.JSONObject

class AntFishPond : ModelTask() {
    private lateinit var fishPondTask: BooleanModelField
    private lateinit var autoFish: BooleanModelField
    private lateinit var fishDailyLimit: IntegerModelField

    private val handledTaskAwards = LinkedHashSet<String>()
    private val handledVisitFinishes = LinkedHashSet<String>()

    override fun getName(): String = "福气鱼池"

    override fun getGroup(): ModelGroup = ModelGroup.FISHPOND

    override fun getIcon(): String = "AntFishPond.png"

    override fun getFields(): ModelFields {
        val modelFields = ModelFields()
        modelFields.addField(
            BooleanModelField("fishPondTask", "鱼池任务 | 领奖", true).withDesc(
                "自动处理福气鱼池签到、宝箱、明日钓竿和已验证任务奖励。"
            ).also { fishPondTask = it }
        )
        modelFields.addField(
            BooleanModelField("autoFish", "自动钓鱼 | 开启", false).withDesc(
                "开启后使用最近捕获的鱼池令牌自动钓鱼；没有令牌时提示原因并跳过。"
            ).also { autoFish = it }
        )
        modelFields.addField(
            IntegerModelField("fishDailyLimit", "自动钓鱼 | 每日次数", DEFAULT_FISH_LIMIT, 0, 200).withDesc(
                "限制当天最多钓鱼的次数，0 表示不限制；默认限制为 30 次。需开启“自动钓鱼 | 开启”。"
            ).also { fishDailyLimit = it }
        )
        return modelFields
    }

    override fun runJava() {
        try {
            Log.fishpond("执行开始-${getName()}")
            handledTaskAwards.clear()
            handledVisitFinishes.clear()

            queryIndex(logProgress = true)

            if (fishPondTask.value == true) {
                handleSubplots()
                handleTaskList()
            }

            val autoFishChanged = if (autoFish.value == true) {
                runAutoFish()
            } else {
                false
            }

            if (fishPondTask.value == true && autoFishChanged) {
                handleSubplots()
                handleTaskList()
            } else if (fishPondTask.value == true && autoFish.value == true) {
                Log.fishpond("本轮未实际钓鱼，跳过钓鱼后任务刷新")
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "start.run err:", t)
        } finally {
            Log.fishpond("执行结束-${getName()}")
        }
    }

    private fun queryIndex(logProgress: Boolean = false): JSONObject? {
        val response = AntFishPondRpcCall.fishpondIndex()
        if (response.isBlank()) {
            Log.runtime(TAG, "fishpondIndex返回空")
            return null
        }
        val jo = JSONObject(response)
        if (!isRpcSuccess(jo)) {
            Log.fishpond("福气鱼池首页查询失败：${formatFailure(jo)}")
            return null
        }

        val payload = payloadOf(jo)
        if (!payload.optBoolean("open", true)) {
            Log.fishpond("福气鱼池未开通，本轮跳过")
            return jo
        }
        if (logProgress) {
            logFishProgress(jo)
        }
        markExchangeReached(jo)
        return jo
    }

    private fun handleSubplots() {
        try {
            val response = AntFishPondRpcCall.querySubplotsActivity()
            if (response.isBlank()) {
                Log.runtime(TAG, "querySubplotsActivity返回空")
                return
            }
            val jo = JSONObject(response)
            if (!isRpcSuccess(jo)) {
                Log.fishpond("鱼池活动查询失败：${formatFailure(jo)}")
                return
            }

            val activityList = payloadOf(jo).optJSONArray("subplotsActivityList") ?: return
            for (i in 0 until activityList.length()) {
                val item = activityList.optJSONObject(i) ?: continue
                val activityType = item.optString("activityType")
                    .ifBlank { item.optString("activityId") }
                val status = item.optString("status")
                val extend = parseObject(item.optString("extend"))
                val extendStatus = extend?.optString("status").orEmpty()

                when (activityType) {
                    ACTIVITY_GIFT_BOX -> handleGiftBox(status, extendStatus)
                    ACTIVITY_TOMORROW_ROD -> handleTomorrowRod(status)
                    ACTIVITY_FISH -> handleFishActivity(status, extend)
                }
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "handleSubplots err:", t)
        }
    }

    private fun handleGiftBox(status: String, extendStatus: String) {
        if (status == STATUS_FINISHED || extendStatus == STATUS_FINISHED) {
            Status.setFlagToday(StatusFlags.FLAG_ANTFISHPOND_GIFT_BOX_DONE)
            return
        }
        if (status != STATUS_TODO && extendStatus != STATUS_TODO) {
            return
        }
        val trigger = AntFishPondRpcCall.triggerSubplotsActivity(ACTIVITY_GIFT_BOX, ACTION_RECEIVE_AWARD)
        val jo = JSONObject(trigger)
        if (isRpcSuccess(jo)) {
            Status.setFlagToday(StatusFlags.FLAG_ANTFISHPOND_GIFT_BOX_DONE)
            Log.fishpond("每日宝箱🎁领取成功")
            AntFishPondRpcCall.fishpondSyncIndex(listOf("GIFT_BOX", "TASK_DISPLAY"))
        } else {
            Log.fishpond("每日宝箱领取失败：${formatFailure(jo)}")
        }
        GlobalThreadPools.sleepCompat(SHORT_INTERVAL_MS)
    }

    private fun handleTomorrowRod(status: String) {
        if (status == "TODAY_FINISH") {
            Status.setFlagToday(StatusFlags.FLAG_ANTFISHPOND_TOMORROW_ROD_DONE)
            return
        }
        if (status != "TODAY_TODO") {
            return
        }
        val trigger = AntFishPondRpcCall.triggerSubplotsActivity(ACTIVITY_TOMORROW_ROD, ACTION_FINISH)
        val jo = JSONObject(trigger)
        if (isRpcSuccess(jo)) {
            Status.setFlagToday(StatusFlags.FLAG_ANTFISHPOND_TOMORROW_ROD_DONE)
            Log.fishpond("明日钓竿🎣领取成功")
            AntFishPondRpcCall.fishpondSyncIndex(listOf("TOMORROW_ROD"))
        } else {
            Log.fishpond("明日钓竿领取失败：${formatFailure(jo)}")
        }
        GlobalThreadPools.sleepCompat(SHORT_INTERVAL_MS)
    }

    private fun handleFishActivity(status: String, extend: JSONObject?) {
        val extendStatus = extend?.optString("status").orEmpty()
        val leftFishTimes = extend?.optInt("leftFishTimes", Int.MAX_VALUE) ?: Int.MAX_VALUE
        val claimable = status in CLAIMABLE_STATUS ||
            extendStatus in CLAIMABLE_STATUS ||
            leftFishTimes <= 0
        if (!claimable) {
            if (leftFishTimes != Int.MAX_VALUE) {
                Log.runtime(TAG, "钓鱼活动奖励还差${leftFishTimes}次")
            }
            return
        }

        val trigger = AntFishPondRpcCall.triggerSubplotsActivity(ACTIVITY_FISH, ACTION_RECEIVE_AWARD)
        val jo = JSONObject(trigger)
        if (isRpcSuccess(jo)) {
            Log.fishpond("钓鱼活动奖励🎣领取成功")
            AntFishPondRpcCall.fishpondSyncIndex(listOf("FISH_ACTIVITY", "TASK_DISPLAY", "TOMORROW_ROD"))
        } else {
            Log.fishpond("钓鱼活动奖励领取失败：${formatFailure(jo)}")
        }
        GlobalThreadPools.sleepCompat(SHORT_INTERVAL_MS)
    }

    private fun handleTaskList() {
        try {
            var listJson = queryTaskList() ?: return
            if (handleSign(listJson)) {
                listJson = queryTaskList() ?: listJson
            }

            var rounds = 0
            while (rounds < MAX_TASK_REFRESH_ROUNDS && !Thread.currentThread().isInterrupted) {
                rounds++
                val handled = handleOneTaskAction(listJson)
                if (!handled) {
                    if (!hasStablePendingTask(listJson)) {
                        Status.setFlagToday(StatusFlags.FLAG_ANTFISHPOND_TASKS_DONE)
                    }
                    break
                }
                listJson = queryTaskList() ?: break
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "handleTaskList err:", t)
        }
    }

    private fun queryTaskList(): JSONObject? {
        val response = AntFishPondRpcCall.listTask()
        if (response.isBlank()) {
            Log.runtime(TAG, "listTask返回空")
            return null
        }
        val jo = JSONObject(response)
        if (!isRpcSuccess(jo)) {
            Log.fishpond("钓竿任务查询失败：${formatFailure(jo)}")
            return null
        }
        return jo
    }

    private fun handleSign(listJson: JSONObject): Boolean {
        val signList = payloadOf(listJson)
            .optJSONObject("signInfo")
            ?.optJSONArray("list")
            ?: return false
        for (i in 0 until signList.length()) {
            val signItem = signList.optJSONObject(i) ?: continue
            if (!signItem.optBoolean("today")) {
                continue
            }
            if (signItem.optBoolean("signed")) {
                Status.setFlagToday(StatusFlags.FLAG_ANTFISHPOND_SIGN_DONE)
                return false
            }

            val signKey = signItem.optString("signKey")
            val response = if (signKey.isBlank()) {
                AntFishPondRpcCall.sign()
            } else {
                AntFishPondRpcCall.sign(signKey)
            }
            val jo = JSONObject(response)
            if (isRpcSuccess(jo)) {
                Status.setFlagToday(StatusFlags.FLAG_ANTFISHPOND_SIGN_DONE)
                val awardCount = signItem.optInt("awardCount", 1)
                Log.fishpond("每日签到🎣领取${awardCount}根钓竿")
                AntFishPondRpcCall.fishpondSyncIndex(listOf("TASK_DISPLAY"))
                GlobalThreadPools.sleepCompat(SHORT_INTERVAL_MS)
                return true
            }
            Log.fishpond("每日签到失败：${formatFailure(jo)}")
            return false
        }
        return false
    }

    private fun handleOneTaskAction(listJson: JSONObject): Boolean {
        val taskList = payloadOf(listJson).optJSONArray("taskList") ?: return false
        for (i in 0 until taskList.length()) {
            val task = taskList.optJSONObject(i) ?: continue
            val taskId = task.optString("taskId")
            val sceneCode = task.optString("sceneCode", TASK_SCENE)
            val actionType = task.optString("actionType")
            val taskStatus = task.optString("taskStatus")
            val taskTitle = taskTitle(task)

            if (TaskBlacklist.isTaskInBlacklist(TASK_BLACKLIST_MODULE, taskId) ||
                TaskBlacklist.isTaskInBlacklist(TASK_BLACKLIST_MODULE, taskTitle)
            ) {
                continue
            }

            if (actionType == ACTION_GO_FISH && shouldClaimGoFishAward(task)) {
                val taskKey = "$sceneCode|$taskId"
                if (handledTaskAwards.contains(taskKey)) {
                    continue
                }
                return claimTaskAward(sceneCode, taskId, taskTitle, taskKey)
            }

            if (actionType == ACTION_VISIT &&
                taskStatus == STATUS_TODO &&
                SUPPORTED_VISIT_TASKS.contains(taskId)
            ) {
                val adBizNo = extractAdBizNo(task)
                if (adBizNo.isBlank()) {
                    Log.fishpond("浏览任务缺少 adBizNo，跳过[$taskTitle]")
                    continue
                }
                val finishKey = "$sceneCode|$taskId|$adBizNo|${task.optInt("rightsTimes", 0)}"
                if (handledVisitFinishes.contains(finishKey)) {
                    continue
                }
                return finishVisitTask(sceneCode, taskId, taskTitle, adBizNo, finishKey)
            }
        }
        return false
    }

    private fun shouldClaimGoFishAward(task: JSONObject): Boolean {
        if (task.optString("taskId") != TASK_GO_FISH) {
            return false
        }
        val taskStatus = task.optString("taskStatus")
        if (taskStatus == "RECEIVED" || taskStatus == "COMPLETE") {
            return false
        }
        if (taskStatus in CLAIMABLE_STATUS) {
            return true
        }
        val taskRequire = task.optInt("taskRequire", -1)
        return taskRequire > 0 && task.optInt("taskProgress", 0) >= taskRequire
    }

    private fun claimTaskAward(sceneCode: String, taskId: String, taskTitle: String, taskKey: String): Boolean {
        val response = AntFishPondRpcCall.receiveTaskAward(taskId, sceneCode)
        val jo = JSONObject(response)
        if (isRpcSuccess(jo)) {
            handledTaskAwards.add(taskKey)
            Log.fishpond("任务奖励🎖️[$taskTitle]领取成功")
            AntFishPondRpcCall.fishpondSyncIndex(listOf("TASK_DISPLAY"))
            GlobalThreadPools.sleepCompat(SHORT_INTERVAL_MS)
            return true
        }
        Log.fishpond("任务奖励领取失败[$taskTitle]：${formatFailure(jo)}")
        if (isTerminalTaskFailure(jo)) {
            handledTaskAwards.add(taskKey)
            Log.runtime(TAG, "任务奖励[$taskTitle]命中终态失败，标记本轮已处理")
            GlobalThreadPools.sleepCompat(SHORT_INTERVAL_MS)
            return true
        }
        GlobalThreadPools.sleepCompat(SHORT_INTERVAL_MS)
        return false
    }

    private fun finishVisitTask(
        sceneCode: String,
        taskId: String,
        taskTitle: String,
        adBizNo: String,
        finishKey: String
    ): Boolean {
        val notice = JSONObject(AntFishPondRpcCall.fishpondAdNotice(adBizNo))
        if (!isRpcSuccess(notice)) {
            Log.fishpond("浏览任务广告通知失败[$taskTitle]：${formatFailure(notice)}")
            if (isTerminalTaskFailure(notice)) {
                handledVisitFinishes.add(finishKey)
                Log.runtime(TAG, "浏览任务[$taskTitle]广告通知命中终态失败，标记本轮已处理")
                GlobalThreadPools.sleepCompat(SHORT_INTERVAL_MS)
                return true
            }
            return false
        }

        Log.runtime(TAG, "浏览任务[$taskTitle]已通知广告，等待完成窗口")
        GlobalThreadPools.sleepCompat(VISIT_WAIT_MS)

        val finish = JSONObject(AntFishPondRpcCall.finishTask(taskId, adBizNo, sceneCode))
        if (isRpcSuccess(finish)) {
            handledVisitFinishes.add(finishKey)
            Log.fishpond("浏览任务🧾[$taskTitle]完成")
            AntFishPondRpcCall.fishpondSyncIndex(listOf("FISH_ACTIVITY", "TASK_DISPLAY", "TOMORROW_ROD", "LOTTERY_PLUS"))
            GlobalThreadPools.sleepCompat(SHORT_INTERVAL_MS)
            return true
        }

        Log.fishpond("浏览任务完成失败[$taskTitle]：${formatFailure(finish)}")
        if (isTerminalTaskFailure(finish)) {
            handledVisitFinishes.add(finishKey)
            Log.runtime(TAG, "浏览任务[$taskTitle]命中终态失败，标记本轮已处理")
            GlobalThreadPools.sleepCompat(SHORT_INTERVAL_MS)
            return true
        }
        GlobalThreadPools.sleepCompat(SHORT_INTERVAL_MS)
        return false
    }

    private fun hasStablePendingTask(listJson: JSONObject): Boolean {
        val taskList = payloadOf(listJson).optJSONArray("taskList") ?: return false
        for (i in 0 until taskList.length()) {
            val task = taskList.optJSONObject(i) ?: continue
            val taskId = task.optString("taskId")
            val sceneCode = task.optString("sceneCode", TASK_SCENE)
            val taskTitle = taskTitle(task)
            if (TaskBlacklist.isTaskInBlacklist(TASK_BLACKLIST_MODULE, taskId) ||
                TaskBlacklist.isTaskInBlacklist(TASK_BLACKLIST_MODULE, taskTitle)
            ) {
                continue
            }
            val actionType = task.optString("actionType")
            val taskStatus = task.optString("taskStatus")
            if (actionType == ACTION_GO_FISH && shouldClaimGoFishAward(task)) {
                val taskKey = "$sceneCode|$taskId"
                if (handledTaskAwards.contains(taskKey)) {
                    continue
                }
                return true
            }
            if (actionType == ACTION_VISIT &&
                taskStatus == STATUS_TODO &&
                SUPPORTED_VISIT_TASKS.contains(taskId)
            ) {
                val adBizNo = extractAdBizNo(task)
                if (adBizNo.isBlank()) {
                    return true
                }
                val finishKey = "$sceneCode|$taskId|$adBizNo|${task.optInt("rightsTimes", 0)}"
                if (handledVisitFinishes.contains(finishKey)) {
                    continue
                }
                return true
            }
        }
        return false
    }

    private fun runAutoFish(): Boolean {
        val riskToken = loadRiskToken()
        if (riskToken.isBlank()) {
            Status.setFlagToday(StatusFlags.FLAG_ANTFISHPOND_RISK_TOKEN_MISSING)
            Log.fishpond("缺少 fishpondAngle riskToken，跳过自动钓鱼；请先手动进入鱼池钓鱼以捕获 token")
            return false
        }
        Status.removeFlag(StatusFlags.FLAG_ANTFISHPOND_RISK_TOKEN_MISSING)

        var indexJson = queryIndex(logProgress = true) ?: return false
        if (markExchangeReached(indexJson)) {
            return false
        }

        var rodCount = extractRodCount(indexJson)
        if (rodCount <= 0) {
            Log.fishpond("当前无可用钓竿，跳过自动钓鱼")
            return false
        }

        val limit = fishDailyLimit.value ?: DEFAULT_FISH_LIMIT
        var usedToday = Status.getIntFlagToday(StatusFlags.FLAG_ANTFISHPOND_FISH_COUNT) ?: 0
        if (limit <= 0 || usedToday < limit) {
            Status.removeFlag(StatusFlags.FLAG_ANTFISHPOND_FISH_LIMIT_REACHED)
        }
        if (limit > 0 && usedToday >= limit) {
            Status.setFlagToday(StatusFlags.FLAG_ANTFISHPOND_FISH_LIMIT_REACHED)
            Log.fishpond("今日自动钓鱼已达每日上限${limit}次，当前累计${usedToday}次，剩余钓竿${rodCount}根")
            return false
        }

        var handledCount = 0
        while (rodCount > 0 && !Thread.currentThread().isInterrupted) {
            if (limit > 0 && usedToday >= limit) {
                break
            }
            if (markExchangeReached(indexJson)) {
                break
            }

            val angleResponse = AntFishPondRpcCall.fishpondAngle(riskToken)
            if (angleResponse.isBlank()) {
                Log.runtime(TAG, "fishpondAngle返回空")
                break
            }
            var angleJson = JSONObject(angleResponse)
            if (!isRpcSuccess(angleJson)) {
                Log.fishpond("钓鱼失败：${formatFailure(angleJson)}")
                if (isRiskFailure(angleJson)) {
                    Status.setFlagToday(StatusFlags.FLAG_ANTFISHPOND_RISK_TOKEN_MISSING)
                }
                break
            }

            handledCount++
            usedToday += 1
            Status.setIntFlagToday(StatusFlags.FLAG_ANTFISHPOND_FISH_COUNT, usedToday)

            val angleInfo = angleInfoOf(angleJson)
            if (angleInfo.optString("fishType") == FISH_TYPE_WELFARE) {
                val bizNo = angleInfo.optString("bizNo")
                if (bizNo.isNotBlank()) {
                    positionBigFish(bizNo)?.let { angleJson = it }
                } else {
                    Log.fishpond("触发福利鱼但缺少 bizNo，跳过大鱼定位")
                }
            }

            logAngleResult(angleJson)
            if (markExchangeReached(angleJson)) {
                break
            }

            val syncJson = syncAfterFish()
            if (syncJson != null) {
                indexJson = syncJson
                rodCount = extractRodCount(syncJson)
                logFishProgress(syncJson)
            } else {
                rodCount = extractRodCount(angleJson).takeIf { it >= 0 } ?: (rodCount - 1)
            }

            GlobalThreadPools.sleepCompat(SHORT_INTERVAL_MS)
        }

        if (limit > 0 && usedToday >= limit) {
            Status.setFlagToday(StatusFlags.FLAG_ANTFISHPOND_FISH_LIMIT_REACHED)
            if (rodCount > 0) {
                Log.fishpond("今日自动钓鱼已达每日上限${limit}次，本轮执行${handledCount}次，剩余钓竿${rodCount}根")
            }
        }
        return handledCount > 0
    }

    private fun positionBigFish(bizNo: String): JSONObject? {
        val special = JSONObject(AntFishPondRpcCall.fishpondAngleRodPositioning(bizNo, AREA_SPECIAL_BIG))
        if (isRpcSuccess(special)) {
            Log.fishpond("福利鱼定位命中[$AREA_SPECIAL_BIG]")
            return special
        }

        Log.fishpond("福利鱼定位[$AREA_SPECIAL_BIG]失败：${formatFailure(special)}，尝试[$AREA_SUPER_BIG]")
        val fallback = JSONObject(AntFishPondRpcCall.fishpondAngleRodPositioning(bizNo, AREA_SUPER_BIG))
        if (isRpcSuccess(fallback)) {
            Log.fishpond("福利鱼定位命中[$AREA_SUPER_BIG]")
            return fallback
        }

        Log.fishpond("福利鱼定位[$AREA_SUPER_BIG]失败：${formatFailure(fallback)}")
        return null
    }

    private fun syncAfterFish(): JSONObject? {
        val response = AntFishPondRpcCall.fishpondSyncIndex(listOf("FISH_ACTIVITY", "TASK_DISPLAY", "TOMORROW_ROD"))
        if (response.isBlank()) {
            Log.runtime(TAG, "fishpondSyncIndex返回空")
            return null
        }
        val jo = JSONObject(response)
        if (!isRpcSuccess(jo)) {
            Log.fishpond("钓鱼后刷新失败：${formatFailure(jo)}")
            return null
        }
        markExchangeReached(jo)
        return jo
    }

    private fun loadRiskToken(): String {
        val userId = UserMap.currentUid
        val vipData = IdMapManager.getInstance(VipDataIdMap::class.java)
        vipData.load(userId)
        return vipData[VIP_RISK_TOKEN_KEY].orEmpty()
    }

    private fun logFishProgress(jo: JSONObject) {
        val payload = payloadOf(jo)
        val fishAsset = payload.optJSONObject("roundInfo")
            ?.optJSONObject("fishAssetInfo")
            ?: return
        val current = fishAsset.optString("currentFishWeight")
        val target = fishAsset.optString("targetFishWeight")
        val diff = fishAsset.optString("diffFishWeight")
        val rodCount = extractRodCount(jo)
        Log.fishpond("鱼池进度：当前${current}斤 / 目标${target}斤，还差${diff}斤，钓竿${rodCount}根")
    }

    private fun markExchangeReached(jo: JSONObject): Boolean {
        val payload = payloadOf(jo)
        val canExchange = payload.optBoolean("canExchange", false) ||
            payload.optJSONObject("roundInfo")?.optBoolean("canExchange", false) == true ||
            payload.optJSONObject("angleResultInfo")?.optBoolean("canExchange", false) == true ||
            payload.optJSONObject("fishResultInfo")?.optBoolean("canExchange", false) == true
        if (!canExchange) {
            return false
        }
        if (!Status.hasFlagToday(StatusFlags.FLAG_ANTFISHPOND_EXCHANGE_REACHED)) {
            Status.setFlagToday(StatusFlags.FLAG_ANTFISHPOND_EXCHANGE_REACHED)
            Log.fishpond("福气鱼池已达到兑换条件；当前未接入兑换红包 RPC，停止自动兑换")
        }
        return true
    }

    private fun logAngleResult(jo: JSONObject) {
        val angleInfo = angleInfoOf(jo)
        val fishType = angleInfo.optString("fishType", "UNKNOWN")
        val fishName = angleInfo.optString("fishName").ifBlank { fishType }
        val fishWeight = angleInfo.optString("fishWeight").ifBlank {
            payloadOf(jo).optString("fishWeight")
        }
        val rodCount = extractRodCount(jo)
        val rodText = if (rodCount >= 0) "，剩余钓竿${rodCount}根" else ""
        Log.fishpond("钓鱼🎣[$fishName/$fishType]#${fishWeight}斤$rodText")
    }

    private fun extractRodCount(jo: JSONObject): Int {
        val payload = payloadOf(jo)
        if (payload.has("rodSumCount")) {
            return payload.optInt("rodSumCount", 0)
        }
        val rodList = payload.optJSONArray("rodAssetInfoList") ?: return -1
        var sum = 0
        for (i in 0 until rodList.length()) {
            sum += rodList.optJSONObject(i)?.optInt("rodCount", 0) ?: 0
        }
        return sum
    }

    private fun angleInfoOf(jo: JSONObject): JSONObject {
        val payload = payloadOf(jo)
        return payload.optJSONObject("angleResultInfo")
            ?: payload.optJSONObject("fishResultInfo")
            ?: payload
    }

    private fun payloadOf(jo: JSONObject): JSONObject {
        return jo.optJSONObject("data") ?: jo
    }

    private fun parseObject(raw: String): JSONObject? {
        if (raw.isBlank()) {
            return null
        }
        return try {
            JSONObject(raw)
        } catch (_: Throwable) {
            null
        }
    }

    private fun taskTitle(task: JSONObject): String {
        return task.optJSONObject("taskDisplayConfig")
            ?.optString("title")
            ?.takeIf { it.isNotBlank() }
            ?: task.optString("taskTitle")
                .ifBlank { task.optString("title") }
                .ifBlank { task.optString("taskId") }
    }

    private fun extractAdBizNo(task: JSONObject): String {
        task.optString("adBizNo").takeIf { it.isNotBlank() }?.let { return it }
        val targetUrl = task.optJSONObject("taskDisplayConfig")?.optString("targetUrl").orEmpty()
        return Regex("""(?:[?&])pwPreBizId=([^&]+)""")
            .find(targetUrl)
            ?.groupValues
            ?.getOrNull(1)
            .orEmpty()
    }

    private fun isRpcSuccess(jo: JSONObject): Boolean {
        val resultCode = jo.optString("resultCode")
        val memo = jo.optString("memo")
        val resultDesc = jo.optString("resultDesc")
        if (jo.optBoolean("success") ||
            jo.optBoolean("isSuccess") ||
            resultCode == "100" ||
            resultCode.equals("SUCCESS", ignoreCase = true) ||
            memo.equals("SUCCESS", ignoreCase = true) ||
            memo == "成功" ||
            resultDesc == "成功"
        ) {
            return true
        }
        return ResChecker.checkRes(TAG, jo)
    }

    private fun formatFailure(jo: JSONObject): String {
        val code = jo.optString("code")
            .ifBlank { jo.optString("errorCode") }
            .ifBlank { jo.optString("resultCode") }
            .ifBlank { "UNKNOWN" }
        val desc = jo.optString("desc")
            .ifBlank { jo.optString("errorMsg") }
            .ifBlank { jo.optString("resultDesc") }
            .ifBlank { jo.optString("memo") }
            .ifBlank { jo.toString() }
        return "code=$code msg=$desc"
    }

    private fun isTerminalTaskFailure(jo: JSONObject): Boolean {
        val code = jo.optString("code")
            .ifBlank { jo.optString("errorCode") }
            .ifBlank { jo.optString("resultCode") }
        val desc = jo.optString("desc")
            .ifBlank { jo.optString("errorMsg") }
            .ifBlank { jo.optString("resultDesc") }
            .ifBlank { jo.optString("memo") }
        return code in TERMINAL_TASK_CODES ||
            desc.contains("已领取") ||
            desc.contains("已完成") ||
            desc.contains("重复") ||
            desc.contains("超过上限") ||
            desc.contains("任务已完结") ||
            desc.contains("任务已结束")
    }

    private fun isRiskFailure(jo: JSONObject): Boolean {
        val text = formatFailure(jo)
        return text.contains("risk", ignoreCase = true) ||
            text.contains("captcha", ignoreCase = true) ||
            text.contains("验证") ||
            text.contains("风控")
    }

    companion object {
        private val TAG = AntFishPond::class.java.simpleName
        private const val TASK_BLACKLIST_MODULE = "福气鱼池"
        private const val VIP_RISK_TOKEN_KEY = "antfishpond_riskToken"
        private const val TASK_SCENE = "ANTFISHPOND_TASK"
        private const val TASK_GO_FISH = "FISH_TASK_14"
        private const val DEFAULT_FISH_LIMIT = 30
        private const val SHORT_INTERVAL_MS = 500L
        private const val VISIT_WAIT_MS = 16_000L
        private const val MAX_TASK_REFRESH_ROUNDS = 8

        private const val ACTIVITY_GIFT_BOX = "GIFT_BOX"
        private const val ACTIVITY_TOMORROW_ROD = "TOMORROW_ROD"
        private const val ACTIVITY_FISH = "FISH_ACTIVITY"
        private const val ACTION_RECEIVE_AWARD = "receiveAward"
        private const val ACTION_FINISH = "FINISH"
        private const val ACTION_VISIT = "VISIT"
        private const val ACTION_GO_FISH = "GOFISH"
        private const val STATUS_TODO = "TODO"
        private const val STATUS_FINISHED = "FINISHED"
        private const val FISH_TYPE_WELFARE = "WELFARE_FISH"
        private const val AREA_SPECIAL_BIG = "SPECIAL_BIG_ZONE"
        private const val AREA_SUPER_BIG = "SUPER_BIG_ZONE"

        private val SUPPORTED_VISIT_TASKS = setOf(
            "GYG_XLIGHT_JX_BUSINEES",
            "GYG_XLIGHT_JX_BUSINEES_3"
        )
        private val CLAIMABLE_STATUS = setOf("FINISHED", "RECEIVABLE", "TODO_RECEIVE")
        private val TERMINAL_TASK_CODES = setOf(
            "400000030",
            "400000012",
            "TASK_ID_INVALID",
            "ILLEGAL_ARGUMENT",
            "PROMISE_TODAY_FINISH_TIMES_LIMIT"
        )
    }
}
