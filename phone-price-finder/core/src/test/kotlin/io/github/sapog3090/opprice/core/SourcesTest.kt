package io.github.sapog3090.opprice.core

import io.github.sapog3090.opprice.core.sources.AliExpressSource
import io.github.sapog3090.opprice.core.sources.AvitoSource
import io.github.sapog3090.opprice.core.sources.EbaySource
import io.github.sapog3090.opprice.core.sources.WildberriesSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Фикстуры повторяют структуру настоящих ответов площадок в сокращённом виде.
 * Если площадка поменяет разметку, сюда стоит положить свежий кусок её HTML.
 */
class SourcesTest {

    @Test fun `wildberries new and old json`() {
        val new = """{"metadata":{},"products":[
            {"id":123456789,"name":"Смартфон 13 12/256 ГБ","brand":"OnePlus","supplier":"ООО Ромашка",
             "totalQuantity":5,"sizes":[{"price":{"basic":7999000,"product":6499000}},{"price":{"product":6599000}}]},
            {"id":987,"name":"Нет в наличии","brand":"OnePlus","totalQuantity":0,"sizes":[{"price":{"product":100}}]}
        ]}"""
        val items = WildberriesSource.parse(new)
        assertEquals(1, items.size)
        assertEquals(64990.0, items[0].price.amount, 0.001)
        assertEquals("OnePlus", items[0].brand)
        assertEquals("https://www.wildberries.ru/catalog/123456789/detail.aspx", items[0].url)

        val old = """{"data":{"products":[{"id":42,"name":"OnePlus 12R","salePriceU":4599000}]}}"""
        assertEquals(45990.0, WildberriesSource.parse(old).single().price.amount, 0.001)
    }

    @Test fun `avito cards`() {
        val html = """<html><body>
            <div data-marker="item" data-item-id="4012345678" itemscope>
              <a data-marker="item-title" href="/moskva/telefony/oneplus_12_16512_4012345678?context=x" title="OnePlus 12, 16/512 ГБ">
                <h3 itemprop="name">OnePlus 12, 16/512 ГБ</h3></a>
              <meta itemprop="price" content="48000">
              <div data-marker="item-address"><span>Москва</span></div>
            </div>
            <div data-marker="item" data-item-id="4012345679">
              <a data-marker="item-title" href="/spb/telefony/x_4012345679" title="OnePlus 11 8/128"></a>
              <span data-marker="item-price">31 500 ₽</span>
            </div>
        </body></html>"""
        val items = AvitoSource.parse(html)
        assertEquals(2, items.size)
        assertEquals("https://www.avito.ru/moskva/telefony/oneplus_12_16512_4012345678", items[0].url)
        assertEquals(48000.0, items[0].price.amount, 0.001)
        assertEquals("Москва", items[0].location)
        assertEquals("OnePlus 11 8/128", items[1].title)
        assertEquals(31500.0, items[1].price.amount, 0.001)
    }

    @Test fun `avito captcha is reported as blocked`() {
        val e = assertThrows(SourceException::class.java) {
            AvitoSource.parse("<html><head><title>Доступ ограничен: проблема с IP</title></head><body></body></html>")
        }
        assertEquals(FailureKind.BLOCKED, e.kind)
    }

    @Test fun `ebay both layouts`() {
        val html = """<ul>
            <li class="s-item"><div class="s-item__title">Shop on eBay</div><span class="s-item__price">$20.00</span>
              <a class="s-item__link" href="https://www.ebay.com/itm/123456"></a></li>
            <li class="s-item"><a class="s-item__link" href="https://www.ebay.com/itm/266512345678?hash=x">
              <div class="s-item__title"><span>New Listing</span>OnePlus 12 256GB Unlocked</div></a>
              <span class="SECONDARY_INFO">Pre-Owned</span><span class="s-item__price">$399.99</span>
              <span class="s-item__location">from China</span></li>
            <li class="s-card"><a class="su-link" href="https://www.ebay.com/itm/OnePlus-13/356612345678"></a>
              <div class="s-card__title"><span>OnePlus 13 16/512 Black Opens in a new window or tab</span></div>
              <div class="s-card__subtitle">Brand New</div><span class="s-card__price">$699.00 to $749.00</span></li>
        </ul>"""
        val items = EbaySource.parse(html)
        assertEquals(listOf("266512345678", "356612345678"), items.map { it.id })
        assertEquals("OnePlus 12 256GB Unlocked", items[0].title)
        assertEquals(Money(399.99, "USD"), items[0].price)
        assertEquals("China", items[0].location)
        assertEquals("OnePlus 13 16/512 Black", items[1].title)
        assertEquals(Money(699.0, "USD"), items[1].price)
        assertEquals("Brand New", items[1].conditionText)
    }

    @Test fun `aliexpress embedded json`() {
        val html = """<html><script>window.__INIT_DATA__ = {"widgets":[{"props":{"products":[
            {"productId":"1005006123456789","title":{"displayTitle":"Смартфон OnePlus 12 Global 16/512"},
             "prices":{"originalPrice":{"value":79990},"salePrice":{"formattedPrice":"54 321,50 ₽"}}},
            {"id":"1005006999999999","name":"Чехол для OnePlus 12","price":{"minAmount":{"value":199.0,"currency":"RUB"}}},
            {"productId":"12","title":"too short id","price":1}
        ]}}]};</script><script>var x = {"broken": </script></html>"""
        val items = AliExpressSource.parse(html)
        assertEquals(listOf("1005006123456789", "1005006999999999"), items.map { it.id })
        assertEquals(Money(54321.5, "RUB"), items[0].price)
        assertEquals("Смартфон OnePlus 12 Global 16/512", items[0].title)
        assertEquals(199.0, items[1].price.amount, 0.001)
    }

    @Test fun `cbr rates`() {
        val rates = CbrRates.parse("""{"Valute":{"USD":{"Nominal":1,"Value":81.25},"CNY":{"Nominal":10,"Value":112.0}}}""")
        assertEquals(81.25, rates.getValue("USD"), 1e-9)
        assertEquals(11.2, rates.getValue("CNY"), 1e-9)
    }
}
