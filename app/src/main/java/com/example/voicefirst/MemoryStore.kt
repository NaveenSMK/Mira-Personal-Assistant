package com.example.voicefirst

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class SavedMemory(
    val id: Long,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * Small local-only memory store. It intentionally saves only when the user explicitly
 * asks VoiceFirst to remember something. This is a foundation, not semantic/vector memory.
 */
class MemoryStore(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "voicefirst_memory.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE memories (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                content TEXT NOT NULL UNIQUE COLLATE NOCASE,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Add explicit migrations here before incrementing the database version.
    }

    fun save(content: String): Long {
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("content", content.trim())
            put("created_at", now)
            put("updated_at", now)
        }
        val id = writableDatabase.insertWithOnConflict(
            "memories", null, values, SQLiteDatabase.CONFLICT_IGNORE
        )
        if (id != -1L) return id

        val update = ContentValues().apply { put("updated_at", now) }
        writableDatabase.update("memories", update, "content = ? COLLATE NOCASE", arrayOf(content.trim()))
        return readableDatabase.rawQuery(
            "SELECT id FROM memories WHERE content = ? COLLATE NOCASE",
            arrayOf(content.trim())
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else -1L }
    }

    fun list(limit: Int = 100): List<SavedMemory> {
        val result = mutableListOf<SavedMemory>()
        readableDatabase.rawQuery(
            "SELECT id, content, created_at, updated_at FROM memories ORDER BY updated_at DESC LIMIT ?",
            arrayOf(limit.coerceIn(1, 500).toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += SavedMemory(
                    id = cursor.getLong(0),
                    content = cursor.getString(1),
                    createdAt = cursor.getLong(2),
                    updatedAt = cursor.getLong(3)
                )
            }
        }
        return result
    }

    fun deleteMatching(query: String): Int =
        writableDatabase.delete("memories", "content LIKE ? ESCAPE '\\'", arrayOf("%${escapeLike(query)}%"))

    fun clearAll(): Int = writableDatabase.delete("memories", null, null)

    private fun escapeLike(value: String): String =
        value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}
