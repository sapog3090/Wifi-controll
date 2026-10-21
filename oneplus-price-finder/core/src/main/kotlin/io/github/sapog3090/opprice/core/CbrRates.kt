package io.github.sapog3090.opprice.core

import io.github.sapog3090.opprice.core.sources.double
import io.github.sapog3090.opprice.core.sources.obj
import io.github.sapog3090.opprice.core.sources.parseJsonOrNull
import kotlinx.serialization.json.JsonObject

/** Курсы ЦБ РФ с cbr-xml-daily.ru: рублей за единицу валюты. */
object CbrRates {
    const val URL = "https://www.cbr-xml-daily.ru/daily_json.js"

    fun parse(body: String): Map<String, Double> {
        val valute = (parseJsonOrNull(body) as? JsonObject)?.obj("Valute") ?: return emptyMap()
        return valute.mapNotNull { (code, v) ->
            val o = v as? JsonObject ?: return@mapNotNull null
            val value = o.double("Value") ?: return@mapNotNull null
            val nominal = o.double("Nominal") ?: 1.0
            code to value / nominal
        }.toMap()
    }
}
