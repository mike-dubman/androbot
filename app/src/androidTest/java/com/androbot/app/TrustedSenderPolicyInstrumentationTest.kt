package com.androbot.app

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrustedSenderPolicyInstrumentationTest {

    private lateinit var context: Context
    private lateinit var policy: TrustedSenderPolicy

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("androbot_trusted_senders", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        policy = TrustedSenderPolicy(context)
    }

    @Test
    fun addAndListTrustedSenders_normalizesAndDeduplicates() {
        assertTrue(policy.addTrustedSender("+1 (555) 123-4567"))
        assertTrue(policy.addTrustedSender("15550000000"))
        assertFalse(policy.addTrustedSender(" 15551234567 "))

        assertEquals(listOf("15550000000", "15551234567"), policy.trustedSenders())
    }

    @Test
    fun removeTrustedSender_updatesList() {
        policy.addTrustedSender("+1 (555) 123-4567")
        policy.addTrustedSender("15550000000")

        assertTrue(policy.removeTrustedSender("(555) 123-4567"))
        assertFalse(policy.removeTrustedSender("19998887777"))

        assertEquals(listOf("15550000000"), policy.trustedSenders())
    }

    @Test
    fun listTrustedSenders_emptyByDefault() {
        assertEquals(emptyList<String>(), policy.trustedSenders())
    }
}
