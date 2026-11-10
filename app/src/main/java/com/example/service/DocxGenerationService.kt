package com.example.service

import com.example.model.DocBlock
import com.example.model.ScannedDocument
import com.example.model.TextAlignment
import com.example.model.TextRun
import com.example.util.DocumentJsonUtil
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.compress.archivers.zip.ZipFile
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.Deflater

class DocxGenerationService {

    /**
     * Generates a native Microsoft Word (.docx) file from the structured JSON result returned by Gemini AI.
     */
    fun generateDocxFromAiResult(
        aiResponseJson: String,
        outputFile: File,
        customTitle: String? = null,
        imageUri: String? = null
    ): ScannedDocument {
        val documentId = UUID.randomUUID().toString()
        var document = DocumentJsonUtil.parseGeminiResponse(
            jsonString = aiResponseJson,
            documentId = documentId,
            imageUri = imageUri
        )

        if (!customTitle.isNullOrBlank()) {
            document = document.copy(title = customTitle)
        }

        generateDocxFromDocument(document, outputFile)

        return document.copy(
            docxFilePath = outputFile.absolutePath,
            fileSizeFormatted = formatFileSize(outputFile.length())
        )
    }

    /**
     * Generates a native Microsoft Word (.docx) file from raw processed text (supports markdown headings, tables, bullets).
     */
    fun generateDocxFromProcessedText(
        rawText: String,
        title: String,
        documentType: String = "Document",
        outputFile: File
    ): ScannedDocument {
        val blocks = parseTextToDocBlocks(rawText)
        val document = ScannedDocument(
            id = UUID.randomUUID().toString(),
            title = title,
            documentType = documentType,
            blocks = blocks
        )

        generateDocxFromDocument(document, outputFile)

        return document.copy(
            docxFilePath = outputFile.absolutePath,
            fileSizeFormatted = formatFileSize(outputFile.length())
        )
    }

    /**
     * Core OpenXML engine using Apache Commons Compress ZipArchiveOutputStream
     */
    fun generateDocxFromDocument(document: ScannedDocument, outputFile: File): File {
        outputFile.parentFile?.mkdirs()

        FileOutputStream(outputFile).use { fos ->
            ZipArchiveOutputStream(fos).use { zos ->
                zos.setLevel(Deflater.BEST_COMPRESSION)
                zos.encoding = "UTF-8"

                // 1. [Content_Types].xml
                addZipEntry(zos, "[Content_Types].xml", buildContentTypesXml())

                // 2. _rels/.rels
                addZipEntry(zos, "_rels/.rels", buildPackageRelsXml())

                // 3. word/_rels/document.xml.rels
                addZipEntry(zos, "word/_rels/document.xml.rels", buildDocumentRelsXml())

                // 4. word/styles.xml
                addZipEntry(zos, "word/styles.xml", buildStylesXml())

                // 5. word/settings.xml
                addZipEntry(zos, "word/settings.xml", buildSettingsXml())

                // 6. word/fontTable.xml
                addZipEntry(zos, "word/fontTable.xml", buildFontTableXml())

                // 7. docProps/core.xml (Standard metadata)
                addZipEntry(zos, "docProps/core.xml", buildCorePropertiesXml(document))

                // 8. docProps/app.xml (Application metadata)
                addZipEntry(zos, "docProps/app.xml", buildAppPropertiesXml(document))

                // 9. word/document.xml (Main document layout and formatted content)
                addZipEntry(zos, "word/document.xml", buildDocumentXml(document))

                zos.finish()
            }
        }

        return outputFile
    }

