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
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * Service for managing AI models - handles registration, downloading, and loading
 * Similar to the Flutter ModelService for consistent behavior across platforms
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
        // Model IDs - using officially supported models
        const val LLM_MODEL_ID = "smollm2-360m-instruct-q8_0"
        const val STT_MODEL_ID = "sherpa-onnx-whisper-tiny.en"
        
        /**
         * Register default models with the SDK.
         * Includes LLM and STT only (for SaakshiAI incident documentation).
         */
        fun registerDefaultModels() {
            // LLM Model - Qwen 2.5 0.5B Instruct (better accuracy for structured extraction)
            RunAnywhere.registerModel(
                id = LLM_MODEL_ID,
                name = "SmolLM2 360M Instruct Q8_0",
                url = "https://huggingface.co/HuggingFaceTB/SmolLM2-360M-Instruct-GGUF/resolve/main/smollm2-360m-instruct-q8_0.gguf",
                framework = InferenceFramework.LLAMA_CPP,
                modality = ModelCategory.LANGUAGE,
                memoryRequirement = 400_000_000
            )
            
            // STT Model - Whisper Tiny English (fast transcription)
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
    
    /**
     * Refresh model loaded states from SDK
     */
    private suspend fun refreshModelState() {
        isLLMLoaded = RunAnywhere.isLLMModelLoaded()
        isSTTLoaded = RunAnywhere.isSTTModelLoaded()
    }
    
    /**
     * Check if a model is downloaded
     */
    private suspend fun isModelDownloaded(modelId: String): Boolean {
        val models = RunAnywhere.availableModels()
        val model = models.find { it.id == modelId }
        return model?.localPath != null
    }
    
    /**
     * Download and load LLM model
     */
    fun downloadAndLoadLLM() {
        if (isLLMDownloading || isLLMLoading) return
        
        viewModelScope.launch {
            try {
                errorMessage = null
                
                // Check if already downloaded
                if (!isModelDownloaded(LLM_MODEL_ID)) {
                    isLLMDownloading = true
                    llmDownloadProgress = 0f
                    
                    RunAnywhere.downloadModel(LLM_MODEL_ID)
                        .catch { e ->
                            errorMessage = "LLM download failed: ${e.message}"
                        }
                        .collect { progress ->
                            llmDownloadProgress = progress.progress
                        }
                    
                    isLLMDownloading = false
                }
                
                // Load the model
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
    }
    
    /**
     * Download and load STT model
     */
    fun downloadAndLoadSTT() {
        if (isSTTDownloading || isSTTLoading) return
        
        viewModelScope.launch {
            try {
                errorMessage = null
                
                // Check if already downloaded
                if (!isModelDownloaded(STT_MODEL_ID)) {
                    isSTTDownloading = true
                    sttDownloadProgress = 0f
                    
                    RunAnywhere.downloadModel(STT_MODEL_ID)
                        .catch { e ->
                            errorMessage = "STT download failed: ${e.message}"
                        }
                        .collect { progress ->
                            sttDownloadProgress = progress.progress
                        }
                    
                    isSTTDownloading = false
                }
                
                // Load the model
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
    }
    
    /**
     * Unload all models
     */
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
    
    /**
     * Clear error message
     */
    fun clearError() {
        errorMessage = null
    }
}
