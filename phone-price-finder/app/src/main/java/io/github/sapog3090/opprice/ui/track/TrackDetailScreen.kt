package io.github.sapog3090.opprice.ui.track

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.sapog3090.opprice.data.SnapshotEntity
import io.github.sapog3090.opprice.data.source
import io.github.sapog3090.opprice.ui.ago
import io.github.sapog3090.opprice.ui.components.ChartPoint
import io.github.sapog3090.opprice.ui.components.Hairline
import io.github.sapog3090.opprice.ui.components.PriceChart
import io.github.sapog3090.opprice.ui.components.SectionLabel
import io.github.sapog3090.opprice.ui.components.SourceTag
import io.github.sapog3090.opprice.ui.dateTime
import io.github.sapog3090.opprice.ui.describe
import io.github.sapog3090.opprice.ui.rub
import io.github.sapog3090.opprice.ui.theme.PriceStyle

@Composable
fun TrackDetailScreen(vm: TrackDetailViewModel, onBack: () -> Unit, openUrl: (String) -> Unit) {
    val tracked by vm.tracked.collectAsStateWithLifecycle()
    val snapshots by vm.snapshots.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    val t = tracked

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") }
            Text(
                t?.describe().orEmpty(),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = "Удалить") }
        }
        Hairline()
        if (t == null) return@Column

        val lowest = snapshots.minByOrNull { it.minPrice }
        val last = snapshots.lastOrNull()
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Column(Modifier.weight(1f)) {
                            SectionLabel("Сейчас")
                            Text(last?.let { rub(it.minPrice) } ?: "—", style = MaterialTheme.typography.headlineMedium.merge(PriceStyle))
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            SectionLabel("Минимум")
                            Text(lowest?.let { rub(it.minPrice) } ?: "—", style = MaterialTheme.typography.titleMedium.merge(PriceStyle))
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    if (snapshots.size >= 2) {
                        PriceChart(
                            min = snapshots.map { ChartPoint(it.time, it.minPrice) },
                            median = snapshots.map { ChartPoint(it.time, it.medianPrice) },
                            modifier = Modifier.fillMaxWidth().height(180.dp),
                        )
                        Text(
                            "сплошная — минимум, пунктир — медиана, точка — лучшая цена за всё время",
                            modifier = Modifier.padding(top = 6.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            "График появится после второй проверки.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = vm::checkNow, enabled = !vm.checking, shape = MaterialTheme.shapes.small) {
                            if (vm.checking) {
                                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 1.5.dp)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text("Проверить сейчас")
                        }
                        t.lastCheckedAt?.let {
                            Text("проверено ${ago(it)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    t.lastError?.let {
                        Text(it, modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.height(16.dp))
                    TargetPrice(t.targetPrice, vm::setTarget)
                }
                Hairline()
            }
            if (last != null) {
                item {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { openUrl(last.bestUrl) }
                            .padding(16.dp),
                    ) {
                        SectionLabel("Лучшее предложение")
                        Spacer(Modifier.height(4.dp))
                        Text(last.bestTitle, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${last.source?.title.orEmpty()} · ${last.count} подходящих · открыть →",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Hairline()
                }
            }
            item { SectionLabel("Проверки", Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)) }
            items(snapshots.reversed(), key = { it.id }) { s -> SnapshotRow(s, isLowest = s.id == lowest?.id, onClick = { openUrl(s.bestUrl) }) }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Перестать следить?") },
            text = { Text("История цен по этому поиску удалится.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.delete(onBack)
                }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun TargetPrice(current: Long?, onSave: (Long?) -> Unit) {
    val focus = LocalFocusManager.current
    var text by rememberSaveable { mutableStateOf(current?.toString().orEmpty()) }
    LaunchedEffect(current) { text = current?.toString().orEmpty() }
    val save = {
        focus.clearFocus()
        onSave(text.filter(Char::isDigit).toLongOrNull()?.takeIf { it > 0 })
    }
    OutlinedTextField(
        value = text,
        onValueChange = { v -> text = v.filter(Char::isDigit).take(9) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text("Уведомить, если дешевле, ₽") },
        supportingText = { Text("Пусто — уведомлять только о новом историческом минимуме") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { save() }),
        trailingIcon = {
            val dirty = text != current?.toString().orEmpty()
            if (dirty) TextButton(onClick = save) { Text("OK") }
        },
        shape = MaterialTheme.shapes.small,
    )
}

@Composable
private fun SnapshotRow(s: SnapshotEntity, isLowest: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(dateTime(s.time), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        s.source?.let {
            SourceTag(it.tag)
            Spacer(Modifier.width(10.dp))
        }
        Text(
            rub(s.minPrice),
            style = MaterialTheme.typography.bodyMedium.merge(PriceStyle),
            color = if (isLowest) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
    Hairline(Modifier.padding(start = 16.dp))
}
