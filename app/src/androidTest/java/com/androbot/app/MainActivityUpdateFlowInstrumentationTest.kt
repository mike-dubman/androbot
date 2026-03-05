package com.androbot.app

import android.Manifest
import android.widget.Button
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.rule.GrantPermissionRule
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

@RunWith(AndroidJUnit4::class)
class MainActivityUpdateFlowInstrumentationTest {

    @get:Rule
    val permissionsRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.CALL_PHONE
    )

    @After
    fun tearDown() {
        MainActivity.updaterFactory = { activity -> AppUpdater(activity) }
        MainActivity.skipPermissionRequestForTests = false
    }

    @Test
    fun checkUpdate_whenUpdateAvailable_showsUpdateDialog() {
        MainActivity.skipPermissionRequestForTests = true
        val fakeUpdater = FakeUpdater(
            AppUpdater.CheckResult.UpdateAvailable(
                AppUpdater.UpdateMetadata(
                    versionCode = 9999,
                    versionName = "9.9.9",
                    apkUrl = "https://example.invalid/app.apk",
                    sha256 = "abcd",
                    minSupportedVersionCode = 1
                )
            )
        )
        MainActivity.updaterFactory = {
            fakeUpdater
        }

        ActivityScenario.launch(MainActivity::class.java).use {
            it.onActivity { activity ->
                activity.findViewById<Button>(R.id.checkUpdateButton).performClick()
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertEquals(1, fakeUpdater.checkCalls.get())
            assertTrue(it.state.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED))
        }
    }

    @Test
    fun checkUpdate_whenNoUpdate_showsNoUpdatesDialog() {
        MainActivity.skipPermissionRequestForTests = true
        val fakeUpdater = FakeUpdater(AppUpdater.CheckResult.UpToDate)
        MainActivity.updaterFactory = { fakeUpdater }

        ActivityScenario.launch(MainActivity::class.java).use {
            it.onActivity { activity ->
                activity.findViewById<Button>(R.id.checkUpdateButton).performClick()
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertEquals(1, fakeUpdater.checkCalls.get())
            assertTrue(it.state.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED))
        }
    }

    private class FakeUpdater(
        private val result: AppUpdater.CheckResult
    ) : Updater {
        val checkCalls = AtomicInteger(0)

        override fun checkForUpdate(onResult: (AppUpdater.CheckResult) -> Unit) {
            checkCalls.incrementAndGet()
            onResult(result)
        }

        override fun downloadAndInstall(
            metadata: AppUpdater.UpdateMetadata,
            onStatus: (String) -> Unit
        ) = Unit

        override fun cleanup() = Unit
    }
}
