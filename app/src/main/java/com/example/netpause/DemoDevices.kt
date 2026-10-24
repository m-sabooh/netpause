package com.example.netpause

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

internal data class DemoDevice(val id: String, @param:StringRes val name: Int, @param:StringRes val type: Int, @param:DrawableRes val icon: Int)

internal val demoDevices = listOf(
    DemoDevice("phone", R.string.this_phone, R.string.android_phone, R.drawable.ic_smartphone),
    DemoDevice("tv", R.string.living_room_tv, R.string.smart_tv, R.drawable.ic_tv),
    DemoDevice("laptop", R.string.noors_laptop, R.string.laptop, R.drawable.ic_laptop),
    DemoDevice("tablet", R.string.bedroom_tablet, R.string.tablet, R.drawable.ic_tablet),
)

@Composable
internal fun targetName(target: String, names: List<String>): String {
    if (target == "all") return stringResource(R.string.everyone)
    val index = demoDevices.indexOfFirst { it.id == target }
    return names[index].ifBlank { stringResource(demoDevices[index].name) }
}

internal fun targetIcon(target: String): Int =
    demoDevices.find { it.id == target }?.icon ?: R.drawable.ic_wifi
