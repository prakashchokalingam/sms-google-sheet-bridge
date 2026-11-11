package io.github.prakashchokalingam.smsgooglesheetbridge.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import io.github.prakashchokalingam.smsgooglesheetbridge.redact.BuiltInRules

class SmsBridgeDb(context: Context) : SQLiteOpenHelper(context, NAME, null, VERSION) {

    init {
        setWriteAheadLoggingEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE sms (
                id TEXT PRIMARY KEY NOT NULL,
                received_at INTEGER NOT NULL,
                sender TEXT NOT NULL,
                sender_name TEXT NOT NULL DEFAULT '',
                sim INTEGER NOT NULL DEFAULT -1,
                body TEXT NOT NULL,
                batch_seq INTEGER,
                masked INTEGER NOT NULL DEFAULT 0,
                redactions INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_sms_batch ON sms(batch_seq, received_at)")
        db.execSQL("CREATE INDEX idx_sms_received ON sms(received_at)")
        db.execSQL(
            """
            CREATE TABLE batch (
                seq INTEGER PRIMARY KEY NOT NULL,
                window_start INTEGER NOT NULL,
                window_end INTEGER NOT NULL,
                count INTEGER NOT NULL DEFAULT 0,
                masked_count INTEGER NOT NULL DEFAULT 0,
                skipped_otp INTEGER NOT NULL DEFAULT 0,
                status TEXT NOT NULL DEFAULT 'PENDING',
                created_at INTEGER NOT NULL,
                sent_at INTEGER,
                attempts INTEGER NOT NULL DEFAULT 0,
                last_error TEXT
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX idx_batch_window ON batch(window_start, window_end)")
        db.execSQL(
            """
            CREATE TABLE window_counter (
                window_start INTEGER PRIMARY KEY NOT NULL,
                masked_count INTEGER NOT NULL DEFAULT 0,
                skipped_otp INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE rule (
                id TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL,
                pattern TEXT NOT NULL,
                action TEXT NOT NULL,
                grp INTEGER NOT NULL DEFAULT 0,
                replacement TEXT NOT NULL DEFAULT '[REDACTED]',
                enabled INTEGER NOT NULL DEFAULT 1,
                priority INTEGER NOT NULL DEFAULT 100,
                built_in INTEGER NOT NULL DEFAULT 0,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        seedBuiltIns(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        seedBuiltIns(db)
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        seedBuiltIns(db)
    }

    fun seedBuiltIns(db: SQLiteDatabase = writableDatabase) {
        for (rule in BuiltInRules.all) {
            db.execSQL(
                """
                INSERT OR IGNORE INTO rule
                (id, name, pattern, action, grp, replacement, enabled, priority, built_in, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, ?)
                """.trimIndent(),
                arrayOf<Any>(
                    rule.id,
                    rule.name,
                    rule.pattern,
                    rule.action.name,
                    rule.group,
                    rule.replacement,
                    if (rule.enabled) 1 else 0,
                    rule.priority,
                    System.currentTimeMillis()
                )
            )
        }
    }

    companion object {
        const val NAME = "sms_sheet.db"
        const val VERSION = 1
    }
}
