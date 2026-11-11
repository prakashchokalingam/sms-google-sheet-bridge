package dev.hark.smsbridge

import android.content.Context
import dev.hark.smsbridge.data.HarkDb
import dev.hark.smsbridge.data.MessageStore
import dev.hark.smsbridge.data.Prefs
import dev.hark.smsbridge.data.RuleStore
import dev.hark.smsbridge.net.SheetUploader
import dev.hark.smsbridge.redact.RedactionEngine
import dev.hark.smsbridge.sms.Backfill
import dev.hark.smsbridge.sms.ContactResolver
import dev.hark.smsbridge.sms.SmsIngest

class AppContainer(context: Context) {
    val db = HarkDb(context)
    val prefs = Prefs(context)
    val messages = MessageStore(db)
    val rules = RuleStore(db)
    val contacts = ContactResolver(context)
    val engine = RedactionEngine()
    val ingest = SmsIngest(messages, rules, prefs, contacts, engine)
    val uploader = SheetUploader(prefs)
    val backfill = Backfill(context, ingest)
}
