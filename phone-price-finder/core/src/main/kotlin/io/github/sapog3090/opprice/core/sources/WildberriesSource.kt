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

    /** Адрес, который сработал в прошлый раз, пробуем первым. */
    @Volatile private var workingEndpoint: String? = null

    private suspend fun fetchPage(query: String, page: Int): String {
        val params = "?ab_testing=false&appType=1&curr=rub&dest=-1257786&lang=ru&page=$page" +
            "&query=${enc(query)}&resultset=catalog&sort=popular&spp=30&suppressSpellcheck=false"
        val endpoints = listOfNotNull(workingEndpoint) + (ENDPOINTS - setOfNotNull(workingEndpoint))
        val seen = mutableListOf<String>()
        var blocked = false
        for (endpoint in endpoints) {
            val r = fetcher.api(endpoint + params, ORIGIN)
            if (r.code == 200 && r.body.trimStart().startsWith("{")) {
                workingEndpoint = endpoint
                return r.body
            }
            if (r.code == 403 || r.code == 429 || r.code == 498) blocked = true
            seen += if (r.code == -1) "сеть" else "${r.code}"
        }
        if (blocked) throw SourceException(FailureKind.BLOCKED, "WB не пустил запрос — нужна проверка на сайте")
        throw SourceException(FailureKind.HTTP, "поиск не ответил (${seen.distinct().joinToString()})")
    }

    companion object {
        private const val PAGES = 2
        private const val ORIGIN = "https://www.wildberries.ru/"

        // Сайт периодически переносит поиск и меняет версию в пути.
        private val ENDPOINTS = listOf(
            "https://www.wildberries.ru/__internal/search/exactmatch/ru/common/v18/search",
            "https://search.wb.ru/exactmatch/ru/common/v18/search",
            "https://search.wb.ru/exactmatch/ru/common/v14/search",
            "https://search.wb.ru/exactmatch/ru/common/v13/search",
            "https://search.wb.ru/exactmatch/ru/common/v9/search",
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
