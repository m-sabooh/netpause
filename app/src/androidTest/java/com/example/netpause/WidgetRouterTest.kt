package com.example.netpause

import android.app.Application
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.core.content.edit
import androidx.test.platform.app.InstrumentationRegistry
import java.net.Inet4Address
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Emulator-only HTTP fixture. It binds the emulator's own address and never contacts a household router. */
class WidgetRouterTest {
    @Test fun widgetActionsUseRouterReadbackAndRejectOldTapsAfterAnAppPause() = runBlocking {
        assumeTrue(Build.HARDWARE in setOf("ranchu", "goldfish"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val storage = AppStorage(context)
        assumeTrue("Do not replace an installation's remembered login", !storage.hasCredentials)
        val manager = context.getSystemService(ConnectivityManager::class.java)
        @Suppress("DEPRECATION")
        val wifi = manager.allNetworks.first {
            manager.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
        val host = manager.getLinkProperties(wifi)!!.linkAddresses.first { it.address is Inet4Address }.address.hostAddress!!
        assumeTrue("Only the emulator's private interface may host this fixture", host.startsWith("10.0.2."))
        assumeTrue(java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) < 23)
        val savedHost = storage.preferences.getString("address", "192.168.1.1")!!
        val credentials = RouterCredentials(host, "fixture-user", "fixture-password")
        val fixture = WidgetRouterFixture(host)
        try {
            storage.saveCredentials(credentials, remember = true)
            val controller = restoredController(context, fixture)
            controller.refresh().join()
            assertNull(controller.state.error)
            controller.widgetAction(WidgetAction.Timed, "", host, System.currentTimeMillis(), 2).join()
            assertNull(controller.state.error)
            val timed = controller.state.pause!!
            assertEquals("all", timed.target)
            assertTrue(timed.deadline > System.currentTimeMillis())
            assertEquals(timed, fixture.configuration().pause())
            controller.widgetAction(WidgetAction.Resume, widgetPauseKey(timed), host, System.currentTimeMillis(), 2).join()
            assertNull(controller.state.pause)
            assertNull(controller.state.error)

            val phone = "02:00:00:00:00:01"
            controller.changePause(phone, null)
            // Refresh queues behind the app operation, exercising the same serialization as a widget worker.
            controller.refresh().join()
            val individual = controller.state.pause!!
            assertEquals(phone, individual.target)
            controller.widgetAction(WidgetAction.Resume, widgetPauseKey(individual), host, System.currentTimeMillis(), 2).join()
            assertNull(controller.state.pause)
            controller.widgetAction(WidgetAction.Indefinite, "", host, System.currentTimeMillis(), 2).join()
            val all = controller.state.pause!!
            assertEquals("all", all.target)
            assertEquals(0L, all.deadline)

            val writes = fixture.writes.get()
            for ((action, expected) in listOf(WidgetAction.Resume to widgetPauseKey(individual), WidgetAction.Timed to "")) {
                controller.widgetAction(action, expected, host, System.currentTimeMillis(), 2).join()
                assertEquals(R.string.error_conflict, controller.state.error)
                assertEquals(all, fixture.configuration().pause())
                assertEquals(writes, fixture.writes.get())
            }
            // Simulate death between committing the encrypted login and its display preferences.
            storage.preferences.edit { putString("address", "192.168.99.1") }
            val restarted = restoredController(context, fixture)
            restarted.widgetAction(WidgetAction.Refresh, "", host, System.currentTimeMillis(), 2).join()
            assertEquals(host, restarted.address)
            assertEquals(all, restarted.state.pause)
            restarted.widgetAction(WidgetAction.Resume, widgetPauseKey(all), host, System.currentTimeMillis(), 2).join()
            assertNull(restarted.state.error)
            assertNull(fixture.configuration().pause())
            assertEquals("Household", fixture.configuration().templates.single().field("Name"))
        } finally {
            fixture.close()
            storage.saveCredentials(credentials, remember = false)
            storage.preferences.edit { putString("address", savedHost) }
        }
    }

    private fun restoredController(context: android.content.Context, fixture: WidgetRouterFixture): NetworkController {
        lateinit var controller: NetworkController
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            controller = NetworkController(context.applicationContext as Application)
            val field = NetworkController::class.java.getDeclaredField("client").apply { isAccessible = true }
            val timeout = android.os.SystemClock.elapsedRealtime() + 3000
            var client = field.get(controller) as? RouterClient
            // restoreClient waits for the main thread before HTTP, allowing a fixture-only port override.
            while (client == null && android.os.SystemClock.elapsedRealtime() < timeout) {
                android.os.SystemClock.sleep(5)
                client = field.get(controller) as? RouterClient
            }
            assertNotNull("Remembered credentials must restore the client", client)
            RouterClient::class.java.getDeclaredField("host").apply { isAccessible = true }
                .set(client, "${fixture.host}:${fixture.port}")
        }
        return controller
    }
}

private class WidgetRouterFixture(val host: String) : AutoCloseable {
    val writes = AtomicInteger()
    private val server = ServerSocket(0, 16, InetAddress.getByName(host))
    val port get() = server.localPort
    private val objects = linkedMapOf("$PARENTAL.Templates.1" to linkedMapOf("Name" to "Household", "StartDate" to "", "EndDate" to ""))
    private var nextTemplate = 2
    private val schema = """
        function TemplatesListClass(domain,Name,StartDate,EndDate) {
            this.domain=domain; this.Name=Name; this.StartDate=StartDate; this.EndDate=EndDate;
        }
        function ChildListClass(domain,MACAddress,Description,TemplateInst) {
            this.domain=domain; this.MACAddress=MACAddress; this.Description=Description; this.TemplateInst=TemplateInst;
        }
        function DurationListClass(domain,StartTime,EndTime,RepeatDay) {
            this.domain=domain; this.StartTime=StartTime; this.EndTime=EndTime; this.RepeatDay=RepeatDay;
        }
    """.trimIndent()

