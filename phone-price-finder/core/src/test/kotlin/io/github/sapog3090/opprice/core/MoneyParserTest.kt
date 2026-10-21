package io.github.sapog3090.opprice.core

import io.github.sapog3090.opprice.core.text.MoneyParser
import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyParserTest {
    @Test fun `formats`() {
        assertEquals(Money(12990.0, "RUB"), MoneyParser.parse("12 990 ₽"))
        assertEquals(Money(12990.0, "RUB"), MoneyParser.parse("12 990 руб."))
        assertEquals(Money(1234.56, "USD"), MoneyParser.parse("$1,234.56"))
        assertEquals(Money(412.5, "USD"), MoneyParser.parse("US $412.50"))
        assertEquals(Money(100.0, "USD"), MoneyParser.parse("$100.00 to $150.00"))
        assertEquals(Money(9876.0, "RUB"), MoneyParser.parse("RUB 9,876.00"))
        assertEquals(Money(12.5, "EUR"), MoneyParser.parse("EUR 12,50"))
        assertEquals(Money(45990.0, "RUB"), MoneyParser.parse("от 45 990"))
        assertEquals(null, MoneyParser.parse("Цена не указана"))
    }
}
