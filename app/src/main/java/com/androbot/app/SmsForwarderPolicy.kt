package com.androbot.app

import android.content.Context

class SmsForwarderPolicy(context: Context) {

    private val store = SmsForwarderStore(context)

    fun state(): SmsForwarderState = store.getState()

    fun enableForSender(sender: String) {
        store.enableForSender(sender)
    }

    fun disable() {
        store.disable()
    }
}
