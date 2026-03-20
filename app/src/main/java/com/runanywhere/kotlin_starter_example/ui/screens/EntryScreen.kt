package com.runanywhere.kotlin_starter_example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runanywhere.kotlin_starter_example.security.PinManager
import com.runanywhere.kotlin_starter_example.ui.theme.*

/**
 * Entry screen with three modes:
 * 1. PIN Setup (first launch)
 * 2. PIN Entry (normal launch)
 * 3. Decoy Calculator (after 2 wrong attempts)
 */
@Composable
fun EntryScreen(onPinCorrect: (String) -> Unit) {
    val context = LocalContext.current
    val isPinSet = remember { PinManager.isPinSet(context) }
    
    var showDecoy by remember { mutableStateOf(false) }
    
    if (showDecoy) {
        DecoyCalculatorScreen()
    } else if (!isPinSet) {
        PinSetupScreen(onPinCorrect = onPinCorrect)
    } else {
        PinEntryScreen(
            onPinCorrect = onPinCorrect,
            onTooManyWrongAttempts = { showDecoy = true }
        )
    }
}

/**
 * PIN Setup screen - shown on first launch.
 */
@Composable
private fun PinSetupScreen(onPinCorrect: (String) -> Unit) {
    val context = LocalContext.current
    
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var step by remember { mutableStateOf(1) } // 1 = enter PIN, 2 = confirm PIN
    var errorMessage by remember { mutableStateOf("") }

    Box(
        modifier = Modifier.fillMaxSize().background(PrimaryDark),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.padding(40.dp)
        ) {
            Spacer(Modifier.height(40.dp))

            Text("🔒", fontSize = 52.sp)

            Text(
                text = "SafeNotes",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary
            )

            if (step == 1) {
                Text(
                    text = "Create a 4-digit PIN",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
                
                Spacer(Modifier.height(8.dp))
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2A1010))
                ) {
                    Text(
                        text = "⚠️ WARNING: If you forget this PIN, your data cannot be recovered. Write it down in a safe place.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFFF6B6B),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Text(
                    text = "Confirm your PIN",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
            }

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = if (step == 1) pin else confirmPin,
                onValueChange = {
                    if (it.length <= 4) {
                        if (step == 1) pin = it else confirmPin = it
                        errorMessage = ""
                    }
                },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true,
                isError = errorMessage.isNotEmpty(),
                textStyle = LocalTextStyle.current.copy(
                    textAlign = TextAlign.Center,
                    fontSize = 28.sp,
                    color = TextPrimary,
                    letterSpacing = 12.sp
                ),
                modifier = Modifier.width(180.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentCyan,
                    unfocusedBorderColor = TextMuted,
                    errorBorderColor = Color(0xFFEF4444)
                )
            )

            if (errorMessage.isNotEmpty()) {
                Text(
                    text = errorMessage,
                    color = Color(0xFFEF4444),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
            }

            Button(
                onClick = {
                    if (step == 1) {
                        if (pin.length == 4) {
                            step = 2
                        } else {
                            errorMessage = "PIN must be 4 digits"
                        }
                    } else {
                        if (confirmPin == pin) {
                            PinManager.setupPin(context, pin)
                            val encryptionKey = PinManager.deriveEncryptionKey(context, pin)
                            onPinCorrect(encryptionKey)
                        } else {
                            errorMessage = "PINs don't match"
                            confirmPin = ""
                        }
                    }
                },
                enabled = (step == 1 && pin.length == 4) || (step == 2 && confirmPin.length == 4),
                colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(if (step == 1) "Continue" else "Create PIN", color = Color.White, fontSize = 16.sp)
            }
            
            if (step == 2) {
                TextButton(onClick = { step = 1; confirmPin = ""; errorMessage = "" }) {
                    Text("Back", color = TextMuted)
                }
            }
        }
    }
}

/**
 * PIN Entry screen - shown on subsequent launches.
 */
