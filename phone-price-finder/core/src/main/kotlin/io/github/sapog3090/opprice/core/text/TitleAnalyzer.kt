package io.github.sapog3090.opprice.core.text

import io.github.sapog3090.opprice.core.Condition
import io.github.sapog3090.opprice.core.Kind
import io.github.sapog3090.opprice.core.Memory

/**
 * Разбирает заголовок объявления: что продаётся, какой бренд и модель, сколько памяти, новое или б/у.
 *
 * Сортировка «по возрастанию цены» на любой площадке выдаёт сначала чехлы и стёкла,
 * поэтому главная работа здесь — отличить телефон от всего, что рядом с ним продаётся.
 */
object TitleAnalyzer {

    data class Result(
        val kind: Kind,
        val brand: String?,
        val model: String?,
        val memory: Memory?,
        val condition: Condition,
    )

    // Слова, после которых объявление почти наверняка не про сам телефон.
    private val ACCESSORY = listOf(
        "чехол", "чехл", "накладк", "бампер", "книжк", "стекл", "пленк", "гидрогел",
        "кабел", "провод", "зарядн", "блок питания", "адаптер", "переходник", "держател",
        "подставк", "кейс", "наушник", "гарнитур", "часы", "ремеш", "браслет", "планшет",
        "стилус", "клавиатур", "муляж", "только коробк", "пустая коробк", "наклейк", "лоток", "сим-лоток",
        "дисплей", "экран", "тачскрин", "корпус", "крышк", "аккумулятор", "батаре", "шлейф",
        "плата", "запчаст", "разъем", "динамик", "микрофон", "кнопк", "рамк", "модуль",
        "case", "cover", "bumper", "glass", "protector", "film", "cable", "charger",
        "adapter", "holder", "stand", "buds", "earbuds", "airpods", "headphone", "watch", "band",
        "strap", "pad", "ipad", "tab", "tablet", "macbook", "imac", "stylus", "keyboard", "dummy",
        "box only", "empty box", "sticker", "skin", "tray", "display", "screen", "lcd", "housing",
        "battery", "flex", "board", "spare", "replacement", "lens", "magsafe",
    ).joinToString("|", "(?:", ")") { wordStart(it) }.let(::Regex)

    private val ANY_LINE = Brands.all.flatMap { it.lines }.joinToString("|", "(?:", ")") { it.regex.pattern }

    private val FOR_BRAND = Regex(
        wordStart("""(?:для|for|на|под|compatible with)\s+(?:смартфон\p{L}*\s+|телефон\p{L}*\s+|phone\s+)?""") + ANY_LINE,
    )

    private val PHONE_MARKER = listOf("смартфон", "телефон", "smartphone", "phone", "mobile", "мобильн")
        .joinToString("|", "(?:", ")") { wordStart(it) }.let(::Regex)

