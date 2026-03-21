package com.runanywhere.kotlin_starter_example.services

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.runanywhere.kotlin_starter_example.data.IncidentProcessor
import com.runanywhere.kotlin_starter_example.data.IncidentRecord
import com.runanywhere.kotlin_starter_example.data.IncidentRepository
import com.runanywhere.sdk.core.types.InferenceFramework
import com.runanywhere.sdk.public.RunAnywhere
import com.runanywhere.sdk.public.extensions.Models.ModelCategory
import com.runanywhere.sdk.public.extensions.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Service for managing AI models.
 * Updated for high accuracy: Llama 3.2 3B + Whisper Base.
 * Includes explicit RAM management for stable multilingual processing.
 */
class ModelService : ViewModel() {

    // LLM state
    var isLLMDownloading by mutableStateOf(false)
        private set
    var llmDownloadProgress by mutableStateOf(0f)
        private set
    var isLLMLoading by mutableStateOf(false)
        private set
    var isLLMLoaded by mutableStateOf(false)
        private set

    // STT state
    var isSTTDownloading by mutableStateOf(false)
        private set
    var sttDownloadProgress by mutableStateOf(0f)
        private set
    var isSTTLoading by mutableStateOf(false)
        private set
    var isSTTLoaded by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    companion object {
        // High Accuracy Choice: Llama 3.2 3B (GGUF Q4_K_M)
        const val LLM_MODEL_ID = "llama-3.2-3b-instruct-q4_k_m"
        // Multilingual Threshold: Whisper Base (Required for Hindi)
        const val STT_MODEL_ID = "sherpa-onnx-whisper-base"

        private val STT_REQUIRED_FILES = listOf("encoder.onnx", "decoder.onnx", "tokens.txt")

        fun registerDefaultModels() {
            // Llama 3.2 3B - The gold standard for mobile reasoning & Hindi accuracy
            RunAnywhere.registerModel(
                id = LLM_MODEL_ID,
                name = "Llama 3.2 3B Instruct",
                url = "https://huggingface.co/bartowski/Llama-3.2-3B-Instruct-GGUF/resolve/main/Llama-3.2-3B-Instruct-Q4_K_M.gguf",
                framework = InferenceFramework.LLAMA_CPP,
                modality = ModelCategory.LANGUAGE,
                memoryRequirement = 2_200_000_000 // ~2.2GB RAM
            )

            // Whisper Base - Reliable multilingual support (English/Hindi)
            RunAnywhere.registerModel(
                id = STT_MODEL_ID,
                name = "Sherpa Whisper Base (Multilingual)",
                url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-whisper-base.tar.bz2",
                framework = InferenceFramework.ONNX,
                modality = ModelCategory.SPEECH_RECOGNITION
            )
        }
    }

    init {
        viewModelScope.launch {
            refreshModelState()
        }
    }

    private suspend fun refreshModelState() {
        isLLMLoaded = RunAnywhere.isLLMModelLoaded()
        isSTTLoaded = RunAnywhere.isSTTModelLoaded()
    }

    private suspend fun isModelDownloaded(modelId: String): Boolean {
        val localPath = getModelLocalPath(modelId) ?: return false
        return when (modelId) {
            STT_MODEL_ID -> isSttModelPathReady(localPath)
            LLM_MODEL_ID -> isLlmModelPathReady(localPath)
            else -> true
        }
    }

    private suspend fun getModelLocalPath(modelId: String): String? {
        val models = RunAnywhere.availableModels()
        return models.find { it.id == modelId }?.localPath
    }

    private fun isLlmModelPathReady(localPath: String): Boolean =
        localPath.endsWith(".gguf") || localPath.contains("llama")

    private fun isSttModelPathReady(localPath: String): Boolean {
        val sttDir = File(localPath)
        if (!sttDir.isDirectory) return false
        return STT_REQUIRED_FILES.all { required -> File(sttDir, required).isFile }
    }

    // --- Extraction & Promotion Logic (Kept same as requested) ---

    private fun ensureSttModelReadyOnDisk(localPath: String): Boolean {
        val path = File(localPath)
        if (!path.exists()) return false
        if (path.isFile) {
            if (!extractTarGzInPlace(path)) return false
        }
        val sttDir = File(localPath)
        if (!sttDir.isDirectory) return false
        if (!promoteSttArtifactsToRoot(sttDir)) return false
        return isSttModelPathReady(localPath)
    }

    private fun extractTarGzInPlace(archivePath: File): Boolean {
        return try {
            val parent = archivePath.parentFile ?: return false
            val isBz2 = archivePath.name.endsWith(".bz2")
            val tempArchive = File(parent, "${archivePath.name}.tmp")
            archivePath.copyTo(tempArchive, overwrite = true)
            archivePath.delete()
            archivePath.mkdirs()

            FileInputStream(tempArchive).use { fis ->
                val decompressor = if (isBz2) BZip2CompressorInputStream(fis) else GzipCompressorInputStream(fis)
                TarArchiveInputStream(decompressor).use { tis ->
                    var entry = tis.nextTarEntry
                    while (entry != null) {
                        val outFile = File(archivePath, entry.name).canonicalFile
                        if (entry.isDirectory) outFile.mkdirs()
                        else {
                            outFile.parentFile?.mkdirs()
                            FileOutputStream(outFile).use { tis.copyTo(it) }
                        }
                        entry = tis.nextTarEntry
                    }
                }
            }
            tempArchive.delete()
            true
        } catch (e: Exception) { false }
    }

