package com.runanywhere.kotlin_starter_example.data

import com.runanywhere.sdk.public.RunAnywhere
import com.runanywhere.sdk.public.extensions.transcribe
import com.runanywhere.sdk.public.extensions.chat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * AI-powered incident processor.
 * 
 * Pipeline:
 * 1. Audio bytes → STT (Whisper Base Multilingual) → transcript (supports 99 languages including English and Hindi)
 * 2. Transcript → LLM (Qwen 2.5 1.5B) → structured JSON (multilingual support)
 * 3. JSON → IncidentRecord
 * 
 * The LLM is instructed to:
 * - Extract key incident details (type, who, threats, witnesses)
 * - Detect pattern language in any language ("again", "like before", "फिर से", "पहले की तरह")
 * - Assign severity based on content
 * - Never add information not stated by the user
 * - Use dignified, non-clinical language
 */
object IncidentProcessor {

    private val SYSTEM_PROMPT = """
You are a private legal documentation assistant for abuse survivors. 
Your ONLY job is to convert a spoken account into a structured JSON record.
The input may be in any language (English, Hindi, or others).

Rules:
- Output ONLY valid JSON. No explanation. No preamble. No markdown. No backticks.
- Never add information the person did not say.
- Never use clinical or cold language.
- If something is unclear, leave that field as an empty string.
- Detect repeat language in ANY language like "again", "like before", "this time", "फिर से", "पहले की तरह", "इस बार" — set patternFlag to true if found.
- Set severityTag based on: physical contact or immediate threat = "Immediate Risk", pattern of control or repeated = "Concerning Pattern", otherwise = "Documentation Only".
- Keep the language of the original transcript when filling fields.

Output this exact JSON structure:
{
  "incidentType": "string — e.g. Physical threat, Verbal threat, Coercive control, Physical contact, or in original language",
  "whoInvolved": "string — name or description of person(s) involved",
  "threatDocumented": true or false,
  "witnessesPresent": "string — who was present, or empty string",
  "patternFlag": true or false,
  "severityTag": "Documentation Only" or "Concerning Pattern" or "Immediate Risk",
  "summary": "string — 1-2 sentence dignified summary of what happened in the original language"
}
    """.trimIndent()

    /**
     * Process audio bytes into a structured IncidentRecord.
     * 
     * @param audioBytes Raw PCM audio data (16kHz, mono, 16-bit)
     * @return IncidentRecord with structured fields populated by AI
     */
    suspend fun process(audioBytes: ByteArray): IncidentRecord = withContext(Dispatchers.IO) {
        // Step 1: Transcribe audio to text using Whisper STT
        val transcript = try {
            RunAnywhere.transcribe(audioBytes)
        } catch (e: Exception) {
            // STT failed - return empty record
            return@withContext IncidentRecord(rawTranscript = "")
        }

        if (transcript.isBlank()) {
            return@withContext IncidentRecord(rawTranscript = "")
        }

        // Step 2: Send transcript to LLM for structuring
        val prompt = """
$SYSTEM_PROMPT

Here is the spoken account to structure:
"$transcript"

Respond with ONLY the JSON object. Nothing else.
        """.trimIndent()

        val rawResponse = try {
            RunAnywhere.chat(prompt)
        } catch (e: Exception) {
            // LLM failed - return record with transcript only
            return@withContext IncidentRecord(rawTranscript = transcript)
        }

        // Step 3: Parse JSON response
        return@withContext try {
            // Clean response — strip any markdown fences if model added them
            val cleaned = rawResponse
                .trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val json = JSONObject(cleaned)

            IncidentRecord(
                rawTranscript = transcript,
                incidentType = json.optString("incidentType", ""),
                whoInvolved = json.optString("whoInvolved", ""),
                threatDocumented = json.optBoolean("threatDocumented", false),
                witnessesPresent = json.optString("witnessesPresent", ""),
                patternFlag = json.optBoolean("patternFlag", false),
                severityTag = json.optString("severityTag", "Documentation Only"),
                rawJson = cleaned
            )
        } catch (e: Exception) {
            // JSON parse failed — save transcript only, flag for manual review
            IncidentRecord(
                rawTranscript = transcript,
                severityTag = "Documentation Only",
                rawJson = rawResponse
            )
        }
    }
}
