package com.runanywhere.kotlin_starter_example.data

import android.content.Context
import java.io.*
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod

/**
 * Handles the secure export of evidence into a single ZIP file.
 * Media remains AES-encrypted with a session key noted in the PDF (ZIP itself is not passworded).
 */
object SecureZipExporter {

    /**
     * Creates a comprehensive evidence package and saves it to the public Downloads folder.
     */
    fun createSecureExport(
        context: Context, 
        record: IncidentRecord, 
        pdfPassword: String
    ): File {
        // 1. Generate a Session Key (Random 12-char string)
        val sessionKey = UUID.randomUUID().toString().replace("-", "").take(12).uppercase()
        val encryptedEntries = mutableListOf<String>()

        // 2. Create a password-protected ZIP (AES-256)
        val tempZipFile = File(context.cacheDir, "temp_evidence_${System.currentTimeMillis()}.zip")
        val zipFile = ZipFile(tempZipFile)

        try {
            // 3. SECURE PDF GENERATION
            val pdfFile = PdfExporter.export(context, listOf(record), pdfPassword, sessionKeyToPrint = sessionKey)
            zipFile.addFile(pdfFile, encryptedParams("00_READ_THIS_REPORT.pdf"))

            // 4. AUDIO EVIDENCE (Re-encrypt with Session Key)
            record.audioFilePath?.let { internalPath ->
                val rawAudio = EncryptedAudioFileManager.getDecryptedStream(context, internalPath)
                if (rawAudio.isNotEmpty()) {
                    val wavAudio = pcm16ToWav(rawAudio)
                    val exportAudio = encryptWithSessionKey(wavAudio, sessionKey)
                    zipFile.addStream(ByteArrayInputStream(exportAudio), encryptedParams("01_Audio_Evidence.enc"))
                    encryptedEntries += "01_Audio_Evidence.enc"
                }
            }

            // 5. VISUAL EVIDENCE
            record.imagePaths.filter { it.isNotBlank() }.forEachIndexed { index, path ->
                val rawImg = EncryptedImageManager.getDecryptedImage(context, path)
                if (rawImg.isNotEmpty()) {
                    val exportImg = encryptWithSessionKey(rawImg, sessionKey)
                    val name = "02_Visual_Evidence_${index + 1}.enc"
                    zipFile.addStream(ByteArrayInputStream(exportImg), encryptedParams(name))
                    encryptedEntries += name
                }
            }

            // 6. DECRYPTION PLAYER (HTML)
            val playerHtml = getPlayerHtml()
            zipFile.addStream(ByteArrayInputStream(playerHtml.toByteArray()), encryptedParams("START_HERE_Player.html"))

            // 7. Session key fingerprint manifest for external verification
            val manifest = buildSessionKeyManifest(sessionKey, encryptedEntries)
            zipFile.addStream(ByteArrayInputStream(manifest.toByteArray()), encryptedParams("99_SESSION_KEY_FINGERPRINT.txt"))

        } finally {
            // ZipFile manages stream closures
        }

        // 7. Limit output: Move temp file to Public Downloads
        val finalFileName = "Sakshi_Evidence_${System.currentTimeMillis()}.zip"
        return saveToPublicDownloads(context, tempZipFile, finalFileName)
    }

