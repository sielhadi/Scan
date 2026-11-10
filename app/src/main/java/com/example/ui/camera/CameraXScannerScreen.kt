package com.example.ui.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.util.SampleDocumentsProvider
import java.util.concurrent.Executors

private const val TAG = "CameraXScanner"

@Composable
fun CameraXScannerScreen(
    onDocumentCaptured: (Bitmap) -> Unit,
    onClose: () -> Unit
) {
    BackHandler { onClose() }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    var isCapturing by remember { mutableStateOf(false) }
    var isTorchEnabled by remember { mutableStateOf(false) }
    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var showSampleDocDialog by remember { mutableStateOf(false) }
    var cameraBindError by remember { mutableStateOf<String?>(null) }

    // Check if running on Android Emulator
    val isEmulator = remember {
        Build.FINGERPRINT.startsWith("generic") ||
                Build.MODEL.contains("google_sdk") ||
                Build.MODEL.contains("Emulator") ||
                Build.HARDWARE.contains("goldfish") ||
                Build.HARDWARE.contains("ranchu")
    }

    // Photo picker launcher directly inside Camera
    val galleryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val bitmap = BitmapFactory.decodeStream(stream)
                    if (bitmap != null) {
                        onDocumentCaptured(bitmap)
                    } else {
                        Toast.makeText(context, "Could not decode chosen image", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load image from gallery", e)
                Toast.makeText(context, "Error reading image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    var camera by remember { mutableStateOf<Camera?>(null) }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .build()
    }

    val previewView = remember {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    // Bind Camera lifecycle safely with fallback
    LaunchedEffect(lensFacing) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder()
                    .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                    .build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }

                // Safely select camera, falling back if chosen lens doesn't exist
                val cameraSelector = when {
                    lensFacing == CameraSelector.LENS_FACING_BACK && cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) ->
                        CameraSelector.DEFAULT_BACK_CAMERA
                    lensFacing == CameraSelector.LENS_FACING_FRONT && cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) ->
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) ->
                        CameraSelector.DEFAULT_BACK_CAMERA
                    cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) ->
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    else -> CameraSelector.DEFAULT_BACK_CAMERA
                }

                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )
                cameraBindError = null
            } catch (e: Exception) {
                Log.e(TAG, "CameraX binding failed", e)
                cameraBindError = e.localizedMessage ?: "Camera binding failed"
            }
        }, ContextCompat.getMainExecutor(context))
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    // Scanner animation
    val infiniteTransition = rememberInfiniteTransition(label = "ScannerBeam")
    val scanProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ScanLineProgress"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("camerax_scanner_view")
    ) {
        // 1. Live Camera Preview
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        // 2. Translucent Document Viewfinder Overlay with Cutout & Corner Guides
        DocumentViewfinderOverlay(scanProgress = scanProgress)

        // 3. Top Action Controls (Close, Torch, Camera Flip)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 44.dp, start = 16.dp, end = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.55f),
                    modifier = Modifier.size(44.dp)
                ) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.testTag("camera_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Camera",
                            tint = Color.White
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.65f),
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Text(
                        text = "Position document in frame",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Torch toggle (if supported)
                    if (camera?.cameraInfo?.hasFlashUnit() == true) {
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.55f),
                            modifier = Modifier.size(44.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    isTorchEnabled = !isTorchEnabled
                                    camera?.cameraControl?.enableTorch(isTorchEnabled)
                                },
                                modifier = Modifier.testTag("camera_torch_button")
                            ) {
                                Icon(
                                    imageVector = if (isTorchEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                    contentDescription = "Toggle Torch",
                                    tint = if (isTorchEnabled) Color(0xFFFACC15) else Color.White
                                )
                            }
                        }
                    }

                    // Switch camera lens
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.55f),
                        modifier = Modifier.size(44.dp)
                    ) {
                        IconButton(
                            onClick = {
                                lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                                    CameraSelector.LENS_FACING_FRONT
                                } else {
                                    CameraSelector.LENS_FACING_BACK
                                }
                            },
                            modifier = Modifier.testTag("camera_switch_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cameraswitch,
                                contentDescription = "Switch Camera",
                                tint = Color.White
                            )
                        }
                    }
                }
            }

            // Emulator / Virtual camera notification badge
            if (isEmulator) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xDD1E293B),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Emulator Camera: Use \"Test Doc\" or \"Gallery\" below to scan real document text & tables.",
                            color = Color(0xFFF1F5F9),
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            if (cameraBindError != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xDD7F1D1D),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Camera notice: $cameraBindError. You can use 'Test Doc' or 'Gallery' to proceed.",
                        color = Color.White,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }

        // 4. Bottom Controls (Shutter, Sample Document, Gallery Picker)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 24.dp, vertical = 24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Tap shutter to capture, or pick sample document",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Test Document Picker Button
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { showSampleDocDialog = true }
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.2f),
                            modifier = Modifier
                                .size(50.dp)
                                .testTag("camera_sample_doc_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = "Test Document",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Test Doc",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Main Shutter Button
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(76.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color.Transparent,
                            border = androidx.compose.foundation.BorderStroke(4.dp, Color.White),
                            modifier = Modifier.fillMaxSize()
                        ) {}

                        Surface(
                            shape = CircleShape,
                            color = if (isCapturing) Color.Gray else Color.White,
                            modifier = Modifier
                                .size(62.dp)
                                .testTag("camera_shutter_button"),
                            onClick = {
                                if (!isCapturing) {
                                    isCapturing = true
                                    captureDocumentPhoto(
                                        imageCapture = imageCapture,
                                        context = context,
                                        executor = cameraExecutor,
                                        onSuccess = { bitmap ->
                                            isCapturing = false
                                            onDocumentCaptured(bitmap)
                                        },
                                        onError = { error ->
                                            isCapturing = false
                                            Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                            }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (isCapturing) {
                                    CircularProgressIndicator(
                                        color = MaterialTheme.colorScheme.primary,
                                        strokeWidth = 3.dp,
                                        modifier = Modifier.size(28.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.PhotoCamera,
                                        contentDescription = "Capture Document",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Pick From Gallery Button
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable {
                            galleryPickerLauncher.launch(
                                androidx.activity.result.PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        }
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.2f),
                            modifier = Modifier
                                .size(50.dp)
                                .testTag("camera_gallery_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.PhotoLibrary,
                                    contentDescription = "Pick Image",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Gallery",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }

    // Sample Document Selection Dialog (Instant clean document scan)
    if (showSampleDocDialog) {
        AlertDialog(
            onDismissRequest = { showSampleDocDialog = false },
            title = {
                Text(
                    text = "Select Document to Scan",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(SampleDocumentsProvider.presets) { preset ->
                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showSampleDocDialog = false
                                    val bitmap = SampleDocumentsProvider.createSampleBitmap(preset.id)
                                    onDocumentCaptured(bitmap)
                                }
                                .testTag("select_camera_preset_${preset.id}")
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = when (preset.iconName) {
                                                "Receipt" -> Icons.AutoMirrored.Outlined.ReceiptLong
                                                "Table" -> Icons.Default.TableChart
                                                else -> Icons.Default.Description
                                            },
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = preset.title,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                    Text(
                                        text = preset.category,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSampleDocDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun DocumentViewfinderOverlay(scanProgress: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val stroke = 3.dp.toPx()
        val cornerLen = 32.dp.toPx()
        val cornerStroke = 5.dp.toPx()

        // Cutout rect for document (standard 8.5 x 11 aspect ratio: ~1.3)
        val frameWidth = size.width * 0.85f
        val frameHeight = frameWidth * 1.35f
        val left = (size.width - frameWidth) / 2f
        val top = (size.height - frameHeight) / 2.2f
        val right = left + frameWidth
        val bottom = top + frameHeight

        // 1. Draw darkened backdrop outside cutout
        val fullPath = Path().apply {
            addRect(Rect(0f, 0f, size.width, size.height))
        }
        val cutoutPath = Path().apply {
            addRoundRect(
                RoundRect(
                    rect = Rect(left, top, right, bottom),
                    cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx())
                )
            )
        }

        clipPath(path = cutoutPath, clipOp = ClipOp.Difference) {
            drawRect(color = Color.Black.copy(alpha = 0.58f))
        }

        // 2. Draw thin border around document cutout
        drawRoundRect(
            color = Color.White.copy(alpha = 0.45f),
            topLeft = Offset(left, top),
            size = Size(frameWidth, frameHeight),
            cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke)
        )

        // 3. Four Corner Highlight Brackets
        val cornerColor = Color(0xFF60A5FA)

        // Top-Left
        drawLine(cornerColor, Offset(left - 2f, top + cornerLen), Offset(left - 2f, top), cornerStroke)
        drawLine(cornerColor, Offset(left - 2f, top), Offset(left + cornerLen, top), cornerStroke)

        // Top-Right
        drawLine(cornerColor, Offset(right + 2f - cornerLen, top), Offset(right + 2f, top), cornerStroke)
        drawLine(cornerColor, Offset(right + 2f, top), Offset(right + 2f, top + cornerLen), cornerStroke)

        // Bottom-Left
        drawLine(cornerColor, Offset(left - 2f, bottom - cornerLen), Offset(left - 2f, bottom), cornerStroke)
        drawLine(cornerColor, Offset(left - 2f, bottom), Offset(left + cornerLen, bottom), cornerStroke)

        // Bottom-Right
        drawLine(cornerColor, Offset(right + 2f - cornerLen, bottom), Offset(right + 2f, bottom), cornerStroke)
        drawLine(cornerColor, Offset(right + 2f, bottom), Offset(right + 2f, bottom - cornerLen), cornerStroke)

        // Animated Scanning Beam line across document cutout
        val scanY = top + (frameHeight * scanProgress)
        drawLine(
            color = Color(0xFF38BDF8).copy(alpha = 0.85f),
            start = Offset(left + 12f, scanY),
            end = Offset(right - 12f, scanY),
            strokeWidth = 3f
        )
    }
}

private fun captureDocumentPhoto(
    imageCapture: ImageCapture,
    context: Context,
    executor: java.util.concurrent.Executor,
    onSuccess: (Bitmap) -> Unit,
    onError: (String) -> Unit
) {
    imageCapture.takePicture(
        executor,
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(imageProxy: ImageProxy) {
                try {
                    val rawBitmap = imageProxy.toBitmap()
                    val rotation = imageProxy.imageInfo.rotationDegrees

                    val rotatedBitmap = if (rotation != 0) {
                        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                        Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
                    } else {
                        rawBitmap
                    }

                    // Crop to the framed document region (85% width, 1.35x aspect ratio)
                    val targetWidth = (rotatedBitmap.width * 0.85f).toInt().coerceAtMost(rotatedBitmap.width)
                    val targetHeight = (targetWidth * 1.35f).toInt().coerceAtMost(rotatedBitmap.height)
                    val cropX = ((rotatedBitmap.width - targetWidth) / 2).coerceAtLeast(0)
                    val cropY = ((rotatedBitmap.height - targetHeight) / 2).coerceAtLeast(0)

                    val finalBitmap = try {
                        if (targetWidth > 100 && targetHeight > 100) {
                            Bitmap.createBitmap(rotatedBitmap, cropX, cropY, targetWidth, targetHeight)
                        } else {
                            rotatedBitmap
                        }
                    } catch (_: Exception) {
                        rotatedBitmap
                    }

                    ContextCompat.getMainExecutor(context).execute {
                        onSuccess(finalBitmap)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Bitmap conversion failed", e)
                    ContextCompat.getMainExecutor(context).execute {
                        onError("Failed to process captured photo: ${e.localizedMessage}")
                    }
                } finally {
                    imageProxy.close()
                }
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e(TAG, "Photo capture failed: ${exception.message}", exception)
                ContextCompat.getMainExecutor(context).execute {
                    onError("Capture failed: ${exception.localizedMessage}")
                }
            }
        }
    )
}
