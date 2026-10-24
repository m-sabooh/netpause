package com.example.netpause

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MainActivityTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun tap(tag: String) = compose.onNodeWithTag(tag).performClick()
    private fun scrollTap(tag: String) = compose.onNodeWithTag(tag).performScrollTo().performClick()
    private fun waitFor(tag: String) {
        compose.waitUntil(8_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
    }
    private fun text(id: Int) = compose.activity.getString(id)

    @Test
    fun everyoneCanBePausedIndefinitelyAndResumed() {
        compose.onNodeWithText(text(R.string.everyone_online)).assertIsDisplayed()
        tap("pause_all")
        compose.onNodeWithTag("mode_indefinite").assertIsSelected()
        scrollTap("confirm_pause")
        waitFor("resume")
        compose.onNodeWithText(text(R.string.all_paused)).assertIsDisplayed()
        compose.onNodeWithTag("countdown").assertDoesNotExist()
        scrollTap("resume")
        waitFor("pause_all")
        compose.onNodeWithTag("device_phone").assertIsDisplayed()
    }

    @Test
    fun timedPauseValidatesMinutesCountsDownAndReplacesOnlyAfterConfirmation() {
        tap("device_tv")
        tap("device_pause")
        tap("preset_10")
        compose.onNodeWithTag("pause_minutes").assertTextEquals("10")
        compose.onNodeWithTag("pause_minutes").performTextReplacement("0")
        scrollTap("confirm_pause")
        compose.onNodeWithTag("minutes_error").assertExists()
        tap("preset_20")
        scrollTap("confirm_pause")
        waitFor("countdown")
        val before = compose.onNodeWithTag("countdown").fetchSemanticsNode().config[SemanticsProperties.Text].first().text
        compose.waitUntil(5_000) {
            compose.onNodeWithTag("countdown").fetchSemanticsNode().config[SemanticsProperties.Text].first().text != before
        }
        scrollTap("choose_target")
        scrollTap("device_phone")
        tap("device_pause")
        tap("mode_indefinite")
        scrollTap("confirm_pause")
        compose.onNodeWithTag("replace_pause").assertExists()
        scrollTap("keep_pause")
        waitFor("resume")
        compose.onNodeWithTag("countdown").assertExists()
        scrollTap("choose_target")
        scrollTap("device_phone")
        tap("device_pause")
        tap("mode_indefinite")
        scrollTap("confirm_pause")
        scrollTap("replace_pause")
        waitFor("resume")
        compose.onNodeWithTag("countdown").assertDoesNotExist()
        compose.onNodeWithText(compose.activity.getString(R.string.target_paused, text(R.string.this_phone))).assertIsDisplayed()
        scrollTap("choose_target")
        scrollTap("device_phone")
        tap("device_pause")
        scrollTap("confirm_pause")
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("close_sheet").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("replace_pause").assertDoesNotExist()
        compose.onNodeWithText(text(R.string.already_paused)).assertExists()
        scrollTap("resume")
        waitFor("pause_all")
    }

    @Test
    fun renameSurvivesActivityRecreation() {
        tap("device_tv")
        tap("rename")
        compose.onNodeWithTag("device_name").performTextReplacement("Family screen")
        scrollTap("save_name")
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("close_sheet").fetchSemanticsNodes().isEmpty() }
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Family screen").assertIsDisplayed()
    }

    @Test
    fun settingsSaveDefaultAndArabicMirrorsNavigationAcrossRecreation() {
        tap("settings")
        compose.onNodeWithTag("widget_minutes").performTextReplacement("10")
        scrollTap("save_default")
        compose.onNodeWithTag("default_saved").assertExists()
        scrollTap("language_picker")
        tap("language_ar")
        compose.onAllNodesWithText("الإعدادات").assertCountEquals(2)
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("widget_minutes").assertTextEquals("10")
        tap("back")
        compose.onNodeWithText("الجميع متصل").assertIsDisplayed()
        val settings = compose.onNodeWithTag("settings").fetchSemanticsNode().boundsInRoot
        val refresh = compose.onNodeWithTag("refresh").fetchSemanticsNode().boundsInRoot
        assertTrue("Arabic toolbar must mirror", settings.left < refresh.left)
        tap("timed_all")
        tap("preset_10")
        scrollTap("confirm_pause")
        waitFor("countdown")
        scrollTap("resume")
        waitFor("pause_all")
    }

    @Test
    fun connectionPreviewIsNavigableAndNeverNeedsRealCredentials() {
        tap("settings")
        scrollTap("edit_connection")
        scrollTap("connect")
        compose.onNodeWithText(text(R.string.connection_required)).assertExists()
        compose.onNodeWithTag("username").performTextReplacement("demo")
        compose.onNodeWithTag("password").performTextReplacement("demo-only")
        scrollTap("connect")
        waitFor("go_network")
        scrollTap("go_network")
        compose.onNodeWithTag("pause_all").assertIsDisplayed()
    }
}
