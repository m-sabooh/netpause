package com.example.netpause

import android.app.Application
import android.content.Context

class NetPauseApplication : Application() {
    internal val controller by lazy { NetworkController(this) }
}

internal val Context.networkController: NetworkController
    get() = (applicationContext as NetPauseApplication).controller
