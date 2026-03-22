package com.runanywhere.kotlin_starter_example.services

import com.runanywhere.kotlin_starter_example.data.IncidentRecord

/**
 * Service to provide legal guidance based on incident records.
 * This can be extended to fetch updates from a remote server.
 */
object LegalGuidanceService {

    private val adviceDatabase = mapOf(
        "threat" to "A documented verbal or physical threat can support an application for a Protection Order under the Protection of Women from Domestic Violence Act, 2005 (PWDVA).",
        "pattern" to "Multiple timestamped records showing a pattern of behaviour strengthen a case significantly — courts look for evidence of repeated conduct, not just isolated incidents.",
        "witness" to "The presence of witnesses, noted here, can be referenced when filing a Domestic Incident Report (DIR) with a Protection Officer.",
        "risk" to "Records documenting immediate physical risk can be used to request emergency relief, including the right to reside in the shared household or exclusion of the respondent.",
        "respondent" to "The named person can be identified as a respondent in legal proceedings."
    )

    fun getLegalContext(record: IncidentRecord): List<String> {
        val points = mutableListOf<String>()

        if (record.threatDocumented) {
            points.add(adviceDatabase["threat"] ?: "")
        }

        if (record.patternFlag) {
            points.add(adviceDatabase["pattern"] ?: "")
        }

        if (record.witnessesPresent.isNotBlank()) {
            points.add(adviceDatabase["witness"] ?: "")
        }

        if (record.severityTag == "Immediate Risk") {
            points.add(adviceDatabase["risk"] ?: "")
        }

        if (record.whoInvolved.isNotBlank()) {
            points.add(adviceDatabase["respondent"] ?: "")
        }

        return points.filter { it.isNotEmpty() }
    }
}
