package io.github.sapog3090.opprice.core.text

import io.github.sapog3090.opprice.core.Condition
import io.github.sapog3090.opprice.core.Kind
import io.github.sapog3090.opprice.core.Memory

/**
 * Разбирает заголовок объявления: что продаётся, какая модель, сколько памяти, новое или б/у.
 *
 * Сортировка «по возрастанию цены» на любой площадке выдаёт сначала чехлы и стёкла,
 * поэтому главная работа здесь — отличить телефон от всего, что рядом с ним продаётся.
 */
object TitleAnalyzer {

    data class Result(
        val kind: Kind,
        val model: String?,
        val memory: Memory?,
        val condition: Condition,
    )

    private const val BRAND = "oneplus"

    // Слова, после которых объявление почти наверняка не про сам телефон.
    private val ACCESSORY = listOf(
        "чехол", "чехл", "накладк", "бампер", "книжк", "стекл", "пленк", "гидрогел",
        "кабел", "провод", "зарядн", "блок питания", "адаптер", "переходник", "держател",
        "подставк", "кейс", "наушник", "гарнитур", "часы", "ремеш", "браслет", "планшет",
        "стилус", "клавиатур", "муляж", "только коробк", "пустая коробк", "наклейк", "лоток", "сим-лоток",
        "дисплей", "экран", "тачскрин", "корпус", "крышк", "аккумулятор", "батаре", "шлейф",
        "плата", "запчаст", "разъем", "динамик", "микрофон", "кнопк", "рамк", "модуль",
        "case", "cover", "bumper", "glass", "protector", "film", "cable", "charger",
        "adapter", "holder", "stand", "buds", "earbuds", "headphone", "watch", "band",
        "strap", "pad", "tablet", "stylus", "keyboard", "dummy", "box only", "empty box", "sticker", "skin",
        "tray", "display", "screen", "lcd", "housing", "battery", "flex", "board",
        "spare", "replacement", "lens",
    ).joinToString("|", "(?:", ")") { wordStart(it) }.let(::Regex)

    private val FOR_BRAND = Regex(
        wordStart("""(?:для|for|на|под|compatible with)\s+(?:смартфон\p{L}*\s+|телефон\p{L}*\s+|phone\s+)?$BRAND"""),
    )

    private val PHONE_MARKER = listOf("смартфон", "телефон", "smartphone", "phone", "mobile", "мобильн")
        .joinToString("|", "(?:", ")") { wordStart(it) }.let(::Regex)

    private val DAMAGED = listOf(
        "на запчаст", "разбит", "сломан", "треснут", "не включ", "неисправ", "нерабоч",
        "утоплен", "залит", "под ремонт", "for parts", "parts only", "broken", "cracked",
        "not working", "faulty",
    ).joinToString("|", "(?:", ")") { wordStart(it) }.let(::Regex)

    private val REFURBISHED = listOf("восстановл", "refurb", "renewed")
        .joinToString("|", "(?:", ")") { wordStart(it) }.let(::Regex)

    private val USED = listOf(
        wholeWord("б/у"), wholeWord("б\\.у\\.?"), wholeWord("бу"), wordStart("как нов"),
        wordStart("почти нов"), wordStart("like new"), wholeWord("used"), wordStart("pre-?owned"),
        wordStart("open box"), wordStart("уцен"), wordStart("с пробегом"),
    ).joinToString("|", "(?:", ")").let(::Regex)

    private val NEW = listOf(
        wholeWord("нов(?:ый|ая|ое|ые|ого)"), wholeWord("new"), wholeWord("brand new"),
        wordStart("запечатан"), wordStart("нераспакован"), wordStart("sealed"),
    ).joinToString("|", "(?:", ")").let(::Regex)

    private val MEMORY_PAIR = Regex(
        """(?<![\p{N}])(\d{1,2})\s*(?:gb|гб|g)?\s*[/+]\s*(\d{2,4}|1)\s*(gb|гб|g|tb|тб|t)?(?![\p{L}\p{N}])""",
    )
    private val STORAGE_ONLY = Regex("""(?<![\p{N}/+])(64|128|256|512)\s*(?:gb|гб|g)(?![\p{L}])""")
    private val TERABYTE = Regex("""(?<![\p{N}/+])1\s*(?:tb|тб)(?![\p{L}])""")
    private val VALID_STORAGE = setOf(64, 128, 256, 512, 1024)

    private val NOT_A_MODEL_WORD = Regex("""^(?:смартфон|телефон|smartphone|phone|мобильный|новый|new)$""")

    private class ModelPattern(val regex: Regex, val format: (MatchResult) -> String?)

