package io.github.sapog3090.opprice.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import io.github.sapog3090.opprice.core.ConditionFilter
import io.github.sapog3090.opprice.core.FilterSpec
import io.github.sapog3090.opprice.core.Source
import kotlinx.coroutines.flow.Flow

/** Сохранённый поиск: текст запроса + фильтры, по которым считается минимальная цена. */
@Entity(tableName = "tracked")
data class TrackedEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val query: String,
    val model: String?,
    val storageGb: Int?,
    val condition: String,
    val sources: String,
    val targetPrice: Long?,
    val createdAt: Long,
    val lastCheckedAt: Long?,
    val lastError: String?,
)

val TrackedEntity.spec: FilterSpec
    get() = FilterSpec(
        model = model,
        storageGb = storageGb,
        condition = ConditionFilter.entries.find { it.name == condition } ?: ConditionFilter.ANY,
    )

val TrackedEntity.sourceSet: Set<Source>
    get() = sources.split(',').mapNotNull { name -> Source.entries.find { it.name == name } }.toSet()

@Entity(
    tableName = "snapshot",
    foreignKeys = [
        ForeignKey(
            entity = TrackedEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackedId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("trackedId")],
)
data class SnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackedId: Long,
    val time: Long,
    val minPrice: Long,
    val medianPrice: Long,
    val count: Int,
    val bestTitle: String,
    val bestUrl: String,
    val bestSource: String,
)

val SnapshotEntity.source: Source? get() = Source.entries.find { it.name == bestSource }

@Dao
abstract class TrackDao {
    @Query("SELECT * FROM tracked ORDER BY createdAt DESC")
    abstract fun observeTracked(): Flow<List<TrackedEntity>>

    @Query("SELECT * FROM tracked WHERE id = :id")
    abstract fun observeTracked(id: Long): Flow<TrackedEntity?>

    @Query("SELECT * FROM tracked WHERE id = :id")
    abstract suspend fun tracked(id: Long): TrackedEntity?

    @Query("SELECT id FROM tracked")
    abstract suspend fun trackedIds(): List<Long>

    @Insert
    abstract suspend fun insert(tracked: TrackedEntity): Long

    @Update
    abstract suspend fun update(tracked: TrackedEntity)

    @Query("UPDATE tracked SET targetPrice = :price WHERE id = :id")
    abstract suspend fun setTarget(id: Long, price: Long?)

    @Query("DELETE FROM snapshot WHERE trackedId = :id")
    abstract suspend fun deleteSnapshots(id: Long)

    @Query("DELETE FROM tracked WHERE id = :id")
    abstract suspend fun deleteTracked(id: Long)

    @Transaction
    open suspend fun delete(id: Long) {
        deleteSnapshots(id)
        deleteTracked(id)
    }

    @Insert
    abstract suspend fun insertSnapshot(snapshot: SnapshotEntity)

    @Query("SELECT * FROM snapshot WHERE trackedId = :id ORDER BY time ASC")
    abstract fun observeSnapshots(id: Long): Flow<List<SnapshotEntity>>

    @Query("SELECT * FROM snapshot ORDER BY time ASC")
    abstract fun observeAllSnapshots(): Flow<List<SnapshotEntity>>

    @Query("SELECT MIN(minPrice) FROM snapshot WHERE trackedId = :id")
    abstract suspend fun lowestEver(id: Long): Long?

    @Query("SELECT * FROM snapshot WHERE trackedId = :id ORDER BY time DESC LIMIT 1")
    abstract suspend fun lastSnapshot(id: Long): SnapshotEntity?
}

@Database(entities = [TrackedEntity::class, SnapshotEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "prices.db").build()
    }
}
