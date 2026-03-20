package com.androbot.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat

class SmsSender(private val context: Context) {

    fun sendSms(targetSender: String, message: String, operation: String): Boolean {
        if (targetSender.isBlank()) {
            Log.i(TAG, "Skipping $operation: missing target sender")
            return false
        }

        if (!hasSendSmsPermission()) {
            Log.i(TAG, "Skipping $operation: missing SEND_SMS permission")
            return false
        }

        return try {
            val smsManager = resolveSmsManager()
            val parts = smsManager.divideMessage(message)
            if (parts.size <= 1) {
                smsManager.sendTextMessage(targetSender, null, message, null, null)
            } else {
                smsManager.sendMultipartTextMessage(targetSender, null, parts, null, null)
            }
            Log.i(TAG, "$operation succeeded")
            true
        } catch (e: SecurityException) {
            Log.i(TAG, "$operation blocked by security policy")
            false
        } catch (e: Exception) {
            Log.i(TAG, "$operation failed: ${e.javaClass.simpleName}")
            false
        }
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
        private const val TAG = "SmsSender"
    }
}
