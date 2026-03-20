package com.androbot.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrustedSenderCommandTest {

    @Test
    fun parseAddCommand() {
        assertEquals(
            TrustedSenderCommand.Add("+15551234567"),
            TrustedSenderCommand.parse("trusted add +15551234567")
        )
    }

    @Test
    fun parseRemoveCommandCaseInsensitive() {
        assertEquals(
            TrustedSenderCommand.Remove("15551234567"),
            TrustedSenderCommand.parse("TrUsTeD ReMoVe 15551234567")
        )
    }

    @Test
    fun parseListCommand() {
        assertEquals(
            TrustedSenderCommand.ListSenders,
            TrustedSenderCommand.parse("trusted list")
        )
    }

    @Test
    fun parseSmsForwarderCommands() {
        assertEquals(
            TrustedSenderCommand.SmsForwarderOn,
            TrustedSenderCommand.parse("sms forwarder on")
        )
        assertEquals(
            TrustedSenderCommand.SmsForwarderOff,
            TrustedSenderCommand.parse("Sms Forwarder Off")
        )
    }

    @Test
    fun rejectUnknownCommand() {
        assertNull(TrustedSenderCommand.parse("trusted replace +15550001111"))
        assertNull(TrustedSenderCommand.parse("sms forwarder maybe"))
    }
}
