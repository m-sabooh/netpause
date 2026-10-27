package com.example.netpause

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.After
import org.junit.Before
import android.content.Context
import androidx.core.content.edit

/** These UI checks never submit credentials or change router rules. */
class MainActivityTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private var savedLanguage = "system"
    private var savedMinutes = 15
    @Before fun savePreferences() {
        val prefs = compose.activity.getSharedPreferences("settings", Context.MODE_PRIVATE)
        savedLanguage = prefs.getString("language", "system")!!
        savedMinutes = prefs.getInt("widgetMinutes", 15)
    }
    @After fun restorePreferences() {
        compose.activity.getSharedPreferences("settings", Context.MODE_PRIVATE).edit {
            putString("language", savedLanguage)
            putInt("widgetMinutes", savedMinutes)
        }
        compose.runOnIdle { compose.activity.networkController.updateWidgets() }
    }
    private fun tap(tag: String) = compose.onNodeWithTag(tag).performClick()
    private fun scrollTap(tag: String) = compose.onNodeWithTag(tag).performScrollTo().performClick()
    private fun openSettings() {
        compose.waitUntil(10_000) { compose.onAllNodes(isRoot()).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
        compose.waitUntil(30_000) {
            !compose.activity.networkController.state.busy
        }
        compose.waitForIdle()
        if (compose.onAllNodesWithTag("connection_screen").fetchSemanticsNodes().isNotEmpty()) tap("back")
        else tap("settings")
    }

    @Test fun settingsPersistAndArabicMirrorsNavigation() {
        openSettings()
        compose.onNodeWithTag("widget_minutes").performTextReplacement("10")
        scrollTap("save_default")
        scrollTap("language_picker")
        tap("language_ar")
        compose.onAllNodesWithText("الإعدادات").assertCountEquals(2)
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("widget_minutes").assertTextEquals("10")
        tap("back")
        val settings = compose.onNodeWithTag("settings").fetchSemanticsNode().boundsInRoot
        val refresh = compose.onNodeWithTag("refresh").fetchSemanticsNode().boundsInRoot
        assertTrue(settings.left < refresh.left)
        tap("settings")
        scrollTap("language_picker")
        tap("language_system")
        compose.onNodeWithTag("widget_minutes").performTextReplacement("15")
        scrollTap("save_default")
    }

    @Test fun missingCredentialsStayOnSetupWithoutRouterRequest() {
        openSettings()
        scrollTap("edit_connection")
        scrollTap("connect")
        compose.onNodeWithTag("connection_screen").assertExists()
        compose.onNodeWithTag("connection_error").assertExists()
    }
}
