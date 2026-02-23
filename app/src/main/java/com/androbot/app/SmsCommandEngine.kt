package com.androbot.app

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telecom.TelecomManager
import android.telephony.PhoneNumberUtils
import android.util.Log
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import kotlin.math.roundToInt

class SmsCommandEngine(private val context: Context) {

    enum class Result {
        EXECUTED,
        IGNORED
    }

    fun execute(rawCommand: String, sender: String? = null): Result {
        val command = parseCommand(rawCommand) ?: return Result.IGNORED
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        var executed = true

        when (command) {
            is Command.VolumeMax -> {
                val callMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
                val ringMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING)
                val mediaMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                safeSetVolume(audioManager, AudioManager.STREAM_VOICE_CALL, callMax)
                safeSetVolume(audioManager, AudioManager.STREAM_RING, ringMax)
                safeSetVolume(audioManager, AudioManager.STREAM_MUSIC, mediaMax)
            }

            is Command.VolumeMin -> {
                safeSetVolume(audioManager, AudioManager.STREAM_VOICE_CALL, 0)
                safeSetVolume(audioManager, AudioManager.STREAM_RING, 0)
                safeSetVolume(audioManager, AudioManager.STREAM_MUSIC, 0)
            }

            is Command.VolumePercent -> {
                val callMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
                val ringMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING)
                val mediaMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val callValue = ((command.percent / 100.0) * callMax).roundToInt()
                val ringValue = ((command.percent / 100.0) * ringMax).roundToInt()
                val mediaValue = ((command.percent / 100.0) * mediaMax).roundToInt()
                safeSetVolume(audioManager, AudioManager.STREAM_VOICE_CALL, callValue)
                safeSetVolume(audioManager, AudioManager.STREAM_RING, ringValue)
                safeSetVolume(audioManager, AudioManager.STREAM_MUSIC, mediaValue)
            }

            is Command.CallMeBack -> {
                val target = sender?.trim().orEmpty()
                if (target.isBlank()) {
                    Log.i(TAG, "Skipping call me back: missing sender")
                    executed = false
                } else {
                    executed = placeCallMeBack(target, audioManager)
                }
            }
        }

        return if (executed) Result.EXECUTED else Result.IGNORED
    }

    sealed interface Command {
        data object VolumeMax : Command
        data object VolumeMin : Command
        data class VolumePercent(val percent: Int) : Command
        data object CallMeBack : Command
    }

    private fun safeSetVolume(audioManager: AudioManager, stream: Int, value: Int) {
        try {
            audioManager.setStreamVolume(stream, value, 0)
        } catch (e: SecurityException) {
            Log.i(TAG, "Skipping stream=$stream volume change due to security policy")
        }
    }

    private fun placeCallMeBack(sender: String, audioManager: AudioManager): Boolean {
        val normalized = PhoneNumberUtils.normalizeNumber(sender)
        if (normalized.isNullOrBlank()) {
            Log.i(TAG, "Skipping call me back: sender normalization failed for '$sender'")
            return false
        }

        Log.i(TAG, "Call me back requested by $normalized")
        if (!hasCallPhonePermission()) {
            Log.i(TAG, "Skipping call me back: missing CALL_PHONE permission")
            return false
        }

        val callUri = Uri.fromParts("tel", normalized, null)
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager

        if (telecomManager != null) {
            try {
                telecomManager.placeCall(callUri, Bundle())
                scheduleSpeakerOn(audioManager)
                Log.i(TAG, "Callback initiated via TelecomManager for $normalized")
                return true
            } catch (e: SecurityException) {
                Log.i(TAG, "TelecomManager callback blocked by security policy")
            } catch (e: Exception) {
                Log.i(TAG, "TelecomManager callback failed: ${e.javaClass.simpleName}")
            }
        }

        return try {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = callUri
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            scheduleSpeakerOn(audioManager)
            Log.i(TAG, "Callback initiated via ACTION_CALL for $normalized")
            true
        } catch (e: SecurityException) {
            Log.i(TAG, "ACTION_CALL blocked by security policy")
            false
        } catch (e: Exception) {
            Log.i(TAG, "ACTION_CALL failed: ${e.javaClass.simpleName}")
            false
        }
    }

    private fun scheduleSpeakerOn(audioManager: AudioManager) {
        val handler = Handler(Looper.getMainLooper())
        for (delayMs in SPEAKER_ENABLE_DELAYS_MS) {
            handler.postDelayed({
                try {
                    audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                    audioManager.isSpeakerphoneOn = true
                    Log.i(TAG, "Speaker enabled for callback")
                } catch (e: SecurityException) {
                    Log.i(TAG, "Speaker enable blocked by security policy")
                }
            }, delayMs)
        }
    }

    private fun hasCallPhonePermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) ==
            PackageManager.PERMISSION_GRANTED

    companion object {
        private const val TAG = "SmsCommandEngine"
        private val PERCENT_REGEX = Regex("^volume\\s+(\\d{1,3})$")
        private val SPEAKER_ENABLE_DELAYS_MS = listOf<Long>(0L, 700L, 1500L)

        fun parseCommand(rawCommand: String): Command? {
            val normalized = rawCommand.trim().lowercase()

            if (normalized == "volume max") {
                return Command.VolumeMax
            }
            if (normalized == "volume min") {
                return Command.VolumeMin
            }
            if (normalized == "call me back") {
                return Command.CallMeBack
            }

            val match = PERCENT_REGEX.matchEntire(normalized) ?: return null
            val percent = match.groupValues[1].toIntOrNull() ?: return null
            if (percent !in 0..100) {
                return null
            }
            return Command.VolumePercent(percent)
        }
    }
}
