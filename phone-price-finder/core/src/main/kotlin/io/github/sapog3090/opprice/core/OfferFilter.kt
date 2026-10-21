package io.github.sapog3090.opprice.core

enum class ConditionFilter { ANY, NEW, USED }

data class FilterSpec(
    val brand: String? = null,
    val model: String? = null,
    val storageGb: Int? = null,
    val condition: ConditionFilter = ConditionFilter.ANY,
)

enum class HideReason(val label: String) {
    ACCESSORY("аксессуары и запчасти"),
    DAMAGED("неисправные"),
    UNKNOWN_BRAND("бренд не распознан"),
    OTHER_BRAND("другой бренд"),
    OTHER_MODEL("другая модель"),
    OTHER_MEMORY("другая память"),
    OTHER_CONDITION("другое состояние"),
    SUSPICIOUS("подозрительно дёшево"),
}

data class Hidden(val offer: Offer, val reason: HideReason)

data class FilterResult(val offers: List<Offer>, val hidden: List<Hidden>) {
    val min: Long? get() = offers.firstOrNull()?.priceRub
    val median: Long? = median(offers.map { it.priceRub })
    val hiddenCounts: List<Pair<HideReason, Int>>
        get() = hidden.groupingBy { it.reason }.eachCount().toList().sortedByDescending { it.second }
}

data class Facets(
    val brands: List<Pair<String, Int>>,
    val models: List<Pair<String, Int>>,
    val storages: List<Pair<Int, Int>>,
)

object OfferFilter {

    /** Цена ниже этой доли от медианы той же модели — почти всегда приманка или ошибка. */
    private const val SUSPICIOUS_SHARE = 0.5
    private const val MIN_GROUP_FOR_OUTLIERS = 4

    fun apply(spec: FilterSpec, offers: List<Offer>): FilterResult {
        val hidden = mutableListOf<Hidden>()
        val candidates = offers.filter { offer ->
            val reason = when {
                offer.kind == Kind.ACCESSORY -> HideReason.ACCESSORY
                offer.kind == Kind.DAMAGED -> HideReason.DAMAGED
                offer.kind == Kind.UNKNOWN_BRAND -> HideReason.UNKNOWN_BRAND
                spec.brand != null && offer.brand != spec.brand -> HideReason.OTHER_BRAND
                spec.model != null && offer.model != spec.model -> HideReason.OTHER_MODEL
                spec.storageGb != null && offer.memory?.storageGb != spec.storageGb -> HideReason.OTHER_MEMORY
                !matches(spec.condition, offer.condition) -> HideReason.OTHER_CONDITION
                else -> null
            }
            if (reason != null) hidden += Hidden(offer, reason)
            reason == null
        }

        // Выбросы считаем внутри одной модели и памяти: Redmi за 10 тысяч — не подозрительная цена
        // для поиска «все модели», а вот iPhone 15 Pro за те же деньги — очень.
        val suspicious = candidates
            .groupBy { it.model to it.memory?.storageGb }
            .values
            .filter { it.size >= MIN_GROUP_FOR_OUTLIERS }
            .flatMap { group ->
                val m = median(group.map { it.priceRub }) ?: return@flatMap emptyList()
                group.filter { it.priceRub < m * SUSPICIOUS_SHARE }
            }
            .toSet()
        suspicious.forEach { hidden += Hidden(it, HideReason.SUSPICIOUS) }

        return FilterResult(
            offers = candidates.filterNot { it in suspicious }.sortedBy { it.priceRub },
            hidden = hidden.sortedBy { it.offer.priceRub },
        )
    }

    /** Какие модели и объёмы встречаются среди найденных телефонов — для чипов фильтра. */
    fun facets(spec: FilterSpec, offers: List<Offer>): Facets {
        val phones = offers.filter { it.kind == Kind.PHONE && matches(spec.condition, it.condition) }
        val brands = phones.mapNotNull { it.brand }.groupingBy { it }.eachCount()
            .toList().sortedByDescending { it.second }
        val ofBrand = phones.filter { spec.brand == null || it.brand == spec.brand }
        val models = ofBrand.mapNotNull { it.model }.groupingBy { it }.eachCount()
            .toList().sortedByDescending { it.second }
        val storages = ofBrand.filter { spec.model == null || it.model == spec.model }
            .mapNotNull { it.memory?.storageGb }.groupingBy { it }.eachCount()
            .toList().sortedBy { it.first }
        return Facets(brands, models, storages)
    }

    private fun matches(filter: ConditionFilter, condition: Condition) = when (filter) {
        ConditionFilter.ANY -> true
        ConditionFilter.NEW -> condition == Condition.NEW
        ConditionFilter.USED -> condition == Condition.USED || condition == Condition.REFURBISHED
    }
}

internal fun median(values: List<Long>): Long? {
    if (values.isEmpty()) return null
    val s = values.sorted()
    val mid = s.size / 2
    return if (s.size % 2 == 1) s[mid] else (s[mid - 1] + s[mid]) / 2
}
