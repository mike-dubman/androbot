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
    fun parseWifiCommands() {
        assertEquals(SmsCommandEngine.Command.WifiReset, SmsCommandEngine.parseCommand("wifi reset"))
        assertEquals(SmsCommandEngine.Command.WifiOn, SmsCommandEngine.parseCommand("wifi on"))
        assertEquals(SmsCommandEngine.Command.WifiOff, SmsCommandEngine.parseCommand("wifi off"))
    }

    @Test
    fun parseDataCommands() {
        assertEquals(SmsCommandEngine.Command.DataReset, SmsCommandEngine.parseCommand("data reset"))
        assertEquals(SmsCommandEngine.Command.DataOn, SmsCommandEngine.parseCommand("data on"))
        assertEquals(SmsCommandEngine.Command.DataOff, SmsCommandEngine.parseCommand("data off"))
    }

    @Test
    fun parseUpdateSoftwareCommand() {
        assertEquals(
            SmsCommandEngine.Command.UpdateSoftware,
            SmsCommandEngine.parseCommand("update software")
        )
    }

    @Test
    fun rejectUnknownCommand() {
        assertNull(SmsCommandEngine.parseCommand("reboot now"))
    }

    @Test
    fun rejectOutOfRangePercent() {
        assertNull(SmsCommandEngine.parseCommand("volume 101"))
    }

    @Test
    fun rejectAliasesAndVariantSpacing() {
        assertNull(SmsCommandEngine.parseCommand("mobile data on"))
        assertNull(SmsCommandEngine.parseCommand("wifi  on"))
    }
}
