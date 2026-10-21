package io.github.sapog3090.opprice.core.sources

import io.github.sapog3090.opprice.core.Condition
import io.github.sapog3090.opprice.core.FailureKind
import io.github.sapog3090.opprice.core.Fetcher
import io.github.sapog3090.opprice.core.Listing
import io.github.sapog3090.opprice.core.Source
import io.github.sapog3090.opprice.core.SourceException

/**
 * AliExpress (aliexpress.ru) рендерит выдачу из JSON, встроенного в страницу.
 * Структура этого JSON меняется часто, поэтому вместо фиксированного пути
 * товары ищутся эвристически — см. [JsonHarvester].
 */
class AliExpressSource(private val fetcher: Fetcher) : OfferSource {
    override val source = Source.ALIEXPRESS
    override val defaultCondition = Condition.NEW

    override fun browserUrl(query: String) = "https://aliexpress.ru/wholesale?SearchText=${enc(query)}&g=y"

    override suspend fun search(query: String): List<Listing> {
        val r = fetcher.get(browserUrl(query), mapOf("Referer" to "https://aliexpress.ru/"))
        if (r.code == 403 || r.code == 429) {
            throw SourceException(FailureKind.BLOCKED, "AliExpress ограничил доступ (HTTP ${r.code})")
        }
        if (r.code != 200) throw SourceException(FailureKind.HTTP, "HTTP ${r.code}")
        return parse(r.body)
    }

    companion object {
        private val BLOCK_MARKERS = listOf("punish", "x5secdata", "baxia", "slide to verify", "captcha")

        fun parse(html: String): List<Listing> {
            val products = JsonHarvester.products(html, defaultCurrency = "RUB")
            if (products.isEmpty()) {
                val lower = html.lowercase()
                if (BLOCK_MARKERS.any { it in lower }) {
                    throw SourceException(FailureKind.BLOCKED, "AliExpress показал проверку «я не робот»")
                }
                throw SourceException(FailureKind.PARSE, "не нашёл товаров в данных страницы")
            }
            return products.map {
                Listing(
                    source = Source.ALIEXPRESS,
                    id = it.id,
                    title = it.title,
                    price = it.price,
                    url = "https://aliexpress.ru/item/${it.id}.html",
                )
            }
        }
    }
}
