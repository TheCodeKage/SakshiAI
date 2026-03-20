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
        val models = RunAnywhere.availableModels()
        return models.find { it.id == modelId }?.localPath != null
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