package com.example.netpause

import androidx.core.content.edit

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException

internal data class NetworkState(
    val devices: List<RouterDevice> = emptyList(), val pause: RouterPause? = null,
    val busy: Boolean = false, val connected: Boolean = false, val stale: Boolean = true, val error: Int? = null,
    val connectionRevision: Int = 0,
)
internal data class RouterPause(val id: String, val target: String, val deadline: Long)

internal class NetworkController(application: Application) : AndroidViewModel(application) {
    private val storage = AppStorage(application)
    val preferences get() = storage.preferences
    var state by mutableStateOf(NetworkState())
        private set
    var address by mutableStateOf(preferences.getString("address", "192.168.1.1")!!)
        private set
    var names by mutableStateOf(storage.names(address))
        private set
    private var client: RouterClient? = null

    init {
        execute {
            storage.loadCredentials()?.let {
                client = RouterClient(application, it)
                readState()
            }
        }
    }

    fun connect(value: RouterCredentials, remember: Boolean) = execute {
        val credentials = value.copy(address = routerHost(value.address))
        val candidate = RouterClient(getApplication(), credentials)
        val devices = parseDevices(candidate.read("/html/bbsp/common/GetLanUserDevInfo.asp", "USERDevice"), candidate.localIps())
        val pause = RouterPauses(candidate).status()
        storage.saveCredentials(credentials, remember)
        withContext(Dispatchers.Main) {
            client = candidate
            address = credentials.address
            preferences.edit { putString("address", address) }
            names = storage.names(address)
            state = state.copy(devices = devices, pause = pause, connected = true, stale = false,
                connectionRevision = state.connectionRevision + 1)
        }
    }

    fun refresh() = execute { readState() }
    private suspend fun readState() {
        val router = client ?: return
        val devices = parseDevices(router.read("/html/bbsp/common/GetLanUserDevInfo.asp", "USERDevice"), router.localIps())
        val pause = RouterPauses(router).status()
        withContext(Dispatchers.Main) { state = state.copy(devices = devices, pause = pause, connected = true, stale = false) }
    }

    fun changePause(target: String?, minutes: Int?) {
        val expected = state.pause
        if (state.stale || !state.connected) {
            state = state.copy(error = R.string.stale_status)
            return
        }
        execute {
            val router = client ?: throw RouterException(R.string.error_session)
            val pause = RouterPauses(router).change(target, minutes, expected)
            withContext(Dispatchers.Main) { state = state.copy(pause = pause, stale = false) }
        }
    }

    fun rename(id: String, name: String) {
        storage.rename(address, id, name)
        names = storage.names(address)
    }

    private fun execute(block: suspend () -> Unit) {
        if (state.busy) return
        state = state.copy(busy = true, error = null)
        viewModelScope.launch {
            try { withContext(Dispatchers.IO) { block() } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (e !is RouterException) android.util.Log.e("NetPause", e.javaClass.simpleName + "\n" + e.stackTrace.take(8).joinToString("\n"))
                state = state.copy(stale = true, error = (e as? RouterException)?.messageId ?: R.string.error_network)
            }
            finally { state = state.copy(busy = false) }
        }
    }
}
