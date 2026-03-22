package com.runanywhere.kotlin_starter_example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import android.graphics.BitmapFactory
import com.runanywhere.kotlin_starter_example.data.EncryptedImageManager
import com.runanywhere.kotlin_starter_example.services.ModelService
import com.runanywhere.kotlin_starter_example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import androidx.lifecycle.viewmodel.compose.viewModel

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
                MediaRecorder.AudioSource.VOICE_RECOGNITION, // enables NS/AGC on many devices
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize * 2
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
    modelService: ModelService = viewModel<ModelService>()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val audioRecorder: AudioRecorder = remember { AudioRecorder() }

    var recordState by remember { mutableStateOf(RecordState.IDLE) }
    var hasPermission by remember { mutableStateOf(false) }
    var hasCameraPermission by remember { mutableStateOf(
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    ) }
    var statusMessage by remember { mutableStateOf("Tap the button and speak") }
    var showCrisisNote by remember { mutableStateOf(false) }

    // Camera Features (in-memory preview capture to avoid file/URI issues)
    var capturedImagePaths by remember { mutableStateOf(listOf<String>()) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        scope.launch(Dispatchers.IO) {
            if (bitmap != null) {
                try {
                    val baos = ByteArrayOutputStream()
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, baos)
                    val bytes = baos.toByteArray()
                    val encryptedPath = EncryptedImageManager.saveEncryptedImage(context, bytes)
                    withContext(Dispatchers.Main) {
                        capturedImagePaths = capturedImagePaths + encryptedPath
                        statusMessage = "Photo encrypted & secured."
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        statusMessage = "Photo save failed: ${e.message ?: "Unknown error"}"
                    }
                }
            } else {
                withContext(Dispatchers.Main) { statusMessage = "Camera unavailable" }
            }
        }
    }

    LaunchedEffect(modelService.processingState) {
        when (val state = modelService.processingState) {
            is ModelService.ProcessingState.Done -> {
                if (state.record.severityTag == "Immediate Risk") showCrisisNote = true
                recordState = RecordState.DONE
                statusMessage = "Saved. Your record is secure."
                modelService.resetProcessingState()
                capturedImagePaths = emptyList() // Clear only after success
            }
            is ModelService.ProcessingState.Error -> {
                recordState = RecordState.ERROR
                statusMessage = "Something went wrong. Please try again."
                modelService.resetProcessingState()
            }
            else -> {}
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted: Boolean -> hasPermission = granted }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted: Boolean ->
        hasCameraPermission = granted
        if (!granted) {
            statusMessage = "Camera permission denied"
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (recordState == RecordState.RECORDING) 1.12f else 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "scale"
    )

    // Derived: are both models ready?
    val modelsReady = modelService.isLLMLoaded && modelService.isSTTLoaded
    // Is any download/load in flight?
    val modelsLoading = modelService.isSTTDownloading || modelService.isSTTLoading ||
            modelService.isLLMDownloading || modelService.isLLMLoading

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Record") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
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
            // ----------------------------------------------------------------
            // Model loader panel — shown until both models are ready
            // FIX: Single "Load AI Models" button triggers STT-then-LLM
            // sequentially, preventing the registry race condition.
            // ----------------------------------------------------------------
            if (!modelsReady) {
                ModelSetupPanel(
                    modelService = modelService,
                    modelsLoading = modelsLoading,
                    onLoadClick = { modelService.downloadAndLoadSTTThenLLM() }
                )
            }

            Spacer(Modifier.weight(1f))

            Text(
                text = statusMessage,
                style = MaterialTheme.typography.bodyLarge,
                color = TextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )

            Spacer(Modifier.height(40.dp))

            // Record button — disabled until both models are ready
            if (!hasPermission) {
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentCyan)
                ) { Text("Allow Microphone") }
            } else {
                Box(Modifier.size(240.dp), contentAlignment = Alignment.Center) {
                    // Pulsing Ring for Recording
                    if (recordState == RecordState.RECORDING) {
                        Box(
                            modifier = Modifier
                                .size(240.dp)
                                .scale(scale)
                                .background(
                                    Brush.radialGradient(
                                        listOf(AccentViolet.copy(alpha = 0.25f), Color.Transparent)
                                    ),
                                    CircleShape
                                )
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        // CAMERA BUTTON (Visible during IDLE or RECORDING)
                        if (recordState == RecordState.IDLE || recordState == RecordState.RECORDING) {
                            SmallFloatingActionButton(
                                onClick = {
                                    if (!hasCameraPermission) {
                                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                        return@SmallFloatingActionButton
                                    }
                                    cameraLauncher.launch(null)
                                },
                                containerColor = SurfaceCard,
                                contentColor = Color.White
                            ) {
                                Icon(Icons.Rounded.CameraAlt, "Take Photo")
                            }
                        }

                        // MAIN RECORD BUTTON
                        FloatingActionButton(
                            onClick = {
                                if (!modelsReady) return@FloatingActionButton
                                when (recordState) {
                                    RecordState.IDLE, RecordState.DONE, RecordState.ERROR -> {
                                        val started = audioRecorder.startRecording()
                                        if (started) {
                                            // capturedImagePaths = emptyList() // Removed to allow pre-recording photos
                                            recordState = RecordState.RECORDING
                                            statusMessage = "Recording… tap again when finished"
                                            showCrisisNote = false
                                        } else {
                                            statusMessage = "Could not start recording"
                                        }
                                    }
                                    RecordState.RECORDING -> {
                                        recordState = RecordState.PROCESSING
                                        statusMessage = "Processing your account…"
                                        scope.launch {
                                            val audioData = withContext(Dispatchers.IO) {
                                                audioRecorder.stopRecording()
                                            }
                                            // Pass images to be linked to this incident
                                            modelService.processAudio(
                                                audioData, 
                                                encryptionKey, 
                                                context,
                                                capturedImagePaths
                                            )
                                        }
                                    }
                                    RecordState.PROCESSING -> { /* wait */ }
                                }
                            },
                            modifier = Modifier.size(96.dp),
                            containerColor = when {
                                !modelsReady -> SurfaceCard.copy(alpha = 0.5f)
                                recordState == RecordState.RECORDING -> AccentViolet
                                recordState == RecordState.PROCESSING -> SurfaceCard
                                recordState == RecordState.DONE -> AccentGreen
                                else -> AccentCyan
                            },
                            contentColor = Color.White
                        ) {
                            when (recordState) {
                                RecordState.PROCESSING -> CircularProgressIndicator(
                                    modifier = Modifier.size(36.dp),
                                    color = AccentCyan,
                                    strokeWidth = 3.dp
                                )
                                RecordState.RECORDING -> Icon(Icons.Rounded.Stop, "Stop", Modifier.size(40.dp))
                                else -> Icon(Icons.Rounded.Mic, "Record", Modifier.size(40.dp))
                            }
                        }
                    }
                }
                
                // Show captured image thumbnails
                if (capturedImagePaths.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "${capturedImagePaths.size} photos secured",
                        style = MaterialTheme.typography.labelSmall,
                        color = AccentCyan
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(capturedImagePaths) { path ->
                            CapturedThumb(path = path, context = context)
                        }
                    }
                }

                if (!modelsReady && !modelsLoading) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Load AI models above to enable recording",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

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

            ZeroNetworkBadge()

            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun CapturedThumb(path: String, context: android.content.Context) {
    var bitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(path) {
        withContext(Dispatchers.IO) {
            val bytes = EncryptedImageManager.getDecryptedImage(context, path)
            if (bytes.isNotEmpty()) {
                // Downsample to avoid large memory
                val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                withContext(Dispatchers.Main) { bitmap = bmp }
            }
        }
    }

    Card(
        modifier = Modifier
            .size(90.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, AccentCyan.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard)
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = "Captured photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = AccentCyan, strokeWidth = 2.dp)
            }
        }
    }
}

