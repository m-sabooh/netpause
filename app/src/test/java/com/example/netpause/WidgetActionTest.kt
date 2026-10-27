package com.example.netpause

import org.junit.Assert.*
import org.junit.Test

class WidgetActionTest {
    private val now = 100_000L
    private val pause = RouterPause("current", "02:00:00:00:00:01", 200_000)
    private fun matches(action: WidgetAction, expected: RouterPause?, current: RouterPause?,
        host: String = "192.168.1.1", issuedAt: Long = now) = widgetRequestMatches(
        action, widgetPauseKey(expected), current, host, "192.168.1.1", issuedAt, now)

    @Test fun idleActionsNeverReplaceAnActivePause() {
        for (action in listOf(WidgetAction.Timed, WidgetAction.Indefinite)) {
            assertTrue(matches(action, null, null))
            assertFalse(matches(action, null, pause))
            assertFalse(matches(action, pause, pause))
        }
    }

    @Test fun resumeMustMatchTheWholeDisplayedPause() {
        assertTrue(matches(WidgetAction.Resume, pause, pause))
        assertFalse(matches(WidgetAction.Resume, pause, pause.copy(id = "new")))
        assertFalse(matches(WidgetAction.Resume, pause, pause.copy(target = "all")))
        assertFalse(matches(WidgetAction.Resume, pause, pause.copy(deadline = 300_000)))
        assertFalse(matches(WidgetAction.Resume, pause, null))
        assertFalse(matches(WidgetAction.Resume, null, null))
    }

    @Test fun queuedActionsExpireAndCannotSwitchRouters() {
        assertTrue(matches(WidgetAction.Resume, pause, pause, issuedAt = now - 60_000))
        assertFalse(matches(WidgetAction.Resume, pause, pause, issuedAt = now - 60_001))
        assertFalse(matches(WidgetAction.Resume, pause, pause, issuedAt = now + 1))
        assertFalse(matches(WidgetAction.Resume, pause, pause, host = "192.168.2.1"))
    }
}
