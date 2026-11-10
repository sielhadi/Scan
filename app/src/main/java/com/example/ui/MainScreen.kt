package com.example.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.model.ScannedDocument
import com.example.ui.review.ExtractedTextReviewScreen

enum class AppTab(val label: String) {
    SCANNER("Scanner"),
    DOCUMENTS("Documents"),
    PRESETS("Presets")
}

@Composable
fun MainScreen(viewModel: DocumentViewModel) {
    var currentTab by remember { mutableIntStateOf(0) }
    val activeDocument by viewModel.activeDocument.collectAsState()
    val extractedForReview by viewModel.extractedDocumentForReview.collectAsState()

    if (activeDocument != null) {
        DocumentViewerScreen(
            document = activeDocument!!,
            viewModel = viewModel,
            onBack = { viewModel.clearActiveDocument() }
        )
    } else if (extractedForReview != null) {
        ExtractedTextReviewScreen(
            document = extractedForReview!!,
            viewModel = viewModel,
            onConfirmed = { confirmedDoc ->
                viewModel.confirmAndGenerateDocx(confirmedDoc)
            },
            onCancel = {
                viewModel.cancelReview()
            }
        )
    } else {
        Scaffold(
            bottomBar = {
                NavigationBar(modifier = Modifier.testTag("bottom_nav_bar")) {
                    NavigationBarItem(
                        selected = currentTab == 0,
                        onClick = { currentTab = 0 },
                        icon = {
                            Icon(
                                imageVector = if (currentTab == 0) Icons.Default.CameraAlt else Icons.Outlined.CameraAlt,
                                contentDescription = "Scanner"
                            )
                        },
                        label = { Text("Scanner") },
                        modifier = Modifier.testTag("nav_scanner")
                    )

                    NavigationBarItem(
                        selected = currentTab == 1,
                        onClick = { currentTab = 1 },
                        icon = {
                            Icon(
                                imageVector = if (currentTab == 1) Icons.Default.Folder else Icons.Outlined.Folder,
                                contentDescription = "Documents"
                            )
                        },
                        label = { Text("My Documents") },
                        modifier = Modifier.testTag("nav_documents")
                    )

                    NavigationBarItem(
                        selected = currentTab == 2,
                        onClick = { currentTab = 2 },
                        icon = {
                            Icon(
                                imageVector = if (currentTab == 2) Icons.Default.AutoAwesome else Icons.Outlined.AutoAwesome,
                                contentDescription = "Presets"
                            )
                        },
                        label = { Text("Presets") },
                        modifier = Modifier.testTag("nav_presets")
                    )
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                AnimatedContent(
                    targetState = currentTab,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "TabContent"
                ) { tab ->
                    when (tab) {
                        0 -> ScannerScreen(
                            viewModel = viewModel,
                            onDocumentReady = { doc -> viewModel.selectDocument(doc) }
                        )
                        1 -> HistoryScreen(
                            viewModel = viewModel,
                            onDocumentSelected = { doc -> viewModel.selectDocument(doc) }
                        )
                        2 -> PresetsScreen(
                            onPresetSelected = { presetId ->
                                viewModel.loadSamplePreset(presetId)
                                currentTab = 0 // Switch to Scanner with sample loaded
                            }
                        )
                    }
                }
            }
        }
    }
}
