package com.jarvis.assistant.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Local, offline-first store. Every task, memory, routine and conversation is
 * kept here so the assistant keeps working with no network and after restarts.
 */
class JarvisDb(context: Context) : SQLiteOpenHelper(context, NAME, null, VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE tasks (
                id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                notes TEXT NOT NULL DEFAULT '',
                status TEXT NOT NULL DEFAULT 'pending',
                priority TEXT NOT NULL DEFAULT 'medium',
                due_at INTEGER,
                reminder_at INTEGER,
                completed_at INTEGER,
                evidence TEXT NOT NULL DEFAULT '',
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX tasks_status_idx ON tasks(status, due_at)")
        db.execSQL(
            """
            CREATE TABLE memories (
                id TEXT PRIMARY KEY,
                kind TEXT NOT NULL DEFAULT 'fact',
                content TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE routines (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                hour INTEGER NOT NULL,
                minute INTEGER NOT NULL,
                weekdays TEXT NOT NULL DEFAULT '',
                enabled INTEGER NOT NULL DEFAULT 1,
                prompt TEXT NOT NULL DEFAULT '',
                created_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE messages (
                id TEXT PRIMARY KEY,
                role TEXT NOT NULL,
                content TEXT NOT NULL,
                provider TEXT NOT NULL DEFAULT '',
                created_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE completion_stats (
                hour INTEGER NOT NULL,
                weekday INTEGER NOT NULL,
                count INTEGER NOT NULL DEFAULT 0,
                last_title TEXT NOT NULL DEFAULT '',
                last_at INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY (hour, weekday)
            )
            """.trimIndent(),
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // v1 is the initial schema; future migrations are added here.
    }

    private companion object {
        const val NAME = "jarvis.db"
        const val VERSION = 1
    }
}
