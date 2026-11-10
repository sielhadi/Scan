package com.example.util

import com.example.model.DocBlock
import com.example.model.ScannedDocument
import com.example.model.TextAlignment
import com.example.model.TextRun
import org.json.JSONArray
import org.json.JSONObject

object DocumentJsonUtil {

    fun parseGeminiResponse(jsonString: String, documentId: String, imageUri: String?): ScannedDocument {
        val root = JSONObject(jsonString)
        val title = root.optString("title", "Scanned Document").trim().ifEmpty { "Scanned Document" }
        val docType = root.optString("documentType", "Document").trim().ifEmpty { "Document" }
        val confidence = root.optDouble("confidenceScore", 0.95).toFloat()

        val blocksList = mutableListOf<DocBlock>()
        val blocksArray = root.optJSONArray("blocks") ?: JSONArray()

        for (i in 0 until blocksArray.length()) {
            val obj = blocksArray.optJSONObject(i) ?: continue
            val block = parseBlockObject(obj)
            if (block != null) {
                blocksList.add(block)
            }
        }

        return ScannedDocument(
            id = documentId,
            title = title,
            documentType = docType,
            confidenceScore = confidence,
            originalImageUri = imageUri,
            blocks = blocksList
        )
    }

    private fun parseBlockObject(obj: JSONObject): DocBlock? {
        val type = obj.optString("type", "").trim().uppercase()
        val text = obj.optString("text", "")
        val alignment = TextAlignment.fromString(obj.optString("alignment", "LEFT"))

        return when {
            type.contains("HEADING") || type.contains("TITLE") -> {
                val level = obj.optInt("headingLevel", 1).coerceIn(1, 3)
                val headingText = if (text.isNotBlank()) text else obj.optString("headingText", "Heading")
                DocBlock.Heading(
                    level = level,
                    text = headingText,
                    alignment = alignment,
                    isBold = obj.optBoolean("bold", true),
                    colorHex = obj.optString("colorHex").takeIf { it.isNotBlank() }
                )
            }
            type.contains("TABLE") -> {
                val headersList = mutableListOf<String>()
                val headersArr = obj.optJSONArray("tableHeaders")
                if (headersArr != null) {
                    for (h in 0 until headersArr.length()) {
                        headersList.add(headersArr.optString(h, ""))
                    }
                }

                val rowsList = mutableListOf<List<String>>()
                val rowsArr = obj.optJSONArray("tableRows")
                if (rowsArr != null) {
                    for (r in 0 until rowsArr.length()) {
                        val rowArr = rowsArr.optJSONArray(r)
                        if (rowArr != null) {
                            val rowCells = mutableListOf<String>()
                            for (c in 0 until rowArr.length()) {
                                rowCells.add(rowArr.optString(c, ""))
                            }
                            rowsList.add(rowCells)
                        }
                    }
                }

                if (headersList.isNotEmpty() || rowsList.isNotEmpty()) {
                    DocBlock.Table(
                        headers = headersList,
                        rows = rowsList,
                        hasHeaderRow = headersList.isNotEmpty(),
                        hasBorders = true
                    )
                } else null
            }
            type.contains("LIST") -> {
                val listType = obj.optString("listType", "BULLET").uppercase()
                val isOrdered = listType.contains("NUMBER") || listType.contains("ORDER")
                val itemsList = mutableListOf<String>()
                val itemsArr = obj.optJSONArray("listItems")
                if (itemsArr != null) {
                    for (itemIndex in 0 until itemsArr.length()) {
                        val itm = itemsArr.optString(itemIndex, "")
                        if (itm.isNotBlank()) itemsList.add(itm)
                    }
                }
                if (itemsList.isEmpty() && text.isNotBlank()) {
                    itemsList.addAll(text.split("\n").filter { it.isNotBlank() })
                }
                if (itemsList.isNotEmpty()) {
                    DocBlock.ListBlock(isOrdered = isOrdered, items = itemsList)
                } else null
            }
            type.contains("KEY_VALUE") || type.contains("GRID") -> {
                val kvPairs = mutableListOf<Pair<String, String>>()
                val kvArr = obj.optJSONArray("keyValuePairs")
                if (kvArr != null) {
                    for (k in 0 until kvArr.length()) {
                        val kvObj = kvArr.optJSONObject(k) ?: continue
                        val kKey = kvObj.optString("key", "").trim()
                        val kVal = kvObj.optString("value", "").trim()
                        if (kKey.isNotBlank() || kVal.isNotBlank()) {
                            kvPairs.add(kKey to kVal)
                        }
                    }
                }
                if (kvPairs.isNotEmpty()) {
                    DocBlock.KeyValueGrid(entries = kvPairs)
                } else null
            }
            type.contains("CALLOUT") || type.contains("QUOTE") -> {
                DocBlock.Callout(
                    title = obj.optString("calloutTitle").takeIf { it.isNotBlank() },
                    text = text.ifEmpty { "Important Note" }
                )
            }
            type.contains("DIVIDER") -> DocBlock.Divider
            else -> {
                // PARAGRAPH
                val runsList = mutableListOf<TextRun>()
                val runsArr = obj.optJSONArray("runs")
                if (runsArr != null && runsArr.length() > 0) {
                    for (r in 0 until runsArr.length()) {
                        val runObj = runsArr.optJSONObject(r) ?: continue
                        val runText = runObj.optString("text", "")
                        if (runText.isNotEmpty()) {
                            runsList.add(
                                TextRun(
                                    text = runText,
                                    isBold = runObj.optBoolean("bold", false),
                                    isItalic = runObj.optBoolean("italic", false),
                                    isUnderline = runObj.optBoolean("underline", false),
                                    isHighlight = runObj.optBoolean("highlight", false),
                                    colorHex = runObj.optString("colorHex").takeIf { it.isNotBlank() }
                                )
                            )
                        }
                    }
                }

                if (runsList.isEmpty() && text.isNotBlank()) {
                    runsList.add(TextRun(text = text))
                }

                if (runsList.isNotEmpty()) {
                    DocBlock.Paragraph(
                        runs = runsList,
                        alignment = alignment,
                        indentationLevel = obj.optInt("indentationLevel", 0),
                        isQuoteOrCallout = obj.optBoolean("isQuote", false)
                    )
                } else null
            }
        }
    }

