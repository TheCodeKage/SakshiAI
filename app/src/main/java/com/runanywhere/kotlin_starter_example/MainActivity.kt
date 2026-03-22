package com.runanywhere.kotlin_starter_example

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity




import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.runanywhere.kotlin_starter_example.security.ShakeDetector
import com.runanywhere.kotlin_starter_example.services.ModelService
import com.runanywhere.kotlin_starter_example.ui.screens.*
import com.runanywhere.kotlin_starter_example.ui.theme.KotlinStarterTheme
import com.runanywhere.sdk.core.onnx.ONNX
import com.runanywhere.sdk.foundation.bridge.extensions.CppBridgeModelPaths
import com.runanywhere.sdk.llm.llamacpp.LlamaCPP
import com.runanywhere.sdk.public.RunAnywhere
import com.runanywhere.sdk.public.SDKEnvironment
import com.runanywhere.sdk.storage.AndroidPlatformContext

class MainActivity : ComponentActivity() {



    // --- AUTO LOCK STATES ---
    private var isLocked by mutableStateOf(true)
    private var encryptionKey by mutableStateOf<String?>(null)
    private var lastPausedTime = 0L
    private val AUTO_LOCK_DELAY_MS = 30_000L // 30 seconds

    // --- SENSOR STATES ---
    private var sensorManager: SensorManager? = null
    private var shakeDetector: ShakeDetector? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        

        // Initialize Android platform context FIRST - this sets up storage paths
        // The SDK requires this before RunAnywhere.initialize() on Android

        // SDK INITIALIZATION (Preserved)
        AndroidPlatformContext.initialize(this)
        RunAnywhere.initialize(environment = SDKEnvironment.DEVELOPMENT)

        // Initialize Shake Detector
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        shakeDetector = ShakeDetector {
            // Check if Quick Exit is enabled in Settings
            val prefs = getSharedPreferences("prefs", Context.MODE_PRIVATE)
            val isQuickExitEnabled = prefs.getBoolean("quick_exit_enabled", false)

            if (isQuickExitEnabled && !isLocked) {
                lockApp() // Trigger lock on shake
            }
        }

        val runanywherePath = java.io.File(filesDir, "runanywhere").absolutePath
        CppBridgeModelPaths.setBaseDirectory(runanywherePath)

        try {
            LlamaCPP.register(priority = 100)
        } catch (e: Throwable) {
            Log.w("MainActivity", "LlamaCPP.register failure: ${e.message}")
        }
        ONNX.register(priority = 100)
        ModelService.registerDefaultModels()

        setContent {
            KotlinStarterTheme {
                if (isLocked) {
                    EntryScreen(onPinCorrect = { key ->
                        encryptionKey = key
                        isLocked = false
                        registerShake() // Start listening when unlocked
                    })
                } else {
                    RunAnywhereAppContent(encryptionKey)
                }
            }
        }
    }

    private fun lockApp() {
        isLocked = true
        encryptionKey = null
        unregisterShake() // Stop listening when locked
    }

    private fun registerShake() {
        val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        sensorManager?.registerListener(shakeDetector, sensor, SensorManager.SENSOR_DELAY_UI)
    }

    private fun unregisterShake() {
        sensorManager?.unregisterListener(shakeDetector)
    }

    override fun onResume() {
        super.onResume()
        // Check if enough time has passed since pause to trigger auto-lock
        if (!isLocked && lastPausedTime > 0 && 
            (System.currentTimeMillis() - lastPausedTime) > AUTO_LOCK_DELAY_MS) {
            lockApp()
        }
        if (!isLocked) registerShake() // Resume listening if already unlocked
    }

    override fun onPause() {
        super.onPause()
        lastPausedTime = System.currentTimeMillis()
        unregisterShake() // Always stop listening when activity is paused
    }

    // Removed onStop() - auto-lock now happens in onResume() based on time elapsed

    @Composable
    fun RunAnywhereAppContent(key: String?) {
        val navController = rememberNavController()
        val modelService: ModelService = viewModel()
        var timelineRefreshKey by remember { mutableStateOf(0) }

        NavHost(navController = navController, startDestination = "timeline") {
            composable("timeline") {
                key?.let { secureKey ->
                    TimelineScreen(
                        encryptionKey = secureKey,
                        refreshTrigger = timelineRefreshKey,
                        modelService = modelService,
                        onNavigateToRecord = { navController.navigate("record") },
                        onNavigateToDetail = { id -> navController.navigate("detail/$id") },
                        onNavigateToSettings = { navController.navigate("settings") }
                    )
                }
            }
            composable("settings") {
                SettingsScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable("record") {
                key?.let { secureKey ->
                    RecordScreen(
                        encryptionKey = secureKey,
                        onNavigateBack = {
                            timelineRefreshKey++
                            navController.popBackStack()
                        },
                        modelService = modelService
                    )
                }
            }
            composable("detail/{incidentId}") { backStackEntry ->
                val incidentId = backStackEntry.arguments?.getString("incidentId") ?: ""
                key?.let { secureKey ->
                    EntryDetailScreen(
                        incidentId = incidentId,
                        encryptionKey = secureKey,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}