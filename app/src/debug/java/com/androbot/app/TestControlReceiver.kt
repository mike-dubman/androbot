package com.androbot.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.util.Log
import kotlin.math.roundToInt

class TestControlReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        when (action) {
            ACTION_ADD_TRUSTED -> {
                val sender = intent.getStringExtra(EXTRA_SENDER).orEmpty()
                val added = TrustedSenderPolicy(context).addTrustedSender(sender)
                Log.i(TAG, "ADD_TRUSTED sender=$sender added=$added")
            }

            ACTION_SET_VOLUME_PERCENT -> {
                val percent = intent.getIntExtra(EXTRA_PERCENT, 0).coerceIn(0, 100)
                val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val callMax = audio.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
                val ringMax = audio.getStreamMaxVolume(AudioManager.STREAM_RING)
                val call = ((percent / 100.0) * callMax).roundToInt()
                val ring = ((percent / 100.0) * ringMax).roundToInt()
                audio.setStreamVolume(AudioManager.STREAM_VOICE_CALL, call, 0)
                audio.setStreamVolume(AudioManager.STREAM_RING, ring, 0)
                Log.i(TAG, "SET_VOLUME_PERCENT percent=$percent call=$call/$callMax ring=$ring/$ringMax")
            }

            ACTION_LOG_VOLUME_SNAPSHOT -> {
                val label = intent.getStringExtra(EXTRA_LABEL).orEmpty()
                val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val callCurrent = audio.getStreamVolume(AudioManager.STREAM_VOICE_CALL)
                val callMax = audio.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
                val ringCurrent = audio.getStreamVolume(AudioManager.STREAM_RING)
                val ringMax = audio.getStreamMaxVolume(AudioManager.STREAM_RING)
                Log.i(
                    TAG,
                    "SNAPSHOT label=$label call=$callCurrent/$callMax ring=$ringCurrent/$ringMax"
                )
            }
        }
    }

    companion object {
        private const val TAG = "TestControlReceiver"

        private const val ACTION_ADD_TRUSTED = "com.androbot.app.TEST_ADD_TRUSTED"
        private const val ACTION_SET_VOLUME_PERCENT = "com.androbot.app.TEST_SET_VOLUME_PERCENT"
        private const val ACTION_LOG_VOLUME_SNAPSHOT = "com.androbot.app.TEST_LOG_VOLUME_SNAPSHOT"

        private const val EXTRA_SENDER = "sender"
        private const val EXTRA_PERCENT = "percent"
        private const val EXTRA_LABEL = "label"
    }
}
