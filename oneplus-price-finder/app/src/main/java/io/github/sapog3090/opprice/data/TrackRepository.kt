package io.github.sapog3090.opprice.data

import io.github.sapog3090.opprice.core.FilterResult
import io.github.sapog3090.opprice.core.FilterSpec
import io.github.sapog3090.opprice.core.OfferFilter
import io.github.sapog3090.opprice.core.SearchEngine
import io.github.sapog3090.opprice.core.SearchQuery
import io.github.sapog3090.opprice.core.Source
import io.github.sapog3090.opprice.core.SourceState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class TrackSummary(val tracked: TrackedEntity, val snapshots: List<SnapshotEntity>) {
    val last: SnapshotEntity? get() = snapshots.lastOrNull()
}

data class CheckOutcome(
    val tracked: TrackedEntity,
    val snapshot: SnapshotEntity?,
    val previous: SnapshotEntity?,
    val lowestBefore: Long?,
) {
    /** Новый исторический минимум или цена впервые опустилась ниже желаемой. */
    val isNews: Boolean
        get() {
            val now = snapshot?.minPrice ?: return false
            val newLow = lowestBefore != null && now < lowestBefore
            val target = tracked.targetPrice
            val crossedTarget = target != null && now <= target && (previous == null || previous.minPrice > target)
            return newLow || crossedTarget
        }
}

class TrackRepository(private val dao: TrackDao, private val engine: SearchEngine) {

    fun observeSummaries(): Flow<List<TrackSummary>> =
        combine(dao.observeTracked(), dao.observeAllSnapshots()) { tracked, snapshots ->
            val byTrack = snapshots.groupBy { it.trackedId }
            tracked.map { TrackSummary(it, byTrack[it.id].orEmpty()) }
        }

    fun observeTracked(id: Long): Flow<TrackedEntity?> = dao.observeTracked(id)

    fun observeSnapshots(id: Long): Flow<List<SnapshotEntity>> = dao.observeSnapshots(id)

    suspend fun trackedIds(): List<Long> = dao.trackedIds()

    /** Сохраняет поиск; текущая выдача сразу становится первой точкой графика. */
    suspend fun add(query: String, spec: FilterSpec, sources: Set<Source>, current: FilterResult?): Long {
        val now = System.currentTimeMillis()
        val id = dao.insert(
            TrackedEntity(
                query = query,
                model = spec.model,
                storageGb = spec.storageGb,
                condition = spec.condition.name,
                sources = sources.joinToString(",") { it.name },
                targetPrice = null,
                createdAt = now,
                lastCheckedAt = now.takeIf { current != null },
                lastError = null,
            ),
        )
        current?.let { snapshotOf(id, now, it) }?.let { dao.insertSnapshot(it) }
        return id
    }

    suspend fun setTarget(id: Long, price: Long?) = dao.setTarget(id, price)

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun check(id: Long): CheckOutcome? {
        val tracked = dao.tracked(id) ?: return null
        val states = engine.searchAll(SearchQuery(tracked.query, tracked.sourceSet))
        val offers = states.values.filterIsInstance<SourceState.Done>().flatMap { it.offers }
        val errors = states.entries
            .mapNotNull { (source, state) -> (state as? SourceState.Failed)?.let { "${source.title}: ${it.message}" } }
            .joinToString("; ")
            .ifBlank { null }

        val previous = dao.lastSnapshot(id)
        val lowestBefore = dao.lowestEver(id)
        val now = System.currentTimeMillis()
        val snapshot = snapshotOf(id, now, OfferFilter.apply(tracked.spec, offers))
        snapshot?.let { dao.insertSnapshot(it) }
        val error = if (snapshot == null) errors ?: "подходящих предложений не нашлось" else errors
        dao.update(tracked.copy(lastCheckedAt = now, lastError = error))
        return CheckOutcome(tracked, snapshot, previous, lowestBefore)
    }

    private fun snapshotOf(id: Long, time: Long, result: FilterResult): SnapshotEntity? {
        val best = result.offers.firstOrNull() ?: return null
        return SnapshotEntity(
            trackedId = id,
            time = time,
            minPrice = best.priceRub,
            medianPrice = result.median ?: best.priceRub,
            count = result.offers.size,
            bestTitle = best.listing.title,
            bestUrl = best.listing.url,
            bestSource = best.source.name,
        )
    }
}
