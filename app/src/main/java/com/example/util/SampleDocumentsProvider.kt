package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import java.io.File
import java.io.FileOutputStream

data class SampleDocPreset(
    val id: String,
    val title: String,
    val category: String,
    val description: String,
    val iconName: String
)

object SampleDocumentsProvider {

    val presets = listOf(
        SampleDocPreset(
            id = "invoice_preset",
            title = "Consulting Invoice #2026-88",
            category = "Financial / Invoice",
            description = "Invoice with company header, client billing details, itemized services table, and totals.",
            iconName = "Receipt"
        ),
        SampleDocPreset(
            id = "executive_report",
            title = "Quarterly Strategic Report",
            category = "Business Report",
            description = "Multi-level headings, executive summary paragraph, key highlights bullet list, and performance metrics.",
            iconName = "Description"
        ),
        SampleDocPreset(
            id = "service_agreement",
            title = "Standard Service Agreement",
            category = "Legal / Contract",
            description = "Formal contract sections, terms, key-value deliverables, and signature block.",
            iconName = "Gavel"
        ),
        SampleDocPreset(
            id = "academic_abstract",
            title = "Research Paper & Abstract",
            category = "Academic / Research",
            description = "Paper title, author credentials, abstract paragraph, and methodology summary.",
            iconName = "School"
        )
    )

