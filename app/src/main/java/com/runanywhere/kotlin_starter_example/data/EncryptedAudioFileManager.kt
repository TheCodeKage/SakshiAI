package com.runanywhere.kotlin_starter_example.data

import android.content.Context
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Manages military-grade encryption for voice evidence.
 * Uses Android KeyStore + AES-256 GCM.
 * Files created here can ONLY be opened by this app on this specific device.
 */
object EncryptedAudioFileManager {

    fun saveEncryptedAudio(context: Context, audioBytes: ByteArray): String {
        // use Android Keystore system
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        // Create directory if it doesn't exist
        val directory = File(context.getExternalFilesDir(null), "secure_evidence")
        if (!directory.exists()) {
            directory.mkdirs()
        }

        val filename = "evidence_${System.currentTimeMillis()}.enc"
        val file = File(directory, filename)
        
        // Delete if exists (rare collision)
        if (file.exists()) file.delete()

        val encryptedFile = EncryptedFile.Builder(
            context,
            file,
            masterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build()

        encryptedFile.openFileOutput().use { output ->
            output.write(audioBytes)
        }

        return file.absolutePath
    }

    fun getDecryptedStream(context: Context, path: String): ByteArray {
        val file = File(path)
        if (!file.exists()) return ByteArray(0)

        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        val encryptedFile = EncryptedFile.Builder(
            context,
            file,
            masterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build()

        return encryptedFile.openFileInput().use { input ->
            val baos = ByteArrayOutputStream()
            input.copyTo(baos)
            baos.toByteArray()
        }
    }
}

