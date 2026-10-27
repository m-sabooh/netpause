package com.example.netpause

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
internal fun SettingsScreen(language: String, onLanguage: (String) -> Unit, widgetMinutes: Int,
    onSaveDefault: (Int) -> Unit, address: String, onConnection: () -> Unit) {
    var languageMenu by remember { mutableStateOf(false) }
    var minutes by rememberSaveable(widgetMinutes) { mutableStateOf(widgetMinutes.toString()) }
    var error by rememberSaveable { mutableStateOf(false) }
    var saved by rememberSaveable { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    Column(Modifier.padding(top = 10.dp).testTag("settings_screen")) {
        Heading(stringResource(R.string.settings))
        SettingsSection(stringResource(R.string.language)) {
            Text(stringResource(R.string.app_language), style = MaterialTheme.typography.labelMedium)
            Box(Modifier.padding(top = 8.dp)) {
                NetButton(stringResource(when (language) {
                    "ar" -> R.string.arabic
                    "en" -> R.string.english
                    else -> R.string.follow_phone
                }), { languageMenu = true }, Modifier.fillMaxWidth().testTag("language_picker"),
                    icon = R.drawable.ic_globe, secondary = true)
                DropdownMenu(languageMenu, { languageMenu = false }) {
                    listOf("system" to R.string.follow_phone, "en" to R.string.english, "ar" to R.string.arabic).forEach { (value, label) ->
                        DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = {
                            languageMenu = false
                            onLanguage(value)
                        }, modifier = Modifier.testTag("language_$value"),
                            trailingIcon = if (language == value) ({ NetIcon(R.drawable.ic_check) }) else null)
                    }
                }
            }
        }
        SettingsSection(stringResource(R.string.widget)) {
            DurationField(minutes, { minutes = it; error = false; saved = false },
                stringResource(R.string.default_timed_pause), "widget_minutes", error)
            SmallText(stringResource(R.string.widget_default_note), Modifier.padding(top = 8.dp))
            if (error) Text(stringResource(R.string.invalid_minutes), Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            NetButton(stringResource(R.string.save_default), {
                val value = minutes.toIntOrNull()
                if (value == null || value < 1) error = true else {
                    keyboard?.hide()
                    onSaveDefault(value)
                    saved = true
                }
            }, Modifier.fillMaxWidth().padding(top = 20.dp).testTag("save_default"), secondary = true)
            if (saved) SmallText(stringResource(R.string.saved), Modifier.padding(top = 8.dp).testTag("default_saved"))
        }
        SettingsSection(stringResource(R.string.router)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.router_address), style = MaterialTheme.typography.labelLarge)
                    Text(address, Modifier.padding(top = 3.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr))
                }
                NetIconButton(R.drawable.ic_pencil, stringResource(R.string.edit_connection), onConnection, Modifier.testTag("edit_connection"))
            }
            Divider()
        }
        SettingsSection(stringResource(R.string.device_names)) { SmallText(stringResource(R.string.device_names_note)) }
        SettingsSection(stringResource(R.string.shared_control)) { SmallText(stringResource(R.string.shared_control_note)) }
        SmallText(stringResource(R.string.preview_note), Modifier.padding(top = 24.dp))
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 27.dp)) {
        Eyebrow(title, Modifier.padding(bottom = 10.dp))
        content()
    }
}

@Composable
internal fun ConnectionScreen(address: String, connected: Boolean, busy: Boolean,
    onConnect: (RouterCredentials, Boolean) -> Unit, onEdit: () -> Unit, onNetwork: () -> Unit) {
    var draftAddress by rememberSaveable(address) { mutableStateOf(address) }
    // Credentials stay out of saved instance state.
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var rememberCredentials by rememberSaveable { mutableStateOf(true) }
    var error by rememberSaveable { mutableStateOf(false) }
    val usernameFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    fun connect() {
        if (busy) return
        if (draftAddress.isBlank() || username.isBlank() || password.isBlank()) error = true else {
            keyboard?.hide()
            focus.clearFocus()
            onConnect(RouterCredentials(draftAddress.trim(), username.trim(), password), rememberCredentials)
        }
    }
    Column(Modifier.padding(top = 10.dp).testTag("connection_screen")) {
        if (connected) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                ConfirmationIcon(R.drawable.ic_check, true)
                Heading(stringResource(R.string.connection_ready), Modifier.padding(bottom = 10.dp),
                    MaterialTheme.typography.headlineMedium.copy(textAlign = TextAlign.Center))
                SmallText(stringResource(R.string.devices_ready))
                NetButton(stringResource(R.string.go_to_network), onNetwork, Modifier.fillMaxWidth().padding(top = 28.dp).testTag("go_network"))
                NetButton(stringResource(R.string.edit_connection), onEdit, Modifier.fillMaxWidth().padding(top = 12.dp), secondary = true)
                SmallText(stringResource(R.string.preview_note), Modifier.padding(top = 24.dp))
            }
        } else {
            Heading(stringResource(R.string.connect_router_title), Modifier.padding(bottom = 10.dp))
            SmallText(stringResource(R.string.login_note))
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(draftAddress, { draftAddress = it; error = false }, Modifier.fillMaxWidth().testTag("router_address"),
                label = { Text(stringResource(R.string.router_address)) }, singleLine = true, enabled = !busy,
                shape = RoundedCornerShape(12.dp), colors = fieldColors(), isError = error && draftAddress.isBlank(),
                textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Ltr),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { usernameFocus.requestFocus() }))
            Spacer(Modifier.height(18.dp))
            OutlinedTextField(username, { username = it; error = false }, Modifier.fillMaxWidth().focusRequester(usernameFocus).testTag("username"),
                label = { Text(stringResource(R.string.username)) }, placeholder = { Text(stringResource(R.string.username_placeholder)) },
                singleLine = true, enabled = !busy, isError = error && username.isBlank(), shape = RoundedCornerShape(12.dp), colors = fieldColors(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next, autoCorrectEnabled = false),
                keyboardActions = KeyboardActions(onNext = { passwordFocus.requestFocus() }))
            Spacer(Modifier.height(18.dp))
            OutlinedTextField(password, { password = it; error = false }, Modifier.fillMaxWidth().focusRequester(passwordFocus).testTag("password"),
                label = { Text(stringResource(R.string.password)) }, placeholder = { Text(stringResource(R.string.password_placeholder)) },
                singleLine = true, enabled = !busy, isError = error && password.isBlank(), shape = RoundedCornerShape(12.dp), colors = fieldColors(),
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    NetIconButton(R.drawable.ic_eye, stringResource(if (showPassword) R.string.hide_password else R.string.show_password),
                        { showPassword = !showPassword })
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { connect() }))
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)
                .toggleable(rememberCredentials, enabled = !busy, role = Role.Checkbox, onValueChange = { rememberCredentials = it }),
                verticalAlignment = Alignment.CenterVertically) {
                Checkbox(rememberCredentials, null, enabled = !busy)
                Text(stringResource(R.string.remember_credentials), style = MaterialTheme.typography.bodyMedium)
            }
            if (error) Text(stringResource(R.string.connection_required), Modifier.padding(bottom = 12.dp).testTag("connection_error"),
                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            if (busy) Notice(stringResource(R.string.checking_description), Modifier.padding(bottom = 12.dp), title = stringResource(R.string.checking))
            NetButton(stringResource(R.string.connect_router), ::connect, Modifier.fillMaxWidth().testTag("connect"), icon = R.drawable.ic_router, enabled = !busy)
            Notice(stringResource(R.string.same_wifi) + "\n" + stringResource(R.string.router_model), modifier = Modifier.padding(top = 18.dp))
            SmallText(stringResource(R.string.credentials_demo), Modifier.padding(top = 12.dp))
        }
    }
}