/**
 * Shows download/load progress for both models with a single trigger button.
 * Progress is shown per-model as each completes sequentially.
 */
@Composable
private fun ModelSetupPanel(
    modelService: ModelService,
    modelsLoading: Boolean,
    onLoadClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "AI Models Required",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
            Text(
                "Models are downloaded once and stored only on this device.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )

            // STT row
            ModelProgressRow(
                label = "Speech Recognition",
                isDownloading = modelService.isSTTDownloading,
                isLoading = modelService.isSTTLoading,
                isLoaded = modelService.isSTTLoaded,
                progress = modelService.sttDownloadProgress
            )

            // LLM row
            ModelProgressRow(
                label = "AI Structuring Model",
                isDownloading = modelService.isLLMDownloading,
                isLoading = modelService.isLLMLoading,
                isLoaded = modelService.isLLMLoaded,
                progress = modelService.llmDownloadProgress
            )

            // Error message
            modelService.errorMessage?.let { err ->
                Text(
                    err,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFFF6B6B)
                )
            }

            Button(
                onClick = onLoadClick,
                enabled = !modelsLoading,
                colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (modelsLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Downloading…", color = Color.White)
                } else {
                    Text("Load AI Models", color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun ModelProgressRow(
    label: String,
    isDownloading: Boolean,
    isLoading: Boolean,
    isLoaded: Boolean,
    progress: Float
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            Text(
                text = when {
                    isLoaded -> "✓ Ready"
                    isLoading -> "Loading…"
                    isDownloading -> "${(progress * 100).toInt()}%"
                    else -> "Pending"
                },
                style = MaterialTheme.typography.bodySmall,
                color = when {
                    isLoaded -> AccentGreen
                    isDownloading || isLoading -> AccentCyan
                    else -> TextMuted
                }
            )
        }
        if (isDownloading) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = AccentCyan,
                trackColor = SurfaceCard
            )
        } else if (isLoading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = AccentViolet,
                trackColor = SurfaceCard
            )
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
        Box(modifier = Modifier.size(8.dp).background(AccentGreen, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(
            "Nothing leaves this device. Ever.",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted
        )
    }
}