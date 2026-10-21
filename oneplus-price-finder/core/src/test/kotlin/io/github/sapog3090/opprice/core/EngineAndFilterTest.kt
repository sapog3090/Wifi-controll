package io.github.sapog3090.opprice.core

import io.github.sapog3090.opprice.core.sources.OfferSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineAndFilterTest {

    private class FakeSource(
        override val source: Source,
        private val items: List<Pair<String, Money>>,
        private val error: Exception? = null,
    ) : OfferSource {
        override val defaultCondition = Condition.UNKNOWN
        override fun browserUrl(query: String) = "https://example.com"
        override suspend fun search(query: String): List<Listing> {
            error?.let { throw it }
            return items.mapIndexed { i, (title, price) -> Listing(source, "$i", title, price, "https://x/$i") }
        }
    }

    private val rates = RateProvider { if (it == "RUB") 1.0 else if (it == "USD") 80.0 else null }

    @Test fun `engine converts currencies and isolates failures`() = runTest {
        val engine = SearchEngine(
            listOf(
                FakeSource(Source.EBAY, listOf("OnePlus 12 256GB" to Money(400.0, "USD"))),
                FakeSource(Source.AVITO, emptyList(), SourceException(FailureKind.BLOCKED, "капча")),
            ),
            rates,
        )
        val states = engine.searchAll(SearchQuery("oneplus", setOf(Source.EBAY, Source.AVITO)))
        val ebay = states.getValue(Source.EBAY) as SourceState.Done
        assertEquals(32000L, ebay.offers.single().priceRub)
        assertEquals("12", ebay.offers.single().model)
        assertEquals(FailureKind.BLOCKED, (states.getValue(Source.AVITO) as SourceState.Failed).kind)
    }

    @Test fun `filter hides accessories, other models and bait prices`() = runTest {
        val titles = listOf(
            "OnePlus 13 12/256" to 62000.0,
            "OnePlus 13 12/256 новый" to 60000.0,
            "OnePlus 13 12/256 б/у" to 52000.0,
            "OnePlus 13 12/256 срочно" to 9000.0,
            "OnePlus 13 16/512" to 70000.0,
            "OnePlus 13R 12/256" to 41000.0,
            "Чехол для OnePlus 13" to 500.0,
        )
        val engine = SearchEngine(
            listOf(FakeSource(Source.WILDBERRIES, titles.map { it.first to Money(it.second, "RUB") })),
            rates,
        )
        val offers = (engine.searchAll(SearchQuery("oneplus 13", setOf(Source.WILDBERRIES)))
            .getValue(Source.WILDBERRIES) as SourceState.Done).offers

        val r = OfferFilter.apply(FilterSpec(model = "13", storageGb = 256), offers)
        assertEquals(listOf(52000L, 60000L, 62000L), r.offers.map { it.priceRub })
        val reasons = r.hidden.associate { it.offer.listing.title to it.reason }
        assertEquals(HideReason.SUSPICIOUS, reasons["OnePlus 13 12/256 срочно"])
        assertEquals(HideReason.ACCESSORY, reasons["Чехол для OnePlus 13"])
        assertEquals(HideReason.OTHER_MODEL, reasons["OnePlus 13R 12/256"])
        assertEquals(HideReason.OTHER_MEMORY, reasons["OnePlus 13 16/512"])

        val onlyNew = OfferFilter.apply(FilterSpec(model = "13", condition = ConditionFilter.NEW), offers)
        assertEquals(listOf(60000L), onlyNew.offers.map { it.priceRub })

        val facets = OfferFilter.facets(FilterSpec(), offers)
        assertEquals("13" to 5, facets.models.first())
        assertTrue(facets.storages.contains(512 to 1))
    }
}
