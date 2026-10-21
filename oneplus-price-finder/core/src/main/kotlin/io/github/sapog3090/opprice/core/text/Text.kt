package io.github.sapog3090.opprice.core.text

private val SPACES = Regex("""[\s  ]+""")
private val BRAND_ALIASES = Regex("""one\s*plus|ван\s*пл[аю]с|уан\s*пл[аю]с""")

/** Нижний регистр, «ё» → «е», все написания бренда сводятся к «oneplus». */
internal fun normalize(text: String): String =
    text.lowercase()
        .replace('ё', 'е')
        .replace(SPACES, " ")
        .replace(BRAND_ALIASES, "oneplus")
        .trim()

/** Регулярка, которая находит [stem] только в начале слова. */
internal fun wordStart(stem: String): String = """(?<![\p{L}\p{N}])$stem"""

/** Регулярка для целого слова. */
internal fun wholeWord(word: String): String = """(?<![\p{L}\p{N}])$word(?![\p{L}\p{N}])"""
