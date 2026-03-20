package com.androbot.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log

class SmsCommandReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }

        val policy = TrustedSenderPolicy(context)
        val forwarderPolicy = SmsForwarderPolicy(context)
        val forwarder = SmsForwarder(context)
        val engine = SmsCommandEngine(context)

        Telephony.Sms.Intents.getMessagesFromIntent(intent).forEach { smsMessage ->
            val sender = smsMessage.displayOriginatingAddress ?: return@forEach
            val body = smsMessage.messageBody ?: return@forEach
            val actionCommand = SmsCommandEngine.parseCommand(body)

            if (policy.trustedSenders().isEmpty()) {
                Log.i(TAG, "Ignoring SMS: no trusted senders configured yet. Add first sender in app UI.")
                return@forEach
            }

            val trustedCommand = TrustedSenderCommand.parse(body)
            if (trustedCommand != null) {
                if (!policy.isTrusted(sender)) {
                    Log.i(TAG, "Ignoring trusted-sender command from untrusted sender: $sender")
                    return@forEach
                }

                handleTrustedCommand(policy, forwarderPolicy, trustedCommand, sender)
                return@forEach
            }

            if (actionCommand != null) {
                if (!policy.isTrusted(sender)) {
                    Log.i(TAG, "Ignoring SMS action command from untrusted sender: $sender")
                    return@forEach
                }

                val result = engine.execute(body, sender)
                Log.i(TAG, "Command from $sender -> ${result.name}")
                return@forEach
            }

            val forwarded = forwarder.forwardIncomingSms(sender, body)
            val trustLabel = if (policy.isTrusted(sender)) "trusted" else "untrusted"
            Log.i(TAG, "Regular SMS from $trustLabel sender $sender -> forwarded=$forwarded")
        }
    }

    private fun handleTrustedCommand(
        policy: TrustedSenderPolicy,
        forwarderPolicy: SmsForwarderPolicy,
        command: TrustedSenderCommand,
        sender: String
    ) {
        when (command) {
            is TrustedSenderCommand.Add -> {
                val added = policy.addTrustedSender(command.sender)
                Log.i(TAG, "Trusted sender add requested by $sender -> ${command.sender} (added=$added)")
            }

            is TrustedSenderCommand.Remove -> {
                val removed = policy.removeTrustedSender(command.sender)
                Log.i(TAG, "Trusted sender remove requested by $sender -> ${command.sender} (removed=$removed)")
            }

            is TrustedSenderCommand.ListSenders -> {
                val senders = policy.trustedSenders().joinToString(",")
                Log.i(TAG, "Trusted sender list requested by $sender -> $senders")
            }

            is TrustedSenderCommand.SmsForwarderOn -> {
                forwarderPolicy.enableForSender(sender)
                Log.i(TAG, "SMS forwarder enabled by $sender")
            }

            is TrustedSenderCommand.SmsForwarderOff -> {
                forwarderPolicy.disable()
                Log.i(TAG, "SMS forwarder disabled by $sender")
            }
        }
    }

    companion object {
        private const val TAG = "SmsCommandReceiver"
    }
}
