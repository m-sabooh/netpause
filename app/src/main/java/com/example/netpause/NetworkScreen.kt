package com.example.netpause

import android.text.format.DateFormat
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Date

@Composable
internal fun NetworkScreen(
    activeTarget: String?, deadline: Long, names: Map<String, String>, busy: Boolean, notice: Int?, celebrate: Boolean,
    onPauseAll: () -> Unit, onTimedAll: () -> Unit, onDevice: (String) -> Unit,
    onResume: () -> Unit, onTargets: () -> Unit,
) {
    Column {
        AnimatedVisibility(busy || notice != null) {
            Box(Modifier.padding(bottom = 16.dp)) {
                if (busy) Notice(stringResource(R.string.checking_description), title = stringResource(R.string.checking))
                else if (notice != null) Notice(stringResource(notice))
            }
        }
        AnimatedContent(activeTarget to deadline, transitionSpec = {
            (fadeIn(tween(300)) + slideInVertically(tween(300)) { 24 }) togetherWith fadeOut(tween(150))
        }, label = "Pause state") { (target, end) ->
            if (target == null) IdleNetwork(names, busy, onPauseAll, onTimedAll, onDevice)
            else ActivePause(target, end, names, busy, celebrate, onResume, onTargets)
        }
        SmallText(stringResource(R.string.preview_note), Modifier.padding(top = 24.dp))
    }
}

@Composable
private fun IdleNetwork(names: Map<String, String>, busy: Boolean, onPauseAll: () -> Unit, onTimedAll: () -> Unit, onDevice: (String) -> Unit) {
    Column {
        Row(Modifier.padding(top = 4.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Box(Modifier.size(6.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
            Text(stringResource(R.string.home_wifi), color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall)
        }
        Entrance {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 22.dp)) {
                    Eyebrow(stringResource(R.string.your_network))
                    Heading(stringResource(R.string.everyone_online), Modifier.padding(top = 9.dp, bottom = 8.dp))
                    SmallText(stringResource(R.string.all_online_description))
                    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 18.dp)) {
                        if (maxWidth < 280.dp || LocalDensity.current.fontScale > 1.3f) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                NetButton(stringResource(R.string.pause_everyone), onPauseAll, Modifier.fillMaxWidth().testTag("pause_all"),
                                    icon = R.drawable.ic_pause, enabled = !busy)
                                NetButton(stringResource(R.string.choose_duration), onTimedAll, Modifier.fillMaxWidth().testTag("timed_all"),
                                    icon = R.drawable.ic_clock, secondary = true, enabled = !busy)
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                NetButton(stringResource(R.string.pause_everyone), onPauseAll, Modifier.weight(1f).testTag("pause_all"),
                                    icon = R.drawable.ic_pause, enabled = !busy)
                                NetButton(stringResource(R.string.choose_duration), onTimedAll, Modifier.weight(1f).testTag("timed_all"),
                                    icon = R.drawable.ic_clock, secondary = true, enabled = !busy)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        DeviceList(names, null, busy, onDevice, animate = true)
    }
}

@Composable
internal fun DeviceList(names: Map<String, String>, activeTarget: String?, busy: Boolean, onDevice: (String) -> Unit, animate: Boolean = false) {
    Column {
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Heading(stringResource(R.string.connected_devices), Modifier.weight(1f), MaterialTheme.typography.titleMedium)
            Text(pluralStringResource(R.plurals.device_count, LocalDevices.current.size, LocalDevices.current.size), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LocalDevices.current.forEachIndexed { index, device ->
            val name = targetName(device.id, names)
            val row: @Composable () -> Unit = {
                Row(Modifier.fillMaxWidth().heightIn(min = 77.dp).testTag("device_${device.id}")
                    .clickable(enabled = !busy, role = Role.Button, onClickLabel = stringResource(R.string.device_options, name)) {
                        onDevice(device.id)
                    }.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                    Box(Modifier.size(43.dp).background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(13.dp)),
                        contentAlignment = Alignment.Center) {
                        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.primary) { NetIcon(device.icon) }
                    }
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(name, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.labelLarge)
                            if (device.isThisPhone) {
                                Text(stringResource(R.string.you), Modifier.padding(start = 4.dp)
                                    .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 3.dp), color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp, fontSize = 9.sp))
                            }
                        }
                        Text(stringResource(R.string.device_status, device.ip,
                            stringResource(if (activeTarget == device.id || activeTarget == "all") R.string.pause_active else R.string.online)),
                            Modifier.padding(top = 3.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall)
                    }
                    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.primary) {
                        NetIcon(R.drawable.ic_chevron_right, Modifier.padding(horizontal = 8.dp))
                    }
                }
            }
            if (animate) Entrance(index + 1, row) else row()
            if (index < LocalDevices.current.lastIndex) Divider()
        }
    }
}

