package io.github.sapog3090.opprice.core.text

/**
 * Линейка внутри бренда: Xiaomi делится на Xiaomi/Redmi/Poco, Motorola — на Motorola/Moto.
 * [aliases] — как линейку пишут в нормализованном заголовке, длинные варианты первыми.
 */
internal class Line(val prefix: String, aliases: List<String>) {
    val regex = Regex(aliases.joinToString("|", "(?:", ")") { wordStart(it) } + """(?![\p{L}])""")
}

/**
 * Бренд и слова, из которых складываются названия его моделей.
 *
 * [words] — слова, которые могут входить в название модели («ultra», «fold», «note»).
 * [mergeWithNumber] — слова, которые пишут то слитно, то раздельно с цифрой: «fold 6» и «fold6»
 *   должны давать одну модель.
 * [standalone] — модели без цифр: OnePlus Open, iPhone SE.
 */
class Brand internal constructor(
    val name: String,
    internal val lines: List<Line>,
    internal val words: Set<String> = emptySet(),
    internal val mergeWithNumber: Set<String> = emptySet(),
    internal val standalone: Set<String> = emptySet(),
)

object Brands {
    val all: List<Brand> = listOf(
        Brand(
            "OnePlus", listOf(Line("OnePlus", listOf("oneplus"))),
            words = setOf("nord", "ce", "ace", "open", "racing"),
            mergeWithNumber = setOf("ce"),
            standalone = setOf("open", "nord"),
        ),
        Brand(
            "Apple", listOf(Line("iPhone", listOf("apple iphone", "iphone"))),
            words = setOf("se", "xr", "xs", "x", "e"),
            standalone = setOf("se", "xr", "xs", "x"),
        ),
        Brand(
            "Samsung", listOf(Line("Galaxy", listOf("samsung galaxy", "samsung", "galaxy"))),
            words = setOf("z", "fold", "flip", "note", "fe", "edge", "s", "a", "m", "f"),
            mergeWithNumber = setOf("fold", "flip"),
        ),
        Brand(
            "Xiaomi",
            listOf(
                Line("Redmi", listOf("xiaomi redmi", "redmi")),
                Line("Poco", listOf("xiaomi poco", "poco")),
                Line("Xiaomi", listOf("xiaomi", "mi")),
            ),
            words = setOf("note", "turbo", "civi", "mix", "flip", "fold"),
            mergeWithNumber = setOf("fold", "flip"),
        ),
        Brand("Google", listOf(Line("Pixel", listOf("google pixel", "pixel"))), words = setOf("fold", "xl")),
        Brand(
            "Honor", listOf(Line("Honor", listOf("honor"))),
            words = setOf("magic", "x", "v", "flip", "play"),
            mergeWithNumber = setOf("magic"),
        ),
        Brand(
            "Huawei", listOf(Line("Huawei", listOf("huawei"))),
            words = setOf("mate", "nova", "pura", "p", "y", "x", "rs", "porsche", "design"),
        ),
        Brand(
            "Realme", listOf(Line("Realme", listOf("realme"))),
            words = setOf("gt", "narzo", "c", "neo", "master"),
            mergeWithNumber = setOf("gt"),
        ),
        Brand("Nothing", listOf(Line("Nothing Phone", listOf("nothing phone", "nothing"))), words = setOf("cmf", "phone")),
        Brand(
            "Motorola",
            listOf(
                Line("Moto", listOf("motorola moto", "moto")),
                Line("Motorola", listOf("motorola")),
            ),
            words = setOf("edge", "razr", "g", "e", "fusion", "power", "stylus", "neo"),
        ),
        Brand("Oppo", listOf(Line("Oppo", listOf("oppo"))), words = setOf("reno", "find", "a", "x", "n")),
        Brand("Vivo", listOf(Line("Vivo", listOf("vivo"))), words = setOf("v", "x", "y", "t", "iqoo")),
        Brand("Tecno", listOf(Line("Tecno", listOf("tecno"))), words = setOf("camon", "spark", "pova", "phantom", "go")),
        Brand("Infinix", listOf(Line("Infinix", listOf("infinix"))), words = setOf("hot", "note", "zero", "smart", "gt")),
        Brand("Sony", listOf(Line("Xperia", listOf("sony xperia", "xperia"))), words = setOf("v", "iv", "vi", "iii")),
        Brand("Asus", listOf(Line("Asus", listOf("asus"))), words = setOf("zenfone", "rog", "phone")),
    )

    /** Русские и слитные написания, которые приводятся к латинице до разбора. */
    internal val SPELLINGS: List<Pair<Regex, String>> = listOf(
        """one\s*plus|ван\s*пл[аю]с|уан\s*пл[аю]с""" to "oneplus",
        """айфон""" to "iphone",
        """самсунг""" to "samsung",
        """сяоми|ксиаоми|шаоми""" to "xiaomi",
        """редми""" to "redmi",
        """поко(?=\s*[xfmc]\d)""" to "poco",
        """пиксель""" to "pixel",
        """хонор""" to "honor",
        """хуавей|хуавэй""" to "huawei",
        """реалми""" to "realme",
        """моторола""" to "motorola",
        """техно(?=\s)""" to "tecno",
    ).map { (pattern, replacement) -> Regex(pattern) to replacement }

    fun byName(name: String?): Brand? = all.find { it.name.equals(name, ignoreCase = true) }
}
