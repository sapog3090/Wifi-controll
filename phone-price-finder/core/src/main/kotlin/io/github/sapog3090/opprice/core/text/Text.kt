package io.github.sapog3090.opprice.core.text

private val SPACES = Regex("""[\s  ]+""")
/** Нижний регистр, «ё» → «е», русские написания брендов («айфон», «сяоми») — латиницей. */
internal fun normalize(text: String): String =
    Brands.SPELLINGS.fold(text.lowercase().replace('ё', 'е').replace(SPACES, " ")) { acc, (regex, latin) ->
        acc.replace(regex, latin)
    }.trim()

/** Регулярка, которая находит [stem] только в начале слова. */
internal fun wordStart(stem: String): String = """(?<![\p{L}\p{N}])$stem"""

/** Регулярка для целого слова. */
internal fun wholeWord(word: String): String = """(?<![\p{L}\p{N}])$word(?![\p{L}\p{N}])"""
