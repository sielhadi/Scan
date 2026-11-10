package com.example.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.File

enum class TextAlignment {
    LEFT, CENTER, RIGHT, JUSTIFY;

    companion object {
        fun fromString(str: String?): TextAlignment {
            return when (str?.trim()?.uppercase()) {
                "CENTER" -> CENTER
                "RIGHT" -> RIGHT
                "JUSTIFY" -> JUSTIFY
                else -> LEFT
            }
        }
    }
}

data class TextRun(
    val text: String,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false,
    val isHighlight: Boolean = false,
    val colorHex: String? = null,
    val fontSizePt: Int? = null
)

sealed class DocBlock {
    data class Heading(
        val level: Int, // 1 = H1, 2 = H2, 3 = H3
        val text: String,
        val alignment: TextAlignment = TextAlignment.LEFT,
        val isBold: Boolean = true,
        val colorHex: String? = null
    ) : DocBlock()

    data class Paragraph(
        val runs: List<TextRun>,
        val alignment: TextAlignment = TextAlignment.LEFT,
        val indentationLevel: Int = 0,
        val isQuoteOrCallout: Boolean = false
    ) : DocBlock() {
        val plainText: String
            get() = runs.joinToString("") { it.text }
    }

    data class Table(
        val headers: List<String>,
        val rows: List<List<String>>,
        val hasHeaderRow: Boolean = true,
        val hasBorders: Boolean = true
    ) : DocBlock()

    data class ListBlock(
        val isOrdered: Boolean, // true = numbered (1, 2, 3), false = bullet
        val items: List<String>
    ) : DocBlock()

    data class KeyValueGrid(
        val entries: List<Pair<String, String>>
    ) : DocBlock()

    data class Callout(
        val title: String?,
        val text: String,
        val borderHighlight: Boolean = true
    ) : DocBlock()

    data object Divider : DocBlock()
}

data class ScannedDocument(
    val id: String,
    val title: String,
    val documentType: String, // e.g. "Invoice", "Formal Report", "Contract", "Letter", "Resume"
    val createdAt: Long = System.currentTimeMillis(),
    val confidenceScore: Float = 0.95f,
    val originalImageUri: String? = null,
    val blocks: List<DocBlock> = emptyList(),
    val docxFilePath: String? = null,
    val fileSizeFormatted: String = ""
) {
    val plainSummaryText: String
        get() {
            val sb = StringBuilder()
            for (block in blocks.take(4)) {
                when (block) {
                    is DocBlock.Heading -> sb.append(block.text).append("\n")
                    is DocBlock.Paragraph -> sb.append(block.plainText).append("\n")
                    is DocBlock.ListBlock -> sb.append(block.items.take(2).joinToString("; ")).append("\n")
                    is DocBlock.Table -> sb.append("Table: ").append(block.headers.joinToString(", ")).append("\n")
                    is DocBlock.KeyValueGrid -> sb.append(block.entries.take(2).joinToString { "${it.first}: ${it.second}" }).append("\n")
                    is DocBlock.Callout -> sb.append(block.text).append("\n")
                    DocBlock.Divider -> {}
                }
            }
            return sb.toString().trim()
        }
}

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey val id: String,
    val title: String,
    val documentType: String,
    val createdAt: Long,
    val imagePath: String?,
    val docxPath: String?,
    val blocksJson: String,
    val fileSizeFormatted: String
)
