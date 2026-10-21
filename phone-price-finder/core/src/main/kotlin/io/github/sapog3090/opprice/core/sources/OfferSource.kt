package io.github.sapog3090.opprice.core.sources

import io.github.sapog3090.opprice.core.Condition
import io.github.sapog3090.opprice.core.Listing
import io.github.sapog3090.opprice.core.Source
import java.net.URLEncoder

interface OfferSource {
    val source: Source

    /** Как площадка описывает свои товары по умолчанию, если в заголовке ничего не сказано. */
    val defaultCondition: Condition

    /** Ссылка на ту же выдачу в браузере — на случай капчи. */
    fun browserUrl(query: String): String

    suspend fun search(query: String): List<Listing>
}

internal fun enc(s: String): String = URLEncoder.encode(s, Charsets.UTF_8)

/**
 * Скачивает до [pages] страниц и склеивает их без дублей.
 * Если сломалась не первая страница, возвращаем уже собранное, а не ошибку.
 */
internal suspend fun collectPages(pages: Int, fetchPage: suspend (Int) -> List<Listing>): List<Listing> {
    val out = LinkedHashMap<String, Listing>()
    for (page in 1..pages) {
        val items = try {
            fetchPage(page)
        } catch (e: Exception) {
            if (page == 1 || e is kotlinx.coroutines.CancellationException) throw e
            break
        }
        val before = out.size
        items.forEach { out.putIfAbsent(it.id, it) }
        if (out.size == before) break
    }
    return out.values.toList()
}
