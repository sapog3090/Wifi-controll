package io.github.sapog3090.opprice.ui.settings

import android.content.Intent
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.sapog3090.opprice.AppContainer
import io.github.sapog3090.opprice.ui.components.Hairline
import io.github.sapog3090.opprice.ui.components.SectionLabel
import io.github.sapog3090.opprice.ui.components.Segmented
import io.github.sapog3090.opprice.ui.dateTime
import io.github.sapog3090.opprice.work.Notifier
import io.github.sapog3090.opprice.work.PriceCheckScheduler
import io.github.sapog3090.opprice.data.Settings as AppSettings

@Composable
fun SettingsScreen(c: AppContainer) {
    val context = LocalContext.current
    val interval by c.settings.intervalHours.collectAsStateWithLifecycle()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 12.dp)) {
            Text("Настройки", style = MaterialTheme.typography.headlineMedium)
        }
        Hairline()

        Column(Modifier.padding(16.dp)) {
            SectionLabel("Фоновая проверка цен")
            Spacer(Modifier.height(8.dp))
            Segmented(
                options = AppSettings.INTERVALS,
                selected = interval,
                label = { "$it ч" },
                onSelect = {
                    c.settings.setIntervalHours(it)
                    PriceCheckScheduler.schedule(context, it, replace = true)
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Android может сдвигать проверки, чтобы экономить батарею. Нужен интернет.",
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                color = muted,
            )
            if (!Notifier.canNotify(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                TextButton(onClick = {
                    context.startActivity(
                        Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName),
                    )
                }) { Text("Уведомления выключены — включить") }
            }
        }
        Hairline()

        Column(Modifier.padding(16.dp)) {
            SectionLabel("Курс валют")
            Spacer(Modifier.height(6.dp))
            val usd = c.rates.cached("USD")
            Text(
                if (usd != null) "1 $ = ${"%.2f".format(usd)} ₽ по курсу ЦБ, обновлён ${dateTime(c.rates.updatedAt)}"
                else "Курс ещё не загружен — появится при первом поиске на eBay.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Цены eBay пересчитываются в рубли без учёта доставки и пошлины.",
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = muted,
            )
        }
        Hairline()

        Column(Modifier.padding(16.dp)) {
            SectionLabel("Как это работает")
            Spacer(Modifier.height(6.dp))
            listOf(
                "Wildberries — публичный поиск, который использует сам сайт. Самый стабильный источник.",
                "Страницы площадок загружаются встроенным браузером, как у обычного посетителя, поэтому поиск занимает 10–30 секунд.",
                "Если площадка показала капчу, нажми «Пройти проверку»: сайт откроется внутри приложения, после проверки поиск повторится.",
                "eBay — только «Купить сейчас», без аукционов, категория «Смартфоны».",
                "Если площадка поменяет вёрстку, приложение напишет «возможно, изменил вёрстку», а не покажет пустой список молча.",
            ).forEach {
                Text("— $it", modifier = Modifier.padding(bottom = 6.dp), style = MaterialTheme.typography.bodySmall, color = muted)
            }
        }
    }
}
