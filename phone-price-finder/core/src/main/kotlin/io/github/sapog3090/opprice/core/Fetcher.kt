package io.github.sapog3090.opprice.core

data class FetchResponse(val code: Int, val body: String, val url: String)

/**
 * Загрузка страниц площадок. В приложении — встроенный браузер (WebView): площадки пускают
 * только клиентов, которые выполняют их JavaScript-проверки и хранят куки.
 */
interface Fetcher {
    /**
     * Открывает страницу и возвращает её HTML после отработки скриптов.
     * [readySelector] — CSS-селектор, появление которого значит «выдача загрузилась».
     */
    suspend fun page(url: String, readySelector: String? = null): FetchResponse

    /**
     * Выполняет запрос к API так, как его делает сам сайт: `fetch()` со страницы [origin],
     * с её куками. Код ответа −1 — запрос не дошёл (сеть или CORS), в body — причина.
     */
    suspend fun api(url: String, origin: String): FetchResponse
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
