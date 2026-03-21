package com.runanywhere.kotlin_starter_example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runanywhere.kotlin_starter_example.data.IncidentRecord
import com.runanywhere.kotlin_starter_example.data.IncidentRepository
import com.runanywhere.kotlin_starter_example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val repository = remember { IncidentRepository(context, encryptionKey) }
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

fun buildLegalContext(record: IncidentRecord): String? {
    val points = mutableListOf<String>()

    if (record.threatDocumented) {
        points.add("A documented verbal or physical threat can support an application for a Protection Order under the Protection of Women from Domestic Violence Act, 2005 (PWDVA).")
    }

    if (record.patternFlag) {
        points.add("Multiple timestamped records showing a pattern of behaviour strengthen a case significantly — courts look for evidence of repeated conduct, not just isolated incidents.")
    }

    if (record.witnessesPresent.isNotBlank()) {
        points.add("The presence of witnesses, noted here, can be referenced when filing a Domestic Incident Report (DIR) with a Protection Officer.")
    }

    if (record.severityTag == "Immediate Risk") {
        points.add("Records documenting immediate physical risk can be used to request emergency relief, including the right to reside in the shared household or exclusion of the respondent.")
    }

    if (record.whoInvolved.isNotBlank()) {
        points.add("The named person can be identified as a respondent in legal proceedings.")
    }

    if (points.isEmpty()) return null

    return points.joinToString("\n\n")
}

@Composable
private fun LegalContextCard(record: IncidentRecord) {
    val context = buildLegalContext(record) ?: return

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
