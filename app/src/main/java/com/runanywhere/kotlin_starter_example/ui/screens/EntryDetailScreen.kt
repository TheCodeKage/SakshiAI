package com.runanywhere.kotlin_starter_example.ui.screens

import android.content.ContentValues
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runanywhere.kotlin_starter_example.data.EncryptedAudioFileManager
import com.runanywhere.kotlin_starter_example.data.IncidentRecord
import com.runanywhere.kotlin_starter_example.data.IncidentRepository
import com.runanywhere.kotlin_starter_example.services.LegalGuidanceService
import com.runanywhere.kotlin_starter_example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryDetailScreen(
    incidentId: String,
    encryptionKey: String,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { IncidentRepository.getInstance(context, encryptionKey) }
    val scope = rememberCoroutineScope()
    var record by remember { mutableStateOf<IncidentRecord?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(incidentId) {
        record = withContext(Dispatchers.IO) {
            repository.getIncidentById(incidentId)
        }
    }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Record?", color = TextPrimary) },
            text = {
                Text(
                    "This will permanently delete this record. This action cannot be undone.",
                    color = TextMuted
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            repository.deleteIncident(incidentId)
                            withContext(Dispatchers.Main) {
                                onNavigateBack()
                            }
                        }
                    }
                ) {
                    Text("Delete", color = Color(0xFFFF453A))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = SurfaceCard
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Entry Detail") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Rounded.Delete, "Delete", tint = Color(0xFFFF453A))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PrimaryDark)
            )
        },
        containerColor = PrimaryDark
    ) { padding ->
        record?.let { r ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val dateFormat = SimpleDateFormat("EEEE, dd MMMM yyyy 'at' HH:mm", Locale.getDefault())
                Text(
                    dateFormat.format(Date(r.timestamp)),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )

                // --- AUDIO PLAYER ---
                if (r.audioFilePath != null) {
                    AudioPlayerCard(audioPath = r.audioFilePath, context = context)
                }
                // -------------------

                SeverityChipDetail(r.severityTag)

                DetailCard("Incident type", r.incidentType.ifBlank { "Not specified" })
                DetailCard("Person involved", r.whoInvolved.ifBlank { "Not specified" })
                DetailCard("Threat documented", if (r.threatDocumented) "Yes" else "No")
                DetailCard("Witnesses present", r.witnessesPresent.ifBlank { "None mentioned" })
                DetailCard("Pattern detected", if (r.patternFlag) "Yes — repeat language found" else "No")

                if (r.rawTranscript.isNotBlank()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceCard)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("Your account", style = MaterialTheme.typography.labelMedium, color = TextMuted)
                            Text(r.rawTranscript, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                        }
                    }
                }

                LegalContextCard(r)

                if (r.severityTag == "Immediate Risk") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A1010))
                    ) {
                        Text(
                            "If you are in immediate danger, please call 112.",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFFF6B6B)
                        )
                    }
                }
            }
        } ?: Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = AccentCyan)
        }
    }
}


