package io.github.sapog3090.opprice.ui.track

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.sapog3090.opprice.data.TrackSummary
import io.github.sapog3090.opprice.ui.ago
import io.github.sapog3090.opprice.ui.components.Hairline
import io.github.sapog3090.opprice.ui.components.Sparkline
import io.github.sapog3090.opprice.ui.date
import io.github.sapog3090.opprice.ui.describe
import io.github.sapog3090.opprice.ui.rub
import io.github.sapog3090.opprice.ui.signedRub
import io.github.sapog3090.opprice.ui.theme.LocalExtraColors
import io.github.sapog3090.opprice.ui.theme.PriceStyle

@Composable
fun TrackListScreen(vm: TrackListViewModel, intervalHours: Int, onOpen: (Long) -> Unit) {
    val summaries by vm.summaries.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 12.dp)) {
                Text("Слежу", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Проверка каждые $intervalHours ч, уведомление — при новом минимуме",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Hairline()
        }
        val list = summaries
        if (list != null && list.isEmpty()) {
            item {
                Text(
                    "Пока пусто. Найди модель на вкладке «Поиск» и нажми «Следить» — " +
                        "здесь появится история её минимальной цены.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(list.orEmpty(), key = { it.tracked.id }) { s -> TrackRow(s, onClick = { onOpen(s.tracked.id) }) }
    }
}

@Composable
private fun TrackRow(s: TrackSummary, onClick: () -> Unit) {
    val extra = LocalExtraColors.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                s.tracked.describe(),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                s.last?.let { rub(it.minPrice) } ?: "—",
                style = MaterialTheme.typography.titleMedium.merge(PriceStyle),
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val points = s.snapshots.takeLast(30).map { it.minPrice }
            if (points.size >= 2) {
                Sparkline(points, Modifier.size(width = 72.dp, height = 18.dp))
                Spacer(Modifier.width(10.dp))
            }
            val first = s.snapshots.firstOrNull()
            val last = s.last
            if (first != null && last != null && first !== last) {
                val delta = last.minPrice - first.minPrice
                Text(
                    "${signedRub(delta)} с ${date(first.time)}",
                    style = MaterialTheme.typography.labelMedium.merge(PriceStyle),
                    color = when {
                        delta < 0 -> extra.drop
                        delta > 0 -> extra.rise
                        else -> muted
                    },
                )
            }
            Spacer(Modifier.weight(1f))
            s.tracked.lastCheckedAt?.let {
                Text(ago(it), style = MaterialTheme.typography.labelSmall, color = muted)
            }
        }
        s.tracked.lastError?.let {
            Text(
                it,
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    Hairline(Modifier.padding(start = 16.dp))
}
