package com.example.generator

import com.example.model.ScannedDocument
import com.example.service.DocxGenerationService
import java.io.File

object DocxGenerator {
    private val service = DocxGenerationService()

    fun generateDocx(document: ScannedDocument, outputFile: File): File {
        return service.generateDocxFromDocument(document, outputFile)
    }

    fun generateDocxFromAiResult(
        aiResponseJson: String,
        outputFile: File,
        customTitle: String? = null,
        imageUri: String? = null
    ): ScannedDocument {
        return service.generateDocxFromAiResult(aiResponseJson, outputFile, customTitle, imageUri)
    }

    fun generateDocxFromProcessedText(
        rawText: String,
        title: String,
        documentType: String = "Document",
        outputFile: File
    ): ScannedDocument {
        return service.generateDocxFromProcessedText(rawText, title, documentType, outputFile)
    }

    fun validateDocx(file: File): Boolean {
        return service.validateDocxArchive(file)
    }
}
