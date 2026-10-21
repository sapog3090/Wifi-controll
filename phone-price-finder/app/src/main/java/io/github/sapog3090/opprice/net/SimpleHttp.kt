package io.github.sapog3090.opprice.net

import io.github.sapog3090.opprice.core.FetchResponse
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Обычный HTTP для открытых API без защиты от ботов — сейчас это только курсы ЦБ. */
class SimpleHttp {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun get(url: String): FetchResponse {
        val call = client.newCall(Request.Builder().url(url).build())
        return suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) cont.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    val result = runCatching {
                        response.use { FetchResponse(it.code, it.body?.string().orEmpty(), it.request.url.toString()) }
                    }
                    if (!cont.isActive) return
                    result.fold(cont::resume) { cont.resumeWithException(it) }
                }
            })
        }
    }
}
