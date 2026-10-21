package io.github.sapog3090.opprice.core.sources

import io.github.sapog3090.opprice.core.Condition
import io.github.sapog3090.opprice.core.FailureKind
import io.github.sapog3090.opprice.core.Fetcher
import io.github.sapog3090.opprice.core.Listing
import io.github.sapog3090.opprice.core.Money
import io.github.sapog3090.opprice.core.Source
import io.github.sapog3090.opprice.core.SourceException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/**
 * Wildberries отдаёт поисковую выдачу публичным JSON-эндпоинтом, которым пользуется их же сайт.
 * Номер версии в пути периодически меняется, поэтому перебираем несколько.
 */
class WildberriesSource(private val fetcher: Fetcher) : OfferSource {
    override val source = Source.WILDBERRIES
    override val defaultCondition = Condition.NEW

    override fun browserUrl(query: String) =
        "https://www.wildberries.ru/catalog/0/search.aspx?search=${enc(query)}"

    override suspend fun search(query: String): List<Listing> = collectPages(PAGES) { page ->
        parse(fetchPage(query, page))
    }

    private suspend fun fetchPage(query: String, page: Int): String {
        var lastCode = 0
        for (version in API_VERSIONS) {
            val url = "https://search.wb.ru/exactmatch/ru/common/$version/search" +
                "?ab_testing=false&appType=1&curr=rub&dest=-1257786&lang=ru&page=$page" +
                "&query=${enc(query)}&resultset=catalog&sort=popular&spp=30&suppressSpellcheck=false"
            val r = fetcher.get(url, HEADERS)
            when (r.code) {
                200 -> return r.body
                403, 429 -> throw SourceException(FailureKind.BLOCKED, "WB ограничил запросы (HTTP ${r.code})")
                else -> lastCode = r.code
            }
        }
        throw SourceException(FailureKind.HTTP, "HTTP $lastCode")
    }

    companion object {
        private const val PAGES = 2
        private val API_VERSIONS = listOf("v13", "v9", "v5")
        private val HEADERS = mapOf(
            "Accept" to "application/json",
            "Origin" to "https://www.wildberries.ru",
            "Referer" to "https://www.wildberries.ru/",
        )

        fun parse(body: String): List<Listing> {
            val root = parseJsonOrNull(body) as? JsonObject
                ?: throw SourceException(FailureKind.PARSE, "WB ответил не JSON")
            val products = (root.obj("data")?.get("products") ?: root["products"]) as? JsonArray
                ?: return emptyList()
            return products.mapNotNull { (it as? JsonObject)?.toListing() }
        }

        private fun JsonObject.toListing(): Listing? {
            val id = long("id") ?: return null
            val name = str("name")?.takeIf { it.isNotBlank() } ?: return null
            if (long("totalQuantity") == 0L) return null
            // Цены в копейках. В новой выдаче — по размерам (у телефонов это цвета/комплектации).
            val kopecks = arr("sizes")
                ?.mapNotNull { (it as? JsonObject)?.obj("price")?.long("product") }
                ?.filter { it > 0 }
                ?.minOrNull()
                ?: long("salePriceU")
                ?: return null
            return Listing(
                source = Source.WILDBERRIES,
                id = id.toString(),
                title = name,
                price = Money(kopecks / 100.0, "RUB"),
                url = "https://www.wildberries.ru/catalog/$id/detail.aspx",
                brand = str("brand"),
                seller = str("supplier"),
            )
        }
    }
}
