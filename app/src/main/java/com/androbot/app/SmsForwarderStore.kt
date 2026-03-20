package com.androbot.app

import android.content.Context

data class SmsForwarderState(
    val enabled: Boolean,
    val targetSender: String?
)

class SmsForwarderStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getState(): SmsForwarderState {
        val enabled = prefs.getBoolean(KEY_ENABLED, false)
        val targetSender = prefs.getString(KEY_TARGET_SENDER, null)
            ?.let(PhoneNumberNormalizer::normalize)
            ?.ifBlank { null }
        return SmsForwarderState(enabled = enabled, targetSender = targetSender)
    }

    fun enableForSender(sender: String) {
        val normalized = PhoneNumberNormalizer.normalize(sender)
        if (normalized.isBlank()) {
            return
        }

        prefs.edit()
            .putBoolean(KEY_ENABLED, true)
            .putString(KEY_TARGET_SENDER, normalized)
            .apply()
    }

    fun disable() {
        prefs.edit()
            .putBoolean(KEY_ENABLED, false)
            .remove(KEY_TARGET_SENDER)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "androbot_sms_forwarder"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_TARGET_SENDER = "target_sender"
    }
}
