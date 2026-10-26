package com.example.netpause

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.Assert.*
import java.io.File
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URL

/** Explicitly requested phone-only checks. Ordinary test runs perform no router operations. */
class PhoneRouterTest {
    @Test fun parserCompilesOnAndroidIcu() {
        val source = """
            function ChildListClass(domain,MACAddress,Description,TemplateInst) {
                this.domain=domain; this.MACAddress=MACAddress; this.Description=Description; this.TemplateInst=TemplateInst;
            }
            function TemplatesListClass(domain,Name,StartDate,EndDate) {
                this.domain=domain; this.Name=Name; this.StartDate=StartDate; this.EndDate=EndDate;
            }
            function DurationListClass(domain,StartTime,EndTime,RepeatDay) {
                this.domain=domain; this.StartTime=StartTime; this.EndTime=EndTime; this.RepeatDay=RepeatDay;
            }
            new TemplatesListClass('$PARENTAL.Templates.1','Existing','','');
        """.trimIndent()
        assertEquals(1, parseConfiguration(source).templates.size)
    }
    @Test fun requestedPhoneCheck() {
        val args = InstrumentationRegistry.getArguments()
        val mode = args.getString("phoneRouterCheck") ?: return
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        if (mode == "probe") {
            val manager = context.getSystemService(ConnectivityManager::class.java)
            @Suppress("DEPRECATION")
            val wifi = manager.allNetworks.first { network ->
                manager.getNetworkCapabilities(network)?.let {
                    it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) && it.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                } == true
            }
            val results = listOf("internet" to "1.1.1.1", "router" to "192.168.1.1").map { (label, host) ->
                val connection = wifi.openConnection(URL("http://$host/"), Proxy.NO_PROXY) as HttpURLConnection
                val status = try {
                    connection.instanceFollowRedirects = false
                    connection.connectTimeout = 4_000
                    connection.readTimeout = 4_000
                    connection.responseCode.toString()
                } catch (_: java.io.IOException) { "unreachable" }
                finally { connection.disconnect() }
                "$label HTTP: $status"
            }
            File(context.noBackupFilesDir, "phone-check.txt").writeText("Wi-Fi network probe. " + results.joinToString(". "))
            return
        }
        val storage = AppStorage(context)
        var phase = "credentials"
        try {
            val credentials = if (mode == "login") RouterCredentials("192.168.1.1",
                args.getString("routerUser") ?: error("Missing username"), args.getString("routerPassword") ?: error("Missing password"))
                else storage.loadCredentials() ?: error("Connect in the app first")
            val client = RouterClient(context, credentials)
            phase = "device discovery"
            val devices = parseDevices(client.read("/html/bbsp/common/GetLanUserDevInfo.asp", "USERDevice"), client.localIps())
            val phone = devices.single { it.isThisPhone }
            assertNotEquals("FF:FF:FF:FF:FF:FF", phone.id)
            phase = "configuration read"
            val pauses = RouterPauses(client)
            val before = pauses.configuration()
            when (mode) {
                "inspect" -> {
                    val active = before.pause()
                    File(context.noBackupFilesDir, "phone-check.txt").writeText(
                        "Router read only. Templates: ${before.templates.size}. Bindings: ${before.bindings.size}. " +
                            "Phone pause: ${active?.target == phone.id}. Deadline: ${active?.deadline ?: 0L}.")
                }
                "login" -> {
                    phase = "credential encryption"
                    storage.saveCredentials(credentials, true)
                    assertEquals(credentials, storage.loadCredentials())
                    File(context.noBackupFilesDir, "phone-check.txt").writeText("Login, discovery, own-phone match and encrypted credential round-trip passed. Devices: ${devices.size}. Templates: ${before.templates.size}. Bindings: ${before.bindings.size}.")
                }
                "timed" -> {
                    assertNull("An existing pause must not be replaced by this test", before.pause())
                    phase = "timed phone pause"
                    val active = pauses.change(phone.id, 2, null)!!
                    assertTrue("Pause target must be this phone", phone.id == active.target)
                    assertEquals(active, pauses.status())
                    File(context.noBackupFilesDir, "phone-check.txt").writeText("Phone-only timed pause confirmed. Deadline: ${active.deadline}")
                }
                "resume" -> {
                    phase = "phone resume"
                    val active = before.pause()
                    if (active != null) {
                        assertTrue("Only the connected phone may be resumed", phone.id == active.target)
                        assertNull(pauses.change(null, null, active))
                    }
                    assertNull(pauses.status())
                    File(context.noBackupFilesDir, "phone-check.txt").writeText("No active NetPause rule remains.")
                }
                else -> error("Unknown phone check")
            }
        } catch (e: Exception) {
            // Exception messages from HTTP/cookie libraries can contain session values.
            throw AssertionError("$phase: ${e.javaClass.simpleName}\n" + e.stackTrace.take(12).joinToString("\n"))
        }
    }
}
