package com.runanywhere.kotlin_starter_example.ui.screens

import android.content.Intent
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.runanywhere.kotlin_starter_example.data.IncidentRecord
import com.runanywhere.kotlin_starter_example.data.IncidentRepository
import com.runanywhere.kotlin_starter_example.data.PdfExporter
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
    onNavigateToRecord: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { IncidentRepository(context, encryptionKey) }

    var incidents by remember { mutableStateOf(listOf<IncidentRecord>()) }
    var isExporting by remember { mutableStateOf(false) }
    var showExportWarning by remember { mutableStateOf(false) }
    var dbError by remember { mutableStateOf<String?>(null) }

    // Use refreshTrigger as the key so this reruns every time we return from RecordScreen
    LaunchedEffect(refreshTrigger) {
        try {
            incidents = withContext(Dispatchers.IO) { repository.getAllIncidents() }
        } catch (e: Exception) {
            // Database error (wrong key, corrupted, etc.)
            dbError = e.message
            incidents = emptyList()
        }
    }

    // PDF Export Warning Dialog
    if (showExportWarning) {
        AlertDialog(
            onDismissRequest = { showExportWarning = false },
            title = { Text("Export Warning", color = TextPrimary) },
            text = {
                Text(
                    "This PDF contains sensitive information. Ensure you:\n\n" +
                            "• Store it in a secure location\n" +
                            "• Delete it after sharing with your legal advisor\n" +
                            "• Never leave it on an unencrypted device\n\n" +
                            "Continue with export?",
                    color = TextMuted
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExportWarning = false
                        isExporting = true
                        scope.launch {
                            try {
                                val file = withContext(Dispatchers.IO) {
                                    PdfExporter.export(context, incidents)
                                }
                                // Open the PDF
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.provider",
                                    file
                                )
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, "application/pdf")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                // Handle error silently — file still saved
                            } finally {
                                isExporting = false
                            }
                        }
                    }
                ) {
                    Text("Export", color = AccentCyan)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportWarning = false }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = SurfaceCard
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SafeNotes") },
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
                                Icon(Icons.Rounded.FileDownload, "Export PDF", tint = AccentCyan)
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
        if (incidents.isEmpty()) {
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
                // Pattern warning banner if any entries have patternFlag
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
private fun IncidentCard(record: IncidentRecord, onClick: () -> Unit) {
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
private fun SeverityChip(severity: String) {
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
