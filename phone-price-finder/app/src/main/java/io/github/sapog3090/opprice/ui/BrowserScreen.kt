package io.github.sapog3090.opprice.ui

import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.sapog3090.opprice.net.Browser
import io.github.sapog3090.opprice.ui.components.Hairline

/**
 * Сайт площадки внутри приложения. Нужен, чтобы вручную пройти капчу: куки общие
 * с невидимыми вкладками поиска, так что после «Готово» поиск повторяется уже «как человек».
 */
@Composable
fun BrowserScreen(url: String, onDone: () -> Unit) {
    var loading by remember { mutableStateOf(true) }
    var host by remember { mutableStateOf(Uri.parse(url).host.orEmpty()) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") }
            Column(Modifier.weight(1f)) {
                Text(host, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "Пройди проверку, если она есть, и нажми «Готово»",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = onDone) { Text("Готово") }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth()) else Hairline()
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    Browser.configure(this, headless = false)
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                            loading = true
                            host = Uri.parse(url).host.orEmpty()
                        }

                        override fun onPageFinished(view: WebView, url: String) {
                            loading = false
                            CookieManager.getInstance().flush()
                        }

                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) =
                            !Browser.isWebUrl(request.url.toString())
                    }
                    loadUrl(url)
                }
            },
            onRelease = { it.destroy() },
        )
    }
}
