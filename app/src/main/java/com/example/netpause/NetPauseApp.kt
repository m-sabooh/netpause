package com.example.netpause

import androidx.core.content.edit

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.netpause.theme.NetPauseTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

private enum class Destination { Network, Settings, Connection }
private enum class Sheet { Device, Compose, Rename, Targets, Replace }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NetPauseApp(controller: NetworkController, withPermission: (() -> Unit) -> Unit) {
    val state = controller.state
    var language by rememberSaveable { mutableStateOf(controller.preferences.getString("language", "system")!!) }
    val baseContext = LocalContext.current
    val systemConfiguration = LocalConfiguration.current
    val configuration = remember(language, systemConfiguration) {
        Configuration(systemConfiguration).apply {
            if (language != "system") setLocale(Locale.forLanguageTag(language))
        }
    }
    val localizedContext = remember(baseContext, configuration) { baseContext.createConfigurationContext(configuration) }
    val direction = if (configuration.layoutDirection == android.view.View.LAYOUT_DIRECTION_RTL) LayoutDirection.Rtl else LayoutDirection.Ltr
    val motion = rememberMotionEnabled()

    // Popup and dialog hosts replace LocalContext with the activity's context.
    CompositionLocalProvider(LocalContext provides localizedContext, LocalConfiguration provides configuration,
        LocalResources provides localizedContext.resources,
        LocalLayoutDirection provides direction, LocalMotionEnabled provides motion, LocalDevices provides state.devices) {
        NetPauseTheme {
            var destination by rememberSaveable { mutableStateOf(if (state.connected) Destination.Network else Destination.Connection) }
            var sheet by rememberSaveable { mutableStateOf<Sheet?>(null) }
            var selectedTarget by rememberSaveable { mutableStateOf("") }
            var initiallyTimed by rememberSaveable { mutableStateOf(true) }
            val activeTarget = state.pause?.target
            val deadline = state.pause?.deadline ?: 0L
            val names = controller.names
            val address = controller.address
            var widgetMinutes by remember { mutableIntStateOf(controller.preferences.getInt("widgetMinutes", 15)) }
            var connected by remember { mutableStateOf(false) }
            var observedConnected by rememberSaveable { mutableStateOf(state.connected) }
            var observedRevision by rememberSaveable { mutableIntStateOf(state.connectionRevision) }
            var pendingTarget by rememberSaveable { mutableStateOf<String?>(null) }
            var pendingMinutes by rememberSaveable { mutableStateOf<Int?>(null) }
            var notice by rememberSaveable { mutableStateOf<Int?>(null) }
            var celebrate by remember { mutableStateOf(false) }
            val busy = state.busy
            val scope = rememberCoroutineScope()
            val keyboard = LocalSoftwareKeyboardController.current
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            val screenState = rememberSaveableStateHolder()
            val navigationDistance = with(LocalDensity.current) { 18.dp.roundToPx() }

            fun hideSheet() {
                keyboard?.hide()
                scope.launch { sheetState.hide(); sheet = null }
            }
            fun openComposer(target: String, timed: Boolean = true) {
                if (busy) return
                selectedTarget = target
                initiallyTimed = timed
                sheet = Sheet.Compose
            }
            fun changePause(target: String?, minutes: Int?) {
                if (busy) return
                pendingTarget = target
                pendingMinutes = minutes
                notice = null
                withPermission { controller.changePause(target, minutes) }
                destination = Destination.Network
                if (sheet != null) hideSheet()
            }

            LaunchedEffect(state.connected, state.connectionRevision) {
                val justConnected = state.connected && (!observedConnected || observedRevision != state.connectionRevision)
                if (justConnected && destination == Destination.Connection) {
                    connected = true
                    destination = Destination.Network
                }
                observedConnected = state.connected
                observedRevision = state.connectionRevision
            }
            LaunchedEffect(state.pause?.id) { if (state.pause != null) celebrate = true }
            LaunchedEffect(deadline) {
                if (deadline > 0L) {
                    delay((deadline - System.currentTimeMillis()).coerceAtLeast(0L) + 1500)
                    controller.refresh()
                }
            }
            LaunchedEffect(celebrate) { if (celebrate) { delay(800); celebrate = false } }
            LaunchedEffect(notice) { if (notice != null) { delay(4000); notice = null } }
            BackHandler(enabled = destination != Destination.Network && sheet == null) {
                destination = if (destination == Destination.Connection) Destination.Settings else Destination.Network
            }

            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding(), contentAlignment = Alignment.TopCenter) {
                    AnimatedContent(destination, Modifier.widthIn(max = 600.dp).fillMaxSize(),
                        transitionSpec = {
                            if (!motion) EnterTransition.None togetherWith ExitTransition.None
                            else {
                                val sign = (if (targetState.ordinal > initialState.ordinal) 1 else -1) *
                                    (if (direction == LayoutDirection.Rtl) -1 else 1)
                                (slideInHorizontally(tween(240)) { sign * navigationDistance } + fadeIn(tween(240))) togetherWith
                                    (slideOutHorizontally(tween(240)) { -sign * navigationDistance } + fadeOut(tween(160)))
                            }
                        }, label = "Navigation") { screen ->
                        screenState.SaveableStateProvider(screen) {
                            val scroll = rememberScrollState()
                            val pauseKey = "$activeTarget/$deadline"
                            var lastPause by rememberSaveable { mutableStateOf(pauseKey) }
                            LaunchedEffect(pauseKey) {
                                if (screen == Destination.Network && lastPause != pauseKey) scroll.scrollTo(0)
                                lastPause = pauseKey
                            }
                            Column(Modifier.fillMaxSize()) {
                                AppHeader(
                                    heading = when (screen) {
                                        Destination.Network -> null
                                        Destination.Settings -> stringResource(R.string.settings)
                                        Destination.Connection -> stringResource(R.string.connection)
                                    },
                                    busy = busy,
                                    onBack = { destination = if (screen == Destination.Connection) Destination.Settings else Destination.Network },
                                    onRefresh = { if (!busy) { notice = null; withPermission { controller.refresh() } } },
                                    onSettings = { destination = Destination.Settings },
                                )
                                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll)
                                    .padding(start = 22.dp, end = 22.dp, bottom = 24.dp)) {
                                    state.error?.let { Notice(stringResource(it), Modifier.padding(bottom = 16.dp).testTag("router_error")) }
                                    if (state.stale && state.connected) Notice(stringResource(R.string.stale_status), Modifier.padding(bottom = 16.dp))
                                    when (screen) {
                                        Destination.Network -> if (!state.connected) {
                                            Notice(stringResource(R.string.same_wifi))
                                            NetButton(stringResource(R.string.connect_router), { destination = Destination.Connection },
                                                Modifier.fillMaxWidth().padding(top = 20.dp))
                                        } else NetworkScreen(activeTarget, deadline, names, busy, notice, celebrate,
                                            onPauseAll = { openComposer("all", false) },
                                            onTimedAll = { openComposer("all") },
                                            onDevice = { selectedTarget = it; sheet = Sheet.Device },
                                            onResume = { changePause(null, null) },
                                            onTargets = { sheet = Sheet.Targets })
                                        Destination.Settings -> SettingsScreen(language, { language = it; controller.preferences.edit { putString("language", it) } }, widgetMinutes,
                                            { widgetMinutes = it; controller.preferences.edit { putInt("widgetMinutes", it) } }, address,
                                            onConnection = { connected = false; destination = Destination.Connection })
                                        Destination.Connection -> ConnectionScreen(address, connected, busy,
                                            onConnect = { value, remember -> withPermission { controller.connect(value, remember) } },
                                            onEdit = { connected = false }, onNetwork = { destination = Destination.Network })
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (sheet != null) {
                ModalBottomSheet(onDismissRequest = { sheet = null }, sheetState = sheetState,
                    containerColor = MaterialTheme.colorScheme.background,
                    scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
                    dragHandle = null,
                ) {
                    Column(Modifier.widthIn(max = 600.dp).fillMaxWidth().align(Alignment.CenterHorizontally)
                        .verticalScroll(rememberScrollState()).animateContentSize(spring(dampingRatio = 0.85f, stiffness = 500f))
                        .padding(start = 22.dp, end = 22.dp, top = 12.dp, bottom = 26.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Heading(stringResource(when (sheet) {
                                Sheet.Device -> R.string.device_details
                                Sheet.Compose -> R.string.choose_pause
                                Sheet.Rename -> R.string.rename_device
                                Sheet.Targets -> R.string.your_devices
                                else -> R.string.replace_pause
                            }), Modifier.weight(1f), MaterialTheme.typography.titleLarge)
                            NetIconButton(R.drawable.ic_x, stringResource(R.string.close), ::hideSheet, Modifier.testTag("close_sheet"))
                        }
                        when (sheet) {
                            Sheet.Device -> DeviceDetails(selectedTarget, names,
                                onPause = { openComposer(selectedTarget) }, onRename = { sheet = Sheet.Rename })
                            Sheet.Compose -> key(selectedTarget) {
                                PauseComposer(selectedTarget, names, initiallyTimed) { minutes ->
                                    keyboard?.hide()
                                    pendingTarget = selectedTarget
                                    pendingMinutes = minutes
                                    if (activeTarget == selectedTarget) {
                                        notice = R.string.already_paused
                                        hideSheet()
                                    } else if (activeTarget != null) sheet = Sheet.Replace else changePause(selectedTarget, minutes)
                                }
                            }
                            Sheet.Rename -> RenameDevice(targetName(selectedTarget, names)) { newName ->
                                controller.rename(selectedTarget, newName)
                                hideSheet()
                            }
                            Sheet.Targets -> TargetPicker(names, activeTarget, busy, onEveryone = { openComposer("all") },
                                onDevice = { selectedTarget = it; sheet = Sheet.Device })
                            Sheet.Replace -> ReplacementSheet(
                                currentName = activeTarget?.let { targetName(it, names) } ?: stringResource(R.string.everyone),
                                nextName = targetName(pendingTarget ?: selectedTarget, names), minutes = pendingMinutes,
                                onReplace = { changePause(pendingTarget, pendingMinutes) }, onKeep = ::hideSheet)
                            null -> Unit
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppHeader(heading: String?, busy: Boolean, onBack: () -> Unit, onRefresh: () -> Unit, onSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 14.dp, top = 10.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (heading == null) {
            NetIcon(R.drawable.ic_pause, Modifier.padding(start = 4.dp).size(22.dp))
            Text(stringResource(R.string.app_name), Modifier.weight(1f).padding(start = 8.dp),
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold))
            NetIconButton(R.drawable.ic_refresh_cw, stringResource(R.string.refresh_status), onRefresh,
                Modifier.testTag("refresh"), enabled = !busy, spinning = busy)
            NetIconButton(R.drawable.ic_settings, stringResource(R.string.open_settings), onSettings, Modifier.testTag("settings"))
        } else {
            NetIconButton(R.drawable.ic_arrow_left, stringResource(R.string.back), onBack, Modifier.testTag("back"))
            Text(heading, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelLarge)
        }
    }
}
