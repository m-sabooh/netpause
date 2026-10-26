package com.example.netpause

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.res.stringResource

internal val LocalDevices = staticCompositionLocalOf<List<RouterDevice>> { emptyList() }

@Composable
internal fun targetName(target: String, names: Map<String, String>): String {
    if (target == "all") return stringResource(R.string.everyone)
    return names[target] ?: LocalDevices.current.find { it.id == target }?.let {
        if (it.isThisPhone) stringResource(R.string.this_phone) else it.name
    } ?: target
}

@Composable
internal fun targetIcon(target: String): Int = LocalDevices.current.find { it.id == target }?.icon ?: R.drawable.ic_wifi
