package com.example.netpause

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
internal fun TargetLabel(target: String, names: Map<String, String>) {
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(14.dp))
        .padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.primary) { NetIcon(targetIcon(target)) }
        Column(Modifier.weight(1f)) {
            Text(targetName(target, names), style = MaterialTheme.typography.labelLarge)
            SmallText(if (target == "all") pluralStringResource(R.plurals.device_count, LocalDevices.current.size, LocalDevices.current.size) else LocalDevices.current.find { it.id == target }?.ip.orEmpty())
        }
    }
}

@Composable
internal fun DeviceDetails(target: String, names: Map<String, String>, onPause: () -> Unit, onRename: () -> Unit) {
    Column {
        Spacer(Modifier.height(15.dp))
        TargetLabel(target, names)
        Spacer(Modifier.height(18.dp))
        NetButton(stringResource(R.string.pause_internet), onPause, Modifier.fillMaxWidth().testTag("device_pause"), icon = R.drawable.ic_pause)
        Spacer(Modifier.height(10.dp))
        NetButton(stringResource(R.string.rename_device), onRename, Modifier.fillMaxWidth().testTag("rename"),
            icon = R.drawable.ic_pencil, secondary = true)
        SmallText(stringResource(R.string.local_access), Modifier.padding(top = 20.dp))
    }
}

@Composable
internal fun PauseComposer(target: String, names: Map<String, String>, initiallyTimed: Boolean, onConfirm: (Int?) -> Unit) {
    var timed by rememberSaveable { mutableStateOf(initiallyTimed) }
    var minutes by rememberSaveable { mutableStateOf("20") }
    var error by rememberSaveable { mutableStateOf(false) }
    val numberScale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val motion = LocalMotionEnabled.current
    Column(Modifier.padding(top = 12.dp)) {
        Eyebrow(stringResource(R.string.one_pause))
        Heading(stringResource(R.string.how_long), Modifier.padding(top = 8.dp, bottom = 18.dp))
        TargetLabel(target, names)
        Spacer(Modifier.height(15.dp))
        DurationSelector(timed) { timed = it; error = false }
        Spacer(Modifier.height(18.dp))
        AnimatedContent(timed, transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) }, label = "Duration mode") { isTimed ->
            if (isTimed) Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(10 to R.string.ten_minutes, 20 to R.string.twenty_minutes).forEach { (value, label) ->
                        val selected = minutes.toIntOrNull() == value
                        val interaction = remember { MutableInteractionSource() }
                        OutlinedButton(onClick = {
                            minutes = value.toString()
                            error = false
                            if (motion) scope.launch {
                                numberScale.snapTo(0.96f)
                                numberScale.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 800f))
                            }
                        }, modifier = Modifier.heightIn(min = 48.dp).testTag("preset_$value").semantics { this.selected = selected }
                            .pressMotion(interaction), interactionSource = interaction,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (selected) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surface,
                                contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)) {
                            Text(stringResource(label), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                Box(Modifier.padding(top = 18.dp).graphicsLayer { scaleX = numberScale.value; scaleY = numberScale.value }) {
                    DurationField(minutes, { minutes = it; error = false }, stringResource(R.string.custom_duration), "pause_minutes", error)
                }
            } else Notice(stringResource(R.string.indefinite_note))
        }
        SmallText(stringResource(if (timed) R.string.automatic_resume else R.string.local_access), Modifier.padding(top = 20.dp))
        if (error) Text(stringResource(R.string.invalid_minutes), Modifier.padding(top = 8.dp).testTag("minutes_error"),
            color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        NetButton(stringResource(R.string.check_pause), {
            val value = minutes.toIntOrNull()
            if (timed && (value == null || value < 1)) error = true else onConfirm(if (timed) value else null)
        }, Modifier.fillMaxWidth().padding(top = 24.dp).testTag("confirm_pause"), icon = R.drawable.ic_pause)
        SmallText(stringResource(R.string.wifi_note), Modifier.padding(top = 13.dp))
    }
}

