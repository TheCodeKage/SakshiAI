package com.runanywhere.kotlin_starter_example.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import javax.crypto.spec.SecretKeySpec

/**
 * Manages PIN-based authentication and encryption key derivation.
 * 
 * Security features:
 * - PIN stored as PBKDF2 hash in EncryptedSharedPreferences
 * - Database encryption key derived from PIN using PBKDF2
 * - No plaintext PIN storage
 * - Unrecoverable by design if PIN is forgotten
 */
object PinManager {
    
    private const val PREFS_NAME = "saakshi_secure_prefs"
    private const val KEY_PIN_HASH = "pin_hash"
    private const val KEY_SALT = "pin_salt"
    private const val PBKDF2_ITERATIONS = 10000
    
    /**
     * Check if a PIN has been set up
     */
    fun isPinSet(context: Context): Boolean {
        val prefs = getEncryptedPrefs(context)
        return prefs.contains(KEY_PIN_HASH)
    }
    
    /**
     * Set up a new PIN (first-time setup)
     * 
     * @param pin The 4-digit PIN to set
     * @return true if setup successful
     */
    fun setupPin(context: Context, pin: String): Boolean {
        if (pin.length != 4 || !pin.all { it.isDigit() }) {
            return false
        }
        
        val prefs = getEncryptedPrefs(context)
        
        // Check if salt already exists (e.g., during PIN change)
        val existingSalt = prefs.getString(KEY_SALT, null)
        val salt = existingSalt ?: generateRandomSalt()
        
        // Hash the PIN with the salt
        val pinHash = hashPin(pin, salt)
        
        // Store both hash and salt
        prefs.edit()
            .putString(KEY_PIN_HASH, pinHash)
            .putString(KEY_SALT, salt)
            .apply()
        
        return true
    }
    
    /**
     * Verify a PIN against the stored hash
     * 
     * @param pin The PIN to verify
     * @return true if PIN matches
     */
    fun verifyPin(context: Context, pin: String): Boolean {
        val prefs = getEncryptedPrefs(context)
        val storedHash = prefs.getString(KEY_PIN_HASH, null) ?: return false
        val salt = prefs.getString(KEY_SALT, null) ?: return false
        
        val inputHash = hashPin(pin, salt)
        return inputHash == storedHash
    }
    
    /**
     * Derive database encryption passphrase from PIN
     * 
     * This generates a strong encryption key from the user's PIN.
     * The same PIN will always produce the same key.
     * 
     * @param pin The user's PIN
     * @return Encryption passphrase for SQLCipher
     */
    fun deriveEncryptionKey(context: Context, pin: String): String {
        val prefs = getEncryptedPrefs(context)
        val salt = prefs.getString(KEY_SALT, null) ?: generateRandomSalt()
        
        // Use PBKDF2 to derive a strong key from the PIN
        val spec = javax.crypto.spec.PBEKeySpec(
            pin.toCharArray(),
            salt.toByteArray(),
            PBKDF2_ITERATIONS,
            256
        )
        
        val factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val key = factory.generateSecret(spec).encoded
        
        // Convert to hex string for SQLCipher
        return key.joinToString("") { "%02x".format(it) }
    }
    
    /**
     * Change existing PIN to a new one
     * 
     * IMPORTANT: This reuses the existing salt so the database encryption key remains unchanged.
     * Only the PIN hash is updated, allowing the user to change their PIN without losing access
     * to their encrypted database.
     * 
     * @param oldPin The current PIN for verification
     * @param newPin The new 4-digit PIN to set
     * @return true if change successful, false if old PIN is incorrect or new PIN is invalid
     */
    fun changePin(context: Context, oldPin: String, newPin: String): Boolean {
        // Verify old PIN first
        if (!verifyPin(context, oldPin)) {
            return false
        }
        
        // Validate new PIN
        if (newPin.length != 4 || !newPin.all { it.isDigit() }) {
            return false
        }
        
        // Set up the new PIN (reuses existing salt for database compatibility)
        return setupPin(context, newPin)
    }
    
    /**
     * Clear all PIN data (for testing/reset - use with caution!)
     * WARNING: This will make the encrypted database unreadable!
     */
    fun clearPin(context: Context) {
        val prefs = getEncryptedPrefs(context)
        prefs.edit().clear().apply()
    }
    
    // Private helper methods
    
    private fun getEncryptedPrefs(context: Context): android.content.SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        
        return EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    
    private fun hashPin(pin: String, salt: String): String {
        val spec = javax.crypto.spec.PBEKeySpec(
            pin.toCharArray(),
            salt.toByteArray(),
            PBKDF2_ITERATIONS,
            256
        )
        
        val factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hash = factory.generateSecret(spec).encoded
        
        return hash.joinToString("") { "%02x".format(it) }
    }
    
    private fun generateRandomSalt(): String {
        val random = java.security.SecureRandom()
        val salt = ByteArray(32)
        random.nextBytes(salt)
        return salt.joinToString("") { "%02x".format(it) }
    }
}
