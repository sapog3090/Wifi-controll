package io.github.sapog3090.opprice.core

enum class Source(val title: String, val tag: String) {
    WILDBERRIES("Wildberries", "WB"),
    AVITO("Avito", "AV"),
    ALIEXPRESS("AliExpress", "AE"),
    EBAY("eBay", "EB"),
}

enum class Condition { NEW, USED, REFURBISHED, UNKNOWN }

/** Что на самом деле продаётся в объявлении. */
enum class Kind { PHONE, ACCESSORY, DAMAGED, UNKNOWN_BRAND }

data class Money(val amount: Double, val currency: String)

data class Memory(val ramGb: Int?, val storageGb: Int?)

/** Объявление в том виде, в каком его отдал парсер площадки. */
data class Listing(
    val source: Source,
    val id: String,
    val title: String,
    val price: Money,
    val url: String,
    val brand: String? = null,
    val conditionText: String? = null,
    val seller: String? = null,
    val location: String? = null,
)

/** Объявление после разбора заголовка и перевода цены в рубли. */
data class Offer(
    val listing: Listing,
    val priceRub: Long,
    val kind: Kind,
    val brand: String?,
    val model: String?,
    val memory: Memory?,
    val condition: Condition,
) {
    val source: Source get() = listing.source
    val key: String get() = "${listing.source.name}:${listing.id}"
}

data class SearchQuery(val text: String, val sources: Set<Source>)
