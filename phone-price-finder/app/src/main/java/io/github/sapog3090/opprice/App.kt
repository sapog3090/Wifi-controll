package io.github.sapog3090.opprice

import android.app.Application
import android.content.Context
import io.github.sapog3090.opprice.core.SearchEngine
import io.github.sapog3090.opprice.core.sources.AliExpressSource
import io.github.sapog3090.opprice.core.sources.AvitoSource
import io.github.sapog3090.opprice.core.sources.EbaySource
import io.github.sapog3090.opprice.core.sources.WildberriesSource
import io.github.sapog3090.opprice.data.AppDatabase
import io.github.sapog3090.opprice.data.Settings
import io.github.sapog3090.opprice.data.TrackRepository
import io.github.sapog3090.opprice.net.BrowserFetcher
import io.github.sapog3090.opprice.net.CachedRates
import io.github.sapog3090.opprice.net.SimpleHttp
import io.github.sapog3090.opprice.work.Notifier
import io.github.sapog3090.opprice.work.PriceCheckScheduler

class App : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifier.createChannel(this)
        PriceCheckScheduler.schedule(this, container.settings.intervalHours.value, replace = false)
    }
}

/** Ручная сборка зависимостей: их немного, DI-фреймворк тут был бы лишним. */
class AppContainer(context: Context) {
    val settings = Settings(context)
    private val fetcher = BrowserFetcher(context)
    val rates = CachedRates(context, SimpleHttp())
    val engine = SearchEngine(
        listOf(
            WildberriesSource(fetcher),
            AvitoSource(fetcher),
            AliExpressSource(fetcher),
            EbaySource(fetcher),
        ),
        rates,
    )
    val tracks = TrackRepository(AppDatabase.create(context).trackDao(), engine)
}

val Context.container: AppContainer get() = (applicationContext as App).container
