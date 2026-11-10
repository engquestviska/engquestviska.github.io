package com.engquest.app.data

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
import kotlinx.coroutines.flow.Flow

/** The last good server reply for one screen, keyed by action + params. */
@Entity(tableName = "cache")
data class CacheEntry(
    @PrimaryKey val key: String,
    val json: String,
    val savedAt: Long,
)

enum class SendState { PENDING, SENDING, FAILED }

/**
 * One task photo send waiting to reach the server. [photoPaths] is a JSON array of files in the
 * app's own storage (already compressed). [rid] makes a resend after a lost reply harmless.
 */
@Entity(tableName = "photo_send")
data class PhotoSend(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rid: String,
    val classId: String,
    val studentNo: Int,
    val taskKey: String,
    val photoPaths: String,
    val createdAt: Long,
    val state: SendState = SendState.PENDING,
    val error: String? = null,
    @ColumnInfo(defaultValue = "0") val attempts: Int = 0,
)

@Dao
interface CacheDao {
    @Query("SELECT * FROM cache WHERE `key` = :key")
    suspend fun get(key: String): CacheEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entry: CacheEntry)

    @Query("DELETE FROM cache")
    suspend fun clear()
}

@Dao
interface PhotoSendDao {
    @Insert
    suspend fun insert(item: PhotoSend): Long

    @Query("SELECT * FROM photo_send WHERE id = :id")
    suspend fun get(id: Long): PhotoSend?

    @Query("SELECT * FROM photo_send ORDER BY id DESC")
    fun observeAll(): Flow<List<PhotoSend>>

    @Query("SELECT * FROM photo_send ORDER BY id DESC")
    suspend fun all(): List<PhotoSend>

    @Query("SELECT * FROM photo_send WHERE state = 'PENDING' ORDER BY id LIMIT 1")
    suspend fun oldestPending(): PhotoSend?

    /** PENDING -> SENDING in one step; 0 when it was removed meanwhile. */
    @Query("UPDATE photo_send SET state = 'SENDING' WHERE id = :id AND state = 'PENDING'")
    suspend fun claim(id: Long): Int

    @Query("UPDATE photo_send SET state = :state, error = :error, attempts = :attempts WHERE id = :id")
    suspend fun setState(id: Long, state: SendState, error: String?, attempts: Int)

    /** After the app was killed mid-send: back in line, same rid, so no double send. */
    @Query("UPDATE photo_send SET state = 'PENDING' WHERE state = 'SENDING'")
    suspend fun resetSending()

    @Query("DELETE FROM photo_send WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM photo_send")
    suspend fun deleteAll()
}

@Database(entities = [CacheEntry::class, PhotoSend::class], version = 1, exportSchema = true)
abstract class AppDb : RoomDatabase() {
    abstract fun cacheDao(): CacheDao
    abstract fun photoSendDao(): PhotoSendDao

    companion object {
        fun open(context: Context): AppDb =
            Room.databaseBuilder(context, AppDb::class.java, "engquest-student.db").build()
    }
}
