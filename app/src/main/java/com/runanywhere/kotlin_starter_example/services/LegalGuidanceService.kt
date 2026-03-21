package com.runanywhere.kotlin_starter_example.services

import com.runanywhere.kotlin_starter_example.data.IncidentRecord

/**
 * Service to provide dynamic legal guidance based on incident context.
 * This analyzes the content of the record to provide specific legal references.
 */
object LegalGuidanceService {

    private val adviceDatabase = mapOf(
        "threat" to "Under Section 503 of the IPC (Criminal Intimidation), a threat to cause injury to person, reputation, or property is a punishable offense. This record documents such an occurrence.",
        "pattern" to "Under the Protection of Women from Domestic Violence Act (PWDVA) 2005, 'Domestic Violence' includes a pattern of abuse. Multiple timestamped records support a claim of 'Continuing Offense'.",
        "witness" to "Third-party observation is key evidence. The presence of witnesses (noted here) can be cited in a Domestic Incident Report (DIR) to a Protection Officer.",
        "risk" to "Immediate physical danger is grounds for an ex-parte Interim Order under Section 23 of the PWDVA, which can restrain the respondent from dispossessing or disturbing the aggrieved person.",
        "cyber" to "Harassment via electronic means falls under Section 66A/67 of the IT Act and Section 354D (Cyber Stalking) of the IPC. Retain all digital copies (screenshots/logs) as evidence.",
        "dowry" to "Demands for property/valuable security are covered under the Dowry Prohibition Act, 1961. This record serves as a contemporaneous note of such a demand.",
        "workplace" to "Harassment at a place of work is covered under the POSH Act (Prevention of Sexual Harassment) 2013 and should be reported to the Internal Complaints Committee (ICC).",
        "stalking" to "Following, contacting, or monitoring a woman despite clear disinterest is defined as Stalking under Section 354D of the IPC.",
        "physical" to "Physical harm is an offense under Section 319 (Hurt) and 320 (Grievous Hurt) of the IPC. Medical examination reports should be linked with this incident record if available.",
        "emotional" to "Emotional or verbal abuse, including insults and ridicule, is recognized as domestic violence under the PWDVA 2005 (Explanation I, Section 3)."
    )

    fun getLegalContext(record: IncidentRecord): List<String> {
        val points = mutableListOf<String>()
        val text = (record.rawTranscript + " " + record.incidentType).lowercase()

        // 1. Flag-based checks (High Priority)
        if (record.threatDocumented || text.contains("threat") || text.contains("kill")) {
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

        // 2. Content-based dynamic checks
        if (text.contains("online") || text.contains("message") || text.contains("whatsapp") || text.contains("call") || text.contains("internet")) {
            points.add(adviceDatabase["cyber"] ?: "")
        }
        
        if (text.contains("dowry") || text.contains("money") || text.contains("cash") || text.contains("demand")) {
            points.add(adviceDatabase["dowry"] ?: "")
        }
        
        if (text.contains("office") || text.contains("work") || text.contains("boss") || text.contains("colleague") || text.contains("job")) {
            points.add(adviceDatabase["workplace"] ?: "")
        }
        
        if (text.contains("follow") || text.contains("everywhere") || text.contains("watch") || text.contains("stalk")) {
            points.add(adviceDatabase["stalking"] ?: "")
        }
        
        if (text.contains("hit") || text.contains("slap") || text.contains("beat") || text.contains("push") || text.contains("injury") || text.contains("hurt")) {
            points.add(adviceDatabase["physical"] ?: "")
        }
        
        if (text.contains("insult") || text.contains("shout") || text.contains("names") || text.contains("emotional")) {
             if (!points.contains(adviceDatabase["threat"])) { // Don't double up if threat is already there
                 points.add(adviceDatabase["emotional"] ?: "")
             }
        }

        return points.filter { it.isNotEmpty() }.distinct()
    }
}
