package com.runanywhere.kotlin_starter_example.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.runanywhere.kotlin_starter_example.data.IncidentRecord
import com.runanywhere.kotlin_starter_example.data.IncidentRepository
import com.runanywhere.kotlin_starter_example.data.SecureZipExporter
import com.runanywhere.kotlin_starter_example.data.EncryptedImageManager
import com.runanywhere.kotlin_starter_example.services.ModelService
import com.runanywhere.kotlin_starter_example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    encryptionKey: String,
    refreshTrigger: Int,
    modelService: ModelService,
    onNavigateToRecord: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Use Singleton to avoid DB lock/lag issues
    val repository = remember { IncidentRepository.getInstance(context, encryptionKey) }

    var incidents by remember { mutableStateOf(listOf<IncidentRecord>()) }
    var isExporting by remember { mutableStateOf(false) }
    var exportPassword by remember { mutableStateOf("") }
    var showExportWarning by remember { mutableStateOf(false) }
    var dbError by remember { mutableStateOf<String?>(null) }

    // Reruns every time we return from RecordScreen or the key changes
    LaunchedEffect(refreshTrigger, encryptionKey) {
        try {
            incidents = withContext(Dispatchers.IO) { repository.getAllIncidents() }
            if (incidents.isEmpty()) {
                dbError = null // Clear error if successful but empty
            }
        } catch (e: Exception) {
            // Database error (wrong key, corrupted, etc.)
            dbError = e.message
            incidents = emptyList()
        }
    }

    // Reload list when AI background processing finishes
    LaunchedEffect(modelService.processingState) {
        if (modelService.processingState is ModelService.ProcessingState.Done) {
            try {
                incidents = withContext(Dispatchers.IO) { repository.getAllIncidents() }
            } catch (e: Exception) {
                dbError = e.message
            }
        }
    }

    // ZIP Export Warning Dialog with Password Input
    if (showExportWarning) {
        AlertDialog(
            onDismissRequest = {
                showExportWarning = false
                exportPassword = ""
            },
            title = { Text("Secure ZIP Export", color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Create an encrypted ZIP with a password-protected PDF plus all audio and photo evidence.",
                        color = TextMuted
                    )
                    OutlinedTextField(
                        value = exportPassword,
                        onValueChange = { exportPassword = it },
                        label = { Text("ZIP/PDF Password (Optional)") },
                        placeholder = { Text("Leave blank for no password") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentCyan,
                            unfocusedBorderColor = TextMuted,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "• File saved to Downloads\n• Keep the password separate\n• ZIP contains all incidents (audio, photos, PDF)",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExportWarning = false
                        isExporting = true
                        val pwd = exportPassword
                        exportPassword = "" // Clear immediately
                        scope.launch {
                            try {
                                val passwordOrEmpty = if (pwd.isBlank()) "" else pwd
                                withContext(Dispatchers.IO) {
                                    SecureZipExporter.createGlobalSecureExport(context, incidents, passwordOrEmpty)
                                }
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "ZIP Saved to Downloads", Toast.LENGTH_LONG).show()
                                }
                            } catch (e: Throwable) {
                                e.printStackTrace()
                                withContext(Dispatchers.Main) {
                                    dbError = "Export failed: ${e.message ?: e::class.java.simpleName}"
                                }
                            } finally {
                                isExporting = false
                            }
                        }
                    }
                ) {
                    Text("Export All Data (ZIP)", color = AccentCyan)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showExportWarning = false
                    exportPassword = ""
                }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = SurfaceCard
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sakshi") }, // Updated title per your context
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Rounded.Settings, "Settings", tint = AccentCyan)
                    }
                    if (incidents.isNotEmpty()) {
                        IconButton(onClick = { showExportWarning = true }) {
                            if (isExporting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = AccentCyan
                                )
                            } else {
                                Icon(Icons.Rounded.FileDownload, "Backup All", tint = AccentCyan)
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PrimaryDark)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToRecord,
                containerColor = AccentCyan,
                contentColor = Color.White
            ) {
                Icon(Icons.Rounded.Add, "New record")
            }
        },
        containerColor = PrimaryDark
    ) { padding ->
        if (incidents.isEmpty() && modelService.processingState !is ModelService.ProcessingState.Processing) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("🔒", style = MaterialTheme.typography.displayMedium)
                    Text("No records yet", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Text(
                        "Tap + to add your first record.\nEverything stays on this device.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                // Background processing banner
                if (modelService.processingState is ModelService.ProcessingState.Processing) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF2A1F00))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    color = Color(0xFFFFBF47),
                                    strokeWidth = 2.dp
                                )
                                Text(
                                    "Securing your record…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFFFBF47)
                                )
                            }
                        }
                    }
                }

                // Pattern warning banner
                if (incidents.any { it.patternFlag }) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF2A1F00))
                        ) {
                            Text(
                                "⚠  A repeating pattern has been detected across your records.",
                                modifier = Modifier.padding(14.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFFFBF47)
                            )
                        }
                    }
                }

                items(incidents) { record ->
                    IncidentCard(record = record, onClick = { onNavigateToDetail(record.id) })
                }
            }
        }
    }
}

@Composable
fun IncidentCard(record: IncidentRecord, onClick: () -> Unit) {
    val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    dateFormat.format(Date(record.timestamp)),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
                SeverityChip(record.severityTag)
            }

            if (record.incidentType.isNotBlank()) {
                Text(
                    record.incidentType,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary
                )
            }

            if (record.rawTranscript.isNotBlank()) {
                Text(
                    record.rawTranscript.take(100) + if (record.rawTranscript.length > 100) "…" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            } else if (record.audioFilePath != null) {
                Text(
                    "Audio Record (No Transcript)",
                    style = MaterialTheme.typography.bodySmall,
                    color = AccentCyan
                )
            }

            if (record.imagePaths.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                IncidentPhotoThumb(path = record.imagePaths.first(), count = record.imagePaths.size)
            }

            if (record.patternFlag) {
                Text(
                    "⚠ Repeat pattern",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFFFBF47)
                )
            }
        }
    }
}

@Composable
fun IncidentPhotoThumb(path: String, count: Int) {
    val ctx = LocalContext.current
    var bitmap by remember(path) { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(path) {
        withContext(Dispatchers.IO) {
            val bytes = EncryptedImageManager.getDecryptedImage(ctx, path)
            if (bytes.isNotEmpty()) {
                val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 }
                val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                withContext(Dispatchers.Main) { bitmap = bmp }
            }
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Card(
            modifier = Modifier.size(54.dp),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard)
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = "Photo thumb",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = AccentCyan)
                }
            }
        }
        Text(
            if (count > 1) "$count photos" else "1 photo",
            style = MaterialTheme.typography.labelSmall,
            color = TextPrimary
        )
    }
}

@Composable
fun SeverityChip(severity: String) {
    val (bg, fg) = when (severity) {
        "Immediate Risk" -> Color(0xFF2A0000) to Color(0xFFFF6B6B)
        "Concerning Pattern" -> Color(0xFF2A1500) to Color(0xFFFFBF47)
        else -> Color(0xFF002A0F) to Color(0xFF4ADE80)
    }
    Surface(shape = RoundedCornerShape(20.dp), color = bg) {
        Text(
            severity,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = fg
        )
    }
}