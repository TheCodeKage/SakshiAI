package com.runanywhere.kotlin_starter_example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.runanywhere.kotlin_starter_example.services.ModelService
import com.runanywhere.kotlin_starter_example.ui.screens.EntryScreen
import com.runanywhere.kotlin_starter_example.ui.screens.TimelineScreen
import com.runanywhere.kotlin_starter_example.ui.screens.RecordScreen
import com.runanywhere.kotlin_starter_example.ui.screens.EntryDetailScreen
import com.runanywhere.kotlin_starter_example.ui.theme.KotlinStarterTheme
import android.util.Log
import com.runanywhere.sdk.core.onnx.ONNX
import com.runanywhere.sdk.foundation.bridge.extensions.CppBridgeModelPaths
import com.runanywhere.sdk.llm.llamacpp.LlamaCPP
import com.runanywhere.sdk.public.RunAnywhere
import com.runanywhere.sdk.public.SDKEnvironment
import com.runanywhere.sdk.storage.AndroidPlatformContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Initialize Android platform context FIRST - this sets up storage paths
        // The SDK requires this before RunAnywhere.initialize() on Android
        AndroidPlatformContext.initialize(this)
        
        // Initialize RunAnywhere SDK for development
        RunAnywhere.initialize(environment = SDKEnvironment.DEVELOPMENT)
        
        // Set the base directory for model storage
        val runanywherePath = java.io.File(filesDir, "runanywhere").absolutePath
        CppBridgeModelPaths.setBaseDirectory(runanywherePath)
        
        // Register backends FIRST - these must be registered before loading any models
        // They provide the inference capabilities (TEXT_GENERATION, STT, TTS, VLM)
        try {
            LlamaCPP.register(priority = 100)  // For LLM + VLM (GGUF models)
        } catch (e: Throwable) {
            // VLM native registration may fail if .so doesn't include nativeRegisterVlm;
            // LLM text generation still works since it was registered before VLM in register()
            Log.w("MainActivity", "LlamaCPP.register partial failure (VLM may be unavailable): ${e.message}")
        }
        ONNX.register(priority = 100)      // For STT/TTS (ONNX models)
        
        // Register default models
        ModelService.registerDefaultModels()
        
        setContent {
            KotlinStarterTheme {
                RunAnywhereApp()
            }
        }
    }
}

@Composable
fun RunAnywhereApp() {
    val navController = rememberNavController()
    val modelService: ModelService = viewModel()
    
    // Store encryption key in memory after successful PIN entry
    var encryptionKey by remember { mutableStateOf<String?>(null) }
    
    NavHost(navController = navController, startDestination = "entry") {

        composable("entry") {
            EntryScreen(onPinCorrect = { key ->
                encryptionKey = key
                navController.navigate("timeline") {
                    popUpTo("entry") { inclusive = true }
                }
            })
        }

        composable("timeline") {
            encryptionKey?.let { key ->
                TimelineScreen(
                    encryptionKey = key,
                    onNavigateToRecord = { navController.navigate("record") },
                    onNavigateToDetail = { id -> navController.navigate("detail/$id") }
                )
            }
        }

        composable("record") {
            encryptionKey?.let { key ->
                RecordScreen(
                    encryptionKey = key,
                    onNavigateBack = { navController.popBackStack() },
                    modelService = modelService
                )
            }
        }

        composable("detail/{incidentId}") { backStackEntry ->
            val incidentId = backStackEntry.arguments?.getString("incidentId") ?: ""
            encryptionKey?.let { key ->
                EntryDetailScreen(
                    incidentId = incidentId,
                    encryptionKey = key,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