    // Global Export Logic
    suspend fun createGlobalSecureExport(
        context: Context,
        incidents: List<IncidentRecord>,
        pdfPassword: String
    ): File {
        // 1. Generate Session Key
        val sessionKey = UUID.randomUUID().toString().replace("-", "").take(12).uppercase()
        val encryptedEntries = mutableListOf<String>()

        // 2. Temp password-protected ZIP
        val tempZipFile = File(context.cacheDir, "temp_global_evidence_${System.currentTimeMillis()}.zip")
        val zipFile = ZipFile(tempZipFile)

        try {
            // 3. MASTER REPORT PDF
            val pdfFile = PdfExporter.export(context, incidents, pdfPassword, sessionKeyToPrint = sessionKey)
            zipFile.addFile(pdfFile, encryptedParams("00_MASTER_REPORT.pdf"))

            // 4. Loop through ALL incidents
            incidents.forEachIndexed { i, record ->
                val prefix = "Entry_${i + 1}_${record.id.take(4)}"
                
                // Audio
                record.audioFilePath?.let { internalPath ->
                    val rawAudio = EncryptedAudioFileManager.getDecryptedStream(context, internalPath)
                    if (rawAudio.isNotEmpty()) {
                        val wavAudio = pcm16ToWav(rawAudio)
                        val exportAudio = encryptWithSessionKey(wavAudio, sessionKey)
                        val name = "${prefix}/Audio.enc"
                        zipFile.addStream(ByteArrayInputStream(exportAudio), encryptedParams(name))
                        encryptedEntries += name
                    }
                }

                // Images
                // Ensure imagePaths are handled, assuming IncidentRecord has this property as List<String>
                record.imagePaths.filter { it.isNotBlank() }.forEachIndexed { j, path ->
                    val rawImg = EncryptedImageManager.getDecryptedImage(context, path)
                    if (rawImg.isNotEmpty()) {
                        val exportImg = encryptWithSessionKey(rawImg, sessionKey)
                        val name = "$prefix/Image_${j + 1}.enc"
                        zipFile.addStream(ByteArrayInputStream(exportImg), encryptedParams(name))
                        encryptedEntries += name
                    }
                }
            }

            // 5. PLAYER
            val playerHtml = getPlayerHtml()
            zipFile.addStream(ByteArrayInputStream(playerHtml.toByteArray()), encryptedParams("START_HERE_Player.html"))

            // 6. Session key fingerprint manifest for external verification
            val manifest = buildSessionKeyManifest(sessionKey, encryptedEntries)
            zipFile.addStream(ByteArrayInputStream(manifest.toByteArray()), encryptedParams("99_SESSION_KEY_FINGERPRINT.txt"))

        } finally {
            // ZipFile manages stream closures
        }

        // 6. Save to Downloads
        val finalFileName = "Sakshi_Full_Export_${System.currentTimeMillis()}.zip"
        return saveToPublicDownloads(context, tempZipFile, finalFileName)
    }