    fun createSampleBitmap(presetId: String): Bitmap {
        val width = 1200
        val height = 1600
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Clean Paper Background
        canvas.drawColor(Color.WHITE)

        val bgPaint = Paint().apply {
            color = Color.rgb(248, 250, 252)
        }
        val borderPaint = Paint().apply {
            color = Color.rgb(226, 232, 240)
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        canvas.drawRoundRect(RectF(40f, 40f, width - 40f, height - 40f), 16f, 16f, bgPaint)
        canvas.drawRoundRect(RectF(40f, 40f, width - 40f, height - 40f), 16f, 16f, borderPaint)

        when (presetId) {
            "invoice_preset" -> drawInvoiceDocument(canvas, width, height)
            "executive_report" -> drawExecutiveReportDocument(canvas, width, height)
            "service_agreement" -> drawServiceAgreementDocument(canvas, width, height)
            else -> drawAcademicPaperDocument(canvas, width, height)
        }

        return bitmap
    }

    fun saveSampleToCache(context: Context, presetId: String): File {
        val bitmap = createSampleBitmap(presetId)
        val cacheDir = File(context.cacheDir, "sample_scans")
        cacheDir.mkdirs()
        val file = File(cacheDir, "${presetId}_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
        }
        return file
    }

    private fun drawInvoiceDocument(canvas: Canvas, w: Int, h: Int) {
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 58, 138)
            textSize = 52f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(100, 116, 139)
            textSize = 26f
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 41, 59)
            textSize = 28f
        }
        val boldTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42)
            textSize = 28f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }

        var y = 120f
        canvas.drawText("ACME SOLUTIONS CORP.", 80f, y, titlePaint)
        y += 40f
        canvas.drawText("100 Innovation Way, Suite 400 • San Francisco, CA", 80f, y, subPaint)
        y += 35f
        canvas.drawText("Tax ID: US-987654321 • billing@acmesolutions.com", 80f, y, subPaint)

        y += 70f
        // Divider
        val linePaint = Paint().apply {
            color = Color.rgb(30, 58, 138)
            strokeWidth = 4f
        }
        canvas.drawLine(80f, y, w - 80f, y, linePaint)

        y += 60f
        canvas.drawText("INVOICE #2026-88", 80f, y, boldTextPaint)
        canvas.drawText("Date: November 10, 2026", w - 420f, y, textPaint)
        y += 40f
        canvas.drawText("Billed To: Global Tech Ventures", 80f, y, boldTextPaint)
        canvas.drawText("Due Date: December 10, 2026", w - 420f, y, textPaint)
        y += 35f
        canvas.drawText("Attn: Finance Department", 80f, y, textPaint)

        y += 80f
        // Table Header
        val headerBg = Paint().apply { color = Color.rgb(30, 58, 138) }
        canvas.drawRect(80f, y, w - 80f, y + 60f, headerBg)

        val headerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 26f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        canvas.drawText("Item / Description", 100f, y + 42f, headerTextPaint)
        canvas.drawText("Hours", 620f, y + 42f, headerTextPaint)
        canvas.drawText("Rate", 780f, y + 42f, headerTextPaint)
        canvas.drawText("Amount", 940f, y + 42f, headerTextPaint)

        y += 60f
        val tableBorder = Paint().apply {
            color = Color.rgb(203, 213, 225)
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }

        val rows = listOf(
            Triple("Cloud Architecture Consultation", "40", "$150.00"),
            Triple("Security & Compliance Audit", "25", "$175.00"),
            Triple("DevOps Pipeline Implementation", "35", "$160.00"),
            Triple("Custom AI Integration & Model Tuning", "50", "$190.00")
        )

        for (row in rows) {
            val amount = row.second.toInt() * row.third.removePrefix("$").toDouble()
            val rowBg = Paint().apply { color = Color.WHITE }
            canvas.drawRect(80f, y, w - 80f, y + 65f, rowBg)
            canvas.drawRect(80f, y, w - 80f, y + 65f, tableBorder)

            canvas.drawText(row.first, 100f, y + 42f, textPaint)
            canvas.drawText(row.second, 640f, y + 42f, textPaint)
            canvas.drawText(row.third, 800f, y + 42f, textPaint)
            canvas.drawText(String.format("$%,.2f", amount), 940f, y + 42f, boldTextPaint)
            y += 65f
        }

        y += 60f
        canvas.drawText("Subtotal:  $25,475.00", w - 400f, y, textPaint)
        y += 40f
        canvas.drawText("Tax (8.5%):  $2,165.38", w - 400f, y, textPaint)
        y += 45f
        val totalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 58, 138)
            textSize = 34f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        canvas.drawText("TOTAL DUE:  $27,640.38", w - 450f, y, totalPaint)

        y += 100f
        // Callout box
        val calloutBg = Paint().apply { color = Color.rgb(239, 246, 255) }
        val calloutBorder = Paint().apply {
            color = Color.rgb(37, 99, 235)
            strokeWidth = 6f
        }
        canvas.drawRect(80f, y, w - 80f, y + 130f, calloutBg)
        canvas.drawLine(80f, y, 80f, y + 130f, calloutBorder)

        canvas.drawText("Payment Instructions:", 110f, y + 45f, boldTextPaint)
        canvas.drawText("Wire Transfer to Chase Bank • Routing #021000021 • Account #9988776655", 110f, y + 85f, subPaint)
    }

    private fun drawExecutiveReportDocument(canvas: Canvas, w: Int, h: Int) {
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 58, 138)
            textSize = 54f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val h1Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 64, 175)
            textSize = 38f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 41, 59)
            textSize = 28f
        }
        val bulletPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 58, 138)
            textSize = 30f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }

        var y = 140f
        canvas.drawText("Executive Strategy & Growth Report", 80f, y, titlePaint)
        y += 45f
        val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(100, 116, 139)
            textSize = 24f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC)
        }
        canvas.drawText("Prepared by: Strategic Planning Committee • Q4 Fiscal Review", 80f, y, metaPaint)

        y += 70f
        canvas.drawText("1. Executive Summary", 80f, y, h1Paint)
        y += 50f
        canvas.drawText("During the past fiscal quarter, our organizational initiatives achieved exceptional", 80f, y, bodyPaint)
        y += 38f
        canvas.drawText("operational momentum across digital transformation and AI workflow adoption.", 80f, y, bodyPaint)
        y += 38f
        canvas.drawText("Enterprise productivity indicators rose by 34%, while customer onboarding time", 80f, y, bodyPaint)
        y += 38f
        canvas.drawText("contracted significantly from fourteen business days down to less than four.", 80f, y, bodyPaint)

        y += 75f
        canvas.drawText("2. Strategic Key Milestones", 80f, y, h1Paint)
        y += 50f

        val bullets = listOf(
            "Launched cloud document intelligence automation pipeline across 12 business units.",
            "Decreased manual document data re-entry latency by over 88% year-over-year.",
            "Integrated cross-platform native Word DOCX generation with preserved formatting.",
            "Expanded client subscription retention rate to an all-time industry high of 97.4%."
        )

        for (bullet in bullets) {
            canvas.drawText("•", 100f, y, bulletPaint)
            canvas.drawText(bullet, 140f, y, bodyPaint)
            y += 50f
        }

        y += 60f
        canvas.drawText("3. Growth Projections", 80f, y, h1Paint)
        y += 50f
        canvas.drawText("Targeted expansion into international markets will drive anticipated recurring", 80f, y, bodyPaint)
        y += 38f
        canvas.drawText("annual revenue growth between 28% and 35% over the forthcoming calendar year.", 80f, y, bodyPaint)
    }

    private fun drawServiceAgreementDocument(canvas: Canvas, w: Int, h: Int) {
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42)
            textSize = 50f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 58, 138)
            textSize = 34f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(51, 65, 85)
            textSize = 26f
            typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        }

        var y = 140f
        canvas.drawText("PROFESSIONAL SERVICES AGREEMENT", 80f, y, titlePaint)
        y += 60f
        canvas.drawText("This Agreement is entered into as of November 10, 2026, by and between:", 80f, y, textPaint)

        y += 55f
        val partyBox = Paint().apply { color = Color.rgb(241, 245, 249) }
        canvas.drawRect(80f, y, w - 80f, y + 100f, partyBox)
        val boldParty = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42)
            textSize = 26f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        canvas.drawText("Client: Apex Capital Partners LLC", 100f, y + 40f, boldParty)
        canvas.drawText("Contractor: Nexus Digital Engineering Inc.", 100f, y + 75f, boldParty)

        y += 150f
        canvas.drawText("Section 1: Scope of Work", 80f, y, sectionPaint)
        y += 45f
        canvas.drawText("Contractor agrees to perform comprehensive document digitization, layout preservation", 80f, y, textPaint)
        y += 36f
        canvas.drawText("architecture, and automated Microsoft Word output verification as specified in Exhibit A.", 80f, y, textPaint)

        y += 65f
        canvas.drawText("Section 2: Confidentiality & Terms", 80f, y, sectionPaint)
        y += 45f
        canvas.drawText("All proprietary algorithms, processed records, and client metadata shall remain", 80f, y, textPaint)
        y += 36f
        canvas.drawText("strictly confidential in compliance with zero-trust information security frameworks.", 80f, y, textPaint)

        y += 120f
        // Signatures
        val sigLine = Paint().apply {
            color = Color.rgb(100, 116, 139)
            strokeWidth = 2f
        }
        canvas.drawLine(100f, y, 480f, y, sigLine)
        canvas.drawLine(w - 480f, y, w - 100f, y, sigLine)
        y += 35f
        canvas.drawText("Client Authorized Signature", 100f, y, textPaint)
        canvas.drawText("Contractor Authorized Signature", w - 480f, y, textPaint)
    }

    private fun drawAcademicPaperDocument(canvas: Canvas, w: Int, h: Int) {
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42)
            textSize = 46f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        val authorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(71, 85, 105)
            textSize = 26f
            typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        }
        val h2Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 58, 138)
            textSize = 32f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 41, 59)
            textSize = 26f
            typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        }

        var y = 140f
        canvas.drawText("Zero-Shot Visual Layout Reconstruction for Modern Office Documents", 80f, y, titlePaint)
        y += 45f
        canvas.drawText("Dr. Elena Vance, Department of Computer Science & Information Systems", 80f, y, authorPaint)
        y += 35f
        canvas.drawText("Journal of Computational Document Intelligence, Vol. 42, pp. 112-128", 80f, y, authorPaint)

        y += 70f
        canvas.drawText("Abstract", 80f, y, h2Paint)
        y += 45f
        canvas.drawText("Document layout preservation represents a fundamental challenge in office productivity workflows.", 80f, y, bodyPaint)
        y += 36f
        canvas.drawText("We introduce a multimodal neural framework that synthesizes hierarchical layout structures", 80f, y, bodyPaint)
        y += 36f
        canvas.drawText("directly into native OpenXML Word packages without loss of fidelity.", 80f, y, bodyPaint)

        y += 70f
        canvas.drawText("1. Introduction & Methodology", 80f, y, h2Paint)
        y += 45f
        canvas.drawText("Traditional OCR tools discard positional coordinates, cell boundaries, and typographic styling.", 80f, y, bodyPaint)
        y += 36f
        canvas.drawText("Our methodology treats typographic runs and tabular grids as first-class primitives,", 80f, y, bodyPaint)
        y += 36f
        canvas.drawText("guaranteeing consistent rendering across modern desktop and mobile word processors.", 80f, y, bodyPaint)
    }

    fun getPresetLayoutJson(presetId: String): String {
        return when (presetId) {
            "invoice_preset" -> """
{
  "title": "Consulting Invoice #2026-88",
  "documentType": "Invoice",
  "confidenceScore": 0.98,
  "blocks": [
    {
      "type": "HEADING",
      "headingLevel": 1,
      "alignment": "LEFT",
      "text": "ACME SOLUTIONS CORP.",
      "bold": true,
      "colorHex": "#1E3A8A"
    },
    {
      "type": "PARAGRAPH",
      "alignment": "LEFT",
      "runs": [
        { "text": "100 Innovation Way, Suite 400 • San Francisco, CA\nTax ID: US-987654321 • billing@acmesolutions.com", "italic": true, "colorHex": "#64748B" }
      ]
    },
    {
      "type": "DIVIDER"
    },
    {
      "type": "KEY_VALUE",
      "keyValuePairs": [
        { "key": "Invoice Number", "value": "INVOICE #2026-88" },
        { "key": "Invoice Date", "value": "November 10, 2026" },
        { "key": "Billed To", "value": "Global Tech Ventures (Attn: Finance)" },
        { "key": "Payment Due Date", "value": "December 10, 2026" }
      ]
    },
    {
      "type": "TABLE",
      "tableHeaders": ["Item / Description", "Hours", "Rate", "Amount"],
      "tableRows": [
        ["Cloud Architecture Consultation", "40", "$150.00", "$6,000.00"],
        ["Security & Compliance Audit", "25", "$175.00", "$4,375.00"],
        ["DevOps Pipeline Implementation", "35", "$160.00", "$5,600.00"],
        ["Custom AI Integration & Model Tuning", "50", "$190.00", "$9,500.00"]
      ]
    },
    {
      "type": "KEY_VALUE",
      "keyValuePairs": [
        { "key": "Subtotal", "value": "$25,475.00" },
        { "key": "Tax (8.5%)", "value": "$2,165.38" },
        { "key": "TOTAL AMOUNT DUE", "value": "$27,640.38" }
      ]
    },
    {
      "type": "CALLOUT",
      "calloutTitle": "Payment Instructions",
      "text": "Wire Transfer to Chase Bank • Routing #021000021 • Account #9988776655 • Due within 30 days."
    }
  ]
}
            """.trimIndent()

            "executive_report" -> """
{
  "title": "Executive Strategy & Growth Report",
  "documentType": "Report",
  "confidenceScore": 0.97,
  "blocks": [
    {
      "type": "HEADING",
      "headingLevel": 1,
      "alignment": "LEFT",
      "text": "Executive Strategy & Growth Report",
      "bold": true,
      "colorHex": "#1E3A8A"
    },
    {
      "type": "PARAGRAPH",
      "alignment": "LEFT",
      "runs": [
        { "text": "Prepared by: Strategic Planning Committee • Q4 Fiscal Review", "italic": true, "colorHex": "#64748B" }
      ]
    },
    {
      "type": "HEADING",
      "headingLevel": 2,
      "alignment": "LEFT",
      "text": "1. Executive Summary",
      "bold": true,
      "colorHex": "#1E40AF"
    },
    {
      "type": "PARAGRAPH",
      "alignment": "LEFT",
      "runs": [
        { "text": "During the past fiscal quarter, our organizational initiatives achieved exceptional operational momentum across digital transformation and AI workflow adoption. Enterprise productivity indicators rose by ", "bold": false },
        { "text": "34%", "bold": true },
        { "text": ", while customer onboarding time contracted significantly from fourteen business days down to less than four.", "bold": false }
      ]
    },
    {
      "type": "HEADING",
      "headingLevel": 2,
      "alignment": "LEFT",
      "text": "2. Strategic Key Milestones",
      "bold": true,
      "colorHex": "#1E40AF"
    },
    {
      "type": "LIST",
      "listType": "BULLET",
      "listItems": [
        "Launched cloud document intelligence automation pipeline across 12 business units.",
        "Decreased manual document data re-entry latency by over 88% year-over-year.",
        "Integrated cross-platform native Word DOCX generation with preserved formatting.",
        "Expanded client subscription retention rate to an all-time industry high of 97.4%."
      ]
    },
    {
      "type": "HEADING",
      "headingLevel": 2,
      "alignment": "LEFT",
      "text": "3. Growth Projections",
      "bold": true,
      "colorHex": "#1E40AF"
    },
    {
      "type": "PARAGRAPH",
      "alignment": "LEFT",
      "runs": [
        { "text": "Targeted expansion into international markets will drive anticipated recurring annual revenue growth between 28% and 35% over the forthcoming calendar year.", "bold": false }
      ]
    }
  ]
}
            """.trimIndent()

            "service_agreement" -> """
{
  "title": "Standard Service Agreement",
  "documentType": "Contract",
  "confidenceScore": 0.96,
  "blocks": [
    {
      "type": "HEADING",
      "headingLevel": 1,
      "alignment": "LEFT",
      "text": "PROFESSIONAL SERVICES AGREEMENT",
      "bold": true,
      "colorHex": "#0F172A"
    },
    {
      "type": "PARAGRAPH",
      "alignment": "LEFT",
      "runs": [
        { "text": "This Agreement is entered into as of November 10, 2026, by and between the parties identified below:" }
      ]
    },
    {
      "type": "KEY_VALUE",
      "keyValuePairs": [
        { "key": "Client", "value": "Apex Capital Partners LLC" },
        { "key": "Contractor", "value": "Nexus Digital Engineering Inc." }
      ]
    },
    {
      "type": "HEADING",
      "headingLevel": 2,
      "alignment": "LEFT",
      "text": "Section 1: Scope of Work",
      "bold": true,
      "colorHex": "#1E3A8A"
    },
    {
      "type": "PARAGRAPH",
      "alignment": "LEFT",
      "runs": [
        { "text": "Contractor agrees to perform comprehensive document digitization, layout preservation architecture, and automated Microsoft Word output verification as specified in Exhibit A." }
      ]
    },
    {
      "type": "HEADING",
      "headingLevel": 2,
      "alignment": "LEFT",
      "text": "Section 2: Confidentiality & Terms",
      "bold": true,
      "colorHex": "#1E3A8A"
    },
    {
      "type": "PARAGRAPH",
      "alignment": "LEFT",
      "runs": [
        { "text": "All proprietary algorithms, processed records, and client metadata shall remain strictly confidential in compliance with zero-trust information security frameworks." }
      ]
    },
    {
      "type": "KEY_VALUE",
      "keyValuePairs": [
        { "key": "Client Authorized Signature", "value": "Apex Capital Partners LLC [Signed]" },
        { "key": "Contractor Authorized Signature", "value": "Nexus Digital Engineering Inc. [Signed]" }
      ]
    }
  ]
}
            """.trimIndent()

            else -> """
{
  "title": "Zero-Shot Visual Layout Reconstruction for Modern Office Documents",
  "documentType": "Academic Paper",
  "confidenceScore": 0.97,
  "blocks": [
    {
      "type": "HEADING",
      "headingLevel": 1,
      "alignment": "LEFT",
      "text": "Zero-Shot Visual Layout Reconstruction for Modern Office Documents",
      "bold": true,
      "colorHex": "#0F172A"
    },
    {
      "type": "PARAGRAPH",
      "alignment": "LEFT",
      "runs": [
        { "text": "Dr. Elena Vance, Department of Computer Science & Information Systems\nJournal of Computational Document Intelligence, Vol. 42, pp. 112-128", "italic": true, "colorHex": "#475569" }
      ]
    },
    {
      "type": "HEADING",
      "headingLevel": 2,
      "alignment": "LEFT",
      "text": "Abstract",
      "bold": true,
      "colorHex": "#1E3A8A"
    },
    {
      "type": "PARAGRAPH",
      "alignment": "LEFT",
      "runs": [
        { "text": "Document layout preservation represents a fundamental challenge in office productivity workflows. We introduce a multimodal neural framework that synthesizes hierarchical layout structures directly into native OpenXML Word packages without loss of fidelity." }
      ]
    },
    {
      "type": "HEADING",
      "headingLevel": 2,
      "alignment": "LEFT",
      "text": "1. Introduction & Methodology",
      "bold": true,
      "colorHex": "#1E3A8A"
    },
    {
      "type": "PARAGRAPH",
      "alignment": "LEFT",
      "runs": [
        { "text": "Traditional OCR tools discard positional coordinates, cell boundaries, and typographic styling. Our methodology treats typographic runs and tabular grids as first-class primitives, guaranteeing consistent rendering across modern desktop and mobile word processors." }
      ]
    }
  ]
}
            """.trimIndent()
        }
    }
}

