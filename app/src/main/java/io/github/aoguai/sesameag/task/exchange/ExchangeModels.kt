package io.github.aoguai.sesameag.task.exchange

import io.github.aoguai.sesameag.entity.MapperEntity

enum class ExchangeSafety {
    AUTO,
    LOG_ONLY,
    UNAVAILABLE
}

data class ExchangeCost(
    val pointText: String = "",
    val cashText: String = ""
)

data class ExchangeLimit(
    val statusText: String = "",
    val stockText: String = "",
    val validText: String = ""
)

data class ExchangeItem(
    val id: String,
    val name: String,
    val cost: ExchangeCost = ExchangeCost(),
    val limit: ExchangeLimit = ExchangeLimit(),
    val safety: ExchangeSafety = ExchangeSafety.AUTO,
    val safetyReason: String = ""
) {
    fun displayName(): String {
        val parts = listOf(
            cost.pointText,
            cost.cashText,
            limit.stockText,
            limit.validText,
            limit.statusText,
            when (safety) {
                ExchangeSafety.AUTO -> ""
                ExchangeSafety.LOG_ONLY -> "仅提醒${safetyReason.takeIf { it.isNotBlank() }?.let { ":$it" }.orEmpty()}"
                ExchangeSafety.UNAVAILABLE -> "不可兑换${safetyReason.takeIf { it.isNotBlank() }?.let { ":$it" }.orEmpty()}"
            }
        ).filter { it.isNotBlank() }
        return if (parts.isEmpty()) name else "$name[${parts.joinToString(" | ")}]"
    }

    fun toMapperEntity(): MapperEntity {
        val item = this
        return object : MapperEntity() {
            init {
                id = item.id
                name = item.displayName()
            }
        }
    }
}

object ExchangeSafetyRules {
    private val orderKeywords = listOf(
        "收货", "发货", "下单", "实付", "支付页", "支付链路", "支付金额", "支付成功", "支付时", "邮寄", "快递", "订单",
        "付邮", "邮费", "包邮", "商品详情", "小程序", "goods", "goodsDetail", "platformPhysicalItem"
    )

    fun hasPositiveCash(vararg rawValues: String?): Boolean {
        return rawValues.any { value ->
            val normalized = value?.trim().orEmpty()
            normalized.isNotEmpty() && normalized.toBigDecimalOrNull()?.signum() == 1
        }
    }

    fun hasOrderLikeText(vararg textValues: String?): Boolean {
        val text = textValues.joinToString(" ").lowercase()
        return orderKeywords.any { text.contains(it.lowercase()) }
    }

    fun classify(
        cashValues: List<String?> = emptyList(),
        textValues: List<String?> = emptyList(),
        defaultReason: String = "涉及实付或下单链路"
    ): Pair<ExchangeSafety, String> {
        return if (hasPositiveCash(*cashValues.toTypedArray()) || hasOrderLikeText(*textValues.toTypedArray())) {
            ExchangeSafety.LOG_ONLY to defaultReason
        } else {
            ExchangeSafety.AUTO to ""
        }
    }
}
