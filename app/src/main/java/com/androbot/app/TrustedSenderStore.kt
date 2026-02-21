package com.androbot.app

import android.content.Context

class TrustedSenderStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        migrateIfNeeded()
    }

    fun getAll(): List<String> {
        return (prefs.getStringSet(KEY_SENDERS, emptySet()) ?: emptySet())
            .map(PhoneNumberNormalizer::normalize)
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }

    fun add(sender: String): Boolean {
        val normalized = PhoneNumberNormalizer.normalize(sender)
        if (normalized.isBlank()) return false

        val current = (prefs.getStringSet(KEY_SENDERS, emptySet()) ?: emptySet()).toMutableSet()
        val added = current.add(normalized)
        if (added) {
            prefs.edit().putStringSet(KEY_SENDERS, current).apply()
        }
        return added
    }

    fun remove(sender: String): Boolean {
        val normalized = PhoneNumberNormalizer.normalize(sender)
        if (normalized.isBlank()) return false

        val current = (prefs.getStringSet(KEY_SENDERS, emptySet()) ?: emptySet()).toMutableSet()
        val removed = current.remove(normalized)
        if (removed) {
            prefs.edit().putStringSet(KEY_SENDERS, current).apply()
        }
        return removed
    }

    private fun migrateIfNeeded() {
        val storedVersion = prefs.getInt(KEY_STORAGE_VERSION, 0)
        if (storedVersion >= CURRENT_STORAGE_VERSION) {
            return
        }

        // Migration 1: normalize and deduplicate existing senders without resetting the list.
        val migratedSenders = (prefs.getStringSet(KEY_SENDERS, emptySet()) ?: emptySet())
            .map(PhoneNumberNormalizer::normalize)
            .filter { it.isNotBlank() }
            .toSet()

        prefs.edit()
            .putStringSet(KEY_SENDERS, migratedSenders)
            .putInt(KEY_STORAGE_VERSION, CURRENT_STORAGE_VERSION)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "androbot_trusted_senders"
        private const val KEY_SENDERS = "senders"
        private const val KEY_STORAGE_VERSION = "storage_version"
        private const val CURRENT_STORAGE_VERSION = 1
    }
}
