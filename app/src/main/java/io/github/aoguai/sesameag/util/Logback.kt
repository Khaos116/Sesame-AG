package io.github.aoguai.sesameag.util

import android.content.Context
import android.util.Log
import ch.qos.logback.classic.AsyncAppender
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.android.LogcatAppender
import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.rolling.FixedWindowRollingPolicy
import ch.qos.logback.core.rolling.RollingFileAppender
import ch.qos.logback.core.rolling.SizeBasedTriggeringPolicy
import ch.qos.logback.core.util.FileSize
import io.github.aoguai.sesameag.data.General
import org.slf4j.LoggerFactory
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Logback {
    private var isFileInitialized = false
    private var lastInitDate: String? = null
    private var appContext: Context? = null

    /**
     * 阶段1：初始化 Logcat (保证控制台一定有日志)
     * 在 Log 类的 init 块中自动调用
     */
    fun initLogcatOnly() {
        try {
            val lc = LoggerFactory.getILoggerFactory() as LoggerContext
            lc.reset() // 清除之前的配置

            // 配置 Logcat Appender
            val encoder = PatternLayoutEncoder().apply {
                context = lc
                pattern = "[%thread] %logger{80} %msg%n" // 保持与 Java 版本一致
                start()
            }

            val logcatAppender = LogcatAppender().apply {
                context = lc
                this.encoder = encoder
                name = "LOGCAT"
                start()
            }

            // 为根 Logger 添加 Logcat 输出
            lc.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME).apply {
                level = Level.DEBUG // 默认根级别
                addAppender(logcatAppender)
            }

        } catch (e: Exception) {
            Log.e("SesameLog", "Logback initLogcatOnly failed", e)
        }
    }

    /**
     * 阶段2：初始化文件日志 (有了 Context 之后调用)
     * 这是一个“追加”操作，不会打断 Logcat 日志
     */
    @Synchronized
    fun initFileLogging(context: Context) {
        this.appContext = context.applicationContext
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        // 如果已初始化且日期未变，则无需重复执行
        if (isFileInitialized && lastInitDate == today) return

        val logDir = resolveLogDir(context)
        val isMainProcess = context.packageName == General.PACKAGE_NAME

        try {
            val lc = LoggerFactory.getILoggerFactory() as LoggerContext

            if (isFileInitialized) {
                lc.reset()
                initLogcatOnly()
            }

            LogCatalog.loggerNames().forEach { logName ->
                // 抓包日志不执行跨天物理归档，而是持续追加，直到触发大小滚动限制
                if (logName != LogChannel.CAPTURE.loggerName) {
                    performManualRolling(logDir, logName, today)
                }
            }

            // 为每个特定业务的 Logger 添加文件 Appender
            LogCatalog.loggerNames().forEach { logName ->
                addFileAppender(lc, logName, logDir, isMainProcess)
            }

            isFileInitialized = true
            lastInitDate = today
            Log.i("SesameLog", "File logging initialized for $today at: $logDir (MainProcess: $isMainProcess)")
        } catch (e: Exception) {
            Log.e("SesameLog", "Logback initFileLogging failed", e)
        }
    }

    /**
     * 供 Log.kt 每次写日志前调用，感应日期变化并自动重定向。
     */
    fun refreshIfCrossDay() {
        appContext?.let { initFileLogging(it) }
    }

    /**
     * 手动判断日期并滚动旧日志文件，解决多进程竞争导致的滚动逻辑混乱。
     */
    private fun performManualRolling(logDir: String, logName: String, today: String) {
        val activeFile = File("$logDir$logName.log")
        val bakDir = File(logDir, "bak")

        // 1. 清理超过 3 天的历史归档
        val threshold = System.currentTimeMillis() - 3 * 24 * 60 * 60 * 1000L
        bakDir.listFiles { file ->
            file.name.startsWith("$logName-") && file.name.endsWith(".log")
        }?.forEach { file ->
            if (file.lastModified() < threshold) {
                file.delete()
            }
        }

        // 2. 跨天滚动逻辑
        if (!activeFile.exists()) return

        val fileDay = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(activeFile.lastModified()))
        if (today != fileDay) {
            val target = File(bakDir, "$logName-$fileDay.log")
            if (activeFile.renameTo(target)) {
                Log.d("SesameLog", "Manual roll successful for $logName: $fileDay -> $today")
            }
        }
    }

    /**
     * 核心路径逻辑
     */
    private fun resolveLogDir(context: Context): String {
        var targetDir = Files.LOG_DIR

        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }

        if (!targetDir.exists() || !targetDir.canWrite()) {
            val fallbackDir = context.getExternalFilesDir("logs")
            targetDir = fallbackDir ?: File(context.filesDir, "logs")
        }

        File(targetDir, "bak").mkdirs()

        return targetDir.absolutePath + File.separator
    }

    private fun addFileAppender(lc: LoggerContext, logName: String, logDir: String, isMainProcess: Boolean) {
        val fileAppender = RollingFileAppender<ILoggingEvent>()

        fileAppender.apply {
            context = lc
            name = if (isMainProcess) "FILE-HOOK-$logName" else "FILE-APP-$logName"
            file = "$logDir$logName.log"
            isAppend = true

            rollingPolicy = FixedWindowRollingPolicy().apply {
                context = lc
                fileNamePattern = "${logDir}bak/$logName.idx%i.log"
                minIndex = 1
                maxIndex = 3
                setParent(fileAppender)
                start()
            }

            triggeringPolicy = SizeBasedTriggeringPolicy<ILoggingEvent>().apply {
                context = lc
                maxFileSize = FileSize.valueOf("7MB")
                start()
            }

            encoder = PatternLayoutEncoder().apply {
                context = lc
                pattern = "%d{dd日 HH:mm:ss.SS} %msg%n"
                start()
            }

            start()
        }

        val finalAppender = AsyncAppender().apply {
            context = lc
            name = "ASYNC-FILE-$logName"
            queueSize = 512
            discardingThreshold = 0
            addAppender(fileAppender)
            start()
        }

        lc.getLogger(logName).apply {
            level = Level.ALL
            isAdditive = true
            addAppender(finalAppender)
        }
    }
}
