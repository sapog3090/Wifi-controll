package io.github.sapog3090.opprice.data

import android.content.Context
import io.github.sapog3090.opprice.core.Source
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _intervalHours = MutableStateFlow(prefs.getInt(KEY_INTERVAL, 6))
    val intervalHours: StateFlow<Int> = _intervalHours.asStateFlow()

    private val _sources = MutableStateFlow(
        prefs.getStringSet(KEY_SOURCES, null)
            ?.mapNotNull { name -> Source.entries.find { it.name == name } }?.toSet()
            ?: Source.entries.toSet(),
    )
    val sources: StateFlow<Set<Source>> = _sources.asStateFlow()

    fun setIntervalHours(hours: Int) {
        prefs.edit().putInt(KEY_INTERVAL, hours).apply()
        _intervalHours.value = hours
    }

    fun setSources(sources: Set<Source>) {
        prefs.edit().putStringSet(KEY_SOURCES, sources.map { it.name }.toSet()).apply()
        _sources.value = sources
    }

    companion object {
        val INTERVALS = listOf(3, 6, 12, 24)
        private const val KEY_INTERVAL = "interval_hours"
        private const val KEY_SOURCES = "sources"
    }
}
