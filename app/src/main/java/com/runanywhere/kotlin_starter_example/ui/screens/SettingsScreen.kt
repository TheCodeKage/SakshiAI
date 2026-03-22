package com.runanywhere.kotlin_starter_example.ui.screens

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.runanywhere.kotlin_starter_example.R
import com.runanywhere.kotlin_starter_example.security.PinManager
import com.runanywhere.kotlin_starter_example.ui.strings.stringResource
import com.runanywhere.kotlin_starter_example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val pkg = context.packageName
    val calculatorAlias = ComponentName(pkg, "$pkg.LauncherCalculator")

    // Access SharedPreferences to save/load settings
    val prefs = remember { context.getSharedPreferences("prefs", Context.MODE_PRIVATE) }

    // --- STATE MANAGEMENT ---

    // Change PIN States
    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmNewPin by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var isChanging by remember { mutableStateOf(false) }

    // Feature Toggles: Loaded from system/prefs to ensure persistence
    var isDisguised by remember {
        mutableStateOf(
            context.packageManager.getComponentEnabledSetting(calculatorAlias) ==
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        )
    }

    // Load Quick Exit and Auto Lock from SharedPreferences
    var quickExitEnabled by remember {
        mutableStateOf(prefs.getBoolean("quick_exit_enabled", false))
    }
    var autoLockEnabled by remember {
        mutableStateOf(prefs.getBoolean("auto_lock_enabled", true))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back), tint = TextPrimary)
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
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- SECURITY FEATURES SECTION ---
            Text(
                text = "Security Features",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )

            SettingsToggleCard(
                title = "App Disguise",
                description = "Hide Sakshi behind a calculator icon and name.",
                isChecked = isDisguised,
                onCheckedChange = { checked ->
                    isDisguised = checked
                    toggleAppIcon(context, checked)
                }
            )

            SettingsToggleCard(
                title = "Quick Exit Gesture",
                description = "Instantly lock the app by shaking your phone.",
                isChecked = quickExitEnabled,
                onCheckedChange = { checked ->
                    quickExitEnabled = checked
                    prefs.edit().putBoolean("quick_exit_enabled", checked).apply()
                }
            )

            SettingsToggleCard(
                title = "Auto Lock",
                description = "Lock the app immediately when minimized.",
                isChecked = autoLockEnabled,
                onCheckedChange = { checked ->
                    autoLockEnabled = checked
                    prefs.edit().putBoolean("auto_lock_enabled", checked).apply()
                }
            )

            HorizontalDivider(color = Color(0xFF48484A), thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))

            // --- CHANGE PIN SECTION ---
            Text(
                text = stringResource(R.string.change_pin),
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )

            Text(
                text = stringResource(R.string.change_pin_description),
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )

            OutlinedTextField(
                value = currentPin,
                onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) { currentPin = it; statusMessage = null } },
                label = { Text(stringResource(R.string.current_pin), color = TextMuted) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, focusedBorderColor = AccentCyan, unfocusedBorderColor = Color(0xFF48484A)),
                modifier = Modifier.fillMaxWidth(),
                enabled = !isChanging
            )

            OutlinedTextField(
                value = newPin,
                onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) { newPin = it; statusMessage = null } },
                label = { Text(stringResource(R.string.new_pin), color = TextMuted) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, focusedBorderColor = AccentCyan, unfocusedBorderColor = Color(0xFF48484A)),
                modifier = Modifier.fillMaxWidth(),
                enabled = !isChanging
            )

            OutlinedTextField(
                value = confirmNewPin,
                onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) { confirmNewPin = it; statusMessage = null } },
                label = { Text(stringResource(R.string.confirm_new_pin), color = TextMuted) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, focusedBorderColor = AccentCyan, unfocusedBorderColor = Color(0xFF48484A)),
                modifier = Modifier.fillMaxWidth(),
                enabled = !isChanging
            )

            if (statusMessage != null) {
                val (message, isSuccess) = statusMessage!!
                Text(text = message, color = if (isSuccess) Color(0xFF30D158) else Color(0xFFFF453A), style = MaterialTheme.typography.bodyMedium)
            }

            Button(
                onClick = {
                    when {
                        currentPin.length != 4 -> statusMessage = context.getString(R.string.pin_error_length) to false
                        newPin.length != 4 -> statusMessage = context.getString(R.string.pin_error_length) to false
                        newPin != confirmNewPin -> statusMessage = context.getString(R.string.pin_error_mismatch) to false
                        currentPin == newPin -> statusMessage = context.getString(R.string.pin_error_same) to false
                        else -> {
                            isChanging = true
                            if (PinManager.changePin(context, currentPin, newPin)) {
                                statusMessage = context.getString(R.string.pin_changed_success) to true
                                currentPin = ""; newPin = ""; confirmNewPin = ""
                            } else {
                                statusMessage = context.getString(R.string.pin_error_incorrect) to false
                            }
                            isChanging = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                enabled = !isChanging && currentPin.length == 4 && newPin.length == 4 && confirmNewPin.length == 4
            ) {
                if (isChanging) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Text(stringResource(R.string.change_pin), color = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = stringResource(R.string.pin_warning_title), style = MaterialTheme.typography.titleMedium, color = Color(0xFFFF9F0A), modifier = Modifier.padding(bottom = 8.dp))
                    Text(text = stringResource(R.string.pin_warning_message), style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                }
            }
        }
    }
}

@Composable
fun SettingsToggleCard(title: String, description: String, isChecked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                Text(description, color = TextMuted, style = MaterialTheme.typography.bodySmall)
            }
            Switch(
                checked = isChecked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(checkedThumbColor = AccentCyan, checkedTrackColor = AccentCyan.copy(alpha = 0.5f))
            )
        }
    }
}

fun toggleAppIcon(context: Context, useCalculator: Boolean) {
    val pm = context.packageManager
    val pkg = context.packageName

    val defaultAlias = ComponentName(pkg, "$pkg.LauncherDefault")
    val calculatorAlias = ComponentName(pkg, "$pkg.LauncherCalculator")

    pm.setComponentEnabledSetting(
        if (useCalculator) calculatorAlias else defaultAlias,
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
        PackageManager.DONT_KILL_APP
    )

    pm.setComponentEnabledSetting(
        if (useCalculator) defaultAlias else calculatorAlias,
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
        PackageManager.DONT_KILL_APP
    )
}