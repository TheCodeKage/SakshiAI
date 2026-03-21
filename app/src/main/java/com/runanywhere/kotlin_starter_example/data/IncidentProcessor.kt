package com.runanywhere.kotlin_starter_example.data

import android.content.Context
import com.runanywhere.sdk.public.RunAnywhere
import com.runanywhere.sdk.public.extensions.transcribe
import com.runanywhere.sdk.public.extensions.chat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * AI-powered incident processor.
 * Optimized for Speed + Positive Sentiment Detection + Forensic Security.
 */
object IncidentProcessor {

    // FAST + DETAILED System Prompt
    private val SYSTEM_PROMPT = """
Task: Analyze transcript. Extract JSON.
Input: Hindi/English/Mixed.
Rules:
1. JSON ONLY. No markdown.
2. CRITICAL - SENTIMENT CHECK:
   - If the user describes a POSITIVE, SAFE, or HAPPY interaction (e.g., "he was kind today", "we had a nice dinner", "no fighting", "feeling better"):
     -> Set "incidentType": "Positive Update"
     -> Set "severityTag": "Safe"
     -> Set "threatDocumented": false
     -> Set "patternFlag": false (IGNORE any repeat words like "again" if the context is positive).
3. NEGATIVE / ABUSIVE CONTEXT ONLY:
   - Classify incidence Type (Physical, Verbal, Coercive Control, Stalking).
   - "patternFlag": Set true ONLY if ABUSIVE behavior is described as repeating (e.g., "hit me again", "phir se mara", "always follows me").
4. "severityTag" Levels:
   - "Immediate Risk": ACTIVE violence, weapons, direct death threats.
   - "Concerning Pattern": Stalking, coercive control, emotional abuse.
   - "Documentation Only": Minor arguments, venting, non-threatening issues.
   - "Safe": Positive updates, good days.
5. Fields:
   - incidentType: Category.
   - whoInvolved: Names/Relations.
   - threatDocumented: boolean (Requires specific threat).
   - witnessesPresent: Names/Empty.
   - summary: 1-sentence summary (Original Language).

Schema:
{
  "incidentType": "string",
  "whoInvolved": "string",
  "threatDocumented": boolean,
  "witnessesPresent": "string",
  "patternFlag": boolean,
  "severityTag": "string",
  "summary": "string"
}
    """.trimIndent()

    /**
     * Process audio bytes into a structured IncidentRecord.
     * 
     * @param context Android context for encryption
     * @param audioBytes Raw PCM audio data (16kHz, mono, 16-bit)
     * @param repository IncidentRepository for history check
     * @return IncidentRecord with structured fields populated by AI
     */
    suspend fun process(
        context: Context,
        audioBytes: ByteArray,
        repository: IncidentRepository
    ): IncidentRecord = withContext(Dispatchers.IO) {
        
        // 1. Secure Evidence Layer
        val savedPath = EncryptedAudioFileManager.saveEncryptedAudio(context, audioBytes)

        // Step 1: Transcribe audio to text using Whisper STT
        val transcript = try {
            RunAnywhere.transcribe(audioBytes)
        } catch (e: Exception) {
            // STT failed - return empty record
            return@withContext IncidentRecord(rawTranscript = "", audioFilePath = savedPath)
        }

        if (transcript.isBlank()) {
            return@withContext IncidentRecord(rawTranscript = "", audioFilePath = savedPath)
        }

        // Step 2: Send transcript to LLM for structuring
        val prompt = """
$SYSTEM_PROMPT

TRANSCRIPT: "$transcript"
JSON:
        """.trimIndent()

        val rawResponse = try {
            RunAnywhere.chat(prompt)
        } catch (e: Exception) {
            // LLM failed - return record with transcript only
            return@withContext IncidentRecord(rawTranscript = transcript, audioFilePath = savedPath)
        }

        // Step 3: Parse JSON response & Perform History Check
        val baseRecord = try {
            // Clean response — strip any markdown fences if model added them
            val cleaned = rawResponse
                .trim()
                .replace("```json", "")
                .replace("```", "")
                .trim()

            val json = JSONObject(cleaned)

            IncidentRecord(
                rawTranscript = transcript,
                incidentType = json.optString("incidentType", "Unknown"),
                whoInvolved = json.optString("whoInvolved", ""),
                threatDocumented = json.optBoolean("threatDocumented", false),
                witnessesPresent = json.optString("witnessesPresent", ""),
                patternFlag = json.optBoolean("patternFlag", false),
                severityTag = json.optString("severityTag", "Documentation Only"),
                rawJson = cleaned,
                audioFilePath = savedPath
            )
        } catch (e: Exception) {
            // JSON parse failed — save transcript only, flag for manual review
            IncidentRecord(
                rawTranscript = transcript,
                severityTag = "Documentation Only",
                rawJson = rawResponse,
                audioFilePath = savedPath
            )
        }

        // 5. Deep Pattern Recognition (Scan History)
        val historyMatches = repository.countIncidentsInvolving(baseRecord.whoInvolved)
        val finalPatternFlag = baseRecord.patternFlag || (historyMatches > 0)
        
        val finalRecord = baseRecord.copy(
            patternFlag = finalPatternFlag,
            severityTag = if (finalPatternFlag && baseRecord.severityTag == "Documentation Only") 
                             "Concerning Pattern" else baseRecord.severityTag
        )

        // 6. Seal the Record
        return@withContext finalRecord.copy(
            integrityHash = repository.generateIntegrityHash(finalRecord)
        )
    }
}
