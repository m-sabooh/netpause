package com.example.netpause

internal enum class WidgetAction { Refresh, Indefinite, Timed, Resume }

internal fun widgetPauseKey(pause: RouterPause?) = pause?.let { "${it.id}|${it.target}|${it.deadline}" } ?: ""

internal fun widgetRequestMatches(action: WidgetAction, expected: String, current: RouterPause?,
    requestedHost: String, currentHost: String, issuedAt: Long, now: Long): Boolean =
    requestedHost == currentHost && now - issuedAt in 0..60_000 && expected == widgetPauseKey(current) &&
        when (action) {
            WidgetAction.Resume -> current != null
            WidgetAction.Indefinite, WidgetAction.Timed -> current == null
            WidgetAction.Refresh -> true
        }
