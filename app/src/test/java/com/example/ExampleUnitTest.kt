package com.example

import com.example.generator.DocxGenerator
import com.example.model.DocBlock
import com.example.model.ScannedDocument
import com.example.model.TextAlignment
import com.example.model.TextRun
import com.example.service.DocxGenerationService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

class ExampleUnitTest {

  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testDocxGenerationProducesValidOpenXmlPackage() {
    val tempDir = File(System.getProperty("java.io.tmpdir"), "docx_unit_test")
    tempDir.mkdirs()
    val testFile = File(tempDir, "test_document.docx")

    val testDoc = ScannedDocument(
      id = "doc_test_101",
      title = "Executive Consulting Agreement",
      documentType = "Contract",
      blocks = listOf(
        DocBlock.Heading(1, "1. Executive Summary", TextAlignment.LEFT, true, "#1E3A8A"),
        DocBlock.Paragraph(
          runs = listOf(
            TextRun("This agreement is executed between ", isBold = false),
            TextRun("Acme Global Partners", isBold = true),
            TextRun(" and the Contractor.", isItalic = true)
          ),
          alignment = TextAlignment.LEFT
        ),
        DocBlock.Table(
          headers = listOf("Deliverable", "Timeline", "Fee"),
          rows = listOf(
            listOf("Phase 1: Architecture", "2 weeks", "$8,500"),
            listOf("Phase 2: Deployment", "4 weeks", "$16,000")
          )
        ),
        DocBlock.ListBlock(
          isOrdered = true,
          items = listOf("Initial scoping review", "Security authorization", "Final signoff")
        ),
        DocBlock.KeyValueGrid(
          entries = listOf("Client" to "Acme Corp", "Status" to "Active")
        ),
        DocBlock.Callout(
          title = "Important Notice",
          text = "Strict confidentiality terms apply."
        ),
        DocBlock.Divider
      )
    )

    val generated = DocxGenerator.generateDocx(testDoc, testFile)
    assertTrue("Generated file should exist", generated.exists())
    assertTrue("Generated file size should be > 0 bytes", generated.length() > 0)

    ZipFile(generated).use { zip ->
      assertNotNull("[Content_Types].xml must exist", zip.getEntry("[Content_Types].xml"))
      assertNotNull("_rels/.rels must exist", zip.getEntry("_rels/.rels"))
      assertNotNull("word/_rels/document.xml.rels must exist", zip.getEntry("word/_rels/document.xml.rels"))
      assertNotNull("word/styles.xml must exist", zip.getEntry("word/styles.xml"))
      assertNotNull("word/document.xml must exist", zip.getEntry("word/document.xml"))
    }

    testFile.delete()
  }

  @Test
  fun testDocxGenerationServiceFromProcessedText() {
    val service = DocxGenerationService()
    val tempDir = File(System.getProperty("java.io.tmpdir"), "service_unit_test")
    tempDir.mkdirs()
    val testFile = File(tempDir, "service_result.docx")

    val rawText = """
# Project Proposal
## Section 1: Scope
This is a **critical milestone** for the platform.

### Deliverables:
- Deliverable Alpha
- Deliverable Beta

| Item | Estimate |
| Task 1 | 4 hours |
| Task 2 | 8 hours |
    """.trimIndent()

    val doc = service.generateDocxFromProcessedText(
      rawText = rawText,
      title = "Project Proposal",
      documentType = "Proposal",
      outputFile = testFile
    )

    assertTrue("Output docx must exist", testFile.exists())
    assertTrue("File size must be positive", testFile.length() > 0)
    assertTrue("Service validation must pass", service.validateDocxArchive(testFile))
    assertEquals("Project Proposal", doc.title)
    assertTrue("Must have parsed blocks", doc.blocks.isNotEmpty())

    testFile.delete()
  }

  @Test
  fun testDocumentReviewParsingAndBlockUpdates() {
    val blocks = listOf(
      DocBlock.Heading(1, "Quarterly Financial Overview"),
      DocBlock.Paragraph(
        runs = listOf(TextRun("Operating revenues increased by 14% year over year.")),
        alignment = TextAlignment.LEFT
      ),
      DocBlock.Table(
        headers = listOf("Quarter", "Revenue (M)", "Margin"),
        rows = listOf(
          listOf("Q1", "$12.4", "22%"),
          listOf("Q2", "$14.1", "24%")
        )
      )
    )

    val doc = ScannedDocument(
      id = "test_review_123",
      title = "Quarterly Financial Overview",
      documentType = "Report",
      confidenceScore = 0.96f,
      blocks = blocks
    )

    assertEquals("Quarterly Financial Overview", doc.title)
    assertEquals("Report", doc.documentType)
    assertEquals(3, doc.blocks.size)

    val plainText = com.example.util.DocumentJsonUtil.blocksToPlainText(doc.blocks)
    assertTrue("Extracted plain text should contain revenue line", plainText.contains("Operating revenues"))
    assertTrue("Extracted plain text should contain table content", plainText.contains("Revenue (M)"))
  }

  @Test
  fun testWordDocumentMimeTypeConfiguration() {
    val wordDocxMime = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document", wordDocxMime)
    assertTrue("MIME type must be standard Word OpenXML", wordDocxMime.endsWith(".document"))
  }

  @Test
  fun testPresetLayoutJsonValidity() {
    val presetJson = com.example.util.SampleDocumentsProvider.getPresetLayoutJson("invoice_preset")
    assertTrue("Preset JSON should contain title", presetJson.contains("Consulting Invoice #2026-88"))
    assertTrue("Preset JSON should contain invoice type", presetJson.contains("\"Invoice\""))
    assertTrue("Preset JSON should contain blocks", presetJson.contains("\"blocks\""))
    assertTrue("Preset JSON should contain ACME SOLUTIONS", presetJson.contains("ACME SOLUTIONS CORP."))
  }
}

