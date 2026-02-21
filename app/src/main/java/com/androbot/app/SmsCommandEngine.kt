package com.androbot.app

import android.content.Context
import android.media.AudioManager
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
                audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, callMax, 0)
                audioManager.setStreamVolume(AudioManager.STREAM_RING, ringMax, 0)
            }

            is Command.VolumeMin -> {
                audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, 0, 0)
                audioManager.setStreamVolume(AudioManager.STREAM_RING, 0, 0)
            }

            is Command.VolumePercent -> {
                val callMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
                val ringMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING)
                val callValue = ((command.percent / 100.0) * callMax).roundToInt()
                val ringValue = ((command.percent / 100.0) * ringMax).roundToInt()
                audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, callValue, 0)
                audioManager.setStreamVolume(AudioManager.STREAM_RING, ringValue, 0)
            }
        }

        return Result.EXECUTED
    }

    sealed interface Command {
        data object VolumeMax : Command
        data object VolumeMin : Command
        data class VolumePercent(val percent: Int) : Command
    }

    companion object {
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
