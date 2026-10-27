package com.example.netpause

import java.util.UUID
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date
import java.util.Calendar
import kotlin.math.abs

private const val INFO = "/html/bbsp/common/parentalctrlinfo.asp"
private const val MAC_PAGE = "/html/bbsp/parentalctrl/parentalctrlmac.asp"
private const val TEMPLATE_PAGE = "/html/bbsp/parentalctrl/parentalctrltemplate.asp"
private const val CONTROL = "/html/bbsp/parentalctrl/"

internal class RouterPauses(private val client: RouterClient) {
    fun configuration() = parseConfiguration(client.read(INFO, "TemplateInst"))

    fun status(): RouterPause? {
        val config = configuration()
        val pause = config.pause() ?: return null
        val binding = config.ownedBindings().single()
        val template = config.template(binding)
        val periods = config.periods.filter { it.domain.startsWith("${template.domain}.Duration.") }
            .map { AllowedPeriod(it.field("StartTime"), it.field("EndTime"), it.field("RepeatDay")) }
        if (pause.deadline == 0L) {
            if (template.field("StartDate").isNotEmpty() || template.field("EndDate").isNotEmpty() ||
                periods.size != 1 || periods.single().start != "00:00" || periods.single().end != "00:01" ||
                periods.single().days !in setOf("1", "2", "3", "4", "5", "6", "7"))
                throw RouterException(R.string.error_conflict)
            return pause
        }
        val clock = routerClock(client.read("/html/ssmp/deviceinfo/deviceinfo.asp", "systemdsttime"))
        if (abs(clock.timeInMillis - System.currentTimeMillis()) > 90_000) throw RouterException(R.string.error_clock)
        fun format(pattern: String) = SimpleDateFormat(pattern, Locale.ROOT).apply { timeZone = clock.timeZone }.format(Date(pause.deadline))
        val endClock = (clock.clone() as Calendar).apply { timeInMillis = pause.deadline }
        val day = ((endClock.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1).toString()
        if (template.field("StartDate") != format("yyyyMMdd") || template.field("EndDate") != format("yyyyMMdd") ||
            periods != listOf(AllowedPeriod(format("HH:mm"), "23:59", day)))
            throw RouterException(R.string.error_conflict)
        if (clock.timeInMillis >= pause.deadline) {
            removeBinding(config, binding)
            return configuration().pause()
        }
        return pause
    }

    fun change(target: String?, minutes: Int?, expected: RouterPause?): RouterPause? {
        val before = configuration()
        if (before.pause() != expected) throw RouterException(R.string.error_conflict)
        if (target == null) {
            val old = before.ownedBindings().singleOrNull() ?: return null
            removeBinding(before, old)
            return configuration().pause()
        }
        val mac = if (target == "all") "FF:FF:FF:FF:FF:FF" else macAddress(target)
        val conflicts = before.bindings - before.ownedBindings().toSet()
        if (conflicts.any { target == "all" || it.field("MACAddress").equals(mac, true) ||
                it.field("MACAddress").equals("FF:FF:FF:FF:FF:FF", true) }) throw RouterException(R.string.error_conflict)
        if (before.templates.size >= 8 || before.bindings.size >= 8) throw RouterException(R.string.error_capacity)
        val clock = routerClock(client.read("/html/ssmp/deviceinfo/deviceinfo.asp", "systemdsttime"))
        val schedule = if (minutes == null) indefiniteSchedule(clock) else timedSchedule(clock, System.currentTimeMillis(), minutes)
        val name = "NP1_${UUID.randomUUID().toString().replace("-", "").take(12)}_${schedule.deadline}"
        client.write(CONTROL + "add.cgi?" + form(mapOf("x_SAVE_A" to "$PARENTAL.Templates",
            "RequestFile" to "html/bbsp/parentalctrl/parentalctrltime.asp")),
            mapOf("x_SAVE_A.Name" to name, "x_SAVE_A.StartDate" to schedule.startDate, "x_SAVE_A.EndDate" to schedule.endDate), TEMPLATE_PAGE)
        val template = configuration().templates.singleOrNull { it.field("Name") == name }
            ?: throw RouterException(R.string.error_network)
        try {
            schedule.periods.forEach { period ->
                client.write(CONTROL + "add.cgi?" + form(mapOf("x" to "${template.domain}.Duration", "y" to template.domain,
                    "RequestFile" to "html/ipv6/not_find_file.asp")), mapOf("x.StartTime" to period.start, "x.EndTime" to period.end,
                    "x.RepeatDay" to period.days, "y.DurationRight" to "0", "y.DurationPolicy" to "0"),
                    CONTROL + "parentalctrltime.asp?TemplateId=${template.id}&FlagStatus=EditTemplate")
                val observed = configuration().periods.filter { it.domain.startsWith("${template.domain}.Duration.") }
                if (observed.none { it.field("StartTime") == period.start && it.field("EndTime") == period.end && it.field("RepeatDay") == period.days })
                    throw RouterException(R.string.error_network)
            }
            var ready = configuration()
            val actualTemplate = ready.templates.singleOrNull { it.domain == template.domain } ?: throw RouterException(R.string.error_conflict)
            val actualPeriods = ready.periods.filter { it.domain.startsWith("${template.domain}.Duration.") }
                .map { AllowedPeriod(it.field("StartTime"), it.field("EndTime"), it.field("RepeatDay")) }
            if (actualTemplate.field("Name") != name || actualTemplate.field("StartDate") != schedule.startDate ||
                actualTemplate.field("EndDate") != schedule.endDate || actualPeriods != schedule.periods || ready.bindings != before.bindings ||
                ready.templates.filter { it.domain != template.domain } != before.templates ||
                ready.periods.filterNot { it.domain.startsWith("${template.domain}.Duration.") } != before.periods)
                throw RouterException(R.string.error_conflict)
            before.ownedBindings().singleOrNull()?.let { removeBinding(ready, it) }
            ready = configuration()
            if (ready.bindings != conflicts || ready.pause() != null) throw RouterException(R.string.error_conflict)
            if (schedule.deadline > 0L && System.currentTimeMillis() >= schedule.deadline - 5_000) throw RouterException(R.string.error_conflict)
            client.write(CONTROL + "add.cgi?" + form(mapOf("x" to "$PARENTAL.MAC",
                "RequestFile" to "html/bbsp/parentalctrl/parentalctrlmac.asp")),
                mapOf("x.MACAddress" to mac, "x.Description" to name, "x.TemplateInst" to template.id), MAC_PAGE)
            val after = configuration()
            val ours = after.bindings.singleOrNull { it.field("Description") == name && it.field("TemplateInst") == template.id &&
                it.field("MACAddress").equals(mac, true) } ?: throw RouterException(R.string.error_network)
            if (after.bindings - ours != conflicts) {
                removeBinding(after, ours)
                throw RouterException(R.string.error_conflict)
            }
            return after.pause()
        } catch (e: Exception) {
            // Never delete a template still referenced by any device, including after an ambiguous binding write.
            try { removeUnboundTemplate(template) } catch (_: Exception) { }
            throw e
        }
    }

    private fun removeBinding(snapshot: RouterConfiguration, binding: RouterObject) {
        val current = configuration()
        if (current.bindings.find { it.domain == binding.domain } != binding || binding !in current.ownedBindings())
            throw RouterException(R.string.error_conflict)
        val template = snapshot.template(binding)
        client.write(CONTROL + "del.cgi?RequestFile=html/bbsp/parentalctrl/parentalctrlmac.asp", mapOf(binding.domain to ""), MAC_PAGE)
        if (configuration().bindings != current.bindings - binding) throw RouterException(R.string.error_network)
        removeUnboundTemplate(template)
    }

    private fun removeUnboundTemplate(template: RouterObject) {
        val current = configuration()
        if (!OWNER_NAME.matches(template.field("Name")) || current.templates.find { it.domain == template.domain } != template ||
            current.bindings.any { it.field("TemplateInst") == template.id }) return
        client.write(CONTROL + "del.cgi?RequestFile=/html/bbsp/parentalctrl/parentalctrlstatus.asp", mapOf(template.domain to ""), TEMPLATE_PAGE)
        val after = configuration()
        if (after.templates != current.templates - template || after.bindings != current.bindings ||
            after.periods != current.periods.filterNot { it.domain.startsWith("${template.domain}.Duration.") })
            throw RouterException(R.string.error_network)
    }
}
