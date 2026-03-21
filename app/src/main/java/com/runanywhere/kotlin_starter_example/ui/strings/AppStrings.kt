package com.runanywhere.kotlin_starter_example.ui.strings

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.runanywhere.kotlin_starter_example.R

/**
 * Provides access to localized strings in Compose.
 * Automatically uses the system language setting (English or Hindi).
 */
object AppStrings {
    @Composable
    fun getString(id: Int): String {
        return LocalContext.current.getString(id)
    }
    
    fun getString(context: Context, id: Int): String {
        return context.getString(id)
    }
}

/**
 * Composable function to get localized strings easily.
 * Usage: val setupPinTitle = stringResource(R.string.setup_pin_title)
 */
@Composable
fun stringResource(id: Int): String {
    return LocalContext.current.getString(id)
}
