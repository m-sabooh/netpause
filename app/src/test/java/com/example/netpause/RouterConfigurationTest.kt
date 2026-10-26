package com.example.netpause

import org.junit.Assert.*
import org.junit.Test

class RouterConfigurationTest {
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
    private val owner = "NP1_012345abcdef_1792923000000"
    private val template = "new TemplatesListClass('$PARENTAL.Templates.4','$owner','20261025','20261025');"
    private val binding = "new ChildListClass('$PARENTAL.MAC.2','02:00:00:00:00:01','$owner','4');"

    @Test fun anotherInstallationCanRecoverOwnedPauseFromRouterAlone() {
        val duplicate = template.replace("TemplatesListClass", "TemplatesListExClass")
        val stats = "new StatsListClass('$PARENTAL.Templates.4.Stats','0','0','0','0');"
        val parsed = parseConfiguration(schema + template + duplicate + stats + binding)
        val pause = parsed.pause()!!
        assertEquals("02:00:00:00:00:01", pause.target)
        assertEquals(1792923000000L, pause.deadline)
        assertEquals(1, parsed.ownedBindings().size)
    }
    @Test fun unrelatedRulesAndChangedOwnershipAreNeverClaimed() {
        val parsed = parseConfiguration(schema + template + binding.replace(owner, "Household rule"))
        assertNull(parsed.pause())
        assertTrue(parsed.ownedBindings().isEmpty())
        assertEquals(1, parsed.bindings.size)
    }
    @Test fun malformedSchemaAndConcurrentPausesFailVisibly() {
        assertThrows(RouterException::class.java) { parseConfiguration("login page") }
        assertThrows(RouterException::class.java) { parseConfiguration(schema + template + binding.replace("'4'", "'9'")).pause() }
        assertThrows(RouterException::class.java) {
            parseConfiguration(schema + template + binding + binding.replace("MAC.2", "MAC.3")).pause()
        }
        assertNull(parseConfiguration(schema).pause())
    }
}
