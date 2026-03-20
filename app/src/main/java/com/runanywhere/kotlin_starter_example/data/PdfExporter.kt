package com.runanywhere.kotlin_starter_example.data

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * PDF export utility for incident records.
 * 
 * Generates a professional, court-ready PDF document containing
 * all incident records with proper formatting and metadata.
 * 
 * Uses Android's built-in PdfDocument API (no external dependencies).
 */
object PdfExporter {

    /**
     * Export all incidents to a PDF file.
     * 
     * @param context Android context
     * @param incidents List of incidents to export
     * @return File object pointing to the generated PDF
     */
    fun export(context: Context, incidents: List<IncidentRecord>): File {
        val document = PdfDocument()
        val pageWidth = 595  // A4 width in points
        val pageHeight = 842 // A4 height in points
        val margin = 50f
        val lineHeight = 20f

        // Paint styles
        val titlePaint = Paint().apply {
            color = Color.BLACK
            textSize = 18f
            isFakeBoldText = true
        }
        
        val headingPaint = Paint().apply {
            color = Color.BLACK
            textSize = 13f
            isFakeBoldText = true
        }
        
        val bodyPaint = Paint().apply {
            color = Color.DKGRAY
            textSize = 11f
        }
        
        val tagPaint = Paint().apply {
            textSize = 11f
            isFakeBoldText = true
        }
        
        val dividerPaint = Paint().apply {
            color = Color.LTGRAY
            strokeWidth = 1f
        }
        
        val footerPaint = Paint().apply {
            color = Color.GRAY
            textSize = 9f
        }

        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        var page = document.startPage(pageInfo)
        var canvas: Canvas = page.canvas
        var y = margin + 30f

        fun newPage() {
            document.finishPage(page)
            pageNumber++
            pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            page = document.startPage(pageInfo)
            canvas = page.canvas
            y = margin + 20f
        }

        fun checkPageBreak(needed: Float = 120f) {
            if (y > pageHeight - needed) newPage()
        }

        // Cover header
        canvas.drawText("SaakshiAI — Incident Documentation", margin, y, titlePaint)
        y += lineHeight * 1.2f
        
        val exportDate = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date())
        canvas.drawText("Exported: $exportDate     Total entries: ${incidents.size}", margin, y, bodyPaint)
        y += lineHeight * 0.5f
        canvas.drawLine(margin, y, (pageWidth - margin), y, dividerPaint)
        y += lineHeight * 1.5f

        canvas.drawText(
            "This document was generated entirely on-device. No data was transmitted to any server.",
            margin, y, footerPaint
        )
        y += lineHeight * 2f

        val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())

        // Render each incident
        for ((index, record) in incidents.withIndex()) {
            checkPageBreak(160f)

            // Entry header
            val entryDate = dateFormat.format(Date(record.timestamp))
            canvas.drawText("Entry ${index + 1}  —  $entryDate", margin, y, headingPaint)
            y += lineHeight

            // Severity tag
            tagPaint.color = when (record.severityTag) {
                "Immediate Risk" -> Color.rgb(180, 0, 0)
                "Concerning Pattern" -> Color.rgb(160, 90, 0)
                else -> Color.rgb(0, 110, 55)
            }
            canvas.drawText("Severity: ${record.severityTag}", margin, y, tagPaint)
            
            if (record.patternFlag) {
                tagPaint.color = Color.rgb(160, 90, 0)
                canvas.drawText("  ⚠ Repeat pattern detected", margin + 200f, y, tagPaint)
            }
            y += lineHeight

            // Incident details
            if (record.incidentType.isNotBlank()) {
                canvas.drawText("Incident type: ${record.incidentType}", margin, y, bodyPaint)
                y += lineHeight
            }
            
            if (record.whoInvolved.isNotBlank()) {
                canvas.drawText("Person involved: ${record.whoInvolved}", margin, y, bodyPaint)
                y += lineHeight
            }
            
            if (record.threatDocumented) {
                canvas.drawText("Direct threat documented: Yes", margin, y, bodyPaint)
                y += lineHeight
            }
            
            if (record.witnessesPresent.isNotBlank()) {
                canvas.drawText("Witnesses: ${record.witnessesPresent}", margin, y, bodyPaint)
                y += lineHeight
            }
            
            if (record.rawTranscript.isNotBlank()) {
                val preview = record.rawTranscript.take(140) + if (record.rawTranscript.length > 140) "…" else ""
                canvas.drawText("Account: $preview", margin, y, bodyPaint)
                y += lineHeight
            }

            // Divider between entries
            y += lineHeight * 0.5f
            canvas.drawLine(margin, y, (pageWidth - margin), y, dividerPaint)
            y += lineHeight * 1.2f
        }

        // Footer on last page
        canvas.drawText(
            "SaakshiAI — Because silence was never a choice.",
            margin, pageHeight - 25f, footerPaint
        )

        document.finishPage(page)

        // Save to file
        val outputDir = File(context.getExternalFilesDir(null), "SaakshiAI")
        outputDir.mkdirs()
        val file = File(outputDir, "saakshi_record_${System.currentTimeMillis()}.pdf")
        
        FileOutputStream(file).use { outputStream ->
            document.writeTo(outputStream)
        }
        
        document.close()

        return file
    }
}
