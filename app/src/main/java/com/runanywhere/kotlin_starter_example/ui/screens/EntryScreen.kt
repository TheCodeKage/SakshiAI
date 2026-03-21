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
import androidx.compose.ui.window.Dialog
import com.runanywhere.kotlin_starter_example.R
import com.runanywhere.kotlin_starter_example.security.PinManager
import com.runanywhere.kotlin_starter_example.ui.strings.stringResource

@Composable
fun EntryScreen(onPinCorrect: (String) -> Unit) {
    val context = LocalContext.current
    var showPinSetup by remember { mutableStateOf(!PinManager.isPinSet(context)) }
    var setupPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var setupError by remember { mutableStateOf<String?>(null) }

    var display by remember { mutableStateOf("0") }
    var inputSequence by remember { mutableStateOf("") } // tracks digits entered
    var previousValue by remember { mutableStateOf("") }
    var operator by remember { mutableStateOf("") }
    var shouldResetDisplay by remember { mutableStateOf(false) }

    fun onNumber(num: String) {
        inputSequence += num
        if (shouldResetDisplay) {
            display = num
            shouldResetDisplay = false
        } else {
            display = if (display == "0") num else display + num
        }
        // Keep only last 4 digits for PIN verification
        if (inputSequence.length > 4) {
            inputSequence = inputSequence.takeLast(4)
        }
    }

    fun onOperator(op: String) {
        inputSequence = "" // reset sequence on operator
        previousValue = display
        operator = op
        shouldResetDisplay = true
        display = display // show current value, operation shown below
    }

    fun onEquals() {
        // Check if PIN matches
        if (PinManager.isPinSet(context) && PinManager.verifyPin(context, inputSequence)) {
            val key = PinManager.deriveEncryptionKey(context, inputSequence)
            
            // Verify the database can be opened with this key before navigating
            try {
                val testRepo = com.runanywhere.kotlin_starter_example.data.IncidentRepository(context, key)
                testRepo.getIncidentCount() // Try to access database
            } catch (e: Exception) {
                // Database exists but can't be opened with this key - it's corrupted
                // Delete it and start fresh
                com.runanywhere.kotlin_starter_example.data.IncidentRepository.deleteDatabase(context)
            }
            
            onPinCorrect(key)
            return
        }

        // Normal calculator equals
        val prev = previousValue.toDoubleOrNull()
        val curr = display.toDoubleOrNull()
        if (prev != null && curr != null && operator.isNotEmpty()) {
            val result = when (operator) {
                "÷" -> if (curr != 0.0) prev / curr else 0.0
                "×" -> prev * curr
                "−" -> prev - curr
                "+" -> prev + curr
                else -> curr
            }
            display = if (result % 1.0 == 0.0) result.toLong().toString()
            else "%.6f".format(result).trimEnd('0').trimEnd('.')
            operator = ""
            previousValue = ""
        }
        inputSequence = ""
        shouldResetDisplay = true
    }

    fun onClear() {
        display = "0"
        previousValue = ""
        operator = ""
        shouldResetDisplay = false
        inputSequence = ""
    }

    fun onPercent() {
        val value = display.toDoubleOrNull() ?: return
        display = (value / 100).let {
            if (it % 1.0 == 0.0) it.toLong().toString() else it.toString()
        }
        inputSequence = ""
    }

    fun onPlusMinus() {
        val value = display.toDoubleOrNull() ?: return
        display = (-value).let {
            if (it % 1.0 == 0.0) it.toLong().toString() else it.toString()
        }
        inputSequence = ""
    }

    fun onDecimal() {
        inputSequence = "" // decimal breaks secret code sequence
        if (shouldResetDisplay) {
            display = "0."
            shouldResetDisplay = false
        } else if (!display.contains(".")) {
            display += "."
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF1C1C1E)),
        contentAlignment = Alignment.BottomCenter
    ) {
        // PIN Setup Dialog on first launch
        if (showPinSetup) {
            Dialog(onDismissRequest = { /* Cannot dismiss - required setup */ }) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2C2E))
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.setup_pin_title),
                            fontSize = 24.sp,
                            color = Color.White,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        
                        Text(
                            text = stringResource(R.string.setup_pin_description),
                            fontSize = 14.sp,
                            color = Color(0xFF8E8E93),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 24.dp)
                        )

                        OutlinedTextField(
                            value = setupPin,
                            onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) setupPin = it },
                            label = { Text(stringResource(R.string.enter_pin), color = Color(0xFF8E8E93)) },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF0A84FF),
                                unfocusedBorderColor = Color(0xFF48484A)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        )

                        OutlinedTextField(
                            value = confirmPin,
                            onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) confirmPin = it },
                            label = { Text(stringResource(R.string.confirm_pin), color = Color(0xFF8E8E93)) },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF0A84FF),
                                unfocusedBorderColor = Color(0xFF48484A)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        )

                        if (setupError != null) {
                            Text(
                                text = setupError!!,
                                color = Color(0xFFFF453A),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                        }

                        Button(
                            onClick = {
                                when {
                                    setupPin.length != 4 -> setupError = context.getString(R.string.pin_error_length)
                                    setupPin != confirmPin -> setupError = context.getString(R.string.pin_error_mismatch)
                                    else -> {
                                        if (PinManager.setupPin(context, setupPin)) {
                                            showPinSetup = false
                                            setupError = null
                                        } else {
                                            setupError = context.getString(R.string.pin_error_setup)
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A84FF)),
                            enabled = setupPin.length == 4 && confirmPin.length == 4
                        ) {
                            Text(stringResource(R.string.set_pin), color = Color.White, fontSize = 16.sp)
                        }
                    }
                }
            }
        }

        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {

            // Operation indicator (small, above main display)
            Text(
                text = if (previousValue.isNotEmpty() && operator.isNotEmpty())
                    "$previousValue $operator" else "",
                fontSize = 24.sp,
                color = Color(0xFF8E8E93),
                textAlign = TextAlign.End,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 0.dp),
                maxLines = 1
            )

            // Main display
            Text(
                text = display.take(12),
                fontSize = if (display.length > 8) 52.sp else 72.sp,
                color = Color.White,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                maxLines = 1
            )

            Spacer(Modifier.height(8.dp))

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
                        // Highlight active operator button
                        val isActiveOp = label == operator
                        Button(
                            onClick = {
                                when (label) {
                                    "C" -> onClear()
                                    "±" -> onPlusMinus()
                                    "%" -> onPercent()
                                    "÷", "×", "−", "+" -> onOperator(label)
                                    else -> onNumber(label)
                                }
                            },
                            modifier = Modifier.weight(1f).height(80.dp),
                            shape = RoundedCornerShape(40.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isActiveOp) Color.White else color
                            )
                        ) {
                            Text(
                                label,
                                fontSize = 28.sp,
                                color = if (isActiveOp) Color(0xFFFF9F0A) else Color.White
                            )
                        }
                    }
                }
            }

            // Bottom row
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { onNumber("0") },
                    modifier = Modifier.weight(2f).height(80.dp),
                    shape = RoundedCornerShape(40.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333))
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "0",
                            fontSize = 28.sp,
                            color = Color.White,
                            modifier = Modifier.align(Alignment.CenterStart).padding(start = 24.dp)
                        )
                    }
                }
                Button(
                    onClick = { onDecimal() },
                    modifier = Modifier.weight(1f).height(80.dp),
                    shape = RoundedCornerShape(40.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333))
                ) { Text(".", fontSize = 28.sp, color = Color.White) }
                Button(
                    onClick = { onEquals() },
                    modifier = Modifier.weight(1f).height(80.dp),
                    shape = RoundedCornerShape(40.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9F0A))
                ) { Text("=", fontSize = 28.sp, color = Color.White) }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}