@Composable
private fun PinEntryScreen(
    onPinCorrect: (String) -> Unit,
    onTooManyWrongAttempts: () -> Unit
) {
    val context = LocalContext.current
    
    var pin by remember { mutableStateOf("") }
    var wrongAttempts by remember { mutableStateOf(0) }
    var shakeError by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxSize().background(PrimaryDark),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.padding(40.dp)
        ) {
            Spacer(Modifier.height(40.dp))

            Text("🔒", fontSize = 52.sp)

            Text(
                text = "SafeNotes",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary
            )

            Text(
                text = "Enter your PIN",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = pin,
                onValueChange = {
                    if (it.length <= 4) {
                        pin = it
                        shakeError = false
                    }
                },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true,
                isError = shakeError,
                textStyle = LocalTextStyle.current.copy(
                    textAlign = TextAlign.Center,
                    fontSize = 28.sp,
                    color = TextPrimary,
                    letterSpacing = 12.sp
                ),
                modifier = Modifier.width(180.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentCyan,
                    unfocusedBorderColor = TextMuted,
                    errorBorderColor = Color(0xFFEF4444)
                )
            )

            if (shakeError) {
                Text(
                    text = "Incorrect PIN",
                    color = Color(0xFFEF4444),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Button(
                onClick = {
                    if (PinManager.verifyPin(context, pin)) {
                        val encryptionKey = PinManager.deriveEncryptionKey(context, pin)
                        onPinCorrect(encryptionKey)
                    } else {
                        wrongAttempts++
                        shakeError = true
                        pin = ""
                        if (wrongAttempts >= 2) {
                            onTooManyWrongAttempts()
                        }
                    }
                },
                enabled = pin.length == 4,
                colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Unlock", color = Color.White, fontSize = 16.sp)
            }
        }
    }
}

/**
 * Decoy Calculator screen - looks like a standard iOS calculator.
 * Shown after 2 wrong PIN attempts to protect against coercion.
 */
@Composable
private fun DecoyCalculatorScreen() {
    val display = remember { mutableStateOf("0") }

    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF1C1C1E)),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            // Display
            Text(
                text = display.value,
                fontSize = 72.sp,
                color = Color.White,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
                maxLines = 1
            )

            val rows = listOf(
                listOf("C" to Color(0xFFA5A5A5), "±" to Color(0xFFA5A5A5), "%" to Color(0xFFA5A5A5), "÷" to Color(0xFFFF9F0A)),
                listOf("7" to Color(0xFF333333), "8" to Color(0xFF333333), "9" to Color(0xFF333333), "×" to Color(0xFFFF9F0A)),
                listOf("4" to Color(0xFF333333), "5" to Color(0xFF333333), "6" to Color(0xFF333333), "−" to Color(0xFFFF9F0A)),
                listOf("1" to Color(0xFF333333), "2" to Color(0xFF333333), "3" to Color(0xFF333333), "+" to Color(0xFFFF9F0A))
            )

            rows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    row.forEach { (label, color) ->
                        Button(
                            onClick = { display.value = if (label == "C") "0" else label },
                            modifier = Modifier.weight(1f).height(80.dp),
                            shape = RoundedCornerShape(40.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = color)
                        ) { Text(label, fontSize = 28.sp, color = Color.White) }
                    }
                }
            }

            // Bottom row: 0 (wide), ., =
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { display.value = "0" },
                    modifier = Modifier.weight(2f).height(80.dp),
                    shape = RoundedCornerShape(40.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333))
                ) { Text("0", fontSize = 28.sp, color = Color.White) }
                Button(
                    onClick = {},
                    modifier = Modifier.weight(1f).height(80.dp),
                    shape = RoundedCornerShape(40.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333))
                ) { Text(".", fontSize = 28.sp, color = Color.White) }
                Button(
                    onClick = {},
                    modifier = Modifier.weight(1f).height(80.dp),
                    shape = RoundedCornerShape(40.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9F0A))
                ) { Text("=", fontSize = 28.sp, color = Color.White) }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
