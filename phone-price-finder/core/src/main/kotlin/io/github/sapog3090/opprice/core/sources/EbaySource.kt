package io.github.sapog3090.opprice.core.sources

import io.github.sapog3090.opprice.core.Condition
import io.github.sapog3090.opprice.core.FailureKind
import io.github.sapog3090.opprice.core.Fetcher
import io.github.sapog3090.opprice.core.Listing
import io.github.sapog3090.opprice.core.Source
import io.github.sapog3090.opprice.core.SourceException
import io.github.sapog3090.opprice.core.text.MoneyParser
import org.jsoup.Jsoup

/**
 * eBay.com, категория 9355 «Cell Phones & Smartphones», только «Купить сейчас» (без аукционов).
 * Поддерживаются обе вёрстки выдачи: старая s-item и новая s-card.
 */
class EbaySource(private val fetcher: Fetcher) : OfferSource {
    override val source = Source.EBAY
    override val defaultCondition = Condition.UNKNOWN

    override fun browserUrl(query: String) =
        "https://www.ebay.com/sch/9355/i.html?_nkw=${enc(query)}&LH_BIN=1&_ipg=120"

    override suspend fun search(query: String): List<Listing> {
        val r = fetcher.page(browserUrl(query), readySelector = "li.s-item, li.s-card")
        if (r.code == 403 || r.code == 429) {
            throw SourceException(FailureKind.BLOCKED, "eBay ограничил доступ (HTTP ${r.code})")
        }
        if (r.code != 200) throw SourceException(FailureKind.HTTP, "HTTP ${r.code}")
        return parse(r.body)
    }

    companion object {
        private val ITEM_ID = Regex("""/itm/(?:[^/?]+/)?(\d{9,})""")
        private val NOISE = Regex("""^New Listing\s*|Opens in a new window or tab""", RegexOption.IGNORE_CASE)

        fun parse(html: String): List<Listing> {
            val doc = Jsoup.parse(html, "https://www.ebay.com/")
            val cards = doc.select("li.s-item, li.s-card")
            if (cards.isEmpty()) {
                if ("Pardon Our Interruption" in doc.title() || doc.selectFirst("#captcha_form, [id*=captcha]") != null) {
                    throw SourceException(FailureKind.BLOCKED, "eBay показал капчу")
                }
                if (doc.selectFirst(".srp-save-null-search, .srp-controls__count-heading") != null) return emptyList()
                throw SourceException(FailureKind.PARSE, "не нашёл карточек — возможно, eBay изменил вёрстку")
            }
            return cards.mapNotNull { card ->
                val title = card.selectFirst(".s-item__title, .s-card__title")?.text()
                    ?.replace(NOISE, "")?.trim()
                if (title.isNullOrBlank() || title.equals("Shop on eBay", ignoreCase = true)) return@mapNotNull null
                val href = card.selectFirst("a.s-item__link, a.su-link, a[href*=/itm/]")?.attr("href")
                    ?: return@mapNotNull null
                val id = ITEM_ID.find(href)?.groupValues?.get(1) ?: return@mapNotNull null
                val price = MoneyParser.parse(card.selectFirst(".s-item__price, .s-card__price")?.text(), "USD")
                    ?.takeIf { it.amount > 0 } ?: return@mapNotNull null
                Listing(
                    source = Source.EBAY,
                    id = id,
                    title = title,
                    price = price,
                    url = "https://www.ebay.com/itm/$id",
                    conditionText = card.selectFirst(".SECONDARY_INFO, .s-card__subtitle")?.text(),
                    location = card.selectFirst(".s-item__location, .s-item__itemLocation")?.text()
                        ?.removePrefix("from ")?.takeIf { it.isNotBlank() },
                )
            }
        }
    }
}
