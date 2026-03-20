package com.runanywhere.kotlin_starter_example.services

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.runanywhere.sdk.core.types.InferenceFramework
import com.runanywhere.sdk.public.RunAnywhere
import com.runanywhere.sdk.public.extensions.Models.ModelCategory
import com.runanywhere.sdk.public.extensions.registerModel
import com.runanywhere.sdk.public.extensions.downloadModel
import com.runanywhere.sdk.public.extensions.loadLLMModel
import com.runanywhere.sdk.public.extensions.loadSTTModel
import com.runanywhere.sdk.public.extensions.unloadLLMModel
import com.runanywhere.sdk.public.extensions.unloadSTTModel
import com.runanywhere.sdk.public.extensions.isLLMModelLoaded
import com.runanywhere.sdk.public.extensions.isSTTModelLoaded
import com.runanywhere.sdk.public.extensions.availableModels
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Service for managing AI models - handles registration, downloading, and loading.
 *
 * FIX: Downloads are now sequential (STT first, then LLM) to prevent a race condition
 * where concurrent downloads cause the SDK registry to assign the wrong file path to
 * the LLM model entry, resulting in error -422 (invalid file format).
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
        const val LLM_MODEL_ID = "smollm2-360m-instruct-q8_0"
        const val STT_MODEL_ID = "sherpa-onnx-whisper-tiny.en"
        private val STT_REQUIRED_FILES = listOf("encoder.onnx", "decoder.onnx", "tokens.txt")

        fun registerDefaultModels() {
            RunAnywhere.registerModel(
                id = LLM_MODEL_ID,
                name = "SmolLM2 360M Instruct Q8_0",
                url = "https://huggingface.co/HuggingFaceTB/SmolLM2-360M-Instruct-GGUF/resolve/main/smollm2-360m-instruct-q8_0.gguf",
                framework = InferenceFramework.LLAMA_CPP,
                modality = ModelCategory.LANGUAGE,
                memoryRequirement = 400_000_000
            )

            RunAnywhere.registerModel(
                id = STT_MODEL_ID,
                name = "Sherpa Whisper Tiny (ONNX)",
                url = "https://github.com/RunanywhereAI/sherpa-onnx/releases/download/runanywhere-models-v1/sherpa-onnx-whisper-tiny.en.tar.gz",
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

    private fun isLlmModelPathReady(localPath: String): Boolean {
        return localPath.endsWith(".gguf") || localPath.contains("llm")
    }

    private fun isSttModelPathReady(localPath: String): Boolean {
        val sttDir = File(localPath)
        if (!sttDir.isDirectory) return false
        return STT_REQUIRED_FILES.all { required -> File(sttDir, required).isFile }
    }

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
            val tempArchive = File(parent, "${archivePath.name}.tmp.tar.gz")

            if (tempArchive.exists()) tempArchive.delete()
            archivePath.copyTo(tempArchive, overwrite = true)
            archivePath.delete()

            if (!archivePath.mkdirs()) return false

            FileInputStream(tempArchive).use { fis ->
                GzipCompressorInputStream(fis).use { gis ->
                    TarArchiveInputStream(gis).use { tis ->
                        var entry = tis.nextTarEntry
                        while (entry != null) {
                            val outFile = File(archivePath, entry.name).canonicalFile
                            val canonicalRoot = archivePath.canonicalFile

                            // Prevent path traversal from archive entries.
                            if (!outFile.path.startsWith(canonicalRoot.path + File.separator) && outFile != canonicalRoot) {
                                return false
                            }

                            if (entry.isDirectory) {
                                outFile.mkdirs()
                            } else {
                                outFile.parentFile?.mkdirs()
                                FileOutputStream(outFile).use { fos ->
                                    tis.copyTo(fos)
                                }
                            }

                            entry = tis.nextTarEntry
                        }
                    }
                }
            }

            tempArchive.delete()
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun promoteSttArtifactsToRoot(sttDir: File): Boolean {
        fun firstMatch(token: String, extension: String): File? {
            return sttDir.walkTopDown()
                .filter { it.isFile }
                .firstOrNull { file ->
                    val lower = file.name.lowercase()
                    lower.contains(token) && lower.endsWith(extension)
                }
        }

        val encoder = firstMatch("encoder", ".onnx") ?: return false
        val decoder = firstMatch("decoder", ".onnx") ?: return false
        val tokens = firstMatch("tokens", ".txt") ?: return false

        val targetEncoder = File(sttDir, "encoder.onnx")
        val targetDecoder = File(sttDir, "decoder.onnx")
        val targetTokens = File(sttDir, "tokens.txt")

        if (encoder.canonicalFile != targetEncoder.canonicalFile) {
            encoder.copyTo(targetEncoder, overwrite = true)
        }
        if (decoder.canonicalFile != targetDecoder.canonicalFile) {
            decoder.copyTo(targetDecoder, overwrite = true)
        }
        if (tokens.canonicalFile != targetTokens.canonicalFile) {
            tokens.copyTo(targetTokens, overwrite = true)
        }

        return true
    }

    private fun deletePathRecursively(path: String) {
        val target = File(path)
        if (!target.exists()) return
        target.deleteRecursively()
    }

    /**
     * FIX: Download and load STT, then automatically start LLM download+load once
     * STT is fully complete. Sequential execution prevents the SDK registry race
     * condition that caused the LLM to load from the wrong file path.
     */
    fun downloadAndLoadSTTThenLLM() {
        if (isSTTDownloading || isSTTLoading) return

        viewModelScope.launch {
            // Step 1: STT
            val sttJob = downloadAndLoadSTTInternal()
            sttJob.join()

            // Only proceed to LLM if STT succeeded
            if (isSTTLoaded && errorMessage == null) {
                downloadAndLoadLLMInternal()
            }
        }
    }

    /**
     * Download and load only the STT model (standalone, e.g. retry button).
     */
    fun downloadAndLoadSTT() {
        if (isSTTDownloading || isSTTLoading) return
        viewModelScope.launch { downloadAndLoadSTTInternal() }
    }

    /**
     * Download and load only the LLM model (standalone, e.g. retry button).
     * Includes path verification to guard against stale registry entries.
     */
    fun downloadAndLoadLLM() {
        if (isLLMDownloading || isLLMLoading) return
        viewModelScope.launch { downloadAndLoadLLMInternal() }
    }

    // -------------------------------------------------------------------------
    // Internal implementations (return Job so they can be .join()ed)
    // -------------------------------------------------------------------------

    private fun downloadAndLoadSTTInternal(): Job = viewModelScope.launch {
        try {
            errorMessage = null

            val existingSttPath = getModelLocalPath(STT_MODEL_ID)
            if (existingSttPath != null && !isSttModelPathReady(existingSttPath)) {
                val repaired = ensureSttModelReadyOnDisk(existingSttPath)
                if (!repaired) {
                    // Fallback cleanup when we cannot repair/normalize existing STT artifacts.
                    deletePathRecursively(existingSttPath)
                }
            }

            if (!isModelDownloaded(STT_MODEL_ID)) {
                isSTTDownloading = true
                sttDownloadProgress = 0f

                RunAnywhere.downloadModel(STT_MODEL_ID)
                    .catch { e ->
                        errorMessage = "STT download failed: ${e.message}"
                        isSTTDownloading = false
                    }
                    .collect { progress ->
                        sttDownloadProgress = progress.progress
                    }

                isSTTDownloading = false
            }

            // FIX: Give the SDK registry time to commit the path before loading.
            delay(300)

            val sttLocalPath = getModelLocalPath(STT_MODEL_ID)
            val sttReady = sttLocalPath != null &&
                    (isSttModelPathReady(sttLocalPath) || ensureSttModelReadyOnDisk(sttLocalPath))
            if (!sttReady) {
                errorMessage =
                    "STT model is not extracted correctly. Expected encoder.onnx, decoder.onnx, tokens.txt in an extracted model folder."
                return@launch
            }

            isSTTLoading = true
            RunAnywhere.loadSTTModel(STT_MODEL_ID)
            isSTTLoaded = true
            isSTTLoading = false

            refreshModelState()
        } catch (e: Exception) {
            errorMessage = "STT load failed: ${e.message}"
            isSTTDownloading = false
            isSTTLoading = false
        }
    }

    private fun downloadAndLoadLLMInternal(): Job = viewModelScope.launch {
        try {
            errorMessage = null

            if (!isModelDownloaded(LLM_MODEL_ID)) {
                isLLMDownloading = true
                llmDownloadProgress = 0f

                RunAnywhere.downloadModel(LLM_MODEL_ID)
                    .catch { e ->
                        errorMessage = "LLM download failed: ${e.message}"
                        isLLMDownloading = false
                    }
                    .collect { progress ->
                        llmDownloadProgress = progress.progress
                    }

                isLLMDownloading = false
            }

            // FIX: Give the SDK registry time to commit the path before loading.
            delay(300)

            // FIX: Verify the path in the registry actually points to a .gguf file,
            // not the STT archive. This is the sentinel check for the race condition.
            val models = RunAnywhere.availableModels()
            val llmModel = models.find { it.id == LLM_MODEL_ID }
            val localPath = llmModel?.localPath

            if (localPath == null) {
                errorMessage = "LLM model path not found after download. Please try again."
                return@launch
            }

            if (!localPath.endsWith(".gguf") && !localPath.contains("llm")) {
                // Path looks wrong — likely still pointing at the STT file.
                // Wait a bit longer and re-check before giving up.
                delay(1000)
                val retryModels = RunAnywhere.availableModels()
                val retryModel = retryModels.find { it.id == LLM_MODEL_ID }
                if (retryModel?.localPath == null ||
                    (!retryModel.localPath!!.endsWith(".gguf") &&
                            !retryModel.localPath!!.contains("llm"))) {
                    errorMessage = "LLM registry path looks incorrect: ${retryModel?.localPath}. Please restart and try again."
                    return@launch
                }
            }

            isLLMLoading = true
            RunAnywhere.loadLLMModel(LLM_MODEL_ID)
            isLLMLoaded = true
            isLLMLoading = false

            refreshModelState()
        } catch (e: Exception) {
            errorMessage = "LLM load failed: ${e.message}"
            isLLMDownloading = false
            isLLMLoading = false
        }
    }

    fun unloadAllModels() {
        viewModelScope.launch {
            try {
                RunAnywhere.unloadLLMModel()
                RunAnywhere.unloadSTTModel()
                refreshModelState()
            } catch (e: Exception) {
                errorMessage = "Failed to unload models: ${e.message}"
            }
        }
    }

    fun clearError() {
        errorMessage = null
    }
}