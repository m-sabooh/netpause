package com.example.netpause

import org.junit.Assert.*
import org.junit.Test

class RouterProtocolTest {
    @Test fun nonceRemovesFirmwareByteOrderMarkWithoutAcceptingErrorPages() {
        val nonce = "ab".repeat(32)
        assertEquals(nonce, loginNonce("\uFEFF$nonce\r\n"))
        assertThrows(RouterException::class.java) { loginNonce("<html>login failed</html>") }
    }
    @Test fun privateRouterAddressRejectsCredentialExfiltration() {
        assertEquals("192.168.1.1", routerHost("http://192.168.1.1/"))
        listOf("https://192.168.1.1", "8.8.8.8", "localhost", "192.168.1.1@evil.example", "192.168.1.1/path",
            "192.168.1.1?x=y", "192.168.1.999", "192.168.1.1:8080", "127.0.0.1").forEach {
            assertThrows(RouterException::class.java) { routerHost(it) }
        }
    }

    @Test fun literalsPreserveCommasEscapesAndUnicodeWithoutExecutingCode() {
        val records = constructorRecords("""new Device('domain', 'TV, kid\'s (room)', 'AA\x3aBB', '\u0627');""")
        assertEquals(listOf("domain", "TV, kid's (room)", "AA:BB", "ا"), records.single().values)
        assertThrows(RouterException::class.java) { constructorRecords("new Device('a', steal())") }
        assertThrows(RouterException::class.java) { constructorRecords("new Device('unterminated)") }
    }

    @Test fun discoveryFiltersOfflineAndMatchesOwnWifiAddress() {
        fun row(ip: String, mac: String, status: String) = "new USERDevice(" +
            listOf("domain", ip, mac, "", "", "", status, "", "", "Phone, one", "1", "0", "", "", "", "", "", "")
                .joinToString(",") { "'$it'" } + ")"
        val devices = parseDevices(row("192.168.1.5", "02:00:00:00:00:01", "Online") +
            row("192.168.1.6", "02:00:00:00:00:02", "Offline"), setOf("192.168.1.5"))
        assertEquals(1, devices.size)
        assertTrue(devices.single().isThisPhone)
        assertEquals("Phone, one", devices.single().name)
        assertThrows(RouterException::class.java) { parseDevices("<html>login</html>", emptySet()) }
    }
}
