package io.github.sapog3090.opprice.core.text

import io.github.sapog3090.opprice.core.Money

object MoneyParser {
    private val RANGE_SPLIT = Regex("""\s+(?:to|до|-|–|—)\s+""")
    private val NUMBER = Regex("""\d[\d\s  .,]*""")
    private val WHITESPACE = Regex("""[\s  ]""")

    /**
     * Разбирает цену вида «12 990 ₽», «$1,234.56», «RUB 9,876.00», «EUR 12,50».
     * Для диапазона («$100.00 to $150.00») берётся нижняя граница.
     */
    fun parse(text: String?, defaultCurrency: String = "RUB"): Money? {
        if (text.isNullOrBlank()) return null
        val first = text.split(RANGE_SPLIT).first()
        val number = NUMBER.find(first) ?: return null
        val amount = parseNumber(number.value) ?: return null
        return Money(amount, currencyOf(first) ?: defaultCurrency)
    }

    fun currencyOf(text: String): String? {
        val t = text.lowercase()
        return when {
            "₽" in t || "руб" in t || "rub" in t || Regex(wholeWord("р\\.?")).containsMatchIn(t) -> "RUB"
            "€" in t || "eur" in t -> "EUR"
            "£" in t || "gbp" in t -> "GBP"
            "$" in t || "usd" in t -> "USD"
            "¥" in t || "cny" in t -> "CNY"
            else -> null
        }
    }

    internal fun parseNumber(raw: String): Double? {
        val s = raw.replace(WHITESPACE, "").trimEnd('.', ',')
        if (s.isEmpty()) return null
        val lastComma = s.lastIndexOf(',')
        val lastDot = s.lastIndexOf('.')
        val normalized = when {
            lastComma >= 0 && lastDot >= 0 ->
                if (lastComma > lastDot) s.replace(".", "").replace(',', '.') else s.replace(",", "")
            lastComma >= 0 -> withSingleSeparator(s, ',')
            lastDot >= 0 -> withSingleSeparator(s, '.')
            else -> s
        }
        return normalized.toDoubleOrNull()
    }

    /** «1,234» и «1.234.567» — разделители тысяч, «12,50» и «412.5» — дробная часть. */
    private fun withSingleSeparator(s: String, sep: Char): String {
        val digitsAfter = s.length - s.lastIndexOf(sep) - 1
        val thousands = s.count { it == sep } > 1 || digitsAfter == 3
        return if (thousands) s.replace(sep.toString(), "") else s.replace(sep, '.')
    }
}
