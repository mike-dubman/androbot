package com.androbot.app

import android.content.Context

class TrustedSenderPolicy(context: Context) {

    private val store = TrustedSenderStore(context)

    fun isTrusted(sender: String): Boolean {
        val normalizedSender = PhoneNumberNormalizer.normalize(sender)
        return trustedSenders().any { it == normalizedSender }
    }

    fun trustedSenders(): List<String> = store.getAll()

    fun addTrustedSender(sender: String): Boolean = store.add(sender)

    fun removeTrustedSender(sender: String): Boolean = store.remove(sender)
}
