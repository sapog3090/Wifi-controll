package io.github.sapog3090.opprice.net

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import io.github.sapog3090.opprice.core.FetchResponse
import io.github.sapog3090.opprice.core.Fetcher
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.json.JSONTokener
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

/** Общие настройки WebView: и для невидимой загрузки, и для экрана «пройти проверку». */
object Browser {
    @SuppressLint("SetJavaScriptEnabled")
    fun configure(web: WebView, headless: Boolean) {
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            blockNetworkImage = headless
            mediaPlaybackRequiresUserGesture = true
            // Маркер «; wv» в User-Agent выдаёт встроенный браузер — некоторые площадки по нему режут доступ.
            userAgentString = WebSettings.getDefaultUserAgent(web.context)
                .replace("; wv", "")
                .replace(Regex("""Version/\d+(\.\d+)* """), "")
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(web, true)
        }
    }

    /** Ссылки вида intent:// и market:// (приглашения «откройте в приложении») не открываем. */
    fun isWebUrl(url: String) = url.startsWith("http://") || url.startsWith("https://")
}

/**
 * [Fetcher] на невидимых WebView. Медленнее, чем голый HTTP, зато площадки видят обычный Chrome:
 * их JS-проверки выполняются, куки (в том числе после ручного прохождения капчи) сохраняются.
 */
class BrowserFetcher(context: Context) : Fetcher {

    private val app = context.applicationContext
    private val slots = Semaphore(PARALLEL_TABS)
    private val originLock = Mutex()
    private val origins = mutableMapOf<String, OriginTab>()
    private val bridge = Bridge()

    override suspend fun page(url: String, readySelector: String?): FetchResponse = slots.withPermit {
        withContext(Dispatchers.Main) {
            val tab = Tab(app, bridge = null)
            try {
                tab.web.loadUrl(url)
                withTimeoutOrNull(PAGE_TIMEOUT) { tab.awaitReady(readySelector) }
                tab.snapshot(url)
            } finally {
                CookieManager.getInstance().flush()
                tab.destroy()
            }
        }
    }

    override suspend fun api(url: String, origin: String): FetchResponse = slots.withPermit {
        withContext(Dispatchers.Main) {
            val tab = originTab(origin)
            val id = bridge.nextId.incrementAndGet()
            val result = CompletableDeferred<FetchResponse>()
            bridge.pending[id] = result
            try {
                tab.web.evaluateJavascript(fetchScript(id, url), null)
                withTimeoutOrNull(API_TIMEOUT) { result.await() } ?: FetchResponse(-1, "таймаут", url)
            } finally {
                bridge.pending.remove(id)
            }
        }
    }

    /** Вкладка с главной страницей сайта, со страницы которой делаются API-запросы. Живёт 15 минут. */
    private suspend fun originTab(origin: String): Tab = originLock.withLock {
        val now = System.currentTimeMillis()
        origins[origin]?.let { if (now - it.openedAt < ORIGIN_TTL) return it.tab else it.tab.destroy() }
        val tab = Tab(app, bridge)
        tab.web.loadUrl(origin)
        withTimeoutOrNull(PAGE_TIMEOUT) { tab.awaitReady(null) }
        // Антибот-заглушки часто перезагружают страницу сами через пару секунд после проверки.
        delay(1_500)
        origins[origin] = OriginTab(tab, now)
        tab
    }

    private class OriginTab(val tab: Tab, val openedAt: Long)

    private fun fetchScript(id: Int, url: String): String {
        val u = JSONObject.quote(url)
        return """
            (function() {
              function send(r) { return r.text().then(function(t) { OpBridge.done($id, r.status, t); }); }
              fetch($u, {credentials: 'include'}).then(send)
                .catch(function() { return fetch($u).then(send); })
                .catch(function(e) { OpBridge.fail($id, String(e)); });
            })();
        """.trimIndent()
    }

    // Не private: WebView вызывает методы моста через рефлексию.
    class Bridge {
        val nextId = AtomicInteger()
        val pending = ConcurrentHashMap<Int, CompletableDeferred<FetchResponse>>()

        @JavascriptInterface
        fun done(id: Int, status: Int, body: String) {
            pending[id]?.complete(FetchResponse(status, body, ""))
        }

        @JavascriptInterface
        fun fail(id: Int, message: String) {
            pending[id]?.complete(FetchResponse(-1, message, ""))
        }
    }

    /** Одна невидимая вкладка. Все методы — только с главного потока. */
    private class Tab(context: Context, bridge: Bridge?) {
        val web = WebView(context)
        private var status = 200
        private var progress = 0
        private var finishedOnce = false

        init {
            Browser.configure(web, headless = true)
            if (bridge != null) web.addJavascriptInterface(bridge, "OpBridge")
            web.webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    progress = newProgress
                }
            }
            web.webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                    status = 200
                }

                override fun onPageFinished(view: WebView, url: String) {
                    finishedOnce = true
                }

                override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                    if (request.isForMainFrame) status = response.statusCode
                }

                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) =
                    !Browser.isWebUrl(request.url.toString())
            }
        }

        /**
         * Ждёт, пока появится [selector] или пока страница перестанет меняться:
         * после загрузки площадки ещё дорисовывают выдачу скриптами.
         */
        suspend fun awaitReady(selector: String?) {
            val started = System.currentTimeMillis()
            var lastLength = -1
            var stableTicks = 0
            while (true) {
                delay(POLL_INTERVAL)
                if (!finishedOnce) continue
                if (selector != null && js("!!document.querySelector(${JSONObject.quote(selector)})") == "true") return
                val length = js("document.documentElement.outerHTML.length").toIntOrNull() ?: 0
                stableTicks = if (length == lastLength && progress >= 100) stableTicks + 1 else 0
                lastLength = length
                if (stableTicks >= STABLE_TICKS && System.currentTimeMillis() - started > MIN_WAIT) return
            }
        }

        suspend fun snapshot(url: String): FetchResponse {
            val raw = js("document.documentElement.outerHTML")
            val html = runCatching { JSONTokener(raw).nextValue() as? String }.getOrNull().orEmpty()
            return FetchResponse(status, html, web.url ?: url)
        }

        private suspend fun js(code: String): String = suspendCancellableCoroutine { cont ->
            web.evaluateJavascript(code) { if (cont.isActive) cont.resume(it ?: "null") }
        }

        fun destroy() {
            web.stopLoading()
            web.destroy()
        }
    }

    private companion object {
        const val PARALLEL_TABS = 2
        const val PAGE_TIMEOUT = 25_000L
        const val API_TIMEOUT = 20_000L
        const val ORIGIN_TTL = 15 * 60 * 1000L
        const val POLL_INTERVAL = 600L
        const val STABLE_TICKS = 3
        const val MIN_WAIT = 2_500L
    }
}
