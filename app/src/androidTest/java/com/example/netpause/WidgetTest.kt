package com.example.netpause

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetHost
import android.content.ComponentName
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.view.View
import android.widget.Button
import android.widget.Chronometer
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith

/** Applies the real RemoteViews, using synthetic state without sending router commands. */
@RunWith(AndroidJUnit4::class)
class WidgetTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private var savedLanguage = "system"
    private var savedMinutes = 15
    private val now = System.currentTimeMillis()
    private val phone = RouterDevice("02:00:00:00:00:01", "Test phone", "192.0.2.1", true)
    private val confirmed = NetworkState(devices = listOf(phone), connected = true, stale = false, checkedAt = now)

    @Before fun save() {
        savedLanguage = preferences.getString("language", "system")!!
        savedMinutes = preferences.getInt("widgetMinutes", 15)
    }

    @After fun restore() {
        preferences.edit { putString("language", savedLanguage); putInt("widgetMinutes", savedMinutes) }
    }

    private fun render(state: NetworkState, remembered: Boolean = true, names: Map<String, String> = emptyMap(),
        widthDp: Int = 360, heightDp: Int = 230, compact: Boolean = false): View {
        lateinit var view: View
        instrumentation.runOnMainSync {
            view = NetPauseWidget.views(context, state, names, remembered, now, compact).apply(context, FrameLayout(context))
            val density = context.resources.displayMetrics.density
            view.measure(View.MeasureSpec.makeMeasureSpec((widthDp * density).toInt(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec((heightDp * density).toInt(), View.MeasureSpec.EXACTLY))
            view.layout(0, 0, view.measuredWidth, view.measuredHeight)
            // A launcher runs another layout pass when drawing resolves inherited RTL padding.
            val firstFrame = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(firstFrame))
            firstFrame.recycle()
            view.measure(View.MeasureSpec.makeMeasureSpec((widthDp * density).toInt(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec((heightDp * density).toInt(), View.MeasureSpec.EXACTLY))
            view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        }
        return view
    }

    private fun View.label(id: Int) = findViewById<TextView>(id).text.toString()

    @Test fun compactStatesKeepControlsAndCountdownInsideMinimumSize() {
        for (language in listOf("en", "ar")) {
            preferences.edit { putString("language", language) }
            val states = listOf(confirmed, confirmed.copy(pause = RouterPause("fixture", "all", now + 600_000)),
                confirmed.copy(pause = RouterPause("fixture", phone.id, 0)), confirmed.copy(busy = true),
                confirmed.copy(stale = true, error = R.string.error_wifi), NetworkState())
            for ((index, state) in states.withIndex()) {
                val view = render(state, widthDp = 140, heightDp = 140, compact = true)
                for (id in listOf(R.id.widget_pause, R.id.widget_timed, R.id.widget_primary, R.id.widget_header, R.id.widget_countdown)) {
                    val child = view.findViewById<View>(id)
                    var ancestor: View? = child
                    var visible = true
                    while (ancestor != null) {
                        visible = visible && ancestor.visibility == View.VISIBLE
                        ancestor = ancestor.parent as? View
                    }
                    if (!visible) continue
                    val bounds = android.graphics.Rect()
                    child.getDrawingRect(bounds)
                    (view as android.view.ViewGroup).offsetDescendantRectToMyCoords(child, bounds)
                    assertTrue("$language state $index: $bounds outside ${view.width}x${view.height}",
                        bounds.left >= 0 && bounds.top >= 0 && bounds.right <= view.width && bounds.bottom <= view.height)
                    if (child is Button) {
                        assertTrue(child.height >= 48 * context.resources.displayMetrics.density - 1)
                        assertTrue(child.width >= 48 * context.resources.displayMetrics.density - 1)
                        for (line in 0 until child.layout.lineCount) assertEquals("Action label must be readable", 0, child.layout.getEllipsisCount(line))
                    }
                }
                assertEquals(if ((state.pause?.deadline ?: 0L) > now) View.VISIBLE else View.GONE,
                    view.findViewById<View>(R.id.widget_countdown).visibility)
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                instrumentation.runOnMainSync { view.draw(android.graphics.Canvas(bitmap)) }
                val directory = File(context.getExternalFilesDir(null), "widget-review").apply { mkdirs() }
                File(directory, "$language-compact-$index.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        }
        val provider = AppWidgetManager.getInstance(context).installedProviders.single {
            it.provider == ComponentName(context, NetPauseWidget::class.java)
        }
        val density = context.resources.displayMetrics.density
        assertTrue(provider.minResizeWidth <= 140 * density + 1)
        assertTrue(provider.minResizeHeight <= 140 * density + 1)
    }

    @Test fun idleAndEveryActivePauseStateRenderInBothLanguages() {
        for (language in listOf("en", "ar")) {
            preferences.edit { putString("language", language); putInt("widgetMinutes", 20) }
            val resources = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
                setLocale(java.util.Locale.forLanguageTag(language))
            }).resources
            val idle = render(confirmed)
            assertEquals(resources.getString(R.string.for_minutes, 20), idle.label(R.id.widget_timed))
            assertEquals(View.VISIBLE, idle.findViewById<View>(R.id.widget_timed).visibility)
            assertEquals(if (language == "ar") View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR, idle.layoutDirection)
            for (size in listOf(idle, render(confirmed, widthDp = 250))) {
                for (id in listOf(R.id.widget_pause, R.id.widget_timed)) {
                    val button = size.findViewById<Button>(id)
                    val parent = button.parent as View
                    assertTrue("Both $language actions must stay inside the widget", button.left >= 0 && button.right <= parent.width)
                }
            }
            for (target in listOf("all", phone.id)) for (deadline in listOf(0L, now + 600_000)) {
                val active = render(confirmed.copy(pause = RouterPause("fixture", target, deadline)))
                assertEquals(resources.getString(R.string.resume_internet), active.label(R.id.widget_primary))
                assertEquals(View.GONE, active.findViewById<View>(R.id.widget_timed).visibility)
                val countdown = active.findViewById<Chronometer>(R.id.widget_countdown)
                assertEquals(if (deadline > 0) View.VISIBLE else View.GONE, countdown.visibility)
                assertTrue(countdown.isCountDown)
                if (target == phone.id) assertTrue(active.label(R.id.widget_title).contains(resources.getString(R.string.this_phone)))
            }
        }
        val renamed = render(confirmed.copy(pause = RouterPause("fixture", phone.id, 0)), names = mapOf(phone.id to "Family tablet"))
        assertTrue(renamed.label(R.id.widget_title).contains("Family tablet"))
    }

    @Test fun pendingFailureExpiredAndMissingLoginNeverShowPauseActions() {
        for (state in listOf(confirmed.copy(busy = true), confirmed.copy(stale = true),
            confirmed.copy(error = R.string.error_wifi, stale = true),
            confirmed.copy(pause = RouterPause("expired", "all", now - 1)))) {
            val view = render(state)
            assertEquals(View.GONE, view.findViewById<View>(R.id.widget_timed).visibility)
            assertEquals(View.GONE, view.findViewById<View>(R.id.widget_countdown).visibility)
            assertEquals(!state.busy, view.findViewById<Button>(R.id.widget_primary).isEnabled)
        }
        preferences.edit { putString("language", "en") }
        assertEquals("Open app", render(confirmed, remembered = false).label(R.id.widget_primary))
        assertEquals("Open app", render(confirmed.copy(error = R.string.error_permission)).label(R.id.widget_primary))
        val provider = AppWidgetManager.getInstance(context).installedProviders.find {
            it.provider == ComponentName(context, NetPauseWidget::class.java)
        }
        assertNotNull("Widget must be discoverable by a launcher", provider)
        @Suppress("DEPRECATION")
        assertFalse(context.packageManager.getReceiverInfo(ComponentName(context, NetPauseWidget::class.java), 0).exported)
    }

    @Test fun realWidgetHostReceivesBackgroundWorkerSetupState() {
        assumeTrue("This host test must not load a household login", !AppStorage(context).hasCredentials)
        preferences.edit { putString("language", "en") }
        val host = AppWidgetHost(context, 799)
        var id = AppWidgetManager.INVALID_APPWIDGET_ID
        val automation = instrumentation.getUiAutomation(android.app.UiAutomation.FLAG_DONT_USE_ACCESSIBILITY)
        var permissionHeld = false
        try {
            automation.adoptShellPermissionIdentity("android.permission.BIND_APPWIDGET")
            permissionHeld = true
            id = host.allocateAppWidgetId()
            val manager = AppWidgetManager.getInstance(context)
            assertTrue(manager.bindAppWidgetIdIfAllowed(id, ComponentName(context, NetPauseWidget::class.java)))
            automation.dropShellPermissionIdentity()
            permissionHeld = false
            lateinit var view: android.appwidget.AppWidgetHostView
            instrumentation.runOnMainSync {
                host.startListening()
                view = host.createView(context, id, manager.getAppWidgetInfo(id))
            }
            val timeout = android.os.SystemClock.elapsedRealtime() + 15_000
            var label = ""
            while (label != "Open app" && android.os.SystemClock.elapsedRealtime() < timeout) {
                instrumentation.runOnMainSync { label = view.findViewById<Button>(R.id.widget_primary)?.text?.toString() ?: "" }
                if (label != "Open app") android.os.SystemClock.sleep(50)
            }
            assertEquals("The provider and WorkManager must deliver a usable setup action", "Open app", label)
        } finally {
            try { if (permissionHeld) automation.dropShellPermissionIdentity() }
            finally {
                instrumentation.runOnMainSync { host.stopListening() }
                if (id != AppWidgetManager.INVALID_APPWIDGET_ID) host.deleteAppWidgetId(id)
            }
        }
    }

    @Test fun captureWidgetStatesForVisualReview() {
        val directory = File(context.getExternalFilesDir(null), "widget-review").apply { mkdirs() }
        for (language in listOf("en", "ar")) {
            preferences.edit { putString("language", language) }
            val states = listOf("idle" to confirmed,
                "all-timed" to confirmed.copy(pause = RouterPause("fixture", "all", now + 600_000)),
                "device-indefinite" to confirmed.copy(pause = RouterPause("fixture", phone.id, 0)),
                "pending" to confirmed.copy(busy = true),
                "error" to confirmed.copy(stale = true, error = R.string.error_wifi))
            for ((name, state) in states) {
                val view = render(state)
                instrumentation.runOnMainSync {
                    val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                    view.draw(android.graphics.Canvas(bitmap))
                    File(directory, "$language-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    bitmap.recycle()
                }
            }
        }
    }
}
