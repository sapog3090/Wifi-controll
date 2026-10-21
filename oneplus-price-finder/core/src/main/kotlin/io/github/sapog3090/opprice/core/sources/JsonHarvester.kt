package io.github.sapog3090.opprice.core.sources

import io.github.sapog3090.opprice.core.Money
import io.github.sapog3090.opprice.core.text.MoneyParser
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/**
 * Достаёт товары из JSON, встроенного в HTML, не зная его схемы.
 *
 * Товаром считается любой объект, у которого есть числовой id, осмысленное название и цена.
 * Это переживает переименование обёрток и перестановку полей — то, что у маркетплейсов
 * происходит чаще всего.
 */
object JsonHarvester {

    data class Product(val id: String, val title: String, val price: Money)

    private val ID_KEYS = listOf("productId", "product_id", "itemId", "item_id", "id")
    private val TITLE_KEYS = listOf("title", "name", "productTitle", "subject", "displayTitle")
    private val PRICE_KEYS = listOf(
        "finalPrice", "salePrice", "activityPrice", "sellingPrice", "minPrice", "price", "prices",
        "priceInfo", "formattedPrice", "minAmount", "amount", "value",
    )
    private val CURRENCY_KEYS = listOf("currency", "currencyCode")
    private const val MAX_DEPTH = 64

    fun products(html: String, defaultCurrency: String): List<Product> {
        val found = LinkedHashMap<String, Product>()
        for (blob in jsonBlobs(html)) {
            val root = parseJsonOrNull(blob) ?: continue
            walk(root, found, defaultCurrency, 0)
        }
        return found.values.toList()
    }

    /** Все сбалансированные `{"…}` в тексте, без вложенных повторов. */
    internal fun jsonBlobs(text: String): Sequence<String> = sequence {
        var from = 0
        while (true) {
            val start = text.indexOf("{\"", from)
            if (start < 0) break
            val end = matchingBrace(text, start)
            if (end < 0) break
            yield(text.substring(start, end + 1))
            from = end + 1
        }
    }

    private fun matchingBrace(text: String, start: Int): Int {
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until text.length) {
            val c = text[i]
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
                continue
            }
            when (c) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> if (--depth == 0) return i
            }
        }
        return -1
    }

    private fun walk(el: JsonElement, out: MutableMap<String, Product>, currency: String, depth: Int) {
        if (depth > MAX_DEPTH) return
        when (el) {
            is JsonObject -> {
                val product = toProduct(el, currency)
                if (product != null) {
                    out.putIfAbsent(product.id, product)
                    return
                }
                el.values.forEach { walk(it, out, currency, depth + 1) }
            }
            is JsonArray -> el.forEach { walk(it, out, currency, depth + 1) }
            else -> Unit
        }
    }

    private fun toProduct(obj: JsonObject, defaultCurrency: String): Product? {
        val id = ID_KEYS.firstNotNullOfOrNull { key ->
            (obj[key] as? JsonPrimitive)?.content?.takeIf { it.length in 9..20 && it.all(Char::isDigit) }
        } ?: return null
        val title = TITLE_KEYS.firstNotNullOfOrNull { textOf(obj[it]) }
            ?.takeIf { t -> t.length >= 8 && t.any(Char::isLetter) } ?: return null
        val price = priceOf(obj, defaultCurrency, 0) ?: return null
        return Product(id, title, price)
    }

    private fun textOf(el: JsonElement?): String? = when (el) {
        is JsonPrimitive -> el.takeIf { it.isString }?.content?.trim()
        is JsonObject -> listOf("displayTitle", "text", "title", "value").firstNotNullOfOrNull { textOf(el[it]) }
        else -> null
    }

    private fun priceOf(obj: JsonObject, currency: String, depth: Int): Money? {
        if (depth > 3) return null
        val localCurrency = CURRENCY_KEYS.firstNotNullOfOrNull { obj.str(it) }?.uppercase() ?: currency
        for (key in PRICE_KEYS) {
            val money = when (val v = obj[key]) {
                is JsonPrimitive ->
                    if (v.isString) MoneyParser.parse(v.content, localCurrency)
                    else v.doubleOrNull?.let { Money(it, localCurrency) }
                is JsonObject -> priceOf(v, localCurrency, depth + 1)
                is JsonArray -> v.firstNotNullOfOrNull { (it as? JsonObject)?.let { o -> priceOf(o, localCurrency, depth + 1) } }
                else -> null
            }
            if (money != null && money.amount > 0) return money
        }
        return null
    }
}
