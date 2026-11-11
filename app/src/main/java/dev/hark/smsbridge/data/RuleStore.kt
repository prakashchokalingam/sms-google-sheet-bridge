package dev.hark.smsbridge.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import dev.hark.smsbridge.redact.RedactionRule
import dev.hark.smsbridge.redact.RuleAction
import org.json.JSONArray
import org.json.JSONObject

class RuleStore(private val helper: HarkDb) {

    private val db: SQLiteDatabase
        get() = helper.writableDatabase

    fun list(): List<RedactionRule> {
        return db.rawQuery(
            "SELECT * FROM rule ORDER BY built_in DESC, action ASC, priority ASC, name ASC",
            null
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(ruleFrom(cursor))
            }
        }
    }

    fun find(id: String): RedactionRule? {
        return db.rawQuery("SELECT * FROM rule WHERE id = ?", arrayOf(id)).use { cursor ->
            if (cursor.moveToFirst()) ruleFrom(cursor) else null
        }
    }

    fun save(rule: RedactionRule) {
        val now = System.currentTimeMillis()
        val existing = find(rule.id)
        val builtIn = existing?.builtIn ?: rule.builtIn
        val values = ContentValues().apply {
            put("id", rule.id)
            put("name", rule.name)
            put("pattern", rule.pattern)
            put("action", rule.action.name)
            put("grp", rule.group)
            put("replacement", rule.replacement)
            put("enabled", if (rule.enabled) 1 else 0)
            put("priority", rule.priority)
            put("built_in", if (builtIn) 1 else 0)
            put("updated_at", now)
        }
        db.insertWithOnConflict("rule", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun setEnabled(id: String, enabled: Boolean) {
        db.execSQL(
            "UPDATE rule SET enabled = ?, updated_at = ? WHERE id = ?",
            arrayOf<Any>(if (enabled) 1 else 0, System.currentTimeMillis(), id)
        )
    }

    fun deleteCustom(id: String) {
        db.delete("rule", "id = ? AND built_in = 0", arrayOf(id))
    }

    fun resetBuiltIns() {
        db.beginTransaction()
        try {
            db.delete("rule", "built_in = 1", null)
            helper.seedBuiltIns(db)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun countCustom(): Int {
        return db.rawQuery("SELECT COUNT(*) FROM rule WHERE built_in = 0", null).use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }
    }

    fun exportJson(): String {
        val array = JSONArray()
        for (rule in list()) {
            val json = JSONObject().apply {
                put("id", rule.id)
                put("name", rule.name)
                put("pattern", rule.pattern)
                put("action", rule.action.name)
                put("group", rule.group)
                put("replacement", rule.replacement)
                put("enabled", rule.enabled)
                put("priority", rule.priority)
                put("builtIn", rule.builtIn)
            }
            array.put(json)
        }
        return array.toString(2)
    }

    fun importJson(text: String, validator: (RedactionRule) -> String? = { null }): ImportOutcome {
        val array = JSONArray(text)
        var imported = 0
        val errors = ArrayList<String>()
        db.beginTransaction()
        try {
            for (i in 0 until array.length()) {
                val json = array.getJSONObject(i)
                val id = json.optString("id").ifBlank { "custom.${System.currentTimeMillis()}.$i" }
                if (find(id)?.builtIn == true) continue
                val rule = RedactionRule(
                    id = id,
                    name = json.optString("name", "Imported rule"),
                    pattern = json.optString("pattern"),
                    action = runCatching {
                        RuleAction.valueOf(json.optString("action", "MASK").uppercase())
                    }.getOrDefault(RuleAction.MASK),
                    group = json.optInt("group", 0),
                    replacement = json.optString("replacement", "[REDACTED]"),
                    enabled = json.optBoolean("enabled", true),
                    priority = json.optInt("priority", 300),
                    builtIn = false
                )
                val error = validator(rule)
                if (error != null) {
                    errors.add("${rule.id}: $error")
                    continue
                }
                save(rule)
                imported++
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return ImportOutcome(imported, errors)
    }

    data class ImportOutcome(val imported: Int, val errors: List<String>)

    private fun ruleFrom(cursor: Cursor): RedactionRule {
        return RedactionRule(
            id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
            name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
            pattern = cursor.getString(cursor.getColumnIndexOrThrow("pattern")),
            action = runCatching {
                RuleAction.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("action")))
            }.getOrDefault(RuleAction.MASK),
            group = cursor.getInt(cursor.getColumnIndexOrThrow("grp")),
            replacement = cursor.getString(cursor.getColumnIndexOrThrow("replacement")),
            enabled = cursor.getInt(cursor.getColumnIndexOrThrow("enabled")) == 1,
            priority = cursor.getInt(cursor.getColumnIndexOrThrow("priority")),
            builtIn = cursor.getInt(cursor.getColumnIndexOrThrow("built_in")) == 1
        )
    }
}
