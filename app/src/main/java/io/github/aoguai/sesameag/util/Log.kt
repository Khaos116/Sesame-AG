package io.github.aoguai.sesameag.util

import android.content.Context
import io.github.aoguai.sesameag.BuildConfig
import io.github.aoguai.sesameag.model.BaseModel
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * 日志工具类，负责统一日志通道与模块分层。
 */
object Log {
    private const val MAX_DUPLICATE_ERRORS = 3

    private val errorCountMap = ConcurrentHashMap<String, AtomicInteger>()
    private val loggerMap: Map<LogChannel, Logger>

    private enum class Severity {
        DEBUG,
        INFO,
        WARN,
        ERROR
    }

    init {
        Logback.initLogcatOnly()
        loggerMap = LogCatalog.channels.associateWith { LoggerFactory.getLogger(it.loggerName) }
    }

    @JvmStatic
    fun init(context: Context) {
        try {
            Logback.initFileLogging(context)
        } catch (e: Exception) {
            android.util.Log.e("SesameLog", "Log init failed", e)
        }
    }

    private fun getLogger(channel: LogChannel): Logger = loggerMap.getValue(channel)

    private fun formatTaggedMessage(tag: String, msg: String): String = "[$tag]: $msg"

    private fun shouldWrite(channel: LogChannel): Boolean {
        return when (channel) {
            LogChannel.RECORD -> BaseModel.recordLog.value == true
            LogChannel.RUNTIME -> BaseModel.runtimeLog.value == true || BuildConfig.DEBUG
            else -> true
        }
    }

    private fun logRaw(channel: LogChannel, severity: Severity, msg: String) {
        Logback.refreshIfCrossDay()

        if (!shouldWrite(channel)) {
            return
        }
        val logger = getLogger(channel)
        when (severity) {
            Severity.DEBUG -> logger.debug("{}", msg)
            Severity.INFO -> logger.info("{}", msg)
            Severity.WARN -> logger.warn("{}", msg)
            Severity.ERROR -> logger.error("{}", msg)
        }
    }


    private fun write(channel: LogChannel, severity: Severity, msg: String, type: Int = 1) {
        if (channel.mirrorToRecord && type == 1) {
            val recordMsg = if (!channel.logTag.isNullOrEmpty()) {
                formatTaggedMessage(channel.logTag, msg)
            } else {
                msg
            }
            logRaw(LogChannel.RECORD, Severity.INFO, recordMsg)
        }
        logRaw(channel, severity, msg)
    }

    private fun business(channel: LogChannel, msg: String, type: Int = 1) {
        write(channel, Severity.INFO, msg, type)
    }

    @JvmStatic
    fun system(msg: String) {
        write(LogChannel.SYSTEM, Severity.INFO, msg)
    }

    @JvmStatic
    fun system(tag: String, msg: String) {
        system(formatTaggedMessage(tag, msg))
    }

    @JvmStatic
    fun runtime(msg: String) {
        write(LogChannel.RUNTIME, Severity.INFO, msg)
    }

    @JvmStatic
    fun runtime(tag: String, msg: String) {
        runtime(formatTaggedMessage(tag, msg))
    }

    @JvmStatic
    fun record(msg: String, type: Int = 1) {
        logRaw(LogChannel.RUNTIME, Severity.DEBUG, msg)

        val shouldRecord = if (type == 1) shouldWrite(LogChannel.RECORD) else false
        if (shouldRecord) {
            logRaw(LogChannel.RECORD, Severity.INFO, msg)
        }
    }

    @JvmStatic
    fun record(tag: String, msg: String, type: Int = 1) {
        record(formatTaggedMessage(tag, msg), type)
    }

    @JvmStatic
    fun summary(msg: String) {
        write(LogChannel.SUMMARY, Severity.INFO, msg)
    }

    @JvmStatic
    fun summary(tag: String, msg: String) {
        summary(formatTaggedMessage(tag, msg))
    }

    @JvmStatic
    fun common(msg: String) {
        business(LogChannel.COMMON, msg)
    }

    @JvmStatic
    fun common(tag: String, msg: String) {
        common(formatTaggedMessage(tag, msg))
    }

    @JvmStatic
    @JvmOverloads
    fun forest(msg: String, type: Int = 1) {
        business(LogChannel.FOREST, msg, type)
    }

    @JvmStatic
    fun orchard(msg: String) {
        business(LogChannel.ORCHARD, msg)
    }

    @JvmStatic
    @JvmOverloads
    fun farm(msg: String, type: Int = 1) {
        business(LogChannel.FARM, msg, type)
    }

    @JvmStatic
    fun stall(msg: String) {
        business(LogChannel.STALL, msg)
    }

    @JvmStatic
    fun ocean(msg: String) {
        business(LogChannel.OCEAN, msg)
    }

    @JvmStatic
    fun dodo(msg: String) {
        business(LogChannel.DODO, msg)
    }

    @JvmStatic
    fun member(msg: String) {
        business(LogChannel.MEMBER, msg)
    }

