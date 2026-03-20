package com.androbot.app

import android.content.Context
import android.util.Log

class SmsForwarder(private val context: Context) {

    private val policy = SmsForwarderPolicy(context)
    private val smsSender = SmsSender(context)
    private val trustedSenderPolicy = TrustedSenderPolicy(context)

    fun forwardIncomingSms(sender: String, body: String): Boolean {
        val state = policy.state()
        if (!state.enabled) {
            return false
        }

        val targetSender = state.targetSender
        if (targetSender.isNullOrBlank()) {
            Log.i(TAG, "Skipping SMS forward: missing target sender while enabled")
            return false
        }

        if (!trustedSenderPolicy.isTrusted(targetSender)) {
            Log.i(TAG, "Disabling SMS forwarder: target sender is no longer trusted")
            policy.disable()
            return false
        }

        val payload = buildPayload(sender, body)
        return smsSender.sendSms(
            targetSender = targetSender,
            message = payload,
            operation = "SMS forward from $sender to $targetSender"
        )
    }

    private fun buildPayload(sender: String, body: String): String {
        val senderLabel = sender.trim().ifBlank {
            PhoneNumberNormalizer.normalize(sender).ifBlank { "unknown" }
        }
        return "SMS from $senderLabel: $body"
    }
    companion object {
        private const val TAG = "SmsForwarder"
    }
}
