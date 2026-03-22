package com.runanywhere.kotlin_starter_example.data

import android.content.Context
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Handles secure storage and retrieval of image evidence.
 * Uses AES-256 GCM HKDF encryption via Android Jetpack Security.
 */
object EncryptedImageManager {
    private const val DIRECTORY_NAME = "secure_visual_evidence"

    /**
     * Saves raw image data as an encrypted file.
     * 
     * @param context Application context
     * @param imageData Raw bytes of the image (JPEG/PNG)
     * @return Absolute path to the encrypted file
     */
    fun saveEncryptedImage(context: Context, imageData: ByteArray): String {
        val filename = "IMG_${System.currentTimeMillis()}.enc"
        val directory = File(context.filesDir, DIRECTORY_NAME).apply { mkdirs() }
        val file = File(directory, filename)

        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        val encryptedFile = EncryptedFile.Builder(
            context,
            file,
            masterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build()

        encryptedFile.openFileOutput().use { outputStream ->
            outputStream.write(imageData)
        }

        return file.absolutePath
    }

    /**
     * Decrypts and retrieves the raw image data.
     * 
     * @param context Application context
     * @param path Absolute path to the encrypted file
     * @return Decrypted byte array of the image
     */
    fun getDecryptedImage(context: Context, path: String): ByteArray {
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

        return try {
            encryptedFile.openFileInput().use { inputStream ->
                val outputStream = ByteArrayOutputStream()
                val buffer = ByteArray(1024)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                }
                outputStream.toByteArray()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            ByteArray(0)
        }
    }
    
    /**
     * Delete an image file safely.
     */
    fun deleteImage(path: String): Boolean {
        return try {
            File(path).delete()
        } catch (e: Exception) {
            false
        }
    }
}

