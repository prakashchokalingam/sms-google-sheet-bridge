package io.github.prakashchokalingam.smsgooglesheetbridge

import android.content.Context
import io.github.prakashchokalingam.smsgooglesheetbridge.data.SmsBridgeDb
import io.github.prakashchokalingam.smsgooglesheetbridge.data.MessageStore
import io.github.prakashchokalingam.smsgooglesheetbridge.data.Prefs
import io.github.prakashchokalingam.smsgooglesheetbridge.data.RuleStore
import io.github.prakashchokalingam.smsgooglesheetbridge.net.SheetUploader
import io.github.prakashchokalingam.smsgooglesheetbridge.redact.RedactionEngine
import io.github.prakashchokalingam.smsgooglesheetbridge.sms.Backfill
import io.github.prakashchokalingam.smsgooglesheetbridge.sms.ContactResolver
import io.github.prakashchokalingam.smsgooglesheetbridge.sms.SmsIngest

class AppContainer(context: Context) {
    val db = SmsBridgeDb(context)
    val prefs = Prefs(context)
    val messages = MessageStore(db)
    val rules = RuleStore(db)
    val contacts = ContactResolver(context)
    val engine = RedactionEngine()
    val ingest = SmsIngest(messages, rules, prefs, contacts, engine)
    val uploader = SheetUploader(prefs)
    val backfill = Backfill(context, ingest)
}
