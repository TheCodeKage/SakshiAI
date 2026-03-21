package com.runanywhere.kotlin_starter_example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.runanywhere.kotlin_starter_example.R
import com.runanywhere.kotlin_starter_example.security.DisguiseManager
import com.runanywhere.kotlin_starter_example.security.PinManager
import com.runanywhere.kotlin_starter_example.ui.theme.*

// ─────────────────────────────────────────────────────────────────────────────
// SettingsScreen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    encryptionKey: String,
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current

    // ── Persisted UI state ────────────────────────────────────────────────────
    var quickExit by remember { mutableStateOf(true) }
    var autoLock  by remember { mutableStateOf(true) }
    var disguise  by remember { mutableStateOf(DisguiseManager.isDisguiseEnabled(context)) }

    // ── Change-secret-code dialog state ───────────────────────────────────────
    var showCodeDialog by remember { mutableStateOf(false) }
    var newCode        by remember { mutableStateOf("") }
    var confirmCode    by remember { mutableStateOf("") }
    var codeError      by remember { mutableStateOf<String?>(null) }
    var codeSuccess    by remember { mutableStateOf(false) }

    // ── Erase-all dialog state ────────────────────────────────────────────────
    var showEraseDialog by remember { mutableStateOf(false) }

    // ─────────────────────────────────────────────────────────────────────────
    // Change Secret Code Dialog
    // ─────────────────────────────────────────────────────────────────────────
    if (showCodeDialog) {
        AlertDialog(
            onDismissRequest = {
                showCodeDialog = false
                newCode = ""; confirmCode = ""; codeError = null; codeSuccess = false
            },
            title = { Text(stringResource(R.string.change_secret_code), color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(R.string.change_code_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )

                    OutlinedTextField(
                        value = newCode,
                        onValueChange = {
                            if (it.all(Char::isDigit) && it.length <= 8) newCode = it
                        },
                        label = { Text(stringResource(R.string.new_code_label)) },
                        placeholder = { Text(stringResource(R.string.new_code_placeholder)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = AccentCyan,
                            unfocusedBorderColor = TextMuted,
                            focusedLabelColor    = AccentCyan,
                            cursorColor          = AccentCyan,
                            focusedTextColor     = TextPrimary,
                            unfocusedTextColor   = TextPrimary
                        )
                    )

                    OutlinedTextField(
                        value = confirmCode,
                        onValueChange = {
                            if (it.all(Char::isDigit) && it.length <= 8) confirmCode = it
                        },
                        label = { Text(stringResource(R.string.confirm_code_label)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = AccentCyan,
                            unfocusedBorderColor = TextMuted,
                            focusedLabelColor    = AccentCyan,
                            cursorColor          = AccentCyan,
                            focusedTextColor     = TextPrimary,
                            unfocusedTextColor   = TextPrimary
                        )
                    )

                    // Error message
                    codeError?.let {
                        Text(it,
                            color = Color(0xFFFF6B6B),
                            style = MaterialTheme.typography.bodySmall)
                    }

                    // Success message
                    if (codeSuccess) {
                        Text(stringResource(R.string.code_updated_success),
                            color = Color(0xFF4ADE80),
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    codeError   = null
                    codeSuccess = false
                    when {
                        newCode.length < 4 ->
                            codeError = context.getString(R.string.error_code_short)
                        newCode != confirmCode ->
                            codeError = context.getString(R.string.error_codes_mismatch)
                        else -> {
                            // Save new code — EntryScreen will pick it up automatically
                            PinManager.changeSecretCode(context, newCode)
                            codeSuccess = true
                            newCode     = ""
                            confirmCode = ""
                        }
                    }
                }) {
                    Text(stringResource(R.string.save), color = AccentCyan)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCodeDialog = false
                    newCode = ""; confirmCode = ""; codeError = null; codeSuccess = false
                }) {
                    Text(stringResource(R.string.cancel), color = TextMuted)
                }
            },
            containerColor = SurfaceCard
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Erase All Data Dialog
    // ─────────────────────────────────────────────────────────────────────────
    if (showEraseDialog) {
        AlertDialog(
            onDismissRequest = { showEraseDialog = false },
            title = { Text(stringResource(R.string.erase_data_title), color = Color(0xFFFF6B6B)) },
            text = {
                Text(
                    stringResource(R.string.erase_data_description),
                    color = TextMuted
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showEraseDialog = false
                    // TODO: call repository.deleteAll() and PinManager.clearPin()
                }) {
                    Text(stringResource(R.string.erase_everything), color = Color(0xFFFF6B6B))
                }
            },
            dismissButton = {
                TextButton(onClick = { showEraseDialog = false }) {
                    Text(stringResource(R.string.cancel), color = TextMuted)
                }
            },
            containerColor = SurfaceCard
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Main Scaffold
    // ─────────────────────────────────────────────────────────────────────────
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {

            // ── SECURITY ─────────────────────────────────────────────────────
            SectionHeader(stringResource(R.string.security_section))
            SettingsCard {
                // Change secret code
                SettingsRowArrow(
                    icon     = Icons.Rounded.Lock,
                    iconTint = Color(0xFF5A9E7A),
                    title    = stringResource(R.string.change_secret_code),
                    subtitle = stringResource(R.string.change_code_subtitle),
                    onClick  = {
                        newCode = ""; confirmCode = ""
                        codeError = null; codeSuccess = false
                        showCodeDialog = true
                    }
                )
                SettingsDivider()

                // Quick Exit Gesture
                SettingsRowToggle(
                    icon            = Icons.Rounded.Vibration,
                    iconTint        = Color(0xFF5A9E7A),
                    title           = stringResource(R.string.quick_exit_title),
                    subtitle        = stringResource(R.string.quick_exit_subtitle),
                    checked         = quickExit,
                    onCheckedChange = { quickExit = it }
                )
                SettingsDivider()

                // Auto-Lock
                SettingsRowToggle(
                    icon            = Icons.Rounded.Shield,
                    iconTint        = Color(0xFF5A9E7A),
                    title           = stringResource(R.string.auto_lock_title),
                    subtitle        = stringResource(R.string.auto_lock_subtitle),
                    checked         = autoLock,
                    onCheckedChange = { autoLock = it }
                )
            }

            // ── DATA ──────────────────────────────────────────────────────────
            SectionHeader(stringResource(R.string.data_section))
            SettingsCard {
                SettingsRowArrow(
                    icon     = Icons.Rounded.FileDownload,
                    iconTint = Color(0xFF5A9E7A),
                    title    = stringResource(R.string.export_data_title),
                    subtitle = stringResource(R.string.export_data_subtitle),
                    onClick  = { /* TODO: trigger PDF / encrypted export */ }
                )
                SettingsDivider()
                SettingsRowArrow(
                    icon       = Icons.Rounded.Delete,
                    iconTint   = Color(0xFFCC4444),
                    title      = stringResource(R.string.erase_everything),
                    subtitle   = stringResource(R.string.erase_data_subtitle),
                    titleColor = Color(0xFFCC4444),
                    onClick    = { showEraseDialog = true }
                )
            }

            // ── ABOUT ─────────────────────────────────────────────────────────
            SectionHeader(stringResource(R.string.about_section))
            SettingsCard {
                // App Disguise — fully wired to PackageManager via DisguiseManager
                SettingsRowToggle(
                    icon     = Icons.Rounded.Visibility,
                    iconTint = Color(0xFF5A9E7A),
                    title    = stringResource(R.string.app_disguise_title),
                    subtitle = if (disguise)
                        stringResource(R.string.app_disguise_on)
                    else
                        stringResource(R.string.app_disguise_off),
                    checked  = disguise,
                    onCheckedChange = { enabled ->
                        disguise = enabled
                        DisguiseManager.setDisguiseEnabled(context, enabled)
                    }
                )
            }

            // Version label
            Text(
                stringResource(R.string.app_version),
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Reusable sub-components
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = TextMuted,
        modifier = Modifier.padding(horizontal = 4.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(content = content)
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(color = PrimaryDark, thickness = 1.dp)
}

@Composable
private fun SettingsRowArrow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    titleColor: Color = TextPrimary,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            IconCircle(icon, iconTint)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, color = titleColor)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = TextMuted)
        }
    }
}

@Composable
private fun SettingsRowToggle(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        IconCircle(icon, iconTint)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor   = Color.White,
                checkedTrackColor   = Color(0xFF5A9E7A),
                uncheckedTrackColor = SurfaceCard
            )
        )
    }
}

@Composable
private fun IconCircle(icon: ImageVector, tint: Color) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = tint.copy(alpha = 0.15f),
        modifier = Modifier.size(38.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        }
    }
}