@Composable
private fun DurationSelector(timed: Boolean, onChange: (Boolean) -> Unit) {
    val position = animateFloatAsState(if (timed) 0f else 1f, tween(180), label = "Selection slide")
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val pill = MaterialTheme.colorScheme.surface
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(12.dp))
        .drawBehind {
            val inset = 5.dp.toPx()
            val gap = 4.dp.toPx()
            val width = (size.width - inset * 2 - gap) / 2
            val fraction = if (rtl) 1f - position.value else position.value
            drawRoundRect(pill, Offset(inset + (width + gap) * fraction, inset),
                Size(width, size.height - inset * 2), CornerRadius(8.dp.toPx()))
        }.padding(5.dp).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(true to R.string.timed_pause, false to R.string.until_resumed).forEach { (value, label) ->
            val interaction = remember { MutableInteractionSource() }
            TextButton({ onChange(value) }, Modifier.weight(1f).heightIn(min = 48.dp)
                .testTag(if (value) "mode_timed" else "mode_indefinite").semantics { selected = timed == value }.pressMotion(interaction),
                interactionSource = interaction,
                shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.textButtonColors(
                    contentColor = if (timed == value) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)) {
                Text(stringResource(label), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
internal fun RenameDevice(currentName: String, onSave: (String) -> Unit) {
    var name by rememberSaveable(currentName) { mutableStateOf(currentName) }
    var error by rememberSaveable { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    fun save() {
        if (name.isBlank()) error = true else { keyboard?.hide(); onSave(name.trim()) }
    }
    Column(Modifier.padding(top = 16.dp)) {
        OutlinedTextField(name, { if (it.length <= 80) name = it; error = false }, Modifier.fillMaxWidth().testTag("device_name"),
            label = { Text(stringResource(R.string.device_name)) }, shape = RoundedCornerShape(12.dp), colors = fieldColors(),
            isError = error, singleLine = true,
            supportingText = if (error) ({ Text(stringResource(R.string.name_required)) }) else null,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { save() }))
        SmallText(stringResource(R.string.name_local_note), Modifier.padding(top = 12.dp))
        NetButton(stringResource(R.string.save_name), ::save, Modifier.fillMaxWidth().padding(top = 24.dp).testTag("save_name"))
    }
}

@Composable
internal fun TargetPicker(names: Map<String, String>, activeTarget: String?, busy: Boolean, onEveryone: () -> Unit, onDevice: (String) -> Unit) {
    Column(Modifier.padding(top = 12.dp)) {
        Heading(stringResource(R.string.choose_target), Modifier.padding(bottom = 18.dp))
        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(24.dp))
            .padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NetIcon(R.drawable.ic_wifi)
            Text(stringResource(R.string.everyone), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
            NetButton(stringResource(R.string.pause), onEveryone, Modifier.testTag("target_all"))
        }
        Spacer(Modifier.height(24.dp))
        DeviceList(names, activeTarget, busy, onDevice)
    }
}

@Composable
internal fun ReplacementSheet(currentName: String, nextName: String, minutes: Int?, onReplace: () -> Unit, onKeep: () -> Unit) {
    Column(Modifier.padding(top = 16.dp)) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.primary) { NetIcon(R.drawable.ic_refresh_cw) }
        Heading(stringResource(R.string.replace_question), Modifier.padding(top = 16.dp, bottom = 10.dp))
        SmallText(stringResource(R.string.only_one_pause))
        Column(Modifier.padding(vertical = 20.dp)) {
            InfoRow(stringResource(R.string.current), currentName)
            Divider()
            InfoRow(stringResource(R.string.new_target), nextName)
            Divider()
            InfoRow(stringResource(R.string.duration), if (minutes == null) stringResource(R.string.until_resumed)
                else stringResource(R.string.for_minutes, minutes))
            Divider()
        }
        SmallText(stringResource(R.string.replace_note))
        NetButton(stringResource(R.string.replace_pause), onReplace, Modifier.fillMaxWidth().padding(top = 22.dp).testTag("replace_pause"))
        NetButton(stringResource(R.string.keep_pause), onKeep, Modifier.fillMaxWidth().padding(top = 9.dp).testTag("keep_pause"), secondary = true)
    }
}