    /**
     * Validates that the generated file is a valid OpenXML package using Apache Commons Compress.
     */
    fun validateDocxArchive(file: File): Boolean {
        if (!file.exists() || file.length() == 0L) return false
        return try {
            ZipFile(file).use { zip ->
                val hasContentTypes = zip.getEntry("[Content_Types].xml") != null
                val hasRels = zip.getEntry("_rels/.rels") != null
                val hasDocument = zip.getEntry("word/document.xml") != null
                val hasStyles = zip.getEntry("word/styles.xml") != null
                hasContentTypes && hasRels && hasDocument && hasStyles
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun addZipEntry(zos: ZipArchiveOutputStream, entryPath: String, content: String) {
        val entry = ZipArchiveEntry(entryPath)
        val bytes = content.toByteArray(Charsets.UTF_8)
        entry.size = bytes.size.toLong()
        zos.putArchiveEntry(entry)
        zos.write(bytes, 0, bytes.size)
        zos.closeArchiveEntry()
    }

    private fun escapeXml(str: String?): String {
        if (str.isNullOrEmpty()) return ""
        val sb = StringBuilder(str.length + 16)
        for (c in str) {
            when (c) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&apos;")
                else -> {
                    if (c.code in 0x20..0xD7FF || c == '\t' || c == '\n' || c == '\r' || c.code in 0xE000..0xFFFD) {
                        sb.append(c)
                    }
                }
            }
        }
        return sb.toString()
    }

    private fun cleanHexColor(hex: String?, defaultColor: String? = null): String? {
        if (hex.isNullOrBlank()) return defaultColor
        val cleaned = hex.trim().removePrefix("#")
        return if (cleaned.length == 6 && cleaned.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) {
            cleaned.uppercase()
        } else {
            defaultColor
        }
    }

    private fun buildContentTypesXml(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
  <Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
  <Override PartName="/word/settings.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.settings+xml"/>
  <Override PartName="/word/fontTable.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.fontTable+xml"/>
  <Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>
  <Override PartName="/docProps/app.xml" ContentType="application/vnd.openxmlformats-officedocument.extended-properties+xml"/>
</Types>""".trimIndent()

    private fun buildPackageRelsXml(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/>
  <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties" Target="docProps/app.xml"/>
</Relationships>""".trimIndent()

    private fun buildDocumentRelsXml(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/settings" Target="settings.xml"/>
  <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/fontTable" Target="fontTable.xml"/>
</Relationships>""".trimIndent()

    private fun buildFontTableXml(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:fonts xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:font w:name="Calibri"><w:panose1 w:val="020F0502020204030204"/><w:family w:val="swiss"/><w:pitch w:val="variable"/></w:font>
  <w:font w:name="Arial"><w:panose1 w:val="020B0604020202020204"/><w:family w:val="swiss"/><w:pitch w:val="variable"/></w:font>
</w:fonts>""".trimIndent()

    private fun buildSettingsXml(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:settings xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:defaultTabStop w:val="720"/>
  <w:characterSpacingControl w:val="doNotCompress"/>
</w:settings>""".trimIndent()

    private fun buildCorePropertiesXml(document: ScannedDocument): String {
        val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date(document.createdAt))
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:dcterms="http://purl.org/dc/terms/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
  <dc:title>${escapeXml(document.title)}</dc:title>
  <dc:creator>ScanToWord AI</dc:creator>
  <cp:lastModifiedBy>ScanToWord AI</cp:lastModifiedBy>
  <dcterms:created xsi:type="dcterms:W3CDTF">$now</dcterms:created>
  <dcterms:modified xsi:type="dcterms:W3CDTF">$now</dcterms:modified>
</cp:coreProperties>""".trimIndent()
    }

    private fun buildAppPropertiesXml(document: ScannedDocument): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Properties xmlns="http://schemas.openxmlformats.org/officeDocument/2006/extended-properties">
  <Template>Normal.dotm</Template>
  <TotalTime>1</TotalTime>
  <Pages>1</Pages>
  <Words>${document.blocks.size * 25}</Words>
  <Application>ScanToWord AI Docx Engine</Application>
  <DocSecurity>0</DocSecurity>
</Properties>""".trimIndent()

    private fun buildStylesXml(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:docDefaults>
    <w:rPrDefault>
      <w:rPr>
        <w:rFonts w:ascii="Calibri" w:hAnsi="Calibri" w:cs="Calibri"/>
        <w:sz w:val="22"/>
        <w:szCs w:val="22"/>
        <w:lang w:val="en-US"/>
      </w:rPr>
    </w:rPrDefault>
    <w:pPrDefault>
      <w:pPr>
        <w:spacing w:after="140" w:line="260" w:lineRule="auto"/>
      </w:pPr>
    </w:pPrDefault>
  </w:docDefaults>

  <w:style w:type="paragraph" w:default="1" w:styleId="Normal">
    <w:name w:val="Normal"/>
    <w:qFormat/>
  </w:style>

  <w:style w:type="paragraph" w:styleId="Title">
    <w:name w:val="Title"/>
    <w:basedOn w:val="Normal"/>
    <w:qFormat/>
    <w:pPr>
      <w:spacing w:before="240" w:after="160"/>
      <w:jc w:val="center"/>
    </w:pPr>
    <w:rPr>
      <w:b/>
      <w:color w:val="1E3A8A"/>
      <w:sz w:val="48"/>
      <w:szCs w:val="48"/>
    </w:rPr>
  </w:style>

  <w:style w:type="paragraph" w:styleId="Heading1">
    <w:name w:val="heading 1"/>
    <w:basedOn w:val="Normal"/>
    <w:next w:val="Normal"/>
    <w:qFormat/>
    <w:pPr>
      <w:spacing w:before="280" w:after="120"/>
    </w:pPr>
    <w:rPr>
      <w:b/>
      <w:color w:val="1E3A8A"/>
      <w:sz w:val="34"/>
      <w:szCs w:val="34"/>
    </w:rPr>
  </w:style>

  <w:style w:type="paragraph" w:styleId="Heading2">
    <w:name w:val="heading 2"/>
    <w:basedOn w:val="Normal"/>
    <w:next w:val="Normal"/>
    <w:qFormat/>
    <w:pPr>
      <w:spacing w:before="200" w:after="100"/>
    </w:pPr>
    <w:rPr>
      <w:b/>
      <w:color w:val="1E40AF"/>
      <w:sz w:val="28"/>
      <w:szCs w:val="28"/>
    </w:rPr>
  </w:style>

  <w:style w:type="paragraph" w:styleId="Heading3">
    <w:name w:val="heading 3"/>
    <w:basedOn w:val="Normal"/>
    <w:next w:val="Normal"/>
    <w:qFormat/>
    <w:pPr>
      <w:spacing w:before="160" w:after="80"/>
    </w:pPr>
    <w:rPr>
      <w:b/>
      <w:color w:val="334155"/>
      <w:sz w:val="24"/>
      <w:szCs w:val="24"/>
    </w:rPr>
  </w:style>

  <w:style w:type="table" w:default="1" w:styleId="TableGrid">
    <w:name w:val="Table Grid"/>
    <w:tblPr>
      <w:tblBorders>
        <w:top w:val="single" w:sz="6" w:space="0" w:color="CBD5E1"/>
        <w:left w:val="single" w:sz="6" w:space="0" w:color="CBD5E1"/>
        <w:bottom w:val="single" w:sz="6" w:space="0" w:color="CBD5E1"/>
        <w:right w:val="single" w:sz="6" w:space="0" w:color="CBD5E1"/>
        <w:insideH w:val="single" w:sz="4" w:space="0" w:color="E2E8F0"/>
        <w:insideV w:val="single" w:sz="4" w:space="0" w:color="E2E8F0"/>
      </w:tblBorders>
    </w:tblPr>
  </w:style>
</w:styles>""".trimIndent()

    private fun buildDocumentXml(document: ScannedDocument): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">""")
        sb.append("<w:body>")

        // Document Title
        if (document.title.isNotBlank()) {
            sb.append("<w:p>")
            sb.append("<w:pPr><w:pStyle w:val=\"Title\"/></w:pPr>")
            sb.append("<w:r><w:t>${escapeXml(document.title)}</w:t></w:r>")
            sb.append("</w:p>")
        }

        // Subtitle / Document Type Tag
        if (document.documentType.isNotBlank() && document.documentType != "Document") {
            sb.append("<w:p>")
            sb.append("<w:pPr><w:jc w:val=\"center\"/><w:spacing w:after=\"280\"/></w:pPr>")
            sb.append("<w:r>")
            sb.append("<w:rPr><w:color w:val=\"64748B\"/><w:sz w:val=\"20\"/><w:i/></w:rPr>")
            sb.append("<w:t>[ ${escapeXml(document.documentType)} ]</w:t>")
            sb.append("</w:r>")
            sb.append("</w:p>")
        }

        // Render each block
        for (block in document.blocks) {
            when (block) {
                is DocBlock.Heading -> renderHeading(sb, block)
                is DocBlock.Paragraph -> renderParagraph(sb, block)
                is DocBlock.Table -> renderTable(sb, block)
                is DocBlock.ListBlock -> renderList(sb, block)
                is DocBlock.KeyValueGrid -> renderKeyValueGrid(sb, block)
                is DocBlock.Callout -> renderCallout(sb, block)
                DocBlock.Divider -> renderDivider(sb)
            }
        }

        // Page Setup: Standard Letter, 1-inch margins
        sb.append("""<w:sectPr>
  <w:pgSz w:w="12240" w:h="15840"/>
  <w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440" w:header="720" w:footer="720" w:gutter="0"/>
</w:sectPr>""")

        sb.append("</w:body>")
        sb.append("</w:document>")

        return sb.toString()
    }

    private fun renderHeading(sb: StringBuilder, heading: DocBlock.Heading) {
        val styleId = when (heading.level) {
            1 -> "Heading1"
            2 -> "Heading2"
            else -> "Heading3"
        }
        val jc = when (heading.alignment) {
            TextAlignment.CENTER -> "center"
            TextAlignment.RIGHT -> "right"
            TextAlignment.JUSTIFY -> "both"
            else -> "left"
        }

        sb.append("<w:p>")
        sb.append("<w:pPr>")
        sb.append("<w:pStyle w:val=\"$styleId\"/>")
        if (jc != "left") sb.append("<w:jc w:val=\"$jc\"/>")
        sb.append("</w:pPr>")

        sb.append("<w:r>")
        val color = cleanHexColor(heading.colorHex)
        if (color != null) {
            sb.append("<w:rPr><w:color w:val=\"$color\"/></w:rPr>")
        }
        sb.append("<w:t>${escapeXml(heading.text)}</w:t>")
        sb.append("</w:r>")
        sb.append("</w:p>")
    }

    private fun renderParagraph(sb: StringBuilder, para: DocBlock.Paragraph) {
        sb.append("<w:p>")
        sb.append("<w:pPr>")

        val jc = when (para.alignment) {
            TextAlignment.CENTER -> "center"
            TextAlignment.RIGHT -> "right"
            TextAlignment.JUSTIFY -> "both"
            else -> "left"
        }
        if (jc != "left") {
            sb.append("<w:jc w:val=\"$jc\"/>")
        }

        if (para.indentationLevel > 0) {
            val indentDxa = para.indentationLevel * 480
            sb.append("<w:ind w:left=\"$indentDxa\"/>")
        }

        if (para.isQuoteOrCallout) {
            sb.append("<w:pBdr><w:left w:val=\"single\" w:sz=\"24\" w:space=\"12\" w:color=\"1E3A8A\"/></w:pBdr>")
            sb.append("<w:ind w:left=\"360\"/>")
            sb.append("<w:spacing w:before=\"120\" w:after=\"120\"/>")
        }

        sb.append("</w:pPr>")

        if (para.runs.isEmpty()) {
            sb.append("<w:r><w:t></w:t></w:r>")
        } else {
            for (run in para.runs) {
                renderRun(sb, run)
            }
        }

        sb.append("</w:p>")
    }

    private fun renderRun(sb: StringBuilder, run: TextRun) {
        sb.append("<w:r>")

        val hasProps = run.isBold || run.isItalic || run.isUnderline || run.isHighlight || run.colorHex != null || run.fontSizePt != null
        if (hasProps) {
            sb.append("<w:rPr>")
            if (run.isBold) sb.append("<w:b/>")
            if (run.isItalic) sb.append("<w:i/>")
            if (run.isUnderline) sb.append("<w:u w:val=\"single\"/>")
            if (run.isHighlight) sb.append("<w:highlight w:val=\"yellow\"/>")
            val color = cleanHexColor(run.colorHex)
            if (color != null) sb.append("<w:color w:val=\"$color\"/>")
            if (run.fontSizePt != null && run.fontSizePt > 0) {
                val halfPt = run.fontSizePt * 2
                sb.append("<w:sz w:val=\"$halfPt\"/>")
            }
            sb.append("</w:rPr>")
        }

        sb.append("<w:t xml:space=\"preserve\">${escapeXml(run.text)}</w:t>")
        sb.append("</w:r>")
    }

    private fun renderList(sb: StringBuilder, list: DocBlock.ListBlock) {
        for (i in list.items.indices) {
            val item = list.items[i]
            val prefix = if (list.isOrdered) "${i + 1}.  " else "•   "

            sb.append("<w:p>")
            sb.append("<w:pPr>")
            sb.append("<w:ind w:left=\"720\" w:hanging=\"360\"/>")
            sb.append("<w:spacing w:after=\"80\"/>")
            sb.append("</w:pPr>")

            sb.append("<w:r>")
            sb.append("<w:rPr><w:b/><w:color w:val=\"1E3A8A\"/></w:rPr>")
            sb.append("<w:t xml:space=\"preserve\">${escapeXml(prefix)}</w:t>")
            sb.append("</w:r>")

            sb.append("<w:r>")
            sb.append("<w:t>${escapeXml(item)}</w:t>")
            sb.append("</w:r>")
            sb.append("</w:p>")
        }
    }

    private fun renderTable(sb: StringBuilder, table: DocBlock.Table) {
        if (table.headers.isEmpty() && table.rows.isEmpty()) return

        val colCount = maxOf(
            table.headers.size,
            table.rows.maxOfOrNull { it.size } ?: 1
        )
        val tableWidthDxa = 9360
        val colWidthDxa = tableWidthDxa / colCount

        sb.append("<w:tbl>")
        sb.append("<w:tblPr>")
        sb.append("<w:tblStyle w:val=\"TableGrid\"/>")
        sb.append("<w:tblW w:w=\"$tableWidthDxa\" w:type=\"dxa\"/>")
        sb.append("<w:jc w:val=\"center\"/>")
        sb.append("<w:tblBorders>")
        sb.append("<w:top w:val=\"single\" w:sz=\"6\" w:space=\"0\" w:color=\"CBD5E1\"/>")
        sb.append("<w:left w:val=\"single\" w:sz=\"6\" w:space=\"0\" w:color=\"CBD5E1\"/>")
        sb.append("<w:bottom w:val=\"single\" w:sz=\"8\" w:space=\"0\" w:color=\"94A3B8\"/>")
        sb.append("<w:right w:val=\"single\" w:sz=\"6\" w:space=\"0\" w:color=\"CBD5E1\"/>")
        sb.append("<w:insideH w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"E2E8F0\"/>")
        sb.append("<w:insideV w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"E2E8F0\"/>")
        sb.append("</w:tblBorders>")
        sb.append("</w:tblPr>")

        // Header Row
        if (table.headers.isNotEmpty() && table.hasHeaderRow) {
            sb.append("<w:tr>")
            sb.append("<w:trPr><w:tblHeader/></w:trPr>")
            for (header in table.headers) {
                sb.append("<w:tc>")
                sb.append("<w:tcPr>")
                sb.append("<w:tcW w:w=\"$colWidthDxa\" w:type=\"dxa\"/>")
                sb.append("<w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"1E3A8A\"/>")
                sb.append("<w:tcMar><w:top w:w=\"120\" w:type=\"dxa\"/><w:bottom w:w=\"120\" w:type=\"dxa\"/><w:left w:w=\"140\" w:type=\"dxa\"/><w:right w:w=\"140\" w:type=\"dxa\"/></w:tcMar>")
                sb.append("</w:tcPr>")
                sb.append("<w:p>")
                sb.append("<w:pPr><w:spacing w:after=\"0\"/></w:pPr>")
                sb.append("<w:r>")
                sb.append("<w:rPr><w:b/><w:color w:val=\"FFFFFF\"/></w:rPr>")
                sb.append("<w:t>${escapeXml(header)}</w:t>")
                sb.append("</w:r>")
                sb.append("</w:p>")
                sb.append("</w:tc>")
            }
            sb.append("</w:tr>")
        }

        // Data Rows
        for (rowIndex in table.rows.indices) {
            val row = table.rows[rowIndex]
            val isEven = rowIndex % 2 == 0
            val fillColor = if (isEven) "F8FAFC" else "FFFFFF"

            sb.append("<w:tr>")
            for (colIndex in 0 until colCount) {
                val cellText = row.getOrNull(colIndex) ?: ""
                sb.append("<w:tc>")
                sb.append("<w:tcPr>")
                sb.append("<w:tcW w:w=\"$colWidthDxa\" w:type=\"dxa\"/>")
                sb.append("<w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"$fillColor\"/>")
                sb.append("<w:tcMar><w:top w:w=\"100\" w:type=\"dxa\"/><w:bottom w:w=\"100\" w:type=\"dxa\"/><w:left w:w=\"140\" w:type=\"dxa\"/><w:right w:w=\"140\" w:type=\"dxa\"/></w:tcMar>")
                sb.append("</w:tcPr>")
                sb.append("<w:p>")
                sb.append("<w:pPr><w:spacing w:after=\"0\"/></w:pPr>")
                sb.append("<w:r>")
                sb.append("<w:rPr><w:color w:val=\"1E293B\"/></w:rPr>")
                sb.append("<w:t>${escapeXml(cellText)}</w:t>")
                sb.append("</w:r>")
                sb.append("</w:p>")
                sb.append("</w:tc>")
            }
            sb.append("</w:tr>")
        }

        sb.append("</w:tbl>")
        sb.append("<w:p><w:pPr><w:spacing w:after=\"160\"/></w:pPr></w:p>")
    }

    private fun renderKeyValueGrid(sb: StringBuilder, grid: DocBlock.KeyValueGrid) {
        if (grid.entries.isEmpty()) return

        val tableWidthDxa = 9360
        val keyWidthDxa = 3200
        val valueWidthDxa = tableWidthDxa - keyWidthDxa

        sb.append("<w:tbl>")
        sb.append("<w:tblPr>")
        sb.append("<w:tblW w:w=\"$tableWidthDxa\" w:type=\"dxa\"/>")
        sb.append("<w:tblBorders>")
        sb.append("<w:top w:val=\"none\"/><w:left w:val=\"none\"/><w:bottom w:val=\"none\"/><w:right w:val=\"none\"/>")
        sb.append("<w:insideH w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"F1F5F9\"/>")
        sb.append("<w:insideV w:val=\"none\"/>")
        sb.append("</w:tblBorders>")
        sb.append("</w:tblPr>")

        for (entry in grid.entries) {
            sb.append("<w:tr>")

            // Key Cell
            sb.append("<w:tc>")
            sb.append("<w:tcPr><w:tcW w:w=\"$keyWidthDxa\" w:type=\"dxa\"/><w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"F8FAFC\"/></w:tcPr>")
            sb.append("<w:p><w:pPr><w:spacing w:after=\"60\" w:before=\"60\"/></w:pPr>")
            sb.append("<w:r><w:rPr><w:b/><w:color w:val=\"334155\"/></w:rPr>")
            sb.append("<w:t>${escapeXml(entry.first)}</w:t></w:r></w:p>")
            sb.append("</w:tc>")

            // Value Cell
            sb.append("<w:tc>")
            sb.append("<w:tcPr><w:tcW w:w=\"$valueWidthDxa\" w:type=\"dxa\"/></w:tcPr>")
            sb.append("<w:p><w:pPr><w:spacing w:after=\"60\" w:before=\"60\"/></w:pPr>")
            sb.append("<w:r><w:rPr><w:color w:val=\"0F172A\"/></w:rPr>")
            sb.append("<w:t>${escapeXml(entry.second)}</w:t></w:r></w:p>")
            sb.append("</w:tc>")

            sb.append("</w:tr>")
        }

        sb.append("</w:tbl>")
        sb.append("<w:p><w:pPr><w:spacing w:after=\"140\"/></w:pPr></w:p>")
    }

    private fun renderCallout(sb: StringBuilder, callout: DocBlock.Callout) {
        sb.append("<w:p>")
        sb.append("<w:pPr>")
        sb.append("<w:pBdr><w:left w:val=\"single\" w:sz=\"24\" w:space=\"12\" w:color=\"2563EB\"/></w:pBdr>")
        sb.append("<w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"EFF6FF\"/>")
        sb.append("<w:ind w:left=\"360\" w:right=\"200\"/>")
        sb.append("<w:spacing w:before=\"140\" w:after=\"140\"/>")
        sb.append("</w:pPr>")

        if (!callout.title.isNullOrBlank()) {
            sb.append("<w:r>")
            sb.append("<w:rPr><w:b/><w:color w:val=\"1E40AF\"/></w:rPr>")
            sb.append("<w:t>${escapeXml(callout.title)}</w:t>")
            sb.append("</w:r>")
            sb.append("<w:r><w:br/></w:r>")
        }

        sb.append("<w:r>")
        sb.append("<w:rPr><w:color w:val=\"1E293B\"/></w:rPr>")
        sb.append("<w:t>${escapeXml(callout.text)}</w:t>")
        sb.append("</w:r>")
        sb.append("</w:p>")
    }

    private fun renderDivider(sb: StringBuilder) {
        sb.append("<w:p>")
        sb.append("<w:pPr>")
        sb.append("<w:pBdr><w:bottom w:val=\"single\" w:sz=\"8\" w:space=\"1\" w:color=\"CBD5E1\"/></w:pBdr>")
        sb.append("<w:spacing w:before=\"120\" w:after=\"120\"/>")
        sb.append("</w:pPr>")
        sb.append("<w:r><w:t></w:t></w:r>")
        sb.append("</w:p>")
    }

    private fun parseTextToDocBlocks(text: String): List<DocBlock> {
        val blocks = mutableListOf<DocBlock>()
        val lines = text.lines()
        var i = 0

        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.isEmpty()) {
                i++
                continue
            }

            when {
                line.startsWith("### ") -> {
                    blocks.add(DocBlock.Heading(level = 3, text = line.removePrefix("### ").trim()))
                    i++
                }
                line.startsWith("## ") -> {
                    blocks.add(DocBlock.Heading(level = 2, text = line.removePrefix("## ").trim()))
                    i++
                }
                line.startsWith("# ") -> {
                    blocks.add(DocBlock.Heading(level = 1, text = line.removePrefix("# ").trim()))
                    i++
                }
                line.startsWith("---") || line.startsWith("***") -> {
                    blocks.add(DocBlock.Divider)
                    i++
                }
                line.startsWith("> ") -> {
                    blocks.add(DocBlock.Callout(title = null, text = line.removePrefix("> ").trim()))
                    i++
                }
                line.startsWith("- ") || line.startsWith("* ") || line.startsWith("• ") -> {
                    val listItems = mutableListOf<String>()
                    while (i < lines.size && (lines[i].trim().startsWith("- ") || lines[i].trim().startsWith("* ") || lines[i].trim().startsWith("• "))) {
                        val itm = lines[i].trim().removePrefix("- ").removePrefix("* ").removePrefix("• ").trim()
                        if (itm.isNotEmpty()) listItems.add(itm)
                        i++
                    }
                    blocks.add(DocBlock.ListBlock(isOrdered = false, items = listItems))
                }
                line.matches(Regex("^\\d+\\.\\s+.*")) -> {
                    val listItems = mutableListOf<String>()
                    while (i < lines.size && lines[i].trim().matches(Regex("^\\d+\\.\\s+.*"))) {
                        val itm = lines[i].trim().replaceFirst(Regex("^\\d+\\.\\s+"), "").trim()
                        if (itm.isNotEmpty()) listItems.add(itm)
                        i++
                    }
                    blocks.add(DocBlock.ListBlock(isOrdered = true, items = listItems))
                }
                line.startsWith("|") && line.endsWith("|") -> {
                    val tableLines = mutableListOf<String>()
                    while (i < lines.size && lines[i].trim().startsWith("|") && lines[i].trim().endsWith("|")) {
                        tableLines.add(lines[i].trim())
                        i++
                    }
                    val tableBlock = parseMarkdownTable(tableLines)
                    if (tableBlock != null) blocks.add(tableBlock)
                }
                else -> {
                    val runs = parseInlineFormatting(line)
                    blocks.add(DocBlock.Paragraph(runs = runs))
                    i++
                }
            }
        }

        return blocks
    }

