package com.androbot.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SmsCommandEngineTest {

    @Test
    fun parseVolumeMax() {
        assertEquals(SmsCommandEngine.Command.VolumeMax, SmsCommandEngine.parseCommand("volume max"))
    }

    @Test
    fun parseVolumePercent() {
        assertEquals(SmsCommandEngine.Command.VolumePercent(75), SmsCommandEngine.parseCommand("volume 75"))
    }

    @Test
    fun parseCallMeBack() {
        assertEquals(SmsCommandEngine.Command.CallMeBack, SmsCommandEngine.parseCommand("call me back"))
    }

    @Test
    fun rejectUnknownCommand() {
        assertNull(SmsCommandEngine.parseCommand("reboot now"))
    }

    @Test
    fun rejectOutOfRangePercent() {
        assertNull(SmsCommandEngine.parseCommand("volume 101"))
    }
}
