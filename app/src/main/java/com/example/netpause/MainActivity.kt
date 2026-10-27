package com.example.netpause

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.activity.result.contract.ActivityResultContracts
import android.app.AlertDialog
import android.content.Intent
import android.content.res.Configuration
import android.content.pm.PackageManager
import androidx.core.net.toUri
import android.os.Build
import android.provider.Settings
import android.view.ContextThemeWrapper
import java.util.Locale

class MainActivity : ComponentActivity() {
  private val controller: NetworkController by viewModels()
  private var pendingNetworkAction: (() -> Unit)? = null
  private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
    val action = pendingNetworkAction
    pendingNetworkAction = null
    if (granted) action?.invoke()
    else if (!shouldShowRequestPermissionRationale(LOCAL_NETWORK_PERMISSION)) {
      permissionDialog().setMessage(R.string.error_permission)
        .setPositiveButton(R.string.open_settings) { _, _ ->
          startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri()))
        }.setNegativeButton(android.R.string.cancel, null).show()
    }
  }

  private fun permissionDialog(): AlertDialog.Builder {
    val language = controller.preferences.getString("language", "system")!!
    val configuration = Configuration(resources.configuration).apply {
      if (language != "system") setLocale(Locale.forLanguageTag(language))
    }
    return AlertDialog.Builder(ContextThemeWrapper(this, theme).apply { applyOverrideConfiguration(configuration) })
  }

  private fun withNetworkPermission(action: () -> Unit) {
    if (Build.VERSION.SDK_INT < 37 || checkSelfPermission(LOCAL_NETWORK_PERMISSION) == PackageManager.PERMISSION_GRANTED) action()
    else {
      pendingNetworkAction = action
      if (shouldShowRequestPermissionRationale(LOCAL_NETWORK_PERMISSION)) {
        permissionDialog().setMessage(R.string.error_permission)
          .setPositiveButton(android.R.string.ok) { _, _ -> permission.launch(LOCAL_NETWORK_PERMISSION) }
          .setNegativeButton(android.R.string.cancel) { _, _ -> pendingNetworkAction = null }.show()
      } else permission.launch(LOCAL_NETWORK_PERMISSION)
    }
  }
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
      navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
    )
    setContent { NetPauseApp(controller, ::withNetworkPermission) }
  }
}
