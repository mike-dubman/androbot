package com.androbot.app

sealed interface TrustedSenderCommand {
    data class Add(val sender: String) : TrustedSenderCommand
    data class Remove(val sender: String) : TrustedSenderCommand
    data object ListSenders : TrustedSenderCommand

    companion object {
        private val ADD_REGEX = Regex("^trusted\\s+add\\s+(.+)$", RegexOption.IGNORE_CASE)
        private val REMOVE_REGEX = Regex("^trusted\\s+remove\\s+(.+)$", RegexOption.IGNORE_CASE)
        private val LIST_REGEX = Regex("^trusted\\s+list$", RegexOption.IGNORE_CASE)

        fun parse(raw: String): TrustedSenderCommand? {
            val command = raw.trim()
            if (LIST_REGEX.matches(command)) {
                return ListSenders
            }

            val addMatch = ADD_REGEX.matchEntire(command)
            if (addMatch != null) {
                return Add(addMatch.groupValues[1].trim())
            }

            val removeMatch = REMOVE_REGEX.matchEntire(command)
            if (removeMatch != null) {
                return Remove(removeMatch.groupValues[1].trim())
            }

            return null
        }
    }
}
