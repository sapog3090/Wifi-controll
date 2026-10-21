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
    }

    @Test fun `damaged and foreign phones`() {
        assertEquals(Kind.DAMAGED, kind("OnePlus 11 на запчасти"))
        assertEquals(
            Kind.DAMAGED,
            TitleAnalyzer.analyze("OnePlus 10 Pro 12/256", conditionText = "For parts or not working").kind,
        )
        assertEquals(Kind.NOT_ONEPLUS, kind("Xiaomi 14 12/512"))
        assertEquals(Kind.PHONE, kind("Смартфон 13R 12/256 ГБ", brand = "OnePlus"))
    }

    @Test fun `models`() {
        assertEquals("13", model("OnePlus 13 12/256"))
        assertEquals("13R", model("oneplus 13r 16/512 Astral Trail"))
        assertEquals("12R", model("Смартфон One Plus 12 R 16+256"))
        assertEquals("Nord CE4 Lite", model("OnePlus Nord CE 4 Lite 8/256"))
        assertEquals("Nord CE4", model("OnePlus Nord CE4 8/128"))
        assertEquals("Nord 4", model("Ванплас Nord 4 12/256"))
        assertEquals("Nord N30", model("OnePlus Nord N30 5G 128GB"))
        assertEquals("Open", model("OnePlus Open 16/1TB Emerald Dusk"))
        assertEquals("Ace 3 Pro", model("OnePlus Ace 3 Pro 24/1TB"))
        assertEquals("10 Pro", model("OnePlus 10 Pro 5G"))
        assertEquals("13", model("Смартфон 13 12/256 ГБ", brand = "OnePlus"))
        assertEquals(null, model("OnePlus 12/256"))
        assertEquals("15", TitleAnalyzer.modelOf("oneplus 15"))
        assertEquals(null, TitleAnalyzer.modelOf("oneplus"))
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
