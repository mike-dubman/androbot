package com.androbot.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdaterVersionNameTest {

    @Test
    fun remotePatchIsNewer() {
        assertTrue(AppUpdater.isVersionNameNewer("0.2.6", "0.2.5"))
    }

    @Test
    fun equalVersionIsNotNewer() {
        assertFalse(AppUpdater.isVersionNameNewer("0.2.6", "0.2.6"))
    }

    @Test
    fun remoteLowerVersionIsNotNewer() {
        assertFalse(AppUpdater.isVersionNameNewer("0.2.5", "0.2.6"))
    }

    @Test
    fun supportsDifferentSegmentCounts() {
        assertTrue(AppUpdater.isVersionNameNewer("1.2.0", "1.1"))
    }

    @Test
    fun rejectsNonNumericVersionNames() {
        assertFalse(AppUpdater.isVersionNameNewer("v0.2.6", "0.2.5"))
        assertFalse(AppUpdater.isVersionNameNewer("0.2.6", "dev-build"))
    }
}
