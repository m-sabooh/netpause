package com.example.netpause

internal const val PARENTAL = "InternetGatewayDevice.X_HW_Security.ParentalCtrl"
internal val OWNER_NAME = Regex("NP1_[a-f0-9]{12}_(0|[0-9]{13})")
internal data class RouterObject(val domain: String, val fields: Map<String, String>) {
    fun field(name: String): String = fields[name.lowercase()] ?: throw RouterException(R.string.error_schema)
    val id: String get() = domain.substringAfterLast('.')
}
internal data class RouterConfiguration(val bindings: List<RouterObject>, val templates: List<RouterObject>, val periods: List<RouterObject>) {
    fun template(binding: RouterObject): RouterObject = templates.singleOrNull { it.id == binding.field("TemplateInst") }
        ?: throw RouterException(R.string.error_schema)
    fun ownedBindings(): List<RouterObject> = bindings.filter { binding ->
        OWNER_NAME.matches(binding.field("Description")) && template(binding).field("Name") == binding.field("Description")
    }
    fun pause(): RouterPause? {
        val owned = ownedBindings()
        if (owned.size > 1) throw RouterException(R.string.error_conflict)
        val binding = owned.singleOrNull() ?: return null
        val name = binding.field("Description")
        return RouterPause(binding.domain + "/" + name, binding.field("MACAddress").let {
            if (it.equals("FF:FF:FF:FF:FF:FF", true)) "all" else macAddress(it)
        }, name.substringAfterLast('_').toLong())
    }
}

internal fun parseConfiguration(source: String): RouterConfiguration {
    val constructors = Regex("function\\s+(\\w+)\\s*\\(([^)]*)\\)\\s*\\{([^}]*)\\}")
        .findAll(source).associate { match ->
            val parameters = match.groupValues[2].split(',').map { it.trim() }
            val fields = Regex("this\\.(\\w+)\\s*=\\s*(\\w+)\\s*;").findAll(match.groupValues[3])
                .mapNotNull { assignment ->
                    val index = parameters.indexOf(assignment.groupValues[2])
                    if (index < 0) null else assignment.groupValues[1].lowercase() to index
                }.toMap()
            match.groupValues[1] to (parameters.size to fields)
        }
    val allFields = constructors.values.flatMap { it.second.keys }.toSet()
    if (!allFields.containsAll(listOf("macaddress", "templateinst", "startdate", "enddate", "starttime", "endtime", "repeatday")))
        throw RouterException(R.string.error_schema)
    val objects = constructorRecords(source).filter {
        it.constructor in setOf("ChildListClass", "TemplatesListClass", "DurationListClass")
    }.map { record ->
        val (count, mapping) = constructors[record.constructor] ?: throw RouterException(R.string.error_schema)
        if (count != record.values.size) throw RouterException(R.string.error_schema)
        RouterObject(record.values.first(), mapping.mapValues { record.values[it.value] })
    }
    fun matching(suffix: String) = objects.filter { Regex(Regex.escape(PARENTAL) + suffix).matches(it.domain) }
    val bindings = matching("\\.MAC\\.[1-9][0-9]*")
    val templates = matching("\\.Templates\\.[1-9][0-9]*")
    val periods = matching("\\.Templates\\.[1-9][0-9]*\\.Duration\\.[1-9][0-9]*")
    if (objects.size != bindings.size + templates.size + periods.size) throw RouterException(R.string.error_schema)
    bindings.forEach { it.field("Description"); macAddress(it.field("MACAddress")); it.field("TemplateInst") }
    templates.forEach { it.field("Name"); it.field("StartDate"); it.field("EndDate") }
    periods.forEach { it.field("StartTime"); it.field("EndTime"); it.field("RepeatDay") }
    return RouterConfiguration(bindings, templates, periods)
}
