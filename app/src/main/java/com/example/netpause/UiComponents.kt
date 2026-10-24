package com.example.netpause

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

internal val LocalMotionEnabled = staticCompositionLocalOf { true }

@Composable
internal fun rememberMotionEnabled(): Boolean {
    val resolver = LocalContext.current.contentResolver
    fun enabled() = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    var enabled by remember(resolver) { mutableStateOf(enabled()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { enabled = enabled() }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return enabled
}

@Composable
internal fun NetIcon(@DrawableRes icon: Int, modifier: Modifier = Modifier, description: String? = null) {
    Icon(painterResource(icon), description, modifier.size(21.dp))
}

@Composable
internal fun Modifier.pressMotion(source: MutableInteractionSource): Modifier {
    val pressed by source.collectIsPressedAsState()
    val motion = LocalMotionEnabled.current
    val scale = animateFloatAsState(if (pressed && motion) 0.96f else 1f,
        spring(dampingRatio = 0.65f, stiffness = 900f), label = "Touch compression")
    return graphicsLayer { scaleX = scale.value; scaleY = scale.value }
}

@Composable
internal fun NetButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    secondary: Boolean = false,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(14.dp)
    val buttonModifier = modifier.heightIn(min = 48.dp).pressMotion(interaction)
    val content: @Composable RowScope.() -> Unit = {
        if (icon != null) { NetIcon(icon, Modifier.size(19.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
    if (secondary) {
        OutlinedButton(onClick, buttonModifier, enabled, shape,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            interactionSource = interaction, content = content)
    } else {
        Button(onClick, buttonModifier, enabled, shape,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            interactionSource = interaction, content = content)
    }
}

@Composable
internal fun NetIconButton(
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    spinning: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val rotation = remember { Animatable(0f) }
    val motion = LocalMotionEnabled.current
    LaunchedEffect(spinning, motion) {
        rotation.snapTo(0f)
        if (motion && spinning) while (true) {
            rotation.animateTo(360f, tween(1000, easing = LinearEasing))
            rotation.snapTo(0f)
        }
    }
    IconButton(onClick, modifier.size(48.dp).pressMotion(interaction), enabled = enabled,
        interactionSource = interaction) {
        NetIcon(icon, Modifier.graphicsLayer { rotationZ = rotation.value }, label)
    }
}

@Composable
internal fun Entrance(index: Int = 0, content: @Composable () -> Unit) {
    val motion = LocalMotionEnabled.current
    val progress = remember { Animatable(if (motion) 0f else 1f) }
    val distance = with(LocalDensity.current) { 12.dp.toPx() }
    LaunchedEffect(motion) {
        if (motion) {
            delay(index * 30L)
            progress.animateTo(1f, tween(300))
        } else progress.snapTo(1f)
    }
    Box(Modifier.graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * distance
    }) { content() }
}

@Composable
internal fun Heading(text: String, modifier: Modifier = Modifier, style: TextStyle = MaterialTheme.typography.headlineMedium) {
    Text(text, modifier.semantics { heading() }, style = style)
}

@Composable
internal fun SmallText(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
}

@Composable
internal fun Eyebrow(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(LocalConfiguration.current.locales[0]), modifier,
        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
}

@Composable
internal fun Notice(text: String, modifier: Modifier = Modifier, title: String? = null) {
    Surface(modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(3.dp).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
            Column(Modifier.padding(12.dp)) {
                if (title != null) Text(title, style = MaterialTheme.typography.labelLarge)
                Text(text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
internal fun DurationField(value: String, onChange: (String) -> Unit, label: String, tag: String, error: Boolean = false) {
    val keyboard = LocalSoftwareKeyboardController.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(value, onChange, Modifier.width(116.dp).testTag(tag).semantics { contentDescription = label },
                singleLine = true, isError = error,
                shape = RoundedCornerShape(12.dp),
                textStyle = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Normal,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center, textDirection = TextDirection.Ltr),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }),
                colors = fieldColors())
            SmallText(stringResource(R.string.minutes))
        }
    }
}

@Composable
internal fun fieldColors() = OutlinedTextFieldDefaults.colors(
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    cursorColor = MaterialTheme.colorScheme.primary,
)

@Composable
internal fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        SmallText(label, Modifier.weight(1f))
        Text(value, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}

@Composable
internal fun Divider() { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) }
