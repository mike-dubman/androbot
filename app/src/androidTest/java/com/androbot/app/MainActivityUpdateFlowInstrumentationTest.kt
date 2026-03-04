package com.androbot.app

import android.Manifest
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.Matcher
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.rule.GrantPermissionRule

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
        MainActivity.updaterFactory = {
            FakeUpdater(
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
        }

        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.checkUpdateButton)).perform(forceClick())
            onView(withText("Update available")).check(matches(isDisplayed()))
            onView(withText("Version 9.9.9 is available.\n\nInstall update now?"))
                .check(matches(isDisplayed()))
        }
    }

    @Test
    fun checkUpdate_whenNoUpdate_showsNoUpdatesDialog() {
        MainActivity.skipPermissionRequestForTests = true
        MainActivity.updaterFactory = { FakeUpdater(AppUpdater.CheckResult.UpToDate) }

        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.checkUpdateButton)).perform(forceClick())
            onView(withText("No updates")).check(matches(isDisplayed()))
            onView(withText("Already on latest version")).check(matches(isDisplayed()))
        }
    }

    private fun forceClick(): ViewAction {
        return object : ViewAction {
            override fun getConstraints(): Matcher<View> = isAssignableFrom(View::class.java)

            override fun getDescription(): String = "force click via view.performClick()"

            override fun perform(uiController: UiController, view: View) {
                view.performClick()
                uiController.loopMainThreadUntilIdle()
            }
        }
    }

    private class FakeUpdater(
        private val result: AppUpdater.CheckResult
    ) : Updater {
        override fun checkForUpdate(onResult: (AppUpdater.CheckResult) -> Unit) {
            onResult(result)
        }

        override fun downloadAndInstall(
            metadata: AppUpdater.UpdateMetadata,
            onStatus: (String) -> Unit
        ) = Unit

        override fun cleanup() = Unit
    }
}
