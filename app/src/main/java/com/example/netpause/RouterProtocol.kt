package com.example.netpause

import java.net.URI
import java.util.Locale

internal class RouterException(val messageId: Int) : Exception()

internal fun loginNonce(body: String): String = body.removePrefix("\uFEFF").trim().trim('"', '\'').also {
    if (!Regex("[a-fA-F0-9]{64}").matches(it)) throw RouterException(R.string.error_schema)
}

internal fun routerHost(value: String): String {
    val uri = try { URI(if (value.contains("://")) value else "http://$value") }
        catch (_: Exception) { throw RouterException(R.string.error_address) }
    val parts = uri.host?.split('.')?.map { it.toIntOrNull() } ?: emptyList()
    if (uri.scheme != "http" || uri.userInfo != null || uri.port != -1 ||
        uri.rawQuery != null || uri.rawFragment != null || uri.path !in listOf("", "/") ||
        parts.size != 4 || parts.any { it == null || it !in 0..255 } ||
        !(parts[0] == 10 || parts[0] == 192 && parts[1] == 168 || parts[0] == 172 && parts[1]!! in 16..31)) {
        throw RouterException(R.string.error_address)
    }
    return parts.joinToString(".")
}

internal fun macAddress(value: String): String {
    val mac = value.uppercase(Locale.ROOT)
    if (!Regex("[0-9A-F]{2}(:[0-9A-F]{2}){5}").matches(mac)) throw RouterException(R.string.error_schema)
    return mac
}

internal data class JsRecord(val constructor: String, val values: List<String>)

// Parse only literal constructor arguments. Router JavaScript is never executed.
internal fun constructorRecords(source: String): List<JsRecord> {
    val records = mutableListOf<JsRecord>()
    val start = Regex("\\bnew\\s+([A-Za-z_][A-Za-z_0-9]*)\\s*\\(")
    var offset = 0
    while (true) {
        val match = start.find(source, offset) ?: break
        var i = match.range.last + 1
        val args = mutableListOf<String>()
        fun whitespace() { while (i < source.length && source[i].isWhitespace()) i++ }
        fun requireChar() { if (i >= source.length) throw RouterException(R.string.error_schema) }
        whitespace()
        // Built-in arrays may contain expressions; only data constructors are relevant.
        if (source.getOrNull(i) !in listOf('\'', '"')) { offset = i; continue }
        while (true) {
            whitespace(); requireChar()
            val quote = source[i++]
            if (quote != '\'' && quote != '"') throw RouterException(R.string.error_schema)
            val value = StringBuilder()
            while (true) {
                requireChar()
                val c = source[i++]
                if (c == quote) break
                if (c != '\\') { value.append(c); continue }
                requireChar()
                when (val escape = source[i++]) {
                    'x', 'u' -> {
                        val count = if (escape == 'x') 2 else 4
                        val hex = source.substring(i, (i + count).coerceAtMost(source.length))
                        val code = hex.takeIf { it.length == count }?.toIntOrNull(16)
                            ?: throw RouterException(R.string.error_schema)
                        value.append(code.toChar()); i += count
                    }
                    'n' -> value.append('\n')
                    'r' -> value.append('\r')
                    't' -> value.append('\t')
                    '\\', '\'', '"', '/' -> value.append(escape)
                    else -> throw RouterException(R.string.error_schema)
                }
            }
            args += value.toString()
            whitespace(); requireChar()
            if (source[i++] == ')') break
            if (source[i - 1] != ',') throw RouterException(R.string.error_schema)
        }
        records += JsRecord(match.groupValues[1], args)
        offset = i
    }
    return records
}

internal data class RouterDevice(val id: String, val name: String, val ip: String, val isThisPhone: Boolean = false) {
    val icon: Int get() = if (isThisPhone) R.drawable.ic_smartphone else R.drawable.ic_wifi
}

internal fun parseDevices(source: String, localIps: Set<String>): List<RouterDevice> {
    val records = constructorRecords(source).filter { it.constructor == "USERDevice" }
    if (records.isEmpty() && !source.contains("function USERDevice")) throw RouterException(R.string.error_schema)
    return records.mapNotNull { record ->
        val v = record.values
        if (v.size != 18) throw RouterException(R.string.error_schema)
        if (!v[6].equals("Online", true)) null else RouterDevice(macAddress(v[2]),
            v[13].ifBlank { v[9] }.ifBlank { v[1] }.ifBlank { v[2] }, v[1], v[1] in localIps)
    }.distinctBy { it.id }.sortedByDescending { it.isThisPhone }
}
