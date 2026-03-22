package com.runanywhere.kotlin_starter_example.data

/**
 * Data model for a single incident record.
 * 
 * This model captures structured information extracted from voice recordings
 * by the AI processing pipeline (STT + LLM).
 * 
 * @property id Unique identifier (UUID)
 * @property timestamp Unix timestamp of when the record was created
 * @property rawTranscript The original voice-to-text transcription
 * @property incidentType Type of incident (e.g., "Physical threat", "Verbal threat", "Coercive control")
 * @property whoInvolved Name or description of person(s) involved
 * @property threatDocumented Whether a direct threat was documented
 * @property witnessesPresent Description of witnesses present, or empty string
 * @property patternFlag Whether the AI detected repeat/pattern language (e.g., "again", "like before")
 * @property severityTag Severity classification: "Documentation Only", "Concerning Pattern", or "Immediate Risk"
 * @property rawJson The raw JSON response from the LLM (for debugging/audit)
 */
data class IncidentRecord(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val rawTranscript: String = "",
    val incidentType: String = "",
    val whoInvolved: String = "",
    val threatDocumented: Boolean = false,
    val witnessesPresent: String = "",
    val patternFlag: Boolean = false,
    val severityTag: String = "Documentation Only",
    val rawJson: String = "",
    // NEW: Forensic Evidence Fields
    val audioFilePath: String? = null, // Path to AES-256 encrypted file
    val integrityHash: String = ""     // SHA-256 Digital Seal
)

// Valid severityTag values:
// - "Documentation Only" : Normal documentation, no immediate concerns
// - "Concerning Pattern" : Repeat pattern detected, escalating behavior
// - "Immediate Risk"      : Physical contact or immediate threat present
