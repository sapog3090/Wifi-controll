package io.github.sapog3090.opprice.core

import io.github.sapog3090.opprice.core.text.TitleAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Test

class TitleAnalyzerTest {

    private fun kind(title: String, brand: String? = null) = TitleAnalyzer.analyze(title, brand).kind
    private fun model(title: String, brand: String? = null) = TitleAnalyzer.analyze(title, brand).model

    @Test fun `phone with gift case is still a phone`() {
        assertEquals(Kind.PHONE, kind("Смартфон OnePlus 13 12/256 ГБ + чехол в подарок"))
        assertEquals(Kind.PHONE, kind("OnePlus 12 16/512 Black, коробка, чек"))
    }

    @Test fun `accessories are recognised`() {
        assertEquals(Kind.ACCESSORY, kind("Чехол для OnePlus 13 силиконовый"))
        assertEquals(Kind.ACCESSORY, kind("Защитное стекло OnePlus 12R"))
        assertEquals(Kind.ACCESSORY, kind("Tempered Glass Screen Protector for OnePlus 12"))
        assertEquals(Kind.ACCESSORY, kind("Дисплей для OnePlus Nord CE 3 Lite с тачскрином"))
        assertEquals(Kind.ACCESSORY, kind("OnePlus Buds Pro 3"))
        assertEquals(Kind.ACCESSORY, kind("OnePlus Pad 2 12/256"))
        assertEquals(Kind.ACCESSORY, kind("Apple AirPods Pro 2"))
    }

    @Test fun `damaged and unknown phones`() {
        assertEquals(Kind.DAMAGED, kind("OnePlus 11 на запчасти"))
        assertEquals(
            Kind.DAMAGED,
            TitleAnalyzer.analyze("OnePlus 10 Pro 12/256", conditionText = "For parts or not working").kind,
        )
        assertEquals(Kind.UNKNOWN_BRAND, kind("Смартфон Doogee S100 12/256"))
        assertEquals(Kind.PHONE, kind("Смартфон 13R 12/256 ГБ", brand = "OnePlus"))
    }

    @Test fun `oneplus models`() {
        assertEquals("OnePlus 13", model("OnePlus 13 12/256"))
        assertEquals("OnePlus 13R", model("oneplus 13r 16/512 Astral Trail"))
        assertEquals("OnePlus 12R", model("Смартфон One Plus 12 R 16+256"))
        assertEquals("OnePlus Nord CE4 Lite", model("OnePlus Nord CE 4 Lite 8/256"))
        assertEquals("OnePlus Nord CE4", model("OnePlus Nord CE4 8/128"))
        assertEquals("OnePlus Nord 4", model("Ванплас Nord 4 12/256"))
        assertEquals("OnePlus Nord N30", model("OnePlus Nord N30 5G 128GB"))
        assertEquals("OnePlus Open", model("OnePlus Open 16/1TB Emerald Dusk"))
        assertEquals("OnePlus Ace 3 Pro", model("OnePlus Ace 3 Pro 24/1TB"))
        assertEquals("OnePlus 10 Pro", model("OnePlus 10 Pro 5G"))
        assertEquals("OnePlus 13", model("Смартфон 13 12/256 ГБ", brand = "OnePlus"))
        assertEquals(null, model("OnePlus 12/256"))
        assertEquals("OnePlus 15", TitleAnalyzer.modelOf("oneplus 15"))
        assertEquals(null, TitleAnalyzer.modelOf("oneplus"))
    }

    @Test fun `other brands`() {
        assertEquals("iPhone 15 Pro Max", model("Apple iPhone 15 Pro Max 256GB Natural Titanium"))
        assertEquals("iPhone 13", model("Айфон 13 128 отличное состояние"))
        assertEquals("iPhone SE", model("iPhone SE 2022 64gb"))
        assertEquals("iPhone X", model("iPhone X 64gb"))
        assertEquals("Galaxy S24 Ultra", model("Samsung Galaxy S24 Ultra 12/256"))
        assertEquals("Galaxy S24 Ultra", model("Самсунг S24 Ultra 12/256"))
        assertEquals("Galaxy A55", model("Samsung Galaxy A 55 8/256"))
        assertEquals("Galaxy Z Fold6", model("Samsung Galaxy Z Fold 6 12/512"))
        assertEquals("Redmi Note 13 Pro+", model("Xiaomi Redmi Note 13 Pro+ 5G 12/512"))
        assertEquals("Poco X6 Pro", model("POCO X6 Pro 12/512"))
        assertEquals("Xiaomi 14T Pro", model("Сяоми 14T Pro 12/512"))
        assertEquals("Pixel 8A", model("Google Pixel 8a 128GB"))
        assertEquals("Honor Magic6 Pro", model("Honor Magic 6 Pro 12/512"))
        assertEquals("Honor 200", model("Хонор 200 8/256"))
        assertEquals("Nothing Phone 2A", model("Nothing Phone (2a) 12/256"))
        assertEquals("Moto G84", model("Motorola Moto G84 12/256"))
        assertEquals("Galaxy S24", model("Смартфон S24 8/256", brand = "Samsung"))

        assertEquals("Apple", TitleAnalyzer.brandOf("айфон 15"))
        assertEquals("Xiaomi", TitleAnalyzer.brandOf("redmi note 13"))
        assertEquals(null, TitleAnalyzer.brandOf("смартфон"))
        assertEquals(Kind.ACCESSORY, kind("Чехол для iPhone 15 Pro MagSafe"))
        assertEquals(Kind.ACCESSORY, kind("Samsung Galaxy Watch 6"))
        assertEquals(Kind.ACCESSORY, kind("Xiaomi Redmi Pad SE 8/256"))
    }

    @Test fun `memory`() {
        fun mem(t: String) = TitleAnalyzer.analyze(t).memory
        assertEquals(Memory(12, 256), mem("OnePlus 13 12/256"))
        assertEquals(Memory(16, 512), mem("OnePlus 12 16 ГБ/512 ГБ"))
        assertEquals(Memory(16, 1024), mem("OnePlus Open 16/1TB"))
        assertEquals(Memory(12, 256), mem("OnePlus 12R 12+256gb"))
        assertEquals(Memory(null, 256), mem("OnePlus 12 256GB Unlocked"))
        assertEquals(Memory(null, 1024), mem("OnePlus 12 1 ТБ"))
        assertEquals(null, mem("OnePlus 12 5G"))
        assertEquals(512, TitleAnalyzer.storageOf("oneplus 12 512"))
    }

    @Test fun `condition`() {
        fun cond(t: String, c: String? = null, d: Condition = Condition.UNKNOWN) =
            TitleAnalyzer.analyze(t, conditionText = c, defaultCondition = d).condition
        assertEquals(Condition.USED, cond("OnePlus 12 как новый"))
        assertEquals(Condition.NEW, cond("OnePlus 12 новый запечатан"))
        assertEquals(Condition.USED, cond("OnePlus 12 б/у"))
        assertEquals(Condition.REFURBISHED, cond("OnePlus 11 восстановленный"))
        assertEquals(Condition.USED, cond("OnePlus 12", c = "Pre-Owned"))
        assertEquals(Condition.NEW, cond("OnePlus 12", c = "Brand New"))
        assertEquals(Condition.USED, cond("OnePlus 12", d = Condition.USED))
    }
}