    private val DAMAGED = listOf(
        "на запчаст", "разбит", "сломан", "треснут", "не включ", "неисправ", "нерабоч",
        "утоплен", "залит", "под ремонт", "icloud lock", "for parts", "parts only", "broken", "cracked",
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

    // Разбор названия модели.
    private val TOKEN = Regex("""[\p{L}\p{N}]+|\+""")
    private val BASE_WORDS = setOf("pro", "max", "plus", "ultra", "lite", "mini", "neo", "prime", "fe", "edge", "note", "se")
    private val NOT_MODEL = Regex("""^(?:\d+g|lte|nfc|dual|duos|sim|esim|\d+(?:gb|гб|tb|тб|mp|мп|hz|гц|w|вт|mah|мач))$""")
    private val NUMBER_SUFFIX = setOf("r", "t", "s", "c", "e", "a", "x")
    private val MODEL_WORD_FILLERS = Regex("""^(?:смартфон|телефон|smartphone|phone|мобильный|новый|new)$""")

    fun analyze(
        title: String,
        brand: String? = null,
        conditionText: String? = null,
        defaultCondition: Condition = Condition.UNKNOWN,
    ): Result {
        val t = normalize(title)
        val hinted = brandOfHint(brand)
        val identified = identify(t, hinted)
        val conditionSource = normalize(conditionText.orEmpty()) + " " + t
        val kind = when {
            DAMAGED.containsMatchIn(conditionSource) -> Kind.DAMAGED
            isAccessory(t) -> Kind.ACCESSORY
            identified.brand == null -> Kind.UNKNOWN_BRAND
            else -> Kind.PHONE
        }
        return Result(
            kind = kind,
            brand = identified.brand?.name,
            model = identified.model,
            memory = parseMemory(t),
            condition = parseCondition(conditionSource) ?: defaultCondition,
        )
    }

    /** Бренд из поискового запроса: «redmi note 13» → Xiaomi. */
    fun brandOf(text: String): String? = identify(normalize(text), null).brand?.name

    /** Модель из поискового запроса: «oneplus 13r» → «OnePlus 13R». */
    fun modelOf(text: String): String? = identify(normalize(text), null).model

    /** Объём накопителя из поискового запроса: «iphone 15 256» → 256. */
    fun storageOf(text: String): Int? {
        val t = normalize(text)
        parseMemory(t)?.storageGb?.let { return it }
        return Regex("""(?<![\p{N}])(128|256|512)(?![\p{N}])""").find(t)?.groupValues?.get(1)?.toInt()
    }

    private class Identified(val brand: Brand?, val model: String?)

    /**
     * Ищет в тексте упоминания линеек всех брендов; берёт первое, после которого читается модель.
     * Если бренд известен только из подсказки площадки (так делает Wildberries), модель ищется с начала слов.
     */
    private fun identify(t: String, hinted: Brand?): Identified {
        val mentions = Brands.all
            .flatMap { brand -> brand.lines.flatMap { line -> line.regex.findAll(t).map { Triple(brand, line, it) } } }
            .sortedWith(compareBy({ it.third.range.first }, { -it.third.value.length }))
        for ((brand, line, match) in mentions) {
            if (hinted != null && brand !== hinted) continue
            val model = readModel(t.substring(match.range.last + 1), brand, line)
            if (model != null) return Identified(brand, model)
        }
        mentions.firstOrNull { hinted == null || it.first === hinted }?.let { return Identified(it.first, null) }
        if (hinted == null) return Identified(null, null)
        val line = hinted.lines.last()
        for (word in Regex("""[\p{L}\p{N}]+""").findAll(t)) {
            if (MODEL_WORD_FILLERS.matches(word.value)) continue
            readModel(t.substring(word.range.first), hinted, line)?.let { return Identified(hinted, it) }
        }
        return Identified(hinted, null)
    }

    private fun brandOfHint(hint: String?): Brand? {
        if (hint.isNullOrBlank()) return null
        val h = normalize(hint)
        return Brands.all.find { b -> b.name.lowercase() == h || b.lines.any { it.regex.containsMatchIn(h) } }
    }

    /**
     * Читает название модели из текста сразу после названия линейки: токены идут подряд,
     * пока это цифры, «буква+цифры» или слова из словаря бренда. Память, «5G», цвет и прочее
     * обрывают чтение.
     */
    private fun readModel(rest: String, brand: Brand, line: Line): String? {
        val end = memoryPosition(rest) ?: rest.length
        val tokens = TOKEN.findAll(rest.substring(0, end)).map { it.value }.take(8).toList()
        val out = mutableListOf<String>()
        var hasNumber = false
        val allowed = BASE_WORDS + brand.words
        for (tok in tokens) {
            val last = out.lastOrNull()
            when {
                tok == "+" -> if (last != null && !last.endsWith("+")) out[out.lastIndex] = "$last+" else break
                NOT_MODEL.matches(tok) -> break
                tok.all(Char::isDigit) -> {
                    val n = tok.toIntOrNull() ?: break
                    if (hasNumber || tok.length > 3 || n == 0 || n in VALID_STORAGE) break
                    if (last != null && (last.isSingleLetter() || last in brand.mergeWithNumber)) {
                        out[out.lastIndex] = last + tok
                    } else {
                        out += tok
                    }
                    hasNumber = true
                }
                tok.any(Char::isDigit) -> {
                    if (hasNumber && tok.first().isDigit()) break
                    out += tok
                    hasNumber = true
                }
                tok.length == 1 && last != null && last.all(Char::isDigit) && tok in NUMBER_SUFFIX ->
                    out[out.lastIndex] = last + tok
                tok.length == 1 && tok.first().isLetter() && tok.first() in 'a'..'z' -> out += tok
                tok in allowed -> out += tok
                else -> break
            }
        }
        while (out.isNotEmpty() && out.last().isSingleLetter() && out.last() !in brand.standalone) out.removeAt(out.lastIndex)
        if (out.isEmpty()) return null
        if (!hasNumber && out.none { it in brand.standalone }) return null
        return (listOf(line.prefix) + out.map(::formatToken)).joinToString(" ")
    }

    private fun String.isSingleLetter() = length == 1 && first().isLetter()

    /** «s24» → «S24», «13r» → «13R», «fold6» → «Fold6», «se» → «SE», «pro+» → «Pro+». */
    private fun formatToken(tok: String): String {
        val plus = if (tok.endsWith("+")) "+" else ""
        val t = tok.removeSuffix("+")
        val letters = t.takeWhile(Char::isLetter)
        val formatted = when {
            t.first().isDigit() -> t.uppercase()
            letters.length < t.length && letters.length <= 2 -> t.uppercase()
            letters.length < t.length -> letters.replaceFirstChar(Char::uppercase) + t.drop(letters.length).uppercase()
            t.length <= 2 || t in ACRONYMS -> t.uppercase()
            else -> t.replaceFirstChar(Char::uppercase)
        }
        return formatted + plus
    }

    private val ACRONYMS = setOf("cmf", "rog", "iii", "iqoo")

    private fun isAccessory(t: String): Boolean {
        val accessoryAt = listOfNotNull(ACCESSORY.find(t)?.range?.first, FOR_BRAND.find(t)?.range?.first)
            .minOrNull() ?: return false
        val phoneAt = listOfNotNull(PHONE_MARKER.find(t)?.range?.first, memoryPosition(t)).minOrNull()
        // «Смартфон iPhone 15 128 ГБ + чехол» — телефон, «Чехол для iPhone 15» — нет.
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
}
