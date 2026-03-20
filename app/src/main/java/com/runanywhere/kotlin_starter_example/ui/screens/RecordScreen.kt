package com.runanywhere.kotlin_starter_example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.runanywhere.kotlin_starter_example.data.IncidentProcessor
import com.runanywhere.kotlin_starter_example.data.IncidentRepository
import com.runanywhere.kotlin_starter_example.services.ModelService
import com.runanywhere.kotlin_starter_example.ui.components.ModelLoaderWidget
import com.runanywhere.kotlin_starter_example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import androidx.lifecycle.viewmodel.compose.viewModel

// Audio recording helper class (copied from SpeechToTextScreen.kt)
private class AudioRecorder {
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private val audioData = ByteArrayOutputStream()

    companion object {
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    fun startRecording(): Boolean {
        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) return false
        return try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize * 2
            )
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) return false
            audioData.reset()
            audioRecord?.startRecording()
            isRecording = true
            Thread {
                val buffer = ByteArray(bufferSize)
                while (isRecording) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read > 0) synchronized(audioData) { audioData.write(buffer, 0, read) }
                }
            }.start()
            true
        } catch (e: SecurityException) { false }
    }

    fun stopRecording(): ByteArray {
        isRecording = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        return synchronized(audioData) { audioData.toByteArray() }
    }
}

enum class RecordState { IDLE, RECORDING, PROCESSING, DONE, ERROR }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordScreen(
    encryptionKey: String,
    onNavigateBack: () -> Unit,
    modelService: ModelService = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val audioRecorder = remember { AudioRecorder() }
    val repository = remember { IncidentRepository(context, encryptionKey) }

    var recordState by remember { mutableStateOf(RecordState.IDLE) }
    var hasPermission by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("Tap the button and speak") }
    var showCrisisNote by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    // Pulse animation for record button
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (recordState == RecordState.RECORDING) 1.12f else 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "scale"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Record") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PrimaryDark)
            )
        },
        containerColor = PrimaryDark
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Model loader — shown if models not ready
            if (!modelService.isLLMLoaded || !modelService.isSTTLoaded) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (!modelService.isSTTLoaded) {
                        ModelLoaderWidget(
                            modelName = "Speech Recognition",
                            isDownloading = modelService.isSTTDownloading,
                            isLoading = modelService.isSTTLoading,
                            isLoaded = modelService.isSTTLoaded,
                            downloadProgress = modelService.sttDownloadProgress,
                            onLoadClick = { modelService.downloadAndLoadSTT() }
                        )
                    }
                    if (!modelService.isLLMLoaded) {
                        ModelLoaderWidget(
                            modelName = "AI Structuring Model",
                            isDownloading = modelService.isLLMDownloading,
                            isLoading = modelService.isLLMLoading,
                            isLoaded = modelService.isLLMLoaded,
                            downloadProgress = modelService.llmDownloadProgress,
                            onLoadClick = { modelService.downloadAndLoadLLM() }
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // Status message
            Text(
                text = statusMessage,
                style = MaterialTheme.typography.bodyLarge,
                color = TextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )

            Spacer(Modifier.height(40.dp))

            // Record button
            if (!hasPermission) {
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentCyan)
                ) { Text("Allow Microphone") }
            } else {
                Box(Modifier.size(140.dp), contentAlignment = Alignment.Center) {
                    if (recordState == RecordState.RECORDING) {
                        Box(
                            modifier = Modifier
                                .size(140.dp)
                                .scale(scale)
                                .background(
                                    Brush.radialGradient(listOf(AccentViolet.copy(alpha = 0.25f), Color.Transparent)),
                                    CircleShape
                                )
                        )
                    }

                    FloatingActionButton(
                        onClick = {
                            when (recordState) {
                                RecordState.IDLE, RecordState.DONE, RecordState.ERROR -> {
                                    val started = audioRecorder.startRecording()
                                    if (started) {
                                        recordState = RecordState.RECORDING
                                        statusMessage = "Recording... tap again when finished"
                                        showCrisisNote = false
                                    } else {
                                        statusMessage = "Could not start recording"
                                    }
                                }
                                RecordState.RECORDING -> {
                                    recordState = RecordState.PROCESSING
                                    statusMessage = "Processing your account…"
                                    scope.launch {
                                        try {
                                            val audioData = withContext(Dispatchers.IO) {
                                                audioRecorder.stopRecording()
                                            }
                                            val record = IncidentProcessor.process(audioData)
                                            repository.saveIncident(record)

                                            if (record.severityTag == "Immediate Risk") {
                                                showCrisisNote = true
                                            }

                                            recordState = RecordState.DONE
                                            statusMessage = "Saved. Your record is secure."
                                        } catch (e: Exception) {
                                            recordState = RecordState.ERROR
                                            statusMessage = "Something went wrong. Please try again."
                                        }
                                    }
                                }
                                RecordState.PROCESSING -> { /* do nothing while processing */ }
                            }
                        },
                        modifier = Modifier.size(96.dp),
                        containerColor = when (recordState) {
                            RecordState.RECORDING -> AccentViolet
                            RecordState.PROCESSING -> SurfaceCard
                            RecordState.DONE -> AccentGreen
                            else -> AccentCyan
                        },
                        contentColor = Color.White
                    ) {
                        when (recordState) {
                            RecordState.PROCESSING -> CircularProgressIndicator(
                                modifier = Modifier.size(36.dp), color = AccentCyan, strokeWidth = 3.dp
                            )
                            RecordState.RECORDING -> Icon(Icons.Rounded.Stop, "Stop", Modifier.size(40.dp))
                            else -> Icon(Icons.Rounded.Mic, "Record", Modifier.size(40.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            // Crisis note — shown if Immediate Risk detected
            if (showCrisisNote) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2A1010))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "If you are in immediate danger, please call 112.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFFF6B6B)
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // Zero network indicator
            ZeroNetworkBadge()

            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun ZeroNetworkBadge() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(AccentGreen, CircleShape)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "Nothing leaves this device. Ever.",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted
        )
    }
}
