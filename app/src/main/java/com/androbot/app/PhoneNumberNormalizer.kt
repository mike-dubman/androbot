package com.androbot.app

object PhoneNumberNormalizer {
    fun normalize(value: String): String {
        // Normalize to digits-only so "+1555123..." and "1555123..." match.
        return value.trim().filter { it.isDigit() }
    }
}
