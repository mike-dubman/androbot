package com.androbot.app

import android.content.Context

class SmsCommandAuditStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun record(sender: String, body: String, result: SmsCommandEngine.Result) {
        prefs.edit()
            .putString(KEY_SENDER, sender)
            .putString(KEY_BODY, body)
            .putString(KEY_RESULT, result.name)
            .putLong(KEY_TIMESTAMP, System.currentTimeMillis())
            .apply()
    }

    fun lastBody(): String = prefs.getString(KEY_BODY, "").orEmpty()

    fun lastResult(): String = prefs.getString(KEY_RESULT, "").orEmpty()

    fun lastTimestamp(): Long = prefs.getLong(KEY_TIMESTAMP, 0L)

    companion object {
        private const val PREFS_NAME = "androbot_sms_audit"
        private const val KEY_SENDER = "last_sender"
        private const val KEY_BODY = "last_body"
        private const val KEY_RESULT = "last_result"
        private const val KEY_TIMESTAMP = "last_timestamp"
    }
}