    private fun parseMarkdownTable(lines: List<String>): DocBlock.Table? {
        if (lines.isEmpty()) return null
        val rows = lines.map { l ->
            l.trim().removeSurrounding("|", "|").split("|").map { it.trim() }
        }.filter { row ->
            // Skip markdown divider row like |---|---|
            !row.all { it.matches(Regex("^-+:?$")) || it.matches(Regex("^:?-+:?$")) }
        }

        if (rows.isEmpty()) return null
        val headers = rows.first()
        val dataRows = if (rows.size > 1) rows.drop(1) else emptyList()

        return DocBlock.Table(
            headers = headers,
            rows = dataRows,
            hasHeaderRow = true,
            hasBorders = true
        )
    }

    private fun parseInlineFormatting(text: String): List<TextRun> {
        val runs = mutableListOf<TextRun>()
        val boldRegex = Regex("\\*\\*(.*?)\\*\\*")
        var lastIndex = 0

        for (match in boldRegex.findAll(text)) {
            val before = text.substring(lastIndex, match.range.first)
            if (before.isNotEmpty()) {
                runs.add(TextRun(text = before))
            }
            val boldContent = match.groupValues[1]
            if (boldContent.isNotEmpty()) {
                runs.add(TextRun(text = boldContent, isBold = true))
            }
            lastIndex = match.range.last + 1
        }

        if (lastIndex < text.length) {
            runs.add(TextRun(text = text.substring(lastIndex)))
        }

        return if (runs.isEmpty()) listOf(TextRun(text = text)) else runs
    }

    private fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 KB"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        return if (mb >= 1.0) {
            String.format(Locale.US, "%.1f MB", mb)
        } else {
            String.format(Locale.US, "%.0f KB", kb.coerceAtLeast(1.0))
        }
    }
}
