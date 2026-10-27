package com.engquest.teacher.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** A phone with the first database version keeps its unsent writes after the update. */
@RunWith(RobolectricTestRunner::class)
class DbMigrationTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val name = "migration-test.db"

    @Before
    fun setUp() {
        context.deleteDatabase(name)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(name)
    }

    /** Version 1 exactly as `schemas/.../1.json` made it, with one waiting write. */
    private fun makeVersion1() {
        val file = context.getDatabasePath(name).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE IF NOT EXISTS `cache` (`key` TEXT NOT NULL, `json` TEXT NOT NULL, `savedAt` INTEGER NOT NULL, PRIMARY KEY(`key`))")
            db.execSQL("CREATE TABLE IF NOT EXISTS `outbox` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `rid` TEXT NOT NULL, `action` TEXT NOT NULL, `className` TEXT NOT NULL, `paramsJson` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `state` TEXT NOT NULL, `error` TEXT)")
            db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'd253322094ff5ea1ef97ef989c6f7e9d')")
            db.execSQL("INSERT INTO outbox (rid, action, className, paramsJson, createdAt, state) VALUES ('r1', 'addStrike', 'XE1', '{}', 5, 'PENDING')")
            db.version = 1
        }
    }

    @Test
    fun `version 1 moves to version 2 keeping waiting writes`() = runBlocking {
        makeVersion1()

        // The same builder the app uses.
        val db = AppDb.open(context, name)
        val item = db.outboxDao().oldestPending()!!
        db.close()

        assertEquals("r1", item.rid)
        assertEquals(OutboxState.PENDING, item.state)
        assertEquals(0, item.attempts)
    }
}