    private fun saveToPublicDownloads(context: Context, tempFile: File, fileName: String): File {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val contentValues = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/zip")
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { output ->
                    FileInputStream(tempFile).use { input ->
                        input.copyTo(output)
                    }
                }
                return File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), fileName) 
                // Return descriptive path (Note: File object might not be directly accessible via path on scoped storage, but good for toast msg)
            }
        } 
        
        // Fallback for older APIs or if MediaStore fails
        val publicDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
        if (!publicDir.exists()) publicDir.mkdirs()
        val destFile = File(publicDir, fileName)
        tempFile.copyTo(destFile, overwrite = true)
        return destFile
    }

    private fun getPlayerHtml(): String {
        return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>SakshiAI Evidence Player</title>
    <style>
        body { font-family: sans-serif; background: #0A0E1A; color: #fff; display: flex; flex-direction: column; align-items: center; justify-content: center; min-height: 100vh; margin: 0; }
        .container { background: #1E2536; padding: 2rem; border-radius: 12px; box-shadow: 0 4px 6px rgba(0,0,0,0.3); max-width: 500px; width: 100%; text-align: center; }
        h1 { color: #06B6D4; margin-bottom: 1rem; }
        input, button { width: 100%; padding: 12px; margin: 8px 0; border-radius: 8px; border: 1px solid #334155; background: #0F172A; color: white; font-size: 16px; box-sizing: border-box; }
        button { background: #06B6D4; border: none; cursor: pointer; font-weight: bold; transition: background 0.2s; }
        button:hover { background: #0891B2; }
        #output { margin-top: 20px; }
        img, audio { max-width: 100%; border-radius: 8px; margin-top: 10px; }
        .error { color: #FF6B6B; margin-top: 10px; font-size: 14px; }
    </style>
</head>
<body>
    <div class="container">
        <h1>SakshiAI Player</h1>
        <p>1. Open the PDF report to find your <strong>Session Key</strong>.</p>
        <p>2. Upload an encrypted file (.enc) below.</p>
        
        <input type="text" id="keyInput" placeholder="Enter Session Key (12 chars)" maxlength="12">
        <input type="file" id="fileInput">
        <button onclick="decryptFile()">Unlock & View</button>
        
        <div id="output"></div>
        <div id="error" class="error"></div>
    </div>

    <script>
        async function decryptFile() {
            const keyString = document.getElementById('keyInput').value.trim();
            const fileInput = document.getElementById('fileInput');
            const output = document.getElementById('output');
            const error = document.getElementById('error');
            
            output.innerHTML = '';
            error.innerText = '';

            if (!keyString || !fileInput.files.length) {
                error.innerText = "Please enter the Session Key and select a file.";
                return;
            }

            try {
                const file = fileInput.files[0];
                const arrayBuffer = await file.arrayBuffer();
                const data = new Uint8Array(arrayBuffer);

                // Java Logic: [SALT (16)] + [IV (12)] + [CIPHERTEXT]
                const salt = data.slice(0, 16);
                const iv = data.slice(16, 28);
                const ciphertext = data.slice(28);

                const enc = new TextEncoder();
                const keyMaterial = await window.crypto.subtle.importKey(
                    "raw", 
                    enc.encode(keyString), 
                    { name: "PBKDF2" }, 
                    false, 
                    ["deriveKey"]
                );

                const key = await window.crypto.subtle.deriveKey(
                    {
                        name: "PBKDF2",
                        salt: salt,
                        iterations: 65536,
                        hash: "SHA-256"
                    },
                    keyMaterial,
                    { name: "AES-GCM", length: 256 },
                    true,
                    ["decrypt"]
                );

                const decryptedBuffer = await window.crypto.subtle.decrypt(
                    { name: "AES-GCM", iv: iv },
                    key,
                    ciphertext
                );

                const lowerName = file.name.toLowerCase();
                let mime = "";
                if (lowerName.includes("audio")) mime = "audio/wav";
                else if (lowerName.includes("visual") || lowerName.includes("image")) mime = "image/jpeg";
                else if (lowerName.includes("zip")) mime = "application/zip";

                if (mime === "audio/wav") {
                    const bytes = new Uint8Array(decryptedBuffer);
                    const header = String.fromCharCode(...bytes.slice(0,4)) + String.fromCharCode(...bytes.slice(8,12));
                    if (header !== "RIFFWAVE") {
                        throw new Error("Decrypted audio is not WAV (header=" + header + ")");
                    }
                }

                const blob = mime ? new Blob([decryptedBuffer], { type: mime }) : new Blob([decryptedBuffer]);
                const url = URL.createObjectURL(blob);

                if (lowerName.includes("audio")) {
                    const audio = document.createElement('audio');
                    audio.controls = true;
                    audio.src = url;
                    audio.style.width = '100%';
                    const hint = document.createElement('div');
                    hint.style.color = '#94a3b8';
                    hint.style.fontSize = '14px';
                    hint.innerText = 'If you do not hear sound, click Play and check volume.';
                    output.appendChild(audio);
                    output.appendChild(hint);
                } else if (lowerName.includes("visual") || lowerName.includes("image")) {
                    const img = document.createElement('img');
                    img.src = url;
                    output.appendChild(img);
                } else {
                    const a = document.createElement('a');
                    a.href = url;
                    a.download = "decrypted_file";
                    a.innerText = "Download Decrypted File";
                    a.style.color = "#06B6D4";
                    output.appendChild(a);
                }

            } catch (e) {
                console.error(e);
                error.innerText = "Decryption failed. Invalid Key or corrupted file.";
            }
        }
    </script>
</body>
</html>
        """.trimIndent()
    }

    /**
     * Standard AES-256-GCM encryption for export portability.
     * We don't use Android KeyStore here because we want the file to be openable elsewhere 
     * (e.g., on a PC) if the user has the key.
     */
    private fun encryptWithSessionKey(data: ByteArray, sessionKey: String): ByteArray {
        require(sessionKey.isNotBlank()) { "Session key missing" }
        try {
            // Derive a key from the text sessionKey using PBKDF2
            // This is safer than using the raw string bytes
            val salt = ByteArray(16).apply { SecureRandom().nextBytes(this) }
            val spec = PBEKeySpec(sessionKey.toCharArray(), salt, 65536, 256)
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val secretKey = SecretKeySpec(factory.generateSecret(spec).encoded, "AES")

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val iv = ByteArray(12).apply { SecureRandom().nextBytes(this) } // 12 bytes IV
            val gcmSpec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)

            val ciphertext = cipher.doFinal(data)

            // Output format: [SALT (16)] + [IV (12)] + [CIPHERTEXT]
            val output = ByteArrayOutputStream()
            output.write(salt)
            output.write(iv)
            output.write(ciphertext)
            return output.toByteArray()
        } catch (e: Exception) {
            e.printStackTrace()
            return ByteArray(0)
        }
    }

    // Wrap raw PCM 16-bit mono into a minimal WAV so browsers can play it
    private fun pcm16ToWav(
        pcm: ByteArray,
        sampleRate: Int = 16_000,
        channels: Int = 1,
        bitsPerSample: Int = 16
    ): ByteArray {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        // Ensure even number of bytes for 16-bit PCM
        val dataSize = if (pcm.size % 2 == 0) pcm.size else pcm.size - 1
        val pcmData = if (dataSize == pcm.size) pcm else pcm.copyOf(dataSize)
        val chunkSize = 36 + dataSize
        val header = ByteArrayOutputStream().apply {
            write("RIFF".toByteArray())
            write(intToLe(chunkSize))
            write("WAVE".toByteArray())
            write("fmt ".toByteArray())
            write(intToLe(16)) // PCM header size
            write(shortToLe(1)) // Audio format PCM
            write(shortToLe(channels.toShort()))
            write(intToLe(sampleRate))
            write(intToLe(byteRate))
            write(shortToLe((channels * bitsPerSample / 8).toShort()))
            write(shortToLe(bitsPerSample.toShort()))
            write("data".toByteArray())
            write(intToLe(dataSize))
        }.toByteArray()
        return header + pcmData
    }

    private fun intToLe(v: Int) = byteArrayOf(
        (v and 0xFF).toByte(),
        (v shr 8 and 0xFF).toByte(),
        (v shr 16 and 0xFF).toByte(),
        (v shr 24 and 0xFF).toByte()
    )

    private fun shortToLe(v: Short) = byteArrayOf(
        (v.toInt() and 0xFF).toByte(),
        (v.toInt() shr 8 and 0xFF).toByte()
    )

    private fun encryptedParams(name: String): ZipParameters = ZipParameters().apply {
        fileNameInZip = name
        compressionMethod = CompressionMethod.DEFLATE
        isEncryptFiles = false // ZIP itself left unencrypted per request
    }

    private fun buildSessionKeyManifest(sessionKey: String, encryptedEntries: List<String>): String {
        val fingerprint = MessageDigest.getInstance("SHA-256")
            .digest(sessionKey.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return buildString {
            appendLine("SessionKeySHA256=$fingerprint")
            appendLine("EncryptedFiles=${encryptedEntries.size}")
            encryptedEntries.forEach { appendLine(it) }
        }
    }
}

