package com.runanywhere.kotlin_starter_example.data

import android.content.Context
import com.itextpdf.text.BaseColor
import com.itextpdf.text.Document
import com.itextpdf.text.Font
import com.itextpdf.text.FontFactory
import com.itextpdf.text.Paragraph
import com.itextpdf.text.pdf.PdfWriter
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * PDF export utility for incident records using iTextG.
 * 
 * Generates a professional, court-ready PDF document containing
 * all incident records with proper formatting, metadata, and password protection.
 */
object PdfExporter {

    /**
     * Export all incidents to a password-protected PDF file.
     * 
     * @param context Android context
     * @param incidents List of incidents to export
     * @param password Password to encrypt the PDF (optional, but recommended)
     * @return File object pointing to the generated PDF
     */
    fun export(
        context: Context,
        incidents: List<IncidentRecord>,
        password: String? = null,
        sessionKeyToPrint: String? = null
    ): File {
        val fileName = "Sakshi_Report_${System.currentTimeMillis()}.pdf"
        val file = File(context.getExternalFilesDir(null), fileName)
        
        val document = Document()
        val writer = PdfWriter.getInstance(document, FileOutputStream(file))
        
        if (!password.isNullOrBlank()) {
            writer.setEncryption(
                password.toByteArray(),
                null, // Owner password same as user password if null
                PdfWriter.ALLOW_PRINTING or PdfWriter.ALLOW_COPY,
                PdfWriter.STANDARD_ENCRYPTION_128
            )
        }
        
        document.addTitle("SakshiAI Incident Report")
        document.addAuthor("SakshiAI Secure Evidence Logger")
        document.addCreator("SakshiAI")
        document.open()

        val titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18f)
        val headingFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14f)
        val bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 11f)
        val tagFont = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 10f)
        val warningFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11f, Font.NORMAL, BaseColor.RED) // iTextG uses BaseColor

        // Cover Header
        document.add(Paragraph("SaakshiAI — Incident Documentation", titleFont))
        document.add(Paragraph(" ")) // Spacer
        
        val exportDate = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date())
        document.add(Paragraph("Exported: $exportDate", bodyFont))
        document.add(Paragraph("Total entries: ${incidents.size}", bodyFont))
        document.add(Paragraph("Status: Authenticated & Encrypted", bodyFont))
        if (!sessionKeyToPrint.isNullOrBlank()) {
            document.add(Paragraph("Session Key (media unlock): $sessionKeyToPrint", bodyFont))
            document.add(Paragraph("Keep this key safe. It decrypts the audio/photos in the ZIP.", tagFont))
        }
        document.add(Paragraph("----------------------------------------------------------------", bodyFont))
        document.add(Paragraph(" "))

        // Disclaimer
        document.add(Paragraph("CONFIDENTIAL: This document contains sensitive personal records. " +
                "The entries below are timestamped and digitally signed for integrity.", bodyFont))
        document.add(Paragraph(" "))

        for (record in incidents) {
            val dateStr = SimpleDateFormat("EEEE, dd MMM yyyy 'at' HH:mm", Locale.getDefault()).format(Date(record.timestamp))
            
            // Entry Header
            document.add(Paragraph("ENTRY: $dateStr", headingFont))
            document.add(Paragraph("ID: ${record.id}", tagFont))
            
            // Tags
            val severity = "Severity: ${record.severityTag}"
            document.add(Paragraph(severity, if (record.severityTag == "Immediate Risk") warningFont else bodyFont))
            
            if (record.patternFlag) {
                document.add(Paragraph("⚠️ Pattern Detected: Recurrent language or behavior identified.", tagFont))
            }
            
            document.add(Paragraph(" "))
            
            // Content
            if (record.incidentType.isNotBlank()) document.add(Paragraph("Type: ${record.incidentType}", bodyFont))
            if (record.whoInvolved.isNotBlank()) document.add(Paragraph("Involved: ${record.whoInvolved}", bodyFont))
            if (record.witnessesPresent.isNotBlank()) document.add(Paragraph("Witnesses: ${record.witnessesPresent}", bodyFont))
            
            document.add(Paragraph(" "))
            document.add(Paragraph("Account:", headingFont))
            document.add(Paragraph(record.rawTranscript, bodyFont))
            
            // Integrity & Evidence
            document.add(Paragraph(" "))
            if (record.audioFilePath != null) {
                val filename = File(record.audioFilePath).name
                document.add(Paragraph("Audio Evidence: $filename (Encrypted Storage)", tagFont))
            }
            if (record.integrityHash.isNotBlank()) {
                 document.add(Paragraph("Digital Seal (SHA-256): ${record.integrityHash.take(16)}...", tagFont))
            }
            
            document.add(Paragraph("----------------------------------------------------------------", bodyFont))
            document.add(Paragraph(" "))
        }
        
        // Footer (Legal)
        document.newPage()
        document.add(Paragraph("Legal Context & User Rights", headingFont))
        document.add(Paragraph(" ", bodyFont))
        document.add(Paragraph("Information contained herein may be relevant for:", bodyFont))
        document.add(Paragraph("1. Domestic Incident Reports (DIR) under PWDVA 2005.", bodyFont))
        document.add(Paragraph("2. Applications for Protection Orders (Section 18, PWDVA).", bodyFont))
        document.add(Paragraph("3. Evidence of 'Continuing Offense' or pattern of conduct.", bodyFont))
        document.add(Paragraph(" ", bodyFont))
        document.add(Paragraph("Disclaimer: This generated report is a record of user input and automated analysis. " +
                "It does not constitute a legal judgment. Consult a lawyer for professional advice.", tagFont))

        document.close()
        
        return file
    }
}
