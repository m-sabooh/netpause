package com.example.netpause

import androidx.core.content.edit

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import android.util.Base64
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal data class RouterCredentials(val address: String, val username: String, val password: String) {
    override fun toString() = "RouterCredentials(redacted)"
}

internal class AppStorage(context: Context) {
    val preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val credentialsFile = AtomicFile(File(context.noBackupFilesDir, "router.bin"))
    val hasCredentials get() = credentialsFile.baseFile.exists()
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("netpause.router", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("netpause.router", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    fun loadCredentials(): RouterCredentials? {
        if (!credentialsFile.baseFile.exists()) return null
        try {
            val value = JSONObject(credentialsFile.openRead().bufferedReader().use { it.readText() })
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(value.getString("iv"), Base64.NO_WRAP)))
            val data = JSONObject(String(cipher.doFinal(Base64.decode(value.getString("data"), Base64.NO_WRAP)), Charsets.UTF_8))
            return RouterCredentials(data.getString("address"), data.getString("username"), data.getString("password"))
        } catch (_: Exception) { throw RouterException(R.string.error_storage) }
    }

    fun saveCredentials(value: RouterCredentials, remember: Boolean) {
        if (!remember) { credentialsFile.delete(); return }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val data = JSONObject().put("address", value.address).put("username", value.username).put("password", value.password)
        val encoded = JSONObject().put("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .put("data", Base64.encodeToString(cipher.doFinal(data.toString().toByteArray(Charsets.UTF_8)), Base64.NO_WRAP))
        val stream = credentialsFile.startWrite()
        try { stream.write(encoded.toString().toByteArray(Charsets.UTF_8)); credentialsFile.finishWrite(stream) }
        catch (e: Exception) { credentialsFile.failWrite(stream); throw e }
    }

    fun names(address: String): Map<String, String> = preferences.all.filterKeys { it.startsWith("name:$address:") }
        .mapKeys { it.key.removePrefix("name:$address:") }.mapValues { it.value as String }
    fun rename(address: String, id: String, name: String) { preferences.edit { putString("name:$address:$id", name) } }
}
