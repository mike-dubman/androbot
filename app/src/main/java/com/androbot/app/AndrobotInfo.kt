package com.androbot.app

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

object AndrobotInfo {

    val supportedCommands: List<String> = listOf(
        "volume max",
        "volume min",
        "volume <0-100>",
        "wifi reset",
        "wifi on",
        "wifi off",
        "data reset",
        "data on",
        "data off",
        "call me back",
        "update software",
        "info"
    )

    fun versionLabel(context: Context): String {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        val name = info.versionName ?: "unknown"
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
        return "v$name ($code)"
    }

    fun smsInfoMessage(context: Context): String {
        val commands = supportedCommands.joinToString(", ")
        return "Androbot ${versionLabel(context)}\nSupported commands: $commands"
    }
}
