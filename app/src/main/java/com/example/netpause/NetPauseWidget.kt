package com.example.netpause

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import androidx.core.net.toUri
import androidx.core.graphics.toColorInt
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.util.Locale
import java.util.concurrent.TimeUnit

private const val WIDGET_ACTION = "com.example.netpause.WIDGET_ACTION"
private const val ACTION_WORK = "widget-action"
private const val EXPIRY_WORK = "widget-expiry"

class NetPauseWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        enqueue(context, WidgetAction.Refresh)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            WIDGET_ACTION -> {
                val action = WidgetAction.entries.find { it.name == intent.getStringExtra("command") } ?: return
                enqueue(context, action, intent.getStringExtra("expected") ?: "",
                    intent.getStringExtra("host") ?: "", intent.getIntExtra("minutes", 15))
            }
            Intent.ACTION_LOCALE_CHANGED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED ->
                enqueue(context, WidgetAction.Refresh)
            else -> super.onReceive(context, intent)
        }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: android.os.Bundle) {
        context.networkController.updateWidgets()
    }

    override fun onDisabled(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(EXPIRY_WORK)
    }

    companion object {
        internal fun ids(context: Context): IntArray = AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, NetPauseWidget::class.java))

        internal fun enqueue(context: Context, action: WidgetAction, expected: String = "", host: String = "", minutes: Int = 15) {
            if (ids(context).isEmpty()) return
            val request = OneTimeWorkRequestBuilder<WidgetWorker>().setInputData(workDataOf(
                "command" to action.name, "expected" to expected, "host" to host, "minutes" to minutes,
                "issuedAt" to System.currentTimeMillis(),
            ))
            if (Build.VERSION.SDK_INT >= 31) request.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            WorkManager.getInstance(context).enqueueUniqueWork(ACTION_WORK, ExistingWorkPolicy.KEEP, request.build())
            context.networkController.updateWidgets()
        }

        internal fun update(context: Context, state: NetworkState, names: Map<String, String>) {
            val widgets = ids(context)
            if (widgets.isEmpty()) return
            val manager = AppWidgetManager.getInstance(context)
            val small = views(context, state, names, compact = true)
            val medium = views(context, state, names, compact = true, details = true)
            val large = views(context, state, names)
            if (Build.VERSION.SDK_INT >= 31) {
                manager.updateAppWidget(widgets, RemoteViews(mapOf(
                    android.util.SizeF(140f, 140f) to small, android.util.SizeF(140f, 180f) to medium,
                    android.util.SizeF(250f, 230f) to large)))
            } else for (id in widgets) {
                val options = manager.getAppWidgetOptions(id)
                val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
                val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
                manager.updateAppWidget(id, when {
                    width >= 250 && height >= 230 -> large
                    height >= 180 -> medium
                    else -> small
                })
            }
            if (!state.busy) {
                val deadline = state.pause?.deadline ?: 0L
                val work = WorkManager.getInstance(context)
                if (!state.stale && deadline > System.currentTimeMillis()) {
                    // shortcut: Android may defer the expiry display refresh, revisit if an exact visual cutoff is needed.
                    val request = OneTimeWorkRequestBuilder<WidgetWorker>()
                        .setInputData(workDataOf("command" to WidgetAction.Refresh.name))
                        .setInitialDelay(deadline - System.currentTimeMillis() + 1000, TimeUnit.MILLISECONDS).build()
                    work.enqueueUniqueWork(EXPIRY_WORK, ExistingWorkPolicy.REPLACE, request)
                } else work.cancelUniqueWork(EXPIRY_WORK)
            }
        }

        internal fun views(context: Context, state: NetworkState, names: Map<String, String>,
            remembered: Boolean = AppStorage(context).hasCredentials, now: Long = System.currentTimeMillis(),
            compact: Boolean = false, details: Boolean = !compact): RemoteViews {
            val preferences = AppStorage(context).preferences
            val configuration = Configuration(context.resources.configuration).apply {
                val language = preferences.getString("language", "system")!!
                if (language != "system") setLocale(Locale.forLanguageTag(language))
            }
            val localized = context.createConfigurationContext(configuration)
            val resources = localized.resources
            fun text(id: Int) = resources.getString(id)
            val pause = state.pause
            val expired = pause != null && pause.deadline > 0 && pause.deadline <= now
            val problem = state.stale || !state.connected || expired
            val setup = !remembered || state.error in setOf(R.string.error_storage, R.string.error_permission, R.string.error_login)
            val minutes = preferences.getInt("widgetMinutes", 15)
            val openApp = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            fun action(command: WidgetAction): PendingIntent {
                val expected = widgetPauseKey(pause)
                val host = preferences.getString("address", "192.168.1.1")!!
                val intent = Intent(context, NetPauseWidget::class.java).setAction(WIDGET_ACTION).apply {
                    // Identity includes the displayed pause, so updating widgets never retargets an old tap.
                    data = ("netpause://widget/" + android.net.Uri.encode("$command|$host|$expected|$minutes")).toUri()
                    putExtra("command", command.name); putExtra("expected", expected)
                    putExtra("host", host); putExtra("minutes", minutes)
                }
                return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            }
            val name = pause?.target?.let { target -> names[target] ?: state.devices.find { it.id == target }?.let {
                if (it.isThisPhone) text(R.string.this_phone) else it.name
            } ?: text(R.string.widget_selected_device) }
            val title = when {
                state.busy -> text(R.string.widget_checking)
                setup -> text(R.string.connect_router_title)
                state.error != null -> text(R.string.widget_failed)
                problem -> text(R.string.widget_unknown)
                pause == null -> text(R.string.everyone_online)
                pause.target == "all" -> text(R.string.widget_all_paused)
                else -> resources.getString(R.string.widget_target_paused, name)
            }
            val subtitle = when {
                state.busy -> text(R.string.checking_description)
                setup -> text(if (state.error == R.string.error_permission) R.string.error_permission else R.string.widget_setup_note)
                state.error != null -> text(state.error)
                problem -> text(if (expired) R.string.widget_expired else R.string.stale_status)
                pause == null -> text(R.string.home_wifi)
                pause.deadline == 0L -> text(R.string.until_you_resume)
                else -> text(R.string.widget_auto_resume)
            }
            val views = RemoteViews(context.packageName, if (compact) R.layout.netpause_widget_compact else R.layout.netpause_widget)
            views.setInt(android.R.id.background, "setLayoutDirection", configuration.layoutDirection)
            views.setTextViewText(R.id.widget_title, title)
            views.setContentDescription(R.id.widget_header, if (compact)
                "${text(R.string.widget_open_app)}. $title. $subtitle" else text(R.string.widget_open_app))
            views.setOnClickPendingIntent(R.id.widget_header, openApp)
            views.setTextViewText(R.id.widget_subtitle, subtitle)
            views.setTextViewText(R.id.widget_brand, "NetPause")
            views.setTextViewText(R.id.widget_status, text(when {
                state.busy -> R.string.widget_checking
                setup || problem || state.error != null -> R.string.widget_refresh_required
                compact && pause == null -> R.string.widget_ready
                compact -> R.string.widget_pause_active
                else -> R.string.simulated
            }))
            if (compact) {
                views.setViewVisibility(R.id.widget_brand_row, if (details) View.VISIBLE else View.GONE)
                views.setInt(R.id.widget_subtitle, "setMaxLines", if (details) 2 else 1)
            }
            val timed = pause != null && pause.deadline > now && !state.busy && !problem && !setup && state.error == null
            views.setViewVisibility(R.id.widget_countdown, if (timed) View.VISIBLE else View.GONE)
            views.setChronometerCountDown(R.id.widget_countdown, true)
            views.setChronometer(R.id.widget_countdown, SystemClock.elapsedRealtime() + ((pause?.deadline ?: now) - now).coerceAtLeast(0), null, timed)
            views.setViewVisibility(R.id.widget_checked, if (details && state.checkedAt > 0) View.VISIBLE else View.GONE)
            views.setTextViewText(R.id.widget_checked, if (state.checkedAt > 0) resources.getString(R.string.widget_checked,
                android.text.format.DateFormat.getTimeFormat(localized).format(java.util.Date(state.checkedAt))) else "")
            val idle = !state.busy && !problem && !setup && state.error == null && pause == null
            views.setViewVisibility(R.id.widget_idle_actions, if (idle) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.widget_primary, if (idle) View.GONE else View.VISIBLE)
            views.setViewVisibility(R.id.widget_timed, if (idle) View.VISIBLE else View.GONE)
            views.setTextViewText(R.id.widget_pause, text(if (compact) R.string.widget_pause_short else R.string.pause_everyone))
            views.setContentDescription(R.id.widget_pause, text(R.string.pause_everyone))
            views.setOnClickPendingIntent(R.id.widget_pause, action(WidgetAction.Indefinite))
            views.setTextViewText(R.id.widget_timed, resources.getString(R.string.for_minutes, minutes))
            views.setOnClickPendingIntent(R.id.widget_timed, action(WidgetAction.Timed))
            views.setTextViewText(R.id.widget_primary, text(when {
                state.busy -> if (compact) R.string.widget_checking else R.string.checking
                setup -> R.string.widget_open_app
                problem || state.error != null -> R.string.refresh_status
                else -> if (compact) R.string.widget_resume_short else R.string.resume_internet
            }))
            views.setInt(R.id.widget_primary, "setBackgroundResource",
                if (problem || setup || state.busy || state.error != null) R.drawable.widget_secondary else R.drawable.widget_primary)
            views.setTextColor(R.id.widget_primary,
                (if (problem || setup || state.busy || state.error != null) "#361828" else "#FFFFFF").toColorInt())
            views.setBoolean(R.id.widget_primary, "setEnabled", !state.busy)
            views.setOnClickPendingIntent(R.id.widget_primary, if (setup) openApp else action(when {
                problem || state.error != null -> WidgetAction.Refresh
                else -> WidgetAction.Resume
            }))
            return views
        }
    }
}

class WidgetWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val command = WidgetAction.entries.find { it.name == inputData.getString("command") } ?: return Result.failure()
        // WorkManager can restart after process death. Reconcile writes instead of replaying them.
        val action = if (runAttemptCount > 0) WidgetAction.Refresh else command
        val controller = applicationContext.networkController
        controller.widgetAction(action, inputData.getString("expected") ?: "",
            inputData.getString("host") ?: "", inputData.getLong("issuedAt", 0), inputData.getInt("minutes", 15)).join()
        return if (controller.state.error == null) Result.success() else Result.failure()
    }
}
