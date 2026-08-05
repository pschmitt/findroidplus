package dev.pschmitt.jellyfin

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tools.fastlane.screengrab.Screengrab
import tools.fastlane.screengrab.locale.LocaleTestRule

/**
 * Captures Play Store listing screenshots (en-US only, see fastlane/Screengrabfile) against the
 * disposable, pre-baked Jellyfin fixture (`ci/jellyfin/docker-compose.yml`, see
 * `ci/jellyfin/README.md`) - a Home screen and a movie detail screen, light and dark. Modeled on
 * the sibling `netbox-and-chill` project's own `StoreScreenshotTest`. Never point this test at a
 * real Jellyfin server - the screenshots it produces show whatever library the connected server
 * has.
 *
 * Scope is intentionally narrow for now, per direct instruction: Jellyfin only, Home + one movie
 * detail screen, no PVR/Sonarr/Radarr/Seerr screens and no attempt to also cover `app/tv`.
 */
@RunWith(AndroidJUnit4::class)
class StoreScreenshotTest {
    companion object {
        @get:ClassRule @JvmStatic val localeTestRule = LocaleTestRule()

        // The fixture's baked admin account (see ci/jellyfin/README.md) - fixed, disposable
        // credentials, not a secret.
        private const val FIXTURE_USERNAME = "admin"
        private const val FIXTURE_PASSWORD = "adminpass123"
        private const val DEFAULT_BASE_URL = "http://127.0.0.1:8096"
    }

    @get:Rule val anrDismissRule = AnrDismissRule()

    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    private val device: UiDevice
        get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    private val arguments
        get() = InstrumentationRegistry.getArguments()

    @Test
    fun captureStoreScreenshots() {
        try {
            val baseUrl = arguments.getString("e2e_base_url") ?: DEFAULT_BASE_URL
            val username = arguments.getString("e2e_username") ?: FIXTURE_USERNAME
            val password = arguments.getString("e2e_password") ?: FIXTURE_PASSWORD

            connectAndLogIn(baseUrl, username, password)
            captureJourney(suffix = "")

            // captureJourney("") always ends back on Home; switch the color scheme there for
            // real, through the same Settings UI a user would use, then repeat the journey with a
            // "_dark" suffix so the store listing gets both variants from one test run.
            switchToDarkModeAndReturnToHome()
            captureJourney(suffix = "_dark")
        } catch (t: Throwable) {
            // The emulator is gone by the time a later CI step could pull a screencap/logcat -
            // android-emulator-runner tears it down synchronously as part of its own failed step.
            // screengrab itself skips pulling any Screengrab.screenshot() captures at all once the
            // test class reports a failure - captureE2eScreenshot writes straight to the app's
            // external files dir instead, which the workflow can adb pull independently.
            captureE2eScreenshot("FAILURE_debug")
            throw t
        }
    }

    /**
     * Walks the full first-run setup flow: Welcome -> Servers (empty, "Add server" FAB) ->
     * AddServer -> Users (empty, "Add user" FAB) -> Login -> Home. There is no way to skip straight
     * to AddServer/Login - a fresh install always starts with no servers and no users saved locally
     * (see NavigationRoot.kt's WelcomeRoute/ServersRoute/UsersRoute wiring).
     */
    private fun connectAndLogIn(baseUrl: String, username: String, password: String) {
        clickWithRetry { composeRule.onNodeWithText("Continue") }
        clickWithRetry { composeRule.onNodeWithText("Add server") }

        composeRule.onNodeWithTag("e2e-server-url").performTextInput(baseUrl)
        clickWithRetry { composeRule.onNodeWithTag("e2e-connect-button") }

        waitForText("Add user", 30_000)
        clickWithRetry { composeRule.onNodeWithText("Add user") }

        waitForTag("e2e-username", 30_000)
        composeRule.onNodeWithTag("e2e-username").performTextInput(username)
        composeRule.onNodeWithTag("e2e-password").performTextInput(password)
        clickWithRetry { composeRule.onNodeWithTag("e2e-login-button") }

        waitForHomeLoaded()
    }

    private fun switchToDarkModeAndReturnToHome() {
        clickWithRetry { composeRule.onNodeWithTag("e2e-settings-button") }
        waitForText("Appearance", 30_000)
        clickWithRetry { composeRule.onNodeWithText("Appearance") }

        waitForText("Theme", 30_000)
        clickWithRetry { composeRule.onNodeWithText("Theme").performScrollTo() }

        waitForText("Dark", 30_000)
        clickWithRetry { composeRule.onNodeWithText("Dark") }

        // SettingsScreen's UpdateTheme event applies immediately (UiModeManager/
        // AppCompatDelegate), no activity restart needed - two back presses unwind the two nested
        // Settings destinations pushed above (root Settings, then the Appearance category) back
        // to Home.
        device.pressBack()
        device.pressBack()
        waitForHomeLoaded()
    }

    private fun captureJourney(suffix: String) {
        captureScreenshot("01_home$suffix")

        clickWithRetry { composeRule.onAllNodesWithTag("e2e-item-card").onFirst() }
        waitForContentDescription("Play", 30_000)
        waitForTag("e2e-movie-title", 30_000)
        captureScreenshot("02_movie_detail$suffix")

        device.pressBack()
        waitForHomeLoaded()
    }

    /**
     * Home has real data once at least one item card has rendered - section titles are
     * data-dependent (library name, whether a resume point exists), so a fixed title/loading text
     * isn't a reliable wait target here.
     */
    private fun waitForHomeLoaded() {
        waitForTag("e2e-home-screen", 30_000)
        waitForTag("e2e-item-card", 60_000)
    }

    private fun captureScreenshot(name: String) {
        Screengrab.screenshot(name)
    }

    /**
     * `AssertionError: Failed to inject touch input` showed up twice in real CI runs, both times on
     * this test's very first click (right after MainActivity's window is created) - neither
     * android-emulator-runner's own emulator-ready wait nor this repo's `ci/android-e2e-wait.sh`
     * (full `boot_completed`/`device_provisioned`/package-service readiness) eliminated it, which
     * points to a transient window-focus race rather than something a longer upfront wait reliably
     * avoids. Retry the click itself instead - re-querying the node each attempt, since a
     * `SemanticsNodeInteraction` can go stale across recompositions.
     */
    private fun clickWithRetry(attempts: Int = 5, node: () -> SemanticsNodeInteraction) {
        repeat(attempts - 1) {
            try {
                node().performClick()
                return
            } catch (e: AssertionError) {
                Thread.sleep(1_000)
            }
        }
        node().performClick()
    }

    private fun waitForText(text: String, timeoutMillis: Long) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule
                .onAllNodesWithText(text, substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private fun waitForContentDescription(description: String, timeoutMillis: Long) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule
                .onAllNodesWithContentDescription(description, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private fun waitForTag(tag: String, timeoutMillis: Long) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
