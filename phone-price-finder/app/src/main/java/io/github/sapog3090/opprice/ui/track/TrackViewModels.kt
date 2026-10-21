package io.github.sapog3090.opprice.ui.track

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.sapog3090.opprice.data.SnapshotEntity
import io.github.sapog3090.opprice.data.TrackRepository
import io.github.sapog3090.opprice.data.TrackSummary
import io.github.sapog3090.opprice.data.TrackedEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TrackListViewModel(repo: TrackRepository) : ViewModel() {
    val summaries: StateFlow<List<TrackSummary>?> =
        repo.observeSummaries().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

class TrackDetailViewModel(private val id: Long, private val repo: TrackRepository) : ViewModel() {
    val tracked: StateFlow<TrackedEntity?> =
        repo.observeTracked(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val snapshots: StateFlow<List<SnapshotEntity>> =
        repo.observeSnapshots(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var checking by mutableStateOf(false)
        private set

    fun checkNow() {
        if (checking) return
        checking = true
        viewModelScope.launch {
            try {
                repo.check(id)
            } finally {
                checking = false
            }
        }
    }

    fun setTarget(price: Long?) {
        viewModelScope.launch { repo.setTarget(id, price) }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            repo.delete(id)
            onDone()
        }
    }
}
