package com.naveensmk.mira

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

enum class TaskStatus { PLANNED, WAITING_FOR_CONFIRMATION, RUNNING, COMPLETED, FAILED, CANCELLED }

data class AgentTask(
    val id: Long,
    val title: String,
    val details: String,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long
)

class TaskStore(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "mira_tasks.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE tasks (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              title TEXT NOT NULL,
              details TEXT NOT NULL DEFAULT '',
              status TEXT NOT NULL,
              created_at INTEGER NOT NULL,
              updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    fun create(title: String, details: String = "", status: String = TaskStatus.PLANNED.name): Long {
        val now = System.currentTimeMillis()
        return writableDatabase.insert("tasks", null, ContentValues().apply {
            put("title", title)
            put("details", details)
            put("status", status)
            put("created_at", now)
            put("updated_at", now)
        })
    }

    fun updateStatus(id: Long, status: TaskStatus) {
        writableDatabase.update("tasks", ContentValues().apply {
            put("status", status.name)
            put("updated_at", System.currentTimeMillis())
        }, "id = ?", arrayOf(id.toString()))
    }

    fun list(limit: Int = 100): List<AgentTask> {
        val result = mutableListOf<AgentTask>()
        readableDatabase.rawQuery(
            "SELECT id,title,details,status,created_at,updated_at FROM tasks ORDER BY updated_at DESC LIMIT ?",
            arrayOf(limit.coerceIn(1, 500).toString())
        ).use { c ->
            while (c.moveToNext()) result += AgentTask(
                c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getLong(4), c.getLong(5)
            )
        }
        return result
    }
}
