package com.example.netpause

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Base64
import java.io.IOException
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URL
import java.net.URLEncoder

internal const val LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"

internal class RouterClient(context: Context, private val credentials: RouterCredentials) {
    private val context = context.applicationContext
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val cookies = CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER)
    private val host = routerHost(credentials.address)
    private var loggedIn = false
    private fun network(): Network {
        if (Build.VERSION.SDK_INT >= 37 && context.checkSelfPermission(LOCAL_NETWORK_PERMISSION) != PackageManager.PERMISSION_GRANTED)
            throw RouterException(R.string.error_permission)
        @Suppress("DEPRECATION")
        return connectivity.allNetworks.firstOrNull {
            val caps = connectivity.getNetworkCapabilities(it)
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
        } ?: throw RouterException(R.string.error_wifi)
    }
    fun localIps(): Set<String> = connectivity.getLinkProperties(network())?.linkAddresses
        ?.mapNotNull { it.address.hostAddress }?.toSet() ?: emptySet()

    private fun request(path: String, body: Map<String, String>? = null): String {
        require(path.startsWith("/") && !path.startsWith("//"))
        val url = URL("http://$host$path")
        val connection = network().openConnection(url, Proxy.NO_PROXY) as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.setRequestProperty("Referer", "http://$host/")
            val cookie = cookies.get(url.toURI(), emptyMap())["Cookie"]?.joinToString("; ")
            connection.setRequestProperty("Cookie", cookie ?: "Cookie=body:Language:english:id=-1")
            if (body != null) {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                connection.outputStream.use { it.write(form(body).toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            cookies.put(url.toURI(), connection.headerFields.filterKeys { it != null })
            if (status in 300..399) throw RouterException(R.string.error_session)
            val input = if (status >= 400) connection.errorStream else connection.inputStream
            return input?.bufferedReader(Charsets.UTF_8)?.use { reader ->
                val text = StringBuilder()
                val buffer = CharArray(4096)
                while (true) {
                    val count = reader.read(buffer)
                    if (count < 0) break
                    text.append(buffer, 0, count)
                    if (text.length > 2_000_000) throw RouterException(R.string.error_schema)
                }
                text.toString()
            } ?: ""
        } finally { connection.disconnect() }
    }

    private fun login() {
        cookies.cookieStore.removeAll()
        val page = request("/")
        val customization = Regex("\\bvar\\s+CfgMode\\s*=\\s*['\"]([^'\"]+)['\"]").find(page)?.groupValues?.get(1)
        if (customization != "TURKCELL2" || !page.contains("HG8245X6")) throw RouterException(R.string.error_firmware)
        val nonce = loginNonce(request("/asp/GetRandCount.asp", emptyMap()))
        request("/login.cgi", mapOf("UserName" to credentials.username,
            "PassWord" to Base64.encodeToString(credentials.password.toByteArray(Charsets.UTF_8), Base64.NO_WRAP),
            "Language" to "english", "x.X_HW_Token" to nonce))
        val check = request("/html/bbsp/common/GetLanUserDevInfo.asp")
        if (!check.contains("USERDevice")) throw RouterException(R.string.error_login)
        loggedIn = true
    }

    fun read(path: String, marker: String): String {
        if (!loggedIn) login()
        var value = request(path)
        if (expired(value)) {
            loggedIn = false
            login()
            value = request(path)
        }
        if (expired(value)) throw RouterException(R.string.error_session)
        if (!value.contains(marker)) throw RouterException(R.string.error_schema)
        return value
    }

    fun write(path: String, fields: Map<String, String>, tokenPage: String) {
        val page = read(tokenPage, "onttoken")
        val input = Regex("<input\\b[^>]*>", RegexOption.IGNORE_CASE).findAll(page).firstOrNull {
            Regex("name\\s*=\\s*['\"]onttoken['\"]", RegexOption.IGNORE_CASE).containsMatchIn(it.value)
        }?.value ?: throw RouterException(R.string.error_schema)
        val token = Regex("value\\s*=\\s*['\"]([^'\"]+)['\"]", RegexOption.IGNORE_CASE)
            .find(input)?.groupValues?.get(1) ?: throw RouterException(R.string.error_schema)
        // A timeout or HTTP error can follow a successful write. The caller must read back, never replay it.
        try { request(path, fields + ("x.X_HW_Token" to token)) } catch (_: IOException) { }
    }

    private fun expired(page: String): Boolean = page.contains("top.location.replace(pageName)") ||
        page.contains("name=\"PassWord\"", true) || Regex("(?:top\\.)?location(?:\\.href)?\\s*=\\s*['\"]/['\"]").containsMatchIn(page)
}

internal fun form(values: Map<String, String>): String = values.entries.joinToString("&") {
    URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value, "UTF-8")
}
