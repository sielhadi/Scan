package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.ai.DocumentAiService
import com.example.generator.DocxGenerator
import com.example.model.DocumentEntity
import com.example.model.ScannedDocument
import com.example.util.DocumentJsonUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class DocumentRepository(
    private val context: Context,
    private val documentDao: DocumentDao,
    private val aiService: DocumentAiService = DocumentAiService(),
    private val docxService: com.example.service.DocxGenerationService = com.example.service.DocxGenerationService()
) {

    val allDocuments: Flow<List<ScannedDocument>> = documentDao.getAllDocuments().map { entities ->
        entities.map { entityToScannedDocument(it) }
    }

    suspend fun getDocumentById(id: String): ScannedDocument? = withContext(Dispatchers.IO) {
        val entity = documentDao.getDocumentById(id) ?: return@withContext null
        entityToScannedDocument(entity)
    }

    suspend fun extractLayoutFromImage(
        bitmap: Bitmap,
        customTitle: String? = null,
        presetId: String? = null
    ): ScannedDocument = withContext(Dispatchers.IO) {
        val docId = UUID.randomUUID().toString()

        // 1. Save scanned image file
        val imagesDir = File(context.filesDir, "scanned_images").apply { mkdirs() }
        val imageFile = File(imagesDir, "scan_$docId.jpg")
        FileOutputStream(imageFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }

        // 2. Call Gemini AI service for structured layout analysis
        val jsonResponse = aiService.analyzeDocumentLayout(bitmap, presetId)

        // 3. Parse Gemini response into ScannedDocument (in-memory model for review)
        var document = DocumentJsonUtil.parseGeminiResponse(
            jsonString = jsonResponse,
            documentId = docId,
            imageUri = imageFile.absolutePath
        )

        if (!customTitle.isNullOrBlank()) {
            document = document.copy(title = customTitle)
        }

        document
    }

    suspend fun confirmAndSaveDocx(document: ScannedDocument): ScannedDocument = withContext(Dispatchers.IO) {
        val docxDir = File(context.filesDir, "word_documents").apply { mkdirs() }
        val sanitizedTitle = document.title
            .replace(Regex("[^a-zA-Z0-9_-]"), "_")
            .take(30)
            .ifEmpty { "document" }
        val docxFile = File(docxDir, "${sanitizedTitle}_${document.id}.docx")

        docxService.generateDocxFromDocument(document, docxFile)
        val formattedSize = formatFileSize(docxFile.length())

        val completedDoc = document.copy(
            docxFilePath = docxFile.absolutePath,
            fileSizeFormatted = formattedSize
        )

        val entity = DocumentEntity(
            id = completedDoc.id,
            title = completedDoc.title,
            documentType = completedDoc.documentType,
            createdAt = completedDoc.createdAt,
            imagePath = completedDoc.originalImageUri,
            docxPath = completedDoc.docxFilePath,
            blocksJson = DocumentJsonUtil.blocksToJson(completedDoc.blocks),
            fileSizeFormatted = formattedSize
        )
        documentDao.insertDocument(entity)

        completedDoc
    }

    suspend fun processAndSaveDocument(
        bitmap: Bitmap,
        customTitle: String? = null,
        presetId: String? = null
    ): ScannedDocument = withContext(Dispatchers.IO) {
        val extracted = extractLayoutFromImage(bitmap, customTitle, presetId)
        confirmAndSaveDocx(extracted)
    }

    suspend fun updateDocument(document: ScannedDocument): ScannedDocument = withContext(Dispatchers.IO) {
        val docxDir = File(context.filesDir, "word_documents").apply { mkdirs() }
        val sanitizedTitle = document.title
            .replace(Regex("[^a-zA-Z0-9_-]"), "_")
            .take(30)
            .ifEmpty { "document" }
        val docxFile = File(docxDir, "${sanitizedTitle}_${document.id}.docx")

        DocxGenerator.generateDocx(document, docxFile)
        val formattedSize = formatFileSize(docxFile.length())

        val updatedDoc = document.copy(
            docxFilePath = docxFile.absolutePath,
            fileSizeFormatted = formattedSize
        )

        val entity = DocumentEntity(
            id = document.id,
            title = updatedDoc.title,
            documentType = updatedDoc.documentType,
            createdAt = updatedDoc.createdAt,
            imagePath = updatedDoc.originalImageUri,
            docxPath = updatedDoc.docxFilePath,
            blocksJson = DocumentJsonUtil.blocksToJson(updatedDoc.blocks),
            fileSizeFormatted = formattedSize
        )
        documentDao.updateDocument(entity)

        updatedDoc
    }

    suspend fun deleteDocument(id: String) = withContext(Dispatchers.IO) {
        val entity = documentDao.getDocumentById(id)
        if (entity != null) {
            entity.docxPath?.let { File(it).delete() }
            entity.imagePath?.let { File(it).delete() }
            documentDao.deleteDocument(entity)
        }
    }

    suspend fun loadBitmapFromUri(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun entityToScannedDocument(entity: DocumentEntity): ScannedDocument {
        val blocks = DocumentJsonUtil.blocksFromJson(entity.blocksJson)
        return ScannedDocument(
            id = entity.id,
            title = entity.title,
            documentType = entity.documentType,
            createdAt = entity.createdAt,
            originalImageUri = entity.imagePath,
            blocks = blocks,
            docxFilePath = entity.docxPath,
            fileSizeFormatted = entity.fileSizeFormatted
        )
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
