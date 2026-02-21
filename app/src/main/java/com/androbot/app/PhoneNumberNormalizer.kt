package com.androbot.app

object PhoneNumberNormalizer {
    fun normalize(value: String): String {
        val trimmed = value.trim()
        return if (trimmed.startsWith("+")) {
            "+" + trimmed.drop(1).filter { it.isDigit() }
        } else {
            trimmed.filter { it.isDigit() }
        }
    }
}
