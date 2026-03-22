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

    // HIGH ACCURACY System Prompt for Llama 3.2 3B — stricter, keyword-rich
    private val SYSTEM_PROMPT = """
You are a Digital Forensic Analyst. Parse the transcript (English/Hindi/Hinglish/code-mixed) and output STRICT JSON only.

MANDATES (do NOT skip):
1) Flag ANY: physical threat/assault ("maar dunga", "dhakka", hit, slap, choke, weapon), verbal abuse (shout, insult, threaten reputation), coercive control (money/phone blocked, location tracking, isolation), stalking/harassment (following, constant calls/messages, waiting outside), property damage, intimidation, gaslighting, sexual coercion.
2) Map relations: who is acting vs who is harmed (husband/wife/partner/bf/gf/boss/colleague/neighbour/relative/parent). Put aggressor names/roles in whoInvolved.
3) Treat "I am fine/ok" as unsafe if any fear/history/conditions are present. Do NOT mark Safe if any harm, fear, or control is implied.

CLASSIFICATION RULES (pick best fit):
- Immediate Risk: explicit threat to harm/kill, weapon, strangulation, severe assault, locked in, self-harm threats, or imminent danger cues.
- Physical Threat/Assault: hitting/pushing/slapping/throwing objects, "maar dunga", weapon mention; no immediate lethal cue.
- Coercive Control: money/phone blocked, surveillance, isolation, forced compliance, threats to livelihood/immigration/reputation.
- Verbal Abuse: insults, shouting, humiliation, name-calling, reputational threats.
- Stalking/Harassment: following, showing up uninvited, repeated calls/messages, watching/waiting.
- Positive Update: ONLY if explicit safety, no fear, no conditions, no past harm in context.

PATTERN:
- patternFlag = true if repetition implied ("again", "hamesha", "roz", "phir se", "habit", "daily", "baar baar", prior incidents referenced).

SEVERITY TAG (must be one of): "Immediate Risk" | "Concerning" | "Documentation Only" | "Safe".
- Immediate Risk: lethal/serious threat, weapon, severe assault, locked in, active danger.
- Concerning: repeated abuse/stalking/control without immediate lethal threat.
- Documentation Only: minor/one-off argument, no threat/control.
- Safe: explicitly safe, no harm/fear/history (rare).

OUTPUT JSON (no markdown, no prose):
{
  "incidentType": "Specific classification string",
  "whoInvolved": "Name or relation of aggressor",
  "threatDocumented": Boolean,
  "witnessesPresent": "String or 'None'",
  "patternFlag": Boolean,
  "severityTag": "Immediate Risk" | "Concerning" | "Documentation Only" | "Safe",
  "summary": "Objective one-liner (English)"
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