    @JvmStatic
    fun fishpond(msg: String) {
        business(LogChannel.FISHPOND, msg)
    }

    @JvmStatic
    fun sports(msg: String) {
        business(LogChannel.SPORTS, msg)
    }

    @JvmStatic
    fun greenFinance(msg: String) {
        business(LogChannel.GREEN_FINANCE, msg)
    }

    @JvmStatic
    fun sesame(msg: String) {
        business(LogChannel.SESAME_CREDIT, msg)
    }

    @JvmStatic
    fun debug(msg: String) {
        write(LogChannel.DEBUG, Severity.DEBUG, msg)
    }

    @JvmStatic
    fun debug(tag: String, msg: String) {
        debug(formatTaggedMessage(tag, msg))
    }

    @JvmStatic
    fun error(msg: String) {
        write(LogChannel.ERROR, Severity.ERROR, msg)
    }

    @JvmStatic
    fun error(tag: String, msg: String) {
        error(formatTaggedMessage(tag, msg))
    }

    @JvmStatic
    fun capture(msg: String) {
        write(LogChannel.CAPTURE, Severity.INFO, msg)
    }

    @JvmStatic
    fun capture(tag: String, msg: String) {
        capture(formatTaggedMessage(tag, msg))
    }

    @JvmStatic
    fun d(tag: String, msg: String) {
        debug(formatTaggedMessage(tag, msg))
    }

    @JvmStatic
    fun i(tag: String, msg: String) {
        record(formatTaggedMessage(tag, msg))
    }

    @JvmStatic
    fun w(tag: String, msg: String) {
        logRaw(LogChannel.RECORD, Severity.WARN, formatTaggedMessage(tag, msg))
    }

    @JvmStatic
    fun w(tag: String, msg: String, th: Throwable?) {
        val finalMsg = if (th == null) {
            formatTaggedMessage(tag, msg)
        } else {
            "${formatTaggedMessage(tag, msg)}\n${android.util.Log.getStackTraceString(th)}"
        }
        logRaw(LogChannel.RECORD, Severity.WARN, finalMsg)
    }

    @JvmStatic
    fun e(tag: String, msg: String, th: Throwable? = null) {
        val finalMsg = if (th == null) {
            formatTaggedMessage(tag, msg)
        } else {
            "${formatTaggedMessage(tag, msg)}\n${android.util.Log.getStackTraceString(th)}"
        }
        write(LogChannel.ERROR, Severity.ERROR, finalMsg)
    }

    private fun shouldSkipDuplicateError(th: Throwable?): Boolean {
        if (th == null) {
            return false
        }

        var errorSignature = th.javaClass.simpleName + ":" + (th.message?.take(50) ?: "null")
        if (th.message?.contains("End of input at character 0") == true) {
            errorSignature = "JSONException:EmptyResponse"
        }

        val count = errorCountMap.computeIfAbsent(errorSignature) { AtomicInteger(0) }
        val currentCount = count.incrementAndGet()

        if (currentCount == MAX_DUPLICATE_ERRORS) {
            summary("错误去重", "错误【$errorSignature】已出现${currentCount}次，后续不再打印详细堆栈")
            return false
        }

        return currentCount > MAX_DUPLICATE_ERRORS
    }

    private fun buildStackTraceMessage(tag: String? = null, msg: String? = null, th: Throwable): String {
        val header = when {
            !tag.isNullOrBlank() && !msg.isNullOrBlank() -> "[$tag] $msg"
            !tag.isNullOrBlank() -> "[$tag] Throwable error"
            !msg.isNullOrBlank() -> msg
            else -> "Throwable error"
        }
        return "$header\n${android.util.Log.getStackTraceString(th)}"
    }

    private fun shouldTreatContextAsTag(context: String): Boolean {
        if (context.isBlank()) {
            return false
        }
        if (context.any { it.isWhitespace() || it > '\u007F' }) {
            return false
        }
        return context.all { it.isLetterOrDigit() || it == '_' || it == '-' || it == '.' || it == '$' }
    }

    @JvmStatic
    fun printStackTrace(th: Throwable) {
        if (shouldSkipDuplicateError(th)) {
            return
        }
        error(buildStackTraceMessage(th = th))
    }

    @JvmStatic
    fun printStackTrace(context: String, th: Throwable) {
        if (shouldSkipDuplicateError(th)) {
            return
        }
        val message = if (shouldTreatContextAsTag(context)) {
            buildStackTraceMessage(tag = context, th = th)
        } else {
            buildStackTraceMessage(msg = context, th = th)
        }
        error(message)
    }

    @JvmStatic
    fun printStackTrace(tag: String, msg: String, th: Throwable) {
        if (shouldSkipDuplicateError(th)) {
            return
        }
        error(buildStackTraceMessage(tag = tag, msg = msg, th = th))
    }

    @JvmStatic
    fun printStack(tag: String) {
        val stackTrace = "stack: " + android.util.Log.getStackTraceString(Exception("获取当前堆栈$tag:"))
        debug(stackTrace)
    }
}
