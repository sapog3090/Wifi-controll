package io.github.sapog3090.opprice.core.sources

import io.github.sapog3090.opprice.core.Condition
import io.github.sapog3090.opprice.core.FailureKind
import io.github.sapog3090.opprice.core.Fetcher
import io.github.sapog3090.opprice.core.Listing
import io.github.sapog3090.opprice.core.Money
import io.github.sapog3090.opprice.core.Source
import io.github.sapog3090.opprice.core.SourceException
import io.github.sapog3090.opprice.core.text.MoneyParser
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/** Avito: HTML выдачи по всей России в разделе «Телефоны». Карточки помечены data-marker. */
class AvitoSource(private val fetcher: Fetcher) : OfferSource {
    override val source = Source.AVITO
    override val defaultCondition = Condition.USED

    override fun browserUrl(query: String) = "https://www.avito.ru/rossiya/telefony?q=${enc(query)}"

    override suspend fun search(query: String): List<Listing> = collectPages(PAGES) { page ->
        val r = fetcher.page(browserUrl(query) + "&p=$page", readySelector = "[data-marker=item]")
        if (r.code == 403 || r.code == 429) {
            throw SourceException(FailureKind.BLOCKED, "Avito ограничил доступ (HTTP ${r.code})")
        }
        if (r.code != 200) throw SourceException(FailureKind.HTTP, "HTTP ${r.code}")
        parse(r.body)
    }

    companion object {
        private const val PAGES = 2
        private val NOTHING_FOUND = Regex("""ничего не найдено|не нашли|нет объявлений""", RegexOption.IGNORE_CASE)

        fun parse(html: String): List<Listing> {
            val doc = Jsoup.parse(html, "https://www.avito.ru/")
            val cards = doc.select("[data-marker=item]")
            if (cards.isEmpty()) {
                if (isBlocked(doc)) throw SourceException(FailureKind.BLOCKED, "Avito показал капчу")
                if (NOTHING_FOUND.containsMatchIn(doc.text())) return emptyList()
                throw SourceException(FailureKind.PARSE, "не нашёл карточек — возможно, Avito изменил вёрстку")
            }
            return cards.mapNotNull { card ->
                val link = card.selectFirst("a[data-marker=item-title]") ?: card.selectFirst("a[itemprop=url]")
                    ?: return@mapNotNull null
                val url = link.absUrl("href").substringBefore('?').ifBlank { return@mapNotNull null }
                val id = card.attr("data-item-id").ifBlank { card.id().removePrefix("i") }.ifBlank { url }
                val title = card.selectFirst("[itemprop=name]")?.text()
                    ?: link.attr("title").ifBlank { link.text() }
                val price = card.selectFirst("meta[itemprop=price]")?.attr("content")?.toDoubleOrNull()
                    ?: MoneyParser.parse(card.selectFirst("[data-marker=item-price]")?.text())?.amount
                if (title.isBlank() || price == null || price <= 0) return@mapNotNull null
                Listing(
                    source = Source.AVITO,
                    id = id,
                    title = title,
                    price = Money(price, "RUB"),
                    url = url,
                    location = card.selectFirst("[data-marker=item-address]")?.text()?.takeIf { it.isNotBlank() },
                )
            }
        }

        private fun isBlocked(doc: Document): Boolean {
            val text = doc.title() + " " + doc.body()?.text().orEmpty().take(2000)
            return "Доступ ограничен" in text || "Подтвердите, что вы не робот" in text ||
                doc.selectFirst("#firewall-container, .firewall-container, [class*=captcha]") != null
        }
    }
}