@Composable
fun AudioPlayerCard(audioPath: String, context: android.content.Context) {
    var isPlaying by remember { mutableStateOf(false) }
    var audioTrack by remember { mutableStateOf<AudioTrack?>(null) }
    val scope = rememberCoroutineScope()

    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose {
            try {
                if (audioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    audioTrack?.stop()
                }
                audioTrack?.release()
            } catch (e: Exception) { }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2A38))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(
                onClick = {
                    if (isPlaying) {
                        try {
                            if (audioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING) {
                                audioTrack?.stop()
                            }
                        } catch (e: Exception) { }
                        isPlaying = false
                    } else {
                        scope.launch(Dispatchers.IO) {
                            try {
                                isPlaying = true
                                val pcmData = EncryptedAudioFileManager.getDecryptedStream(context, audioPath)
                                if (pcmData.isNotEmpty()) {
                                    val minBufferSize = AudioTrack.getMinBufferSize(
                                        16000,
                                        AudioFormat.CHANNEL_OUT_MONO,
                                        AudioFormat.ENCODING_PCM_16BIT
                                    )
                                    val bufferSize = maxOf(minBufferSize, pcmData.size)

                                    val track = AudioTrack.Builder()
                                        .setAudioAttributes(
                                            AudioAttributes.Builder()
                                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                                .build()
                                        )
                                        .setAudioFormat(
                                            AudioFormat.Builder()
                                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                                .setSampleRate(16000)
                                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                                .build()
                                        )
                                        .setBufferSizeInBytes(bufferSize)
                                        .setTransferMode(AudioTrack.MODE_STATIC)
                                        .build()

                                    audioTrack = track
                                    track.write(pcmData, 0, pcmData.size)
                                    track.play()
                                    
                                    // Reset state when done (approximation based on duration)
                                    val durationMs = (pcmData.size / 2) / 16 // 16 samples per ms
                                    kotlinx.coroutines.delay(durationMs.toLong() + 100)
                                    isPlaying = false
                                } else {
                                    isPlaying = false
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                isPlaying = false
                            }
                        }
                    }
                },
                modifier = Modifier
                    .size(48.dp)
                    .background(AccentCyan, androidx.compose.foundation.shape.CircleShape),
                colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Stop" else "Play"
                )
            }
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Voice Evidence",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White
                )
                Text(
                    "Encrypted & Secure",
                    style = MaterialTheme.typography.labelSmall,
                    color = AccentCyan
                )
            }

            // Export Button
            IconButton(
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        try {
                            // Decrypt first
                            val pcmData = EncryptedAudioFileManager.getDecryptedStream(context, audioPath)
                            if (pcmData.isEmpty()) return@launch

                            val filename = "Evidence_${System.currentTimeMillis()}.pcm" // Raw PCM for now as converting to WAV is tricky without header logic
                            // Actually, let's just save raw PCM or try to wrap it if possible but raw is safer than broken WAV
                            // Or better: let's wrap it in a simple WAV header if we can, or just save as .pcm (audacity can open)
                            // For simplicity and robustness, saving as .pcm (raw 16bit 16kHz mono)
                            
                            // Saving to Downloads (API 29+) or Audio (API < 29)
                            val resolver = context.contentResolver
                            val contentValues = ContentValues().apply {
                                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                                put(MediaStore.MediaColumns.MIME_TYPE, "audio/x-pcm")
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                                }
                            }
                            
                            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                            } else {
                                resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, contentValues)
                            }
                            uri?.let {
                                resolver.openOutputStream(it)?.use { os ->
                                    os.write(pcmData)
                                }
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Saved to Downloads: $filename", Toast.LENGTH_LONG).show()
                                }
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            ) {
                Icon(Icons.Rounded.FileDownload, "Save to device", tint = Color.LightGray)
            }
        }
    }
}

@Composable
private fun DetailCard(label: String, value: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = TextMuted)
            Text(value, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
        }
    }
}

@Composable
private fun SeverityChipDetail(severity: String) {
    val (bg, fg) = when (severity) {
        "Immediate Risk" -> Color(0xFF2A0000) to Color(0xFFFF6B6B)
        "Concerning Pattern" -> Color(0xFF2A1500) to Color(0xFFFFBF47)
        else -> Color(0xFF002A0F) to Color(0xFF4ADE80)
    }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = bg,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Text(
            severity,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = fg
        )
    }
}


@Composable
private fun LegalContextCard(record: IncidentRecord) {
    val points = LegalGuidanceService.getLegalContext(record)
    if (points.isEmpty()) return
    
    val context = points.joinToString("\n\n")

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1F2D))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("⚖️", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "What this record could mean",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color(0xFF60B4D8)
                )
            }

            Text(
                text = context,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFB0CDD8),
                lineHeight = 18.sp
            )

            HorizontalDivider(
                color = Color(0xFF1E3A4A),
                thickness = 1.dp
            )

            Text(
                text = "This is not legal advice. For guidance specific to your situation, speak with a lawyer or contact iCall (9152987821) or the National Commission for Women (7827170170).",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF607D8B),
                lineHeight = 15.sp
            )
        }
    }
}
