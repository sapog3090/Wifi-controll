package io.github.sapog3090.opprice.ui.search

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.sapog3090.opprice.core.ConditionFilter
import io.github.sapog3090.opprice.core.FailureKind
import io.github.sapog3090.opprice.core.Hidden
import io.github.sapog3090.opprice.core.Offer
import io.github.sapog3090.opprice.core.Source
import io.github.sapog3090.opprice.core.SourceState
import io.github.sapog3090.opprice.ui.components.FacetChip
import io.github.sapog3090.opprice.ui.components.Hairline
import io.github.sapog3090.opprice.ui.components.SectionLabel
import io.github.sapog3090.opprice.ui.components.Segmented
import io.github.sapog3090.opprice.ui.components.SourceTag
import io.github.sapog3090.opprice.ui.condition
import io.github.sapog3090.opprice.ui.conditionFilter
import io.github.sapog3090.opprice.ui.memory
import io.github.sapog3090.opprice.ui.money
import io.github.sapog3090.opprice.ui.rub
import io.github.sapog3090.opprice.ui.storage
import io.github.sapog3090.opprice.ui.theme.LocalExtraColors
import io.github.sapog3090.opprice.ui.theme.PriceStyle
import io.github.sapog3090.opprice.work.Notifier
import kotlin.math.roundToInt

@Composable
fun SearchScreen(vm: SearchViewModel, openUrl: (String) -> Unit) {
    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    var showHidden by rememberSaveable { mutableStateOf(false) }
    val result = vm.result

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { QueryBar(vm) }
        item { SourceStrip(vm) }
        item { Failures(vm, openUrl) }
        if (vm.searched != null) {
            item { Filters(vm) }
            item {
                Summary(
                    vm = vm,
                    onTrack = {
                        if (Build.VERSION.SDK_INT >= 33 && !Notifier.canNotify(context)) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        vm.track()
                    },
                )
            }
            itemsIndexed(result.offers, key = { _, o -> o.key }) { i, offer ->
                OfferRow(offer, median = result.median, best = i == 0, onClick = { openUrl(offer.listing.url) })
            }
            if (result.hidden.isNotEmpty()) {
                item { HiddenHeader(result.hiddenCounts, expanded = showHidden) { showHidden = !showHidden } }
                if (showHidden) {
                    items(result.hidden, key = { "h:" + it.offer.key }) { h ->
                        HiddenRow(h, onClick = { openUrl(h.offer.listing.url) })
                    }
                }
            }
        } else {
            item { Intro() }
        }
    }
}

@Composable
private fun QueryBar(vm: SearchViewModel) {
    val focus = LocalFocusManager.current
    val submit = {
        focus.clearFocus()
        vm.search()
    }
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp)) {
        Text("Цены на смартфоны", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = vm.query,
            onValueChange = { vm.query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("iPhone 15 128, Galaxy S24, Redmi Note 13…") },
            trailingIcon = {
                IconButton(onClick = submit) { Icon(Icons.Filled.Search, contentDescription = "Искать") }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { submit() }),
            shape = MaterialTheme.shapes.small,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.onSurface,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            ),
        )
    }
}

/** Площадки: нажатие включает/выключает, справа — сколько нашлось или что пошло не так. */
@Composable
private fun SourceStrip(vm: SearchViewModel) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(Source.entries) { source ->
            val enabled = source in vm.sources
            val state = vm.states[source]
            val colors = MaterialTheme.colorScheme
            Row(
                Modifier
                    .alpha(if (enabled) 1f else 0.45f)
                    .clickable { vm.toggleSource(source) }
                    .padding(vertical = 4.dp, horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    Modifier
                        .size(8.dp)
                        .background(if (enabled) colors.onSurface else colors.outline, MaterialTheme.shapes.extraSmall),
                )
                Text(source.title, style = MaterialTheme.typography.labelLarge)
                when (state) {
                    SourceState.Loading -> CircularProgressIndicator(Modifier.size(10.dp), strokeWidth = 1.5.dp)
                    is SourceState.Done -> Text(
                        state.offers.size.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                    )
                    is SourceState.Failed -> Text("✕", style = MaterialTheme.typography.labelMedium, color = colors.error)
                    null -> Unit
                }
            }
        }
    }
}

