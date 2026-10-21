package io.github.sapog3090.opprice.ui.search

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.sapog3090.opprice.AppContainer
import io.github.sapog3090.opprice.core.ConditionFilter
import io.github.sapog3090.opprice.core.FilterSpec
import io.github.sapog3090.opprice.core.Offer
import io.github.sapog3090.opprice.core.OfferFilter
import io.github.sapog3090.opprice.core.SearchQuery
import io.github.sapog3090.opprice.core.Source
import io.github.sapog3090.opprice.core.SourceState
import io.github.sapog3090.opprice.core.text.TitleAnalyzer
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class SearchViewModel(private val c: AppContainer) : ViewModel() {

    var query by mutableStateOf("")
    var sources by mutableStateOf(c.settings.sources.value)
        private set
    var spec by mutableStateOf(FilterSpec())
    var states by mutableStateOf<Map<Source, SourceState>>(emptyMap())
        private set
    /** Запрос, по которому получены текущие результаты (поле ввода могли уже поменять). */
    var searched by mutableStateOf<String?>(null)
        private set
    var trackedId by mutableStateOf<Long?>(null)
        private set

    private val offers: List<Offer> by derivedStateOf {
        states.values.filterIsInstance<SourceState.Done>().flatMap { it.offers }
    }
    val result by derivedStateOf { OfferFilter.apply(spec, offers) }
    val facets by derivedStateOf { OfferFilter.facets(spec, offers) }
    val loading by derivedStateOf { states.values.any { it == SourceState.Loading } }
    val allFailed by derivedStateOf { states.isNotEmpty() && states.values.all { it is SourceState.Failed } }

    private var job: Job? = null

    fun toggleSource(source: Source) {
        val next = if (source in sources) sources - source else sources + source
        if (next.isEmpty()) return
        sources = next
        c.settings.setSources(next)
    }

    /** Повторяет последний поиск — например, после ручного прохождения капчи. */
    fun retry() {
        searched?.let { search(it) }
    }

    fun search(text: String = query.trim()) {
        if (text.isEmpty()) return
        job?.cancel()
        searched = text
        trackedId = null
        // Если бренд, модель или память названы в запросе — сразу включаем соответствующий фильтр.
        spec = spec.copy(
            brand = TitleAnalyzer.brandOf(text),
            model = TitleAnalyzer.modelOf(text),
            storageGb = TitleAnalyzer.storageOf(text),
        )
        states = sources.associateWith { SourceState.Loading }
        job = viewModelScope.launch {
            c.engine.search(SearchQuery(text, sources)).collect { update ->
                states = states + (update.source to update.state)
            }
        }
    }

    fun setBrand(brand: String?) {
        spec = spec.copy(brand = brand, model = null, storageGb = null)
        trackedId = null
    }

    fun setModel(model: String?) {
        spec = spec.copy(model = model, storageGb = null)
        trackedId = null
    }

    fun setStorage(gb: Int?) {
        spec = spec.copy(storageGb = gb)
        trackedId = null
    }

    fun setCondition(condition: ConditionFilter) {
        spec = spec.copy(condition = condition)
        trackedId = null
    }

    fun browserUrl(source: Source): String? = searched?.let { c.engine.browserUrl(source, it) }

    fun track() {
        val text = searched ?: return
        if (trackedId != null) return
        val current = result.takeIf { it.offers.isNotEmpty() }
        val sourcesNow = states.keys
        val specNow = spec
        viewModelScope.launch {
            trackedId = c.tracks.add(text, specNow, sourcesNow, current)
        }
    }
}
