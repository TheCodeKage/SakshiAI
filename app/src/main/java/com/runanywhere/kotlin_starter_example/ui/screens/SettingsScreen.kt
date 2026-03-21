package com.runanywhere.kotlin_starter_example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmNewPin by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf<Pair<String, Boolean>?>(null) } // message, isSuccess
    var isChanging by remember { mutableStateOf(false) }

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
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.change_pin),
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = stringResource(R.string.change_pin_description),
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            OutlinedTextField(
                value = currentPin,
                onValueChange = {
                    if (it.length <= 4 && it.all { c -> c.isDigit() }) {
                        currentPin = it
                        statusMessage = null
                    }
                },
                label = { Text(stringResource(R.string.current_pin), color = TextMuted) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = AccentCyan,
                    unfocusedBorderColor = Color(0xFF48484A)
                ),
                modifier = Modifier.fillMaxWidth(),
                enabled = !isChanging
            )

            OutlinedTextField(
                value = newPin,
                onValueChange = {
                    if (it.length <= 4 && it.all { c -> c.isDigit() }) {
                        newPin = it
                        statusMessage = null
                    }
                },
                label = { Text(stringResource(R.string.new_pin), color = TextMuted) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = AccentCyan,
                    unfocusedBorderColor = Color(0xFF48484A)
                ),
                modifier = Modifier.fillMaxWidth(),
                enabled = !isChanging
            )

            OutlinedTextField(
                value = confirmNewPin,
                onValueChange = {
                    if (it.length <= 4 && it.all { c -> c.isDigit() }) {
                        confirmNewPin = it
                        statusMessage = null
                    }
                },
                label = { Text(stringResource(R.string.confirm_new_pin), color = TextMuted) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = AccentCyan,
                    unfocusedBorderColor = Color(0xFF48484A)
                ),
                modifier = Modifier.fillMaxWidth(),
                enabled = !isChanging
            )

            if (statusMessage != null) {
                val (message, isSuccess) = statusMessage!!
                Text(
                    text = message,
                    color = if (isSuccess) Color(0xFF30D158) else Color(0xFFFF453A),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
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
                            val success = PinManager.changePin(context, currentPin, newPin)
                            if (success) {
                                statusMessage = context.getString(R.string.pin_changed_success) to true
                                currentPin = ""
                                newPin = ""
                                confirmNewPin = ""
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
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                } else {
                    Text(stringResource(R.string.change_pin), color = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.pin_warning_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFFFF9F0A),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = stringResource(R.string.pin_warning_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                }
            }
        }
    }
}