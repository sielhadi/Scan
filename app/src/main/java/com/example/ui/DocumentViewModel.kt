package com.example.ui

import android.app.Application
import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.widget.Toast
import android.graphics.Bitmap
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.DocumentRepository
import com.example.model.ScannedDocument
import com.example.util.SampleDocumentsProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

private const val TAG = "DocumentViewModel"

enum class ImageEnhancement {
    ORIGINAL,
    HIGH_CONTRAST,
    DOCUMENT_BW
}

sealed interface ScanUiState {
    data object Idle : ScanUiState
    data class Processing(val stepDescription: String, val stepIndex: Int, val totalSteps: Int = 4) : ScanUiState
    data class Success(val document: ScannedDocument) : ScanUiState
    data class Error(val message: String) : ScanUiState
}

class DocumentViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: DocumentRepository by lazy {
        val db = AppDatabase.getDatabase(application)
        DocumentRepository(application, db.documentDao())
    }

    val documentsHistory: StateFlow<List<ScannedDocument>> = repository.allDocuments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _scanUiState = MutableStateFlow<ScanUiState>(ScanUiState.Idle)
    val scanUiState: StateFlow<ScanUiState> = _scanUiState.asStateFlow()

    private val _previewBitmap = MutableStateFlow<Bitmap?>(null)
    val previewBitmap: StateFlow<Bitmap?> = _previewBitmap.asStateFlow()

    private val _rotationDegrees = MutableStateFlow(0)
    val rotationDegrees: StateFlow<Int> = _rotationDegrees.asStateFlow()

    private val _enhancement = MutableStateFlow(ImageEnhancement.ORIGINAL)
    val enhancement: StateFlow<ImageEnhancement> = _enhancement.asStateFlow()

    private val _activeDocument = MutableStateFlow<ScannedDocument?>(null)
    val activeDocument: StateFlow<ScannedDocument?> = _activeDocument.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private var rawSelectedBitmap: Bitmap? = null
    private var activePresetId: String? = null

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun onImageSelected(bitmap: Bitmap) {
        rawSelectedBitmap = bitmap
        activePresetId = null
        _rotationDegrees.value = 0
        _enhancement.value = ImageEnhancement.ORIGINAL
        updateProcessedPreview()
        _scanUiState.value = ScanUiState.Idle
    }

    fun onImageUriSelected(uri: Uri) {
        viewModelScope.launch {
            val bitmap = repository.loadBitmapFromUri(uri)
            if (bitmap != null) {
                onImageSelected(bitmap)
            } else {
                _scanUiState.value = ScanUiState.Error("Unable to load selected image.")
            }
        }
    }

    fun loadSamplePreset(presetId: String) {
        activePresetId = presetId
        viewModelScope.launch(Dispatchers.IO) {
            val bitmap = SampleDocumentsProvider.createSampleBitmap(presetId)
            withContext(Dispatchers.Main) {
                rawSelectedBitmap = bitmap
                _rotationDegrees.value = 0
                _enhancement.value = ImageEnhancement.ORIGINAL
                updateProcessedPreview()
                _scanUiState.value = ScanUiState.Idle
            }
        }
    }

    fun rotateImage() {
        _rotationDegrees.value = (_rotationDegrees.value + 90) % 360
        updateProcessedPreview()
    }

    fun setEnhancement(mode: ImageEnhancement) {
        _enhancement.value = mode
        updateProcessedPreview()
    }

    private fun updateProcessedPreview() {
        val src = rawSelectedBitmap ?: return
        viewModelScope.launch(Dispatchers.Default) {
            val processed = applyRotationAndEnhancement(src, _rotationDegrees.value, _enhancement.value)
            _previewBitmap.value = processed
        }
    }

    private val _extractedDocumentForReview = MutableStateFlow<ScannedDocument?>(null)
    val extractedDocumentForReview: StateFlow<ScannedDocument?> = _extractedDocumentForReview.asStateFlow()

    private val _isGeneratingDocx = MutableStateFlow(false)
    val isGeneratingDocx: StateFlow<Boolean> = _isGeneratingDocx.asStateFlow()

    fun startAiLayoutTransformation(customTitle: String? = null) {
        startAiExtraction(customTitle)
    }

    fun startAiExtraction(customTitle: String? = null) {
        val bitmap = _previewBitmap.value ?: rawSelectedBitmap
        if (bitmap == null) {
            _scanUiState.value = ScanUiState.Error("Please select or capture a document to scan.")
            return
        }

        viewModelScope.launch {
            try {
                _scanUiState.value = ScanUiState.Processing(
                    stepDescription = "Gemini AI extracting text, headings, tables & layout...",
                    stepIndex = 1,
                    totalSteps = 2
                )

                val document = repository.extractLayoutFromImage(bitmap, customTitle, activePresetId)

                _scanUiState.value = ScanUiState.Idle
                _extractedDocumentForReview.value = document
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Document AI extraction failed", e)
                _scanUiState.value = ScanUiState.Error(
                    e.localizedMessage ?: "Failed to extract text from document."
                )
            }
        }
    }

    fun confirmAndGenerateDocx(documentToSave: ScannedDocument) {
        viewModelScope.launch {
            try {
                _isGeneratingDocx.value = true
                val completed = repository.confirmAndSaveDocx(documentToSave)
                _extractedDocumentForReview.value = null
                _isGeneratingDocx.value = false
                _activeDocument.value = completed
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _isGeneratingDocx.value = false
                Log.e(TAG, "DOCX generation failed", e)
                _scanUiState.value = ScanUiState.Error(
                    e.localizedMessage ?: "Failed to generate Word document."
                )
            }
        }
    }

    fun updateReviewTitle(newTitle: String) {
        val current = _extractedDocumentForReview.value ?: return
        _extractedDocumentForReview.value = current.copy(title = newTitle)
    }

    fun updateReviewDocumentType(newType: String) {
        val current = _extractedDocumentForReview.value ?: return
        _extractedDocumentForReview.value = current.copy(documentType = newType)
    }

    fun cancelReview() {
        _extractedDocumentForReview.value = null
    }

    fun selectDocument(document: ScannedDocument) {
        _activeDocument.value = document
    }

    fun clearActiveDocument() {
        _activeDocument.value = null
    }

    fun clearScanner() {
        rawSelectedBitmap = null
        activePresetId = null
        _previewBitmap.value = null
        _rotationDegrees.value = 0
        _enhancement.value = ImageEnhancement.ORIGINAL
        _scanUiState.value = ScanUiState.Idle
    }

    fun deleteDocument(id: String) {
        viewModelScope.launch {
            repository.deleteDocument(id)
            if (_activeDocument.value?.id == id) {
                _activeDocument.value = null
            }
        }
    }

    fun updateTitle(newTitle: String) {
        val current = _activeDocument.value ?: return
        if (newTitle.isBlank()) return
        viewModelScope.launch {
            val updated = repository.updateDocument(current.copy(title = newTitle))
            _activeDocument.value = updated
        }
    }

    fun getDocxUri(context: Context, docxPath: String): Uri? {
        val file = File(docxPath)
        if (!file.exists()) return null
        val authority = "${context.packageName}.fileprovider"
        return try {
            FileProvider.getUriForFile(context, authority, file)
        } catch (e: Exception) {
            Log.e(TAG, "Error generating FileProvider URI", e)
            null
        }
    }

    fun createShareIntent(context: Context, docxPath: String, title: String): Intent? {
        val uri = getDocxUri(context, docxPath) ?: return null
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, "Exported Word document: $title.docx")
            clipData = ClipData.newRawUri("Word Document", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun shareDocx(context: Context, docxPath: String, title: String) {
        val intent = createShareIntent(context, docxPath, title)
        if (intent != null) {
            val chooser = Intent.createChooser(intent, "Share \"$title\" (.docx)").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } else {
            Toast.makeText(context, "Document file not found or inaccessible", Toast.LENGTH_SHORT).show()
        }
    }

    fun createViewInWordIntent(context: Context, docxPath: String): Intent? {
        val uri = getDocxUri(context, docxPath) ?: return null
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    suspend fun saveDocxToDownloads(context: Context, docxPath: String, title: String): String? = withContext(Dispatchers.IO) {
        val srcFile = File(docxPath)
        if (!srcFile.exists()) return@withContext null

        val fileName = "${title.replace(Regex("[^a-zA-Z0-9_-]"), "_")}.docx"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues) ?: return@withContext null
            resolver.openOutputStream(uri)?.use { out ->
                FileInputStream(srcFile).use { input ->
                    input.copyTo(out)
                }
            }
            "Saved to Downloads/$fileName"
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            downloadsDir.mkdirs()
            val destFile = File(downloadsDir, fileName)
            FileInputStream(srcFile).use { input ->
                FileOutputStream(destFile).use { out ->
                    input.copyTo(out)
                }
            }
            "Saved to ${destFile.absolutePath}"
        }
    }

    private fun applyRotationAndEnhancement(
        src: Bitmap,
        rotation: Int,
        mode: ImageEnhancement
    ): Bitmap {
        var result = src

        // 1. Rotation
        if (rotation != 0) {
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            result = Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
        }

        // 2. Enhancement
        if (mode != ImageEnhancement.ORIGINAL) {
            val enhanced = Bitmap.createBitmap(result.width, result.height, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(enhanced)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            when (mode) {
                ImageEnhancement.HIGH_CONTRAST -> {
                    val cm = ColorMatrix()
                    // Contrast scale
                    val contrast = 1.35f
                    val translate = (-0.5f * contrast + 0.5f) * 255f
                    cm.set(floatArrayOf(
                        contrast, 0f, 0f, 0f, translate,
                        0f, contrast, 0f, 0f, translate,
                        0f, 0f, contrast, 0f, translate,
                        0f, 0f, 0f, 1f, 0f
                    ))
                    paint.colorFilter = ColorMatrixColorFilter(cm)
                }
                ImageEnhancement.DOCUMENT_BW -> {
                    val cm = ColorMatrix()
                    cm.setSaturation(0f)
                    // Boost contrast for clean document B&W text
                    val contrast = 1.5f
                    val translate = (-0.5f * contrast + 0.5f) * 255f
                    val cmContrast = ColorMatrix(floatArrayOf(
                        contrast, 0f, 0f, 0f, translate,
                        0f, contrast, 0f, 0f, translate,
                        0f, 0f, contrast, 0f, translate,
                        0f, 0f, 0f, 1f, 0f
                    ))
                    cm.postConcat(cmContrast)
                    paint.colorFilter = ColorMatrixColorFilter(cm)
                }
                ImageEnhancement.ORIGINAL -> {}
            }

            canvas.drawBitmap(result, 0f, 0f, paint)
            result = enhanced
        }

        return result
    }
}
