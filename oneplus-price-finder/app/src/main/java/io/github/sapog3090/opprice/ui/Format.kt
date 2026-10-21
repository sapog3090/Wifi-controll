package io.github.sapog3090.opprice.ui

import io.github.sapog3090.opprice.core.Condition
import io.github.sapog3090.opprice.core.ConditionFilter
import io.github.sapog3090.opprice.core.Memory
import io.github.sapog3090.opprice.core.Money
import io.github.sapog3090.opprice.data.TrackedEntity
import io.github.sapog3090.opprice.data.spec
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

private const val NBSP = ' '

fun groupDigits(value: Long): String {
    val digits = abs(value).toString().reversed().chunked(3).joinToString(NBSP.toString()).reversed()
    return if (value < 0) "−$digits" else digits
}

fun rub(value: Long): String = "${groupDigits(value)}$NBSP₽"

fun signedRub(delta: Long): String = when {
    delta > 0 -> "+${rub(delta)}"
    delta < 0 -> "−${rub(-delta)}"
    else -> "0$NBSP₽"
}

fun money(m: Money): String {
    val symbol = when (m.currency) {
        "USD" -> "$"
        "EUR" -> "€"
        "GBP" -> "£"
        "CNY" -> "¥"
        else -> m.currency + NBSP
    }
    val whole = m.amount.roundToLong()
    return symbol + groupDigits(whole)
}

fun memory(m: Memory?): String? {
    val gb = m?.storageGb ?: return null
    val ram = m.ramGb
    return if (ram != null) "$ram/${storage(gb)}" else storage(gb)
}

fun storage(gb: Int): String = if (gb >= 1024) "${gb / 1024}${NBSP}ТБ" else "$gb${NBSP}ГБ"

fun condition(c: Condition): String? = when (c) {
    Condition.NEW -> "новый"
    Condition.USED -> "б/у"
    Condition.REFURBISHED -> "восстановленный"
    Condition.UNKNOWN -> null
}

fun conditionFilter(c: ConditionFilter): String = when (c) {
    ConditionFilter.ANY -> "Любые"
    ConditionFilter.NEW -> "Новые"
    ConditionFilter.USED -> "Б/у"
}

/** «OnePlus 13 · 256 ГБ · новые» — короткое имя сохранённого поиска. */
fun TrackedEntity.describe(): String {
    val spec = spec
    return listOfNotNull(
        spec.model?.let { "OnePlus $it" } ?: query,
        spec.storageGb?.let(::storage),
        conditionFilter(spec.condition).lowercase().takeIf { spec.condition != ConditionFilter.ANY },
    ).joinToString(" · ")
}

fun ago(time: Long, now: Long = System.currentTimeMillis()): String {
    val minutes = (now - time) / 60_000
    return when {
        minutes < 1 -> "только что"
        minutes < 60 -> "$minutes мин назад"
        minutes < 24 * 60 -> "${minutes / 60} ч назад"
        minutes < 48 * 60 -> "вчера"
        else -> date(time)
    }
}

fun date(time: Long): String = SimpleDateFormat("d MMM", Locale("ru")).format(Date(time))

fun dateTime(time: Long): String = SimpleDateFormat("d MMM, HH:mm", Locale("ru")).format(Date(time))