    fun blocksToJson(blocks: List<DocBlock>): String {
        val array = JSONArray()
        for (block in blocks) {
            val obj = JSONObject()
            when (block) {
                is DocBlock.Heading -> {
                    obj.put("type", "HEADING")
                    obj.put("headingLevel", block.level)
                    obj.put("text", block.text)
                    obj.put("alignment", block.alignment.name)
                    obj.put("bold", block.isBold)
                    if (block.colorHex != null) obj.put("colorHex", block.colorHex)
                }
                is DocBlock.Paragraph -> {
                    obj.put("type", "PARAGRAPH")
                    obj.put("alignment", block.alignment.name)
                    obj.put("indentationLevel", block.indentationLevel)
                    obj.put("isQuote", block.isQuoteOrCallout)
                    val runsArr = JSONArray()
                    for (run in block.runs) {
                        val rObj = JSONObject()
                        rObj.put("text", run.text)
                        if (run.isBold) rObj.put("bold", true)
                        if (run.isItalic) rObj.put("italic", true)
                        if (run.isUnderline) rObj.put("underline", true)
                        if (run.isHighlight) rObj.put("highlight", true)
                        if (run.colorHex != null) rObj.put("colorHex", run.colorHex)
                        runsArr.put(rObj)
                    }
                    obj.put("runs", runsArr)
                }
                is DocBlock.Table -> {
                    obj.put("type", "TABLE")
                    val headersArr = JSONArray()
                    block.headers.forEach { headersArr.put(it) }
                    obj.put("tableHeaders", headersArr)

                    val rowsArr = JSONArray()
                    for (row in block.rows) {
                        val rArr = JSONArray()
                        row.forEach { rArr.put(it) }
                        rowsArr.put(rArr)
                    }
                    obj.put("tableRows", rowsArr)
                }
                is DocBlock.ListBlock -> {
                    obj.put("type", "LIST")
                    obj.put("listType", if (block.isOrdered) "NUMBERED" else "BULLET")
                    val itemsArr = JSONArray()
                    block.items.forEach { itemsArr.put(it) }
                    obj.put("listItems", itemsArr)
                }
                is DocBlock.KeyValueGrid -> {
                    obj.put("type", "KEY_VALUE")
                    val kvArr = JSONArray()
                    for (pair in block.entries) {
                        val pObj = JSONObject()
                        pObj.put("key", pair.first)
                        pObj.put("value", pair.second)
                        kvArr.put(pObj)
                    }
                    obj.put("keyValuePairs", kvArr)
                }
                is DocBlock.Callout -> {
                    obj.put("type", "CALLOUT")
                    if (block.title != null) obj.put("calloutTitle", block.title)
                    obj.put("text", block.text)
                }
                DocBlock.Divider -> {
                    obj.put("type", "DIVIDER")
                }
            }
            array.put(obj)
        }
        return array.toString()
    }

    fun blocksFromJson(jsonString: String): List<DocBlock> {
        val list = mutableListOf<DocBlock>()
        try {
            val array = JSONArray(jsonString)
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val block = parseBlockObject(obj)
                if (block != null) list.add(block)
            }
        } catch (_: Exception) {}
        return list
    }

    fun blocksToPlainText(blocks: List<DocBlock>): String {
        val sb = StringBuilder()
        for (block in blocks) {
            when (block) {
                is DocBlock.Heading -> {
                    val prefix = "#".repeat(block.level)
                    sb.append("$prefix ${block.text}\n\n")
                }
                is DocBlock.Paragraph -> {
                    val pText = block.runs.joinToString("") { it.text }
                    sb.append("$pText\n\n")
                }
                is DocBlock.Table -> {
                    if (block.headers.isNotEmpty()) {
                        sb.append(block.headers.joinToString(" | "))
                        sb.append("\n")
                        sb.append(block.headers.joinToString(" | ") { "---" })
                        sb.append("\n")
                    }
                    for (row in block.rows) {
                        sb.append(row.joinToString(" | "))
                        sb.append("\n")
                    }
                    sb.append("\n")
                }
                is DocBlock.ListBlock -> {
                    block.items.forEachIndexed { idx, item ->
                        val marker = if (block.isOrdered) "${idx + 1}." else "-"
                        sb.append("$marker $item\n")
                    }
                    sb.append("\n")
                }
                is DocBlock.KeyValueGrid -> {
                    block.entries.forEach { (key, value) ->
                        sb.append("$key: $value\n")
                    }
                    sb.append("\n")
                }
                is DocBlock.Callout -> {
                    if (!block.title.isNullOrBlank()) {
                        sb.append("> **${block.title}**\n")
                    }
                    sb.append("> ${block.text}\n\n")
                }
                DocBlock.Divider -> {
                    sb.append("---\n\n")
                }
            }
        }
        return sb.toString().trim()
    }
}