@Composable
private fun ActivePause(target: String, deadline: Long, names: Map<String, String>, busy: Boolean, celebrate: Boolean,
    onResume: () -> Unit, onTargets: () -> Unit) {
    val name = targetName(target, names)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Eyebrow(stringResource(R.string.pause_active))
        ConfirmationIcon(targetIcon(target), celebrate)
        Heading(if (target == "all") stringResource(R.string.all_paused) else stringResource(R.string.target_paused, name),
            Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp),
            MaterialTheme.typography.headlineLarge.copy(textAlign = TextAlign.Center))
        Text(stringResource(R.string.wifi_note), Modifier.fillMaxWidth().padding(horizontal = 6.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        if (deadline > 0L) Countdown(deadline) else {
            Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Divider()
                Heading(stringResource(R.string.until_you_resume), Modifier.padding(top = 18.dp, bottom = 8.dp),
                    MaterialTheme.typography.titleLarge.copy(textAlign = TextAlign.Center))
                SmallText(stringResource(R.string.manual_resume), Modifier.padding(bottom = 18.dp))
                Divider()
            }
        }
        NetButton(stringResource(R.string.resume_internet), onResume, Modifier.fillMaxWidth().testTag("resume"),
            icon = R.drawable.ic_play, enabled = !busy)
        SmallText(stringResource(if (deadline > 0L) R.string.early_resume else R.string.local_access), Modifier.padding(top = 12.dp))
        Column(Modifier.fillMaxWidth().padding(vertical = 24.dp)) {
            Divider()
            InfoRow(stringResource(R.string.target), name)
            Divider()
            InfoRow(stringResource(R.string.mode), stringResource(if (deadline > 0L) R.string.timed_pause else R.string.until_resumed))
            Divider()
            InfoRow(stringResource(R.string.demo_status), stringResource(R.string.simulated))
            Divider()
        }
        NetButton(stringResource(R.string.choose_another), onTargets,
            Modifier.fillMaxWidth().testTag("choose_target"), secondary = true, enabled = !busy)
    }
}

@Composable
private fun Countdown(deadline: Long) {
    val seconds by produceState(((deadline - System.currentTimeMillis() + 999L) / 1000L).coerceAtLeast(0L), deadline) {
        while (true) {
            value = ((deadline - System.currentTimeMillis() + 999L) / 1000L).coerceAtLeast(0L)
            delay(1000L)
        }
    }
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    Text(String.format(locale, "%d:%02d", seconds / 60L, seconds % 60L), Modifier.padding(top = 28.dp).testTag("countdown"),
        style = MaterialTheme.typography.headlineLarge.copy(fontSize = 64.sp, lineHeight = 74.sp,
            fontWeight = FontWeight.Medium, letterSpacing = (-3).sp, fontFeatureSettings = "tnum", textDirection = TextDirection.Ltr))
    val endTime = remember(deadline, locale) {
        DateFormat.getTimeFormat(context).format(Date(deadline))
    }
    SmallText(stringResource(R.string.remaining_until, endTime), Modifier.padding(top = 4.dp, bottom = 24.dp))
}

@Composable
internal fun ConfirmationIcon(icon: Int, celebrate: Boolean) {
    val scale = remember { Animatable(1f) }
    val ring = remember { Animatable(1f) }
    val motion = LocalMotionEnabled.current
    LaunchedEffect(celebrate, motion) {
        if (celebrate && motion) {
            scale.snapTo(0.88f)
            scale.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 600f))
        } else scale.snapTo(1f)
    }
    LaunchedEffect(celebrate, motion) {
        if (celebrate && motion) { ring.snapTo(0f); ring.animateTo(1f, tween(650)) }
        else ring.snapTo(1f)
    }
    val accent = MaterialTheme.colorScheme.primary
    Box(Modifier.padding(top = 28.dp, bottom = 20.dp).size(68.dp)
        .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
        .drawBehind {
            drawCircle(accent.copy(alpha = (1f - ring.value) * 0.3f),
                radius = size.minDimension / 2 + 24.dp.toPx() * ring.value, style = Stroke(2.dp.toPx()))
        }.background(MaterialTheme.colorScheme.surfaceContainerLow, CircleShape), contentAlignment = Alignment.Center) {
        CompositionLocalProvider(LocalContentColor provides accent) { NetIcon(icon, Modifier.size(28.dp)) }
    }
}
