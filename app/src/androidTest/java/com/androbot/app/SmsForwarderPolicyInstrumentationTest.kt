package com.androbot.app

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmsForwarderPolicyInstrumentationTest {

    private lateinit var context: Context
    private lateinit var policy: SmsForwarderPolicy

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("androbot_sms_forwarder", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        policy = SmsForwarderPolicy(context)
    }

    @Test
    fun stateDisabledByDefault() {
        val state = policy.state()

        assertFalse(state.enabled)
        assertNull(state.targetSender)
    }

    @Test
    fun enablePersistsNormalizedTarget() {
        policy.enableForSender("+1 (555) 123-4567")

        assertEquals(SmsForwarderState(true, "15551234567"), policy.state())
    }

    @Test
    fun disableClearsForwarderState() {
        policy.enableForSender("+1 (555) 123-4567")

        policy.disable()

        assertEquals(SmsForwarderState(false, null), policy.state())
    }
}
