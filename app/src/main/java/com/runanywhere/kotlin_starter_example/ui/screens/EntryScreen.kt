package com.runanywhere.kotlin_starter_example.ui.screens

import android.content.ComponentName
import android.content.pm.PackageManager
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
import androidx.compose.ui.window.Dialog
import com.runanywhere.kotlin_starter_example.R
import com.runanywhere.kotlin_starter_example.security.PinManager
import com.runanywhere.kotlin_starter_example.ui.strings.stringResource

@Composable
fun EntryScreen(onPinCorrect: (String) -> Unit) {
    val context = LocalContext.current

    // Check if Disguise is active via the Launcher Alias status
    val isDisguiseActive = remember {
        val pkg = context.packageName
        val calculatorAlias = ComponentName(pkg, "$pkg.LauncherCalculator")
        context.packageManager.getComponentEnabledSetting(calculatorAlias) ==
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    }

    val isPinAlreadySet = remember { PinManager.isPinSet(context) }
    var showPinSetup by remember { mutableStateOf(!isPinAlreadySet) }

    // Standard PIN login state (for when disguise is OFF)
    var loginPinInput by remember { mutableStateOf("") }
    var loginError by remember { mutableStateOf(false) }

    // Calculator state (for when disguise is ON)
    var display by remember { mutableStateOf("0") }
    var inputSequence by remember { mutableStateOf("") }
    var previousValue by remember { mutableStateOf("") }
    var operator by remember { mutableStateOf("") }
    var shouldResetDisplay by remember { mutableStateOf(false) }

    // --- Core Logic Functions ---
    fun attemptLogin(pin: String) {
        if (PinManager.verifyPin(context, pin)) {
            val key = PinManager.deriveEncryptionKey(context, pin)
            onPinCorrect(key)
        } else {
            loginError = true
        }
    }

    fun onNumber(num: String) {
        inputSequence += num
        if (shouldResetDisplay) { display = num; shouldResetDisplay = false }
        else { display = if (display == "0") num else display + num }
        if (inputSequence.length > 4) inputSequence = inputSequence.takeLast(4)
    }

    fun onEquals() {
        if (PinManager.verifyPin(context, inputSequence)) {
            val key = PinManager.deriveEncryptionKey(context, inputSequence)
            onPinCorrect(key)
        } else {
            // Preserved math functionality if PIN doesn't match
            val prev = previousValue.toDoubleOrNull()
            val curr = display.toDoubleOrNull()
            if (prev != null && curr != null && operator.isNotEmpty()) {
                val result = when (operator) {
                    "+" -> prev + curr; "−" -> prev - curr; "×" -> prev * curr
                    "÷" -> if (curr != 0.0) prev / curr else 0.0; else -> curr
                }
                display = if (result % 1.0 == 0.0) result.toLong().toString() else result.toString()
            }
            inputSequence = ""; shouldResetDisplay = true
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF1C1C1E)),
        contentAlignment = Alignment.Center
    ) {
        if (!showPinSetup) {
            if (isDisguiseActive) {
                // MODE: DISGUISE ON - Calculator Verification
                Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.Bottom) {
                    Text(text = display, fontSize = 72.sp, color = Color.White, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth().padding(16.dp))

                    val rows = listOf(
                        listOf("C", "±", "%", "÷"), listOf("7", "8", "9", "×"),
                        listOf("4", "5", "6", "−"), listOf("1", "2", "3", "+")
                    )
                    rows.forEach { row ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { label ->
                                Button(
                                    onClick = { if (label == "C") display = "0" else if ("÷×−+".contains(label)) { previousValue = display; operator = label; shouldResetDisplay = true; inputSequence = "" } else onNumber(label) },
                                    modifier = Modifier.weight(1f).height(80.dp),
                                    shape = RoundedCornerShape(40.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = if ("÷×−+".contains(label)) Color(0xFFFF9F0A) else Color(0xFF333333))
                                ) { Text(label, fontSize = 28.sp, color = Color.White) }
                            }
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { onNumber("0") }, modifier = Modifier.weight(2f).height(80.dp), shape = RoundedCornerShape(40.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333))) { Text("0", fontSize = 28.sp, color = Color.White) }
                        Button(onClick = { onEquals() }, modifier = Modifier.weight(1f).height(80.dp), shape = RoundedCornerShape(40.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9F0A))) { Text("=", fontSize = 28.sp, color = Color.White) }
                    }
                }
            } else {
                // MODE: DISGUISE OFF - Simple PIN Entry (Matches your Image)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    OutlinedTextField(
                        value = loginPinInput,
                        onValueChange = {
                            if (it.length <= 4 && it.all { c -> c.isDigit() }) {
                                loginPinInput = it
                                if (it.length == 4) attemptLogin(it)
                            }
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF0A84FF),
                            unfocusedBorderColor = Color(0xFF48484A)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.width(180.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("enter password", color = Color.White, fontSize = 16.sp)
                    if (loginError) {
                        Text("Incorrect PIN", color = Color(0xFFFF453A), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        }

        // --- PIN SETUP DIALOG (Only on very first run) ---
        if (showPinSetup) {
            Dialog(onDismissRequest = { }) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2C2E))
                ) {
                    Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Setup Your PIN", fontSize = 24.sp, color = Color.White)
                        Spacer(Modifier.height(24.dp))

                        var setupPinLocal by remember { mutableStateOf("") }
                        var confirmPinLocal by remember { mutableStateOf("") }

                        OutlinedTextField(
                            value = setupPinLocal,
                            onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) setupPinLocal = it },
                            label = { Text("Enter PIN") },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = confirmPinLocal,
                            onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) confirmPinLocal = it },
                            label = { Text("Confirm PIN") },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(24.dp))
                        Button(
                            onClick = {
                                if (setupPinLocal == confirmPinLocal && setupPinLocal.length == 4) {
                                    PinManager.setupPin(context, setupPinLocal)
                                    (context as? android.app.Activity)?.recreate()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Set PIN") }
                    }
                }
            }
        }
    }
}