package io.github.sapog3090.opprice.core

data class FetchResponse(val code: Int, val body: String, val url: String)

/** HTTP-клиент. В приложении это OkHttp, в тестах — заглушка. */
fun interface Fetcher {
    suspend fun get(url: String, headers: Map<String, String>): FetchResponse
}

enum class FailureKind {
    /** Площадка показала капчу или ограничила запросы. */
    BLOCKED,
    HTTP,
    NETWORK,
    /** Ответ пришёл, но разобрать его не вышло — скорее всего, поменялась разметка. */
    PARSE,
    OTHER,
}

class SourceException(val kind: FailureKind, message: String) : Exception(message)

fun interface RateProvider {
    /** Сколько рублей стоит единица валюты, или null, если курс неизвестен. */
    suspend fun rubPer(currency: String): Double?
}
