package com.runanywhere.kotlin_starter_example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runanywhere.kotlin_starter_example.security.PinManager

// Secret code: type these numbers then tap = to unlock
// Change before demo. Currently: 1234=
private const val SECRET_CODE = "1234"

@Composable
fun EntryScreen(onPinCorrect: (String) -> Unit) {
    val context = LocalContext.current

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
        // Keep only last N digits where N = secret code length
        if (inputSequence.length > SECRET_CODE.length) {
            inputSequence = inputSequence.takeLast(SECRET_CODE.length)
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
        // Check secret code FIRST
        if (inputSequence == SECRET_CODE) {
            val isFirstTime = !PinManager.isPinSet(context)
            if (isFirstTime) PinManager.setupPin(context, SECRET_CODE)
            val key = PinManager.deriveEncryptionKey(context, SECRET_CODE)
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