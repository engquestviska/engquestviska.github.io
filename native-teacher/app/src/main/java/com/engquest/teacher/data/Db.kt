package com.engquest.teacher.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    /** Busy/error replies from the server in a row; after [OutboxWorker.MAX_SERVER_TRIES] the item fails. */
    @ColumnInfo(defaultValue = "0") val attempts: Int = 0,
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

    /** PENDING -> SENDING in one step; returns 0 when the item was cancelled meanwhile. */
    @Query("UPDATE outbox SET state = 'SENDING' WHERE id = :id AND state = 'PENDING'")
    suspend fun claim(id: Long): Int

    /** A busy/error reply from the server: back in line, with [attempts] such replies so far. */
    @Query("UPDATE outbox SET state = 'PENDING', attempts = :attempts WHERE id = :id")
    suspend fun waitAfterServerTry(id: Long, attempts: Int)

    /** FAILED -> PENDING (Retry); the item keeps its rid and its server tries start again. */
    @Query("UPDATE outbox SET state = 'PENDING', error = NULL, attempts = 0 WHERE id = :id AND state = 'FAILED'")
    suspend fun requeueFailed(id: Long): Int

    /** After the app was killed mid-send, the item goes back in line (same rid, so no double write). */
    @Query("UPDATE outbox SET state = 'PENDING' WHERE state = 'SENDING'")
    suspend fun resetSending()

    @Query("DELETE FROM outbox WHERE id = :id")
    suspend fun delete(id: Long)

    /** Removes the item unless it is being sent right now; returns the number removed (0 or 1). */
    @Query("DELETE FROM outbox WHERE id = :id AND state != 'SENDING'")
    suspend fun deleteUnlessSending(id: Long): Int

    /** Log out: every unsent and failed item is dropped. */
    @Query("DELETE FROM outbox")
    suspend fun deleteAll()
}

@Database(entities = [CacheEntry::class, OutboxItem::class], version = 2)
abstract class AppDb : RoomDatabase() {
    abstract fun cacheDao(): CacheDao
    abstract fun outboxDao(): OutboxDao

    companion object {
        /** The app's on-disk database. */
        fun open(context: Context, name: String = "engquest-teacher.db"): AppDb =
            Room.databaseBuilder(context, AppDb::class.java, name).addMigrations(MIGRATION_1_2).build()

        /** Adds the count of server tries to each waiting write (existing ones start at 0). */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE outbox ADD COLUMN attempts INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
