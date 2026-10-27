package com.example.netpause

import android.app.AlertDialog
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.core.content.edit
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/** Uses synthetic state only. No credentials are submitted and no router rules are changed. */
class ArabicScreensTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var controller: NetworkController
    private var savedLanguage = "system"
    private lateinit var savedState: NetworkState
    private val phone = RouterDevice("02:00:00:00:00:01", "Test phone", "192.0.2.1", true)
    private val pc = RouterDevice("02:00:00:00:00:02", "Test PC", "192.0.2.2")
    private val arabic by lazy {
        compose.activity.createConfigurationContext(Configuration(compose.activity.resources.configuration).apply {
            setLocale(Locale.forLanguageTag("ar"))
        }).resources
    }

    @Before fun prepare() {
        controller = compose.activity.networkController
        savedLanguage = controller.preferences.getString("language", "system")!!
        compose.waitUntil(30_000) { !controller.state.busy }
        savedState = controller.state
        if (compose.onAllNodesWithTag("connection_screen").fetchSemanticsNodes().isNotEmpty()) tap("back")
        else tap("settings")
        tap("language_picker")
        tap("language_en")
        tap("language_picker")
        tap("language_ar")
    }

    @After fun restoreLanguage() {
        controller.preferences.edit { putString("language", savedLanguage) }
        compose.runOnIdle {
            NetworkController::class.java.getDeclaredMethod("setState", NetworkState::class.java).apply {
                isAccessible = true
            }.invoke(controller, savedState)
            controller.updateWidgets()
        }
    }

    private fun tap(tag: String) = compose.onNodeWithTag(tag).performClick()
    private fun scrollTap(tag: String) = compose.onNodeWithTag(tag).performScrollTo().performClick()
    private fun assertArabic(vararg strings: Int) {
        strings.forEach { compose.onAllNodesWithText(arabic.getString(it)).assertAny(hasText(arabic.getString(it))) }
    }

    private fun state(pause: RouterPause? = null, error: Int? = null, busy: Boolean = false) {
        // Keep fixture injection in tests rather than adding a router bypass to the app.
        compose.runOnIdle {
            NetworkController::class.java.getDeclaredMethod("setState", NetworkState::class.java).apply {
                isAccessible = true
            }.invoke(controller, NetworkState(listOf(phone, pc), pause, busy, connected = true, stale = error != null, error = error))
        }
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val drawn = CountDownLatch(1)
        compose.runOnUiThread {
            compose.activity.window.decorView.postOnAnimation {
                compose.activity.window.decorView.postOnAnimation { drawn.countDown() }
            }
        }
        assertTrue("The screen must draw before capture", drawn.await(5, TimeUnit.SECONDS))
        val directory = File(compose.activity.getExternalFilesDir(null), "arabic-review").apply { mkdirs() }
        instrumentation.uiAutomation.takeScreenshot().also { bitmap ->
            File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    @Test fun everyScreenAndSheetUsesChosenArabicOnEnglishPhone() {
        assertArabic(R.string.settings, R.string.widget, R.string.device_names, R.string.shared_control)
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onAllNodesWithText(arabic.getString(R.string.settings)).onLast()
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals("The settings title must not break an Arabic word", 1, layouts.single().lineCount)
        screenshot("01-settings")
        tap("language_picker")
        screenshot("02-language-menu")
        assertArabic(R.string.follow_phone)
        tap("language_ar")
        compose.onNodeWithTag("widget_minutes").performTextReplacement("0")
        scrollTap("save_default")
        assertArabic(R.string.invalid_minutes)
        screenshot("03-settings-validation")
        scrollTap("edit_connection")
        assertArabic(R.string.connection, R.string.connect_router_title, R.string.username, R.string.password)
        screenshot("04-connection")
        scrollTap("connect")
        assertArabic(R.string.connection_required)
        screenshot("05-connection-validation")
        tap("back")
        tap("back")
        screenshot("06-disconnected")

        state()
        assertArabic(R.string.everyone_online, R.string.connected_devices, R.string.this_phone)
        screenshot("07-network")
        scrollTap("device_${phone.id}")
        assertArabic(R.string.device_details, R.string.pause_internet, R.string.rename_device)
        screenshot("08-device-details")
        tap("rename")
        assertArabic(R.string.rename_device, R.string.device_name, R.string.name_local_note)
        screenshot("09-rename")
        compose.onNodeWithTag("device_name").performTextClearance()
        compose.onNodeWithTag("device_name").performImeAction()
        assertArabic(R.string.name_required)
        screenshot("10-rename-validation")
        tap("close_sheet")
        scrollTap("timed_all")
        assertArabic(R.string.choose_pause, R.string.how_long, R.string.everyone, R.string.custom_duration)
        screenshot("11-timed-pause")
        compose.onNodeWithTag("pause_minutes").performTextReplacement("0")
        scrollTap("confirm_pause")
        assertArabic(R.string.invalid_minutes)
        screenshot("12-pause-validation")
        scrollTap("mode_indefinite")
        assertArabic(R.string.indefinite_note, R.string.until_resumed)
        screenshot("13-indefinite-pause")
        tap("close_sheet")

        state(RouterPause("test", phone.id, 0L))
        assertArabic(R.string.pause_active, R.string.until_you_resume, R.string.resume_internet)
        screenshot("14-active-indefinite")
        scrollTap("choose_target")
        assertArabic(R.string.your_devices, R.string.choose_target, R.string.everyone)
        screenshot("15-target-picker")
        scrollTap("device_${pc.id}")
        tap("device_pause")
        scrollTap("confirm_pause")
        assertArabic(R.string.replace_question, R.string.current, R.string.new_target, R.string.replace_pause)
        screenshot("16-replacement")
        tap("keep_pause")

        state(RouterPause("test", "all", System.currentTimeMillis() + 600_000))
        assertArabic(R.string.all_paused, R.string.early_resume)
        screenshot("17-active-timed")
        state(error = R.string.error_conflict)
        assertArabic(R.string.error_conflict, R.string.stale_status)
        screenshot("18-router-error")
        compose.mainClock.autoAdvance = false
        state(busy = true)
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(350)
        assertArabic(R.string.checking, R.string.checking_description)
        screenshot("19-pending")
        state()
        compose.mainClock.autoAdvance = true
        lateinit var dialog: AlertDialog
        compose.runOnIdle {
            val builder = MainActivity::class.java.getDeclaredMethod("permissionDialog").apply {
                isAccessible = true
            }.invoke(compose.activity) as AlertDialog.Builder
            dialog = builder.setMessage(R.string.error_permission)
                .setPositiveButton(R.string.open_settings, null)
                .setNegativeButton(android.R.string.cancel, null).create()
            dialog.window?.setWindowAnimations(0)
            dialog.show()
            assertEquals(arabic.getString(R.string.open_settings), dialog.getButton(AlertDialog.BUTTON_POSITIVE).text)
            assertEquals(arabic.getString(android.R.string.cancel), dialog.getButton(AlertDialog.BUTTON_NEGATIVE).text)
        }
        InstrumentationRegistry.getInstrumentation().uiAutomation.waitForIdle(500, 5_000)
        screenshot("20-permission-dialog")
        compose.runOnIdle { dialog.dismiss() }
    }
}