    private fun promoteSttArtifactsToRoot(sttDir: File): Boolean {
        fun firstMatch(token: String, extension: String): File? =
            sttDir.walkTopDown().filter { it.isFile }
                .firstOrNull { it.name.lowercase().contains(token) && it.name.lowercase().endsWith(extension) }

        val encoder = firstMatch("encoder", ".onnx") ?: return false
        val decoder = firstMatch("decoder", ".onnx") ?: return false
        val tokens = firstMatch("tokens", ".txt") ?: return false

        encoder.copyTo(File(sttDir, "encoder.onnx"), overwrite = true)
        decoder.copyTo(File(sttDir, "decoder.onnx"), overwrite = true)
        tokens.copyTo(File(sttDir, "tokens.txt"), overwrite = true)
        return true
    }

    // --- Sequence & Internal Loading ---

    fun downloadAndLoadSTTThenLLM() {
        if (isSTTDownloading || isSTTLoading) return
        viewModelScope.launch {
            val sttJob = downloadAndLoadSTTInternal()
            sttJob.join()
            if (isSTTLoaded && errorMessage == null) {
                downloadAndLoadLLMInternal()
            }
        }
    }

    fun downloadAndLoadSTT() {
        if (isSTTDownloading || isSTTLoading) return
        viewModelScope.launch { downloadAndLoadSTTInternal() }
    }

    fun downloadAndLoadLLM() {
        if (isLLMDownloading || isLLMLoading) return
        viewModelScope.launch { downloadAndLoadLLMInternal() }
    }

    private fun downloadAndLoadSTTInternal(): Job = viewModelScope.launch {
        try {
            errorMessage = null
            val existingPath = getModelLocalPath(STT_MODEL_ID)
            if (existingPath != null && !isSttModelPathReady(existingPath)) {
                if (!ensureSttModelReadyOnDisk(existingPath)) File(existingPath).deleteRecursively()
            }

            if (!isModelDownloaded(STT_MODEL_ID)) {
                isSTTDownloading = true
                RunAnywhere.downloadModel(STT_MODEL_ID).catch { e ->
                    errorMessage = "STT Download Failed: ${e.message}"
                    isSTTDownloading = false
                }.collect { sttDownloadProgress = it.progress }
                isSTTDownloading = false
            }

            delay(300)
            isSTTLoading = true
            RunAnywhere.loadSTTModel(STT_MODEL_ID)
            isSTTLoaded = true
            isSTTLoading = false
            refreshModelState()
        } catch (e: Exception) {
            errorMessage = "STT Load Error: ${e.message}"
            isSTTLoading = false
        }
    }

    private fun downloadAndLoadLLMInternal(): Job = viewModelScope.launch {
        try {
            errorMessage = null
            if (!isModelDownloaded(LLM_MODEL_ID)) {
                isLLMDownloading = true
                RunAnywhere.downloadModel(LLM_MODEL_ID).catch { e ->
                    errorMessage = "LLM Download Failed: ${e.message}"
                    isLLMDownloading = false
                }.collect { llmDownloadProgress = it.progress }
                isLLMDownloading = false
            }

            delay(300)
            val path = getModelLocalPath(LLM_MODEL_ID)
            if (path == null || !isLlmModelPathReady(path)) {
                errorMessage = "LLM Registry Error. Please retry."
                return@launch
            }

            isLLMLoading = true
            RunAnywhere.loadLLMModel(LLM_MODEL_ID)
            isLLMLoaded = true
            isLLMLoading = false
            refreshModelState()
        } catch (e: Exception) {
            errorMessage = "LLM Load Error: ${e.message}"
            isLLMLoading = false
        }
    }

    // --- Processing Logic ---

    var processingState by mutableStateOf<ProcessingState>(ProcessingState.Idle)
        private set

    sealed class ProcessingState {
        object Idle : ProcessingState()
        object Processing : ProcessingState()
        data class Done(val record: IncidentRecord) : ProcessingState()
        data class Error(val message: String) : ProcessingState()
    }

    /**
     * Professional Lifecycle:
     * 1. Transcribe (STT)
     * 2. Unload STT (Free RAM)
     * 3. Load LLM (Reasoning)
     * 4. Save & Done
     */
    fun processAudio(audioBytes: ByteArray, encryptionKey: String, context: Context) {
        if (processingState is ProcessingState.Processing) return

        viewModelScope.launch {
            processingState = ProcessingState.Processing
            try {
                // Ensure STT is loaded for transcription
                if (!RunAnywhere.isSTTModelLoaded()) {
                    RunAnywhere.loadSTTModel(STT_MODEL_ID)
                }

                val record = IncidentProcessor.process(audioBytes)

                // RAM MANAGEMENT: Unload STT to make room for Llama 3B
                RunAnywhere.unloadSTTModel()
                isSTTLoaded = false

                // Load LLM if needed for post-processing/summarization
                if (!RunAnywhere.isLLMModelLoaded()) {
                    RunAnywhere.loadLLMModel(LLM_MODEL_ID)
                    isLLMLoaded = true
                }

                withContext(Dispatchers.IO) {
                    IncidentRepository(context, encryptionKey).saveIncident(record)
                }

                processingState = ProcessingState.Done(record)
            } catch (e: Exception) {
                processingState = ProcessingState.Error(e.message ?: "Unknown error")
            } finally {
                refreshModelState()
            }
        }
    }

    fun resetProcessingState() {
        processingState = ProcessingState.Idle
    }

    fun unloadAllModels() {
        viewModelScope.launch {
            RunAnywhere.unloadLLMModel()
            RunAnywhere.unloadSTTModel()
            refreshModelState()
        }
    }

    fun clearError() {
        errorMessage = null
    }
}