@Composable
private fun Failures(vm: SearchViewModel, openUrl: (String) -> Unit) {
    val failed = vm.states.mapNotNull { (s, st) -> (st as? SourceState.Failed)?.let { s to it } }
    if (failed.isEmpty()) return
    Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp)) {
        failed.forEach { (source, f) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${source.title}: ${f.message}",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                val url = vm.browserUrl(source)
                if (url != null && f.kind != FailureKind.NETWORK) {
                    TextButton(onClick = { openUrl(url) }) { Text("Открыть сайт") }
                }
            }
        }
    }
}

@Composable
private fun Filters(vm: SearchViewModel) {
    val facets = vm.facets
    Column(Modifier.padding(top = 4.dp)) {
        Segmented(
            options = ConditionFilter.entries,
            selected = vm.spec.condition,
            label = ::conditionFilter,
            onSelect = vm::setCondition,
            modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
        )
        if (facets.brands.size > 1 || vm.spec.brand != null) {
            SectionLabel("Бренд", Modifier.padding(start = 16.dp, top = 16.dp, bottom = 6.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item { FacetChip("Все", null, vm.spec.brand == null) { vm.setBrand(null) } }
                val brands = facets.brands.toMutableList()
                vm.spec.brand?.let { b -> if (brands.none { it.first == b }) brands.add(0, b to 0) }
                items(brands, key = { it.first }) { (brand, count) ->
                    FacetChip(brand, count, vm.spec.brand == brand) {
                        vm.setBrand(if (vm.spec.brand == brand) null else brand)
                    }
                }
            }
        }
        if (facets.models.isNotEmpty() || vm.spec.model != null) {
            SectionLabel("Модель", Modifier.padding(start = 16.dp, top = 16.dp, bottom = 6.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item { FacetChip("Все", null, vm.spec.model == null) { vm.setModel(null) } }
                val models = facets.models.toMutableList()
                // Выбранная модель остаётся видимой, даже если в выдаче её не оказалось.
                vm.spec.model?.let { m -> if (models.none { it.first == m }) models.add(0, m to 0) }
                items(models, key = { it.first }) { (model, count) ->
                    FacetChip(model, count, vm.spec.model == model) {
                        vm.setModel(if (vm.spec.model == model) null else model)
                    }
                }
            }
        }
        if (facets.storages.isNotEmpty() || vm.spec.storageGb != null) {
            SectionLabel("Память", Modifier.padding(start = 16.dp, top = 12.dp, bottom = 6.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item { FacetChip("Любая", null, vm.spec.storageGb == null) { vm.setStorage(null) } }
                val storages = facets.storages.toMutableList()
                vm.spec.storageGb?.let { s -> if (storages.none { it.first == s }) storages.add(s to 0) }
                items(storages, key = { it.first }) { (gb, count) ->
                    FacetChip(storage(gb), count, vm.spec.storageGb == gb) {
                        vm.setStorage(if (vm.spec.storageGb == gb) null else gb)
                    }
                }
            }
        }
    }
}

@Composable
private fun Summary(vm: SearchViewModel, onTrack: () -> Unit) {
    val r = vm.result
    Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 20.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (r.min != null) {
                    Text("от ${rub(r.min!!)}", style = MaterialTheme.typography.titleLarge.merge(PriceStyle))
                    Text(
                        listOfNotNull(
                            "${r.offers.size} ${plural(r.offers.size, "предложение", "предложения", "предложений")}",
                            r.median?.let { "медиана ${rub(it)}" },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (!vm.loading) {
                    Text("Подходящих предложений нет", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Попробуй ослабить фильтры или посмотри скрытое ниже",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text("Ищу…", style = MaterialTheme.typography.titleMedium)
                }
            }
            val tracked = vm.trackedId != null
            TextButton(onClick = onTrack, enabled = !tracked && !vm.loading) {
                Icon(
                    if (tracked) Icons.Filled.Check else Icons.Filled.Star,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(if (tracked) "Слежу" else "Следить")
            }
        }
    }
    Hairline(Modifier.padding(top = 8.dp))
}

@Composable
private fun OfferRow(offer: Offer, median: Long?, best: Boolean, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val extra = LocalExtraColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .drawBehind {
                if (best) drawRect(accent, topLeft = Offset.Zero, size = size.copy(width = 3.dp.toPx()))
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Text(offer.listing.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SourceTag(offer.source.tag)
                Spacer(Modifier.width(6.dp))
                Text(
                    meta(offer),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(rub(offer.priceRub), style = MaterialTheme.typography.titleMedium.merge(PriceStyle))
            if (median != null && median > 0) {
                val pct = ((offer.priceRub - median) * 100.0 / median).roundToInt()
                if (pct != 0) {
                    Text(
                        if (pct < 0) "−${-pct}%" else "+$pct%",
                        style = MaterialTheme.typography.labelMedium.merge(PriceStyle),
                        color = if (pct < 0) extra.drop else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (offer.listing.price.currency != "RUB") {
                Text(
                    "≈ ${money(offer.listing.price)}",
                    style = MaterialTheme.typography.labelSmall.merge(PriceStyle),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    Hairline(Modifier.padding(start = 16.dp))
}

private fun meta(offer: Offer): String = listOfNotNull(
    offer.model ?: offer.brand,
    memory(offer.memory),
    condition(offer.condition),
    offer.listing.location,
).joinToString(" · ")

@Composable
private fun HiddenHeader(counts: List<Pair<io.github.sapog3090.opprice.core.HideReason, Int>>, expanded: Boolean, onClick: () -> Unit) {
    val total = counts.sumOf { it.second }
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            (if (expanded) "Скрыть отсеянное" else "Отсеяно: $total"),
            style = MaterialTheme.typography.labelLarge,
        )
        Text(
            counts.joinToString(", ") { (reason, n) -> "${reason.label} — $n" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Hairline()
}

@Composable
private fun HiddenRow(h: Hidden, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .alpha(0.6f)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(h.offer.listing.title, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                SourceTag(h.offer.source.tag)
                Spacer(Modifier.width(6.dp))
                Text(h.reason.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(rub(h.offer.priceRub), style = MaterialTheme.typography.bodyMedium.merge(PriceStyle))
    }
}

@Composable
private fun Intro() {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        IntroLine("1", "Ищет любой смартфон сразу на Wildberries, Avito, AliExpress и eBay: iPhone, Galaxy, Xiaomi, Pixel, OnePlus, Honor и другие.")
        IntroLine("2", "Отсеивает чехлы, стёкла, запчасти и подозрительно дешёвые объявления — их можно посмотреть отдельно.")
        IntroLine("3", "Бренд, модель и память можно указать в запросе («айфон 15 256») или выбрать после поиска.")
        IntroLine("4", "«Следить» сохраняет поиск: приложение будет проверять минимум в фоне и напишет, когда он упадёт.")
    }
}

@Composable
private fun IntroLine(n: String, text: String) {
    Row {
        Text(
            n,
            modifier = Modifier.width(20.dp),
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.primary,
        )
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

internal fun plural(n: Int, one: String, few: String, many: String): String {
    val mod100 = n % 100
    val mod10 = n % 10
    return when {
        mod100 in 11..14 -> many
        mod10 == 1 -> one
        mod10 in 2..4 -> few
        else -> many
    }
}
