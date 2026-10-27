package com.engquest.teacher.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/** The last good server reply for one screen, keyed by [Repo.cacheKey]. */
@Entity(tableName = "cache")
data class CacheEntry(
    @PrimaryKey val key: String,
    val json: String,
    val savedAt: Long,
)

enum class OutboxState { PENDING, SENDING, FAILED }

/** One teacher write waiting to reach the server. Credentials are never stored here. */
@Entity(tableName = "outbox")
data class OutboxItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rid: String,
    val action: String,
    val className: String,
    val paramsJson: String,
    val createdAt: Long,
    val state: OutboxState = OutboxState.PENDING,
    val error: String? = null,
)

@Dao
interface CacheDao {
    @Query("SELECT * FROM cache WHERE `key` = :key")
    suspend fun get(key: String): CacheEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entry: CacheEntry)
}

/** "Unsent" = PENDING or SENDING; FAILED items wait for the teacher's Retry or Discard. */
@Dao
interface OutboxDao {
    @Insert
    suspend fun insert(item: OutboxItem): Long

    @Query("SELECT * FROM outbox WHERE id = :id")
    suspend fun get(id: Long): OutboxItem?

    @Query("SELECT * FROM outbox WHERE state = 'PENDING' ORDER BY id LIMIT 1")
    suspend fun oldestPending(): OutboxItem?

    @Query("SELECT COUNT(*) FROM outbox WHERE state IN ('PENDING', 'SENDING')")
    fun observeUnsentCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM outbox WHERE state IN ('PENDING', 'SENDING')")
    suspend fun countUnsent(): Int

    @Query("SELECT COUNT(*) FROM outbox WHERE className = :className AND state IN ('PENDING', 'SENDING')")
    suspend fun countUnsent(className: String): Int

    @Query("SELECT * FROM outbox WHERE state = 'FAILED' ORDER BY id")
    fun observeFailed(): Flow<List<OutboxItem>>

    @Query("UPDATE outbox SET state = :state, error = :error WHERE id = :id")
    suspend fun setState(id: Long, state: OutboxState, error: String? = null)

    /** After the app was killed mid-send, the item goes back in line (same rid, so no double write). */
    @Query("UPDATE outbox SET state = 'PENDING' WHERE state = 'SENDING'")
    suspend fun resetSending()

    @Query("DELETE FROM outbox WHERE id = :id")
    suspend fun delete(id: Long)

    /** Removes the item unless it is being sent right now; returns the number removed (0 or 1). */
    @Query("DELETE FROM outbox WHERE id = :id AND state != 'SENDING'")
    suspend fun deleteUnlessSending(id: Long): Int
}

@Database(entities = [CacheEntry::class, OutboxItem::class], version = 1)
abstract class AppDb : RoomDatabase() {
    abstract fun cacheDao(): CacheDao
    abstract fun outboxDao(): OutboxDao
}