    private val MODEL_PATTERNS = listOf(
        ModelPattern(Regex("""^nord\s*ce\s*(\d)\s*lite""")) { "Nord CE${it.groupValues[1]} Lite" },
        ModelPattern(Regex("""^nord\s*ce\s*(\d)?""")) { "Nord CE${it.groupValues[1]}" },
        ModelPattern(Regex("""^nord\s*n\s*(\d{2,3})""")) { "Nord N${it.groupValues[1]}" },
        ModelPattern(Regex("""^nord\s*(\d)(?![\p{N}])""")) { "Nord ${it.groupValues[1]}" },
        ModelPattern(Regex("""^nord(?![\p{L}])""")) { "Nord" },
        ModelPattern(Regex("""^ace\s*(\d)?\s*(pro|ultra|racing|v)?(?![\p{L}])""")) { m ->
            listOf("Ace", m.groupValues[1], m.groupValues[2].replaceFirstChar { it.uppercase() })
                .filter { it.isNotEmpty() }.joinToString(" ")
        },
        ModelPattern(Regex("""^open(?![\p{L}])""")) { "Open" },
        ModelPattern(Regex("""^(\d{1,2})\s*(r|t|s|pro)?(?![\p{L}\p{N}/+.,])""")) { m ->
            val n = m.groupValues[1].toInt()
            if (n !in 1..20) return@ModelPattern null
            when (m.groupValues[2]) {
                "" -> "$n"
                "pro" -> "$n Pro"
                else -> "$n${m.groupValues[2].uppercase()}"
            }
        },
    )

    fun analyze(
        title: String,
        brand: String? = null,
        conditionText: String? = null,
        defaultCondition: Condition = Condition.UNKNOWN,
    ): Result {
        val t = normalize(title)
        val brandIsOnePlus = brand != null && BRAND in normalize(brand)
        val memory = parseMemory(t)
        val conditionSource = normalize(conditionText.orEmpty()) + " " + t
        val kind = when {
            DAMAGED.containsMatchIn(conditionSource) -> Kind.DAMAGED
            isAccessory(t) -> Kind.ACCESSORY
            BRAND !in t && !brandIsOnePlus -> Kind.NOT_ONEPLUS
            else -> Kind.PHONE
        }
        return Result(
            kind = kind,
            model = parseModel(t, assumeBrand = brandIsOnePlus),
            memory = memory,
            condition = parseCondition(conditionSource) ?: defaultCondition,
        )
    }

    /** Модель из поискового запроса пользователя: «oneplus 13r» → «13R». */
    fun modelOf(text: String): String? = parseModel(normalize(text), assumeBrand = false)

    /** Объём накопителя из поискового запроса: «oneplus 12 512» → 512. */
    fun storageOf(text: String): Int? {
        val t = normalize(text)
        parseMemory(t)?.storageGb?.let { return it }
        return Regex("""(?<![\p{N}])(128|256|512)(?![\p{N}])""").find(t)?.groupValues?.get(1)?.toInt()
    }

    private fun isAccessory(t: String): Boolean {
        val accessoryAt = listOfNotNull(ACCESSORY.find(t)?.range?.first, FOR_BRAND.find(t)?.range?.first)
            .minOrNull() ?: return false
        val phoneAt = listOfNotNull(PHONE_MARKER.find(t)?.range?.first, memoryPosition(t)).minOrNull()
        // «Смартфон OnePlus 13 12/256 + чехол» — телефон, «Чехол для OnePlus 13» — нет.
        return phoneAt == null || accessoryAt < phoneAt
    }

    private fun parseCondition(t: String): Condition? = when {
        REFURBISHED.containsMatchIn(t) -> Condition.REFURBISHED
        USED.containsMatchIn(t) -> Condition.USED
        NEW.containsMatchIn(t) -> Condition.NEW
        else -> null
    }

    private fun memoryPosition(t: String): Int? =
        MEMORY_PAIR.findAll(t).firstOrNull { pairToMemory(it) != null }?.range?.first
            ?: STORAGE_ONLY.find(t)?.range?.first
            ?: TERABYTE.find(t)?.range?.first

    private fun parseMemory(t: String): Memory? {
        MEMORY_PAIR.findAll(t).firstNotNullOfOrNull(::pairToMemory)?.let { return it }
        STORAGE_ONLY.find(t)?.let { return Memory(null, it.groupValues[1].toInt()) }
        if (TERABYTE.containsMatchIn(t)) return Memory(null, 1024)
        return null
    }

    private fun pairToMemory(m: MatchResult): Memory? {
        val ram = m.groupValues[1].toInt()
        val raw = m.groupValues[2].toInt()
        val unit = m.groupValues[3]
        val storage = if (raw == 1 || unit.startsWith("t") || unit.startsWith("т")) raw * 1024 else raw
        return if (ram in 3..24 && storage in VALID_STORAGE) Memory(ram, storage) else null
    }

    private fun parseModel(t: String, assumeBrand: Boolean): String? {
        var idx = t.indexOf(BRAND)
        while (idx >= 0) {
            val rest = t.substring(idx + BRAND.length).trimStart(' ', '-', ':', '_')
            matchModel(rest)?.let { return it }
            idx = t.indexOf(BRAND, idx + BRAND.length)
        }
        if (!assumeBrand) return null
        // Бренд указан отдельно (так делает Wildberries) — ищем модель с начала любого слова.
        for (word in Regex("""[\p{L}\p{N}]+""").findAll(t)) {
            if (NOT_A_MODEL_WORD.matches(word.value)) continue
            matchModel(t.substring(word.range.first))?.let { return it }
        }
        return null
    }

    private fun matchModel(rest: String): String? {
        for (p in MODEL_PATTERNS) {
            val m = p.regex.find(rest) ?: continue
            return p.format(m)
        }
        return null
    }
}
