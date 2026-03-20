package com.androbot.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat

class SmsForwarder(private val context: Context) {

    private val policy = SmsForwarderPolicy(context)
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

        if (!hasSendSmsPermission()) {
            Log.i(TAG, "Skipping SMS forward: missing SEND_SMS permission")
            return false
        }

        val payload = buildPayload(sender, body)
        return try {
            val smsManager = resolveSmsManager()
            val parts = smsManager.divideMessage(payload)
            if (parts.size <= 1) {
                smsManager.sendTextMessage(targetSender, null, payload, null, null)
            } else {
                smsManager.sendMultipartTextMessage(targetSender, null, parts, null, null)
            }
            Log.i(TAG, "Forwarded SMS from $sender to $targetSender")
            true
        } catch (e: SecurityException) {
            Log.i(TAG, "SMS forward blocked by security policy")
            false
        } catch (e: Exception) {
            Log.i(TAG, "SMS forward failed: ${e.javaClass.simpleName}")
            false
        }
    }

    private fun buildPayload(sender: String, body: String): String {
        val senderLabel = sender.trim().ifBlank {
            PhoneNumberNormalizer.normalize(sender).ifBlank { "unknown" }
        }
        return "SMS from $senderLabel: $body"
    }

    private fun hasSendSmsPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) ==
            PackageManager.PERMISSION_GRANTED

    private fun resolveSmsManager(): SmsManager {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)?.let { return it }
        }
        @Suppress("DEPRECATION")
        return SmsManager.getDefault()
    }

    companion object {
        private const val TAG = "SmsForwarder"
    }
}