    init {
        thread(isDaemon = true, name = "widget-router-fixture") {
            while (!server.isClosed) {
                try {
                    server.accept().use { socket ->
                        socket.soTimeout = 5000
                        val reader = socket.getInputStream().bufferedReader()
                        val request = reader.readLine().split(' ')
                        var length = 0
                        while (true) {
                            val header = reader.readLine() ?: break
                            if (header.isEmpty()) break
                            if (header.startsWith("Content-Length:", true)) length = header.substringAfter(':').trim().toInt()
                        }
                        val body = CharArray(length)
                        var offset = 0
                        while (offset < length) {
                            val count = reader.read(body, offset, length - offset)
                            check(count > 0)
                            offset += count
                        }
                        val response = respond(URI(request[1]), String(body)).toByteArray()
                        socket.getOutputStream().apply {
                            write("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${response.size}\r\nConnection: close\r\n\r\n".toByteArray())
                            write(response); flush()
                        }
                    }
                } catch (e: Exception) { if (!server.isClosed) throw e }
            }
        }
    }

    @Synchronized fun configuration() = parseConfiguration(configurationText())
    private fun configurationText(): String = schema + objects.entries.joinToString("\n") { (domain, fields) ->
        val (constructor, keys) = when {
            ".MAC." in domain -> "ChildListClass" to listOf("MACAddress", "Description", "TemplateInst")
            ".Duration." in domain -> "DurationListClass" to listOf("StartTime", "EndTime", "RepeatDay")
            else -> "TemplatesListClass" to listOf("Name", "StartDate", "EndDate")
        }
        "new $constructor(" + (listOf(domain) + keys.map { fields.getValue(it) }).joinToString(",") { "'$it'" } + ");"
    }
    private fun fields(form: String) = form.split('&').filter { it.isNotEmpty() }.associate {
        URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('=', ""), "UTF-8")
    }
    @Synchronized private fun respond(uri: URI, body: String): String {
        val data = fields(body)
        val query = fields(uri.rawQuery ?: "")
        return when {
            uri.path == "/" -> "HG8245X6; var CfgMode='TURKCELL2';"
            uri.path == "/asp/GetRandCount.asp" -> "a".repeat(64)
            uri.path == "/login.cgi" -> "ok"
            uri.path.endsWith("GetLanUserDevInfo.asp") -> "function USERDevice() {}"
            uri.path.endsWith("parentalctrlinfo.asp") -> configurationText()
            uri.path.endsWith("deviceinfo.asp") -> "var systemdsttime='${SimpleDateFormat("yyyy-MM-dd HH:mm:ssXXX", Locale.ROOT).apply {
                timeZone = java.util.TimeZone.getTimeZone("GMT+03:00")
            }.format(java.util.Date())}';"
            uri.path.endsWith("add.cgi") -> {
                writes.incrementAndGet()
                val domain = query["x_SAVE_A"]?.let { "$it.${nextTemplate++}" } ?: "${query.getValue("x")}.1"
                val prefix = if ("x_SAVE_A" in query) "x_SAVE_A." else "x."
                objects[domain] = LinkedHashMap(data.filterKeys { it.startsWith(prefix) && it != "x.X_HW_Token" }.mapKeys { it.key.removePrefix(prefix) })
                "ok"
            }
            uri.path.endsWith("del.cgi") -> {
                writes.incrementAndGet()
                data.keys.filter { it.startsWith(PARENTAL) }.forEach { domain ->
                    objects.keys.removeAll { it == domain || it.startsWith("$domain.") }
                }
                "ok"
            }
            else -> "<input name=\"onttoken\" value=\"fixture-token\">"
        }
    }
    override fun close() = server.close()
}
