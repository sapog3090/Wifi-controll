package io.github.sapog3090.opprice.net

import android.content.Context
import io.github.sapog3090.opprice.core.CbrRates
import io.github.sapog3090.opprice.core.Fetcher
import io.github.sapog3090.opprice.core.RateProvider
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Курсы ЦБ, обновляются не чаще раза в 12 часов; при недоступности ЦБ берётся последний известный курс. */
class CachedRates(context: Context, private val fetcher: Fetcher) : RateProvider {

    private val prefs = context.getSharedPreferences("rates", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private var lastAttempt = 0L

    val updatedAt: Long get() = prefs.getLong(KEY_UPDATED, 0L)

    fun cached(currency: String): Double? = prefs.getString("rate_$currency", null)?.toDoubleOrNull()

    override suspend fun rubPer(currency: String): Double? {
        if (currency == "RUB") return 1.0
        mutex.withLock {
            val now = System.currentTimeMillis()
            if (now - updatedAt > MAX_AGE && now - lastAttempt > RETRY_AFTER) {
                lastAttempt = now
                refresh()
            }
        }
        return cached(currency)
    }

    private suspend fun refresh() {
        val rates = runCatching { fetcher.get(CbrRates.URL, emptyMap()) }
            .getOrNull()
            ?.takeIf { it.code == 200 }
            ?.let { CbrRates.parse(it.body) }
            .orEmpty()
        if (rates.isEmpty()) return
        prefs.edit().apply {
            rates.forEach { (code, value) -> putString("rate_$code", value.toString()) }
            putLong(KEY_UPDATED, System.currentTimeMillis())
        }.apply()
    }

    private companion object {
        const val KEY_UPDATED = "updated"
        const val MAX_AGE = 12 * 60 * 60 * 1000L
        const val RETRY_AFTER = 5 * 60 * 1000L
    }
}
