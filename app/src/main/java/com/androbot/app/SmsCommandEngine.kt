package com.androbot.app

import android.content.Context
import android.media.AudioManager
import android.util.Log
import kotlin.math.roundToInt

class SmsCommandEngine(private val context: Context) {

    enum class Result {
        EXECUTED,
        IGNORED
    }

    fun execute(rawCommand: String): Result {
        val command = parseCommand(rawCommand) ?: return Result.IGNORED
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

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
        }

        return Result.EXECUTED
    }

    sealed interface Command {
        data object VolumeMax : Command
        data object VolumeMin : Command
        data class VolumePercent(val percent: Int) : Command
    }

    private fun safeSetVolume(audioManager: AudioManager, stream: Int, value: Int) {
        try {
            audioManager.setStreamVolume(stream, value, 0)
        } catch (e: SecurityException) {
            Log.i(TAG, "Skipping stream=$stream volume change due to security policy")
        }
    }

    companion object {
        private const val TAG = "SmsCommandEngine"
        private val PERCENT_REGEX = Regex("^volume\\s+(\\d{1,3})$")

        fun parseCommand(rawCommand: String): Command? {
            val normalized = rawCommand.trim().lowercase()

            if (normalized == "volume max") {
                return Command.VolumeMax
            }
            if (normalized == "volume min") {
                return Command.VolumeMin
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
