package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.DocBlock
import com.example.model.ScannedDocument
import com.example.model.TextAlignment
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentViewerScreen(
    document: ScannedDocument,
    viewModel: DocumentViewModel,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }
    var showEditTitleDialog by remember { mutableStateOf(false) }
    var editedTitle by remember { mutableStateOf(document.title) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = document.title,
                            maxLines = 1,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "${document.documentType} • ${document.fileSizeFormatted}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("viewer_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            document.docxFilePath?.let {
                                viewModel.shareDocx(context, it, document.title)
                            } ?: Toast.makeText(context, "Document file not found", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.testTag("viewer_share_button")
                    ) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = "Share Document")
                    }
                    IconButton(
                        onClick = { showEditTitleDialog = true },
                        modifier = Modifier.testTag("viewer_edit_title_button")
                    ) {
                        Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit Title")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 6.dp,
                shadowElevation = 10.dp,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            document.docxFilePath?.let { path ->
                                viewModel.shareDocx(context, path, document.title)
                            } ?: Toast.makeText(context, "Document file not found", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("share_docx_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Share .docx to Apps",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            val intent = document.docxFilePath?.let {
                                viewModel.createViewInWordIntent(context, it)
                            }
                            if (intent != null) {
                                try {
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    viewModel.shareDocx(context, document.docxFilePath ?: "", document.title)
                                }
                            }
                        },
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("viewer_open_word_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = "Open in Word",
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                val result = document.docxFilePath?.let {
                                    viewModel.saveDocxToDownloads(context, it, document.title)
                                }
                                Toast.makeText(context, result ?: "Saved to Downloads", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("viewer_download_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Save to Downloads",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Action Banner for Word Document
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    "W",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                "Native Word Document (.docx)",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                "Formatting & layout preserved",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            onClick = {
                                document.docxFilePath?.let {
                                    viewModel.shareDocx(context, it, document.title)
                                } ?: Toast.makeText(context, "Document file not found", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.testTag("action_banner_share_button")
                        ) {
                            Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Share", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                val intent = document.docxFilePath?.let {
                                    viewModel.createViewInWordIntent(context, it)
                                }
                                if (intent != null) {
                                    try {
                                        context.startActivity(intent)
                                    } catch (_: Exception) {
                                        viewModel.shareDocx(context, document.docxFilePath ?: "", document.title)
                                    }
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("open_in_word_button")
                        ) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Open", fontSize = 12.sp)
                        }
                    }
                }
            }

            // Tabs: "Formatted Layout", "Structure & Blocks", "Original Scan"
            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Layout Preview", fontWeight = FontWeight.SemiBold) },
                    modifier = Modifier.testTag("tab_layout_preview")
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Blocks (${document.blocks.size})", fontWeight = FontWeight.SemiBold) },
                    modifier = Modifier.testTag("tab_blocks")
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Original Scan", fontWeight = FontWeight.SemiBold) },
                    modifier = Modifier.testTag("tab_original_scan")
                )
            }

            // Tab Content
            when (selectedTab) {
                0 -> FormattedPaperPreview(document = document)
                1 -> StructureBlocksView(blocks = document.blocks)
                2 -> OriginalScanView(imagePath = document.originalImageUri)
            }
        }
    }

    // Edit Title Dialog
    if (showEditTitleDialog) {
        AlertDialog(
            onDismissRequest = { showEditTitleDialog = false },
            title = { Text("Rename Document") },
            text = {
                OutlinedTextField(
                    value = editedTitle,
                    onValueChange = { editedTitle = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.updateTitle(editedTitle)
                    showEditTitleDialog = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditTitleDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun FormattedPaperPreview(document: ScannedDocument) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFE2E8F0))
            .padding(12.dp)
    ) {
        // Render as a clean sheet of Paper (white background, subtle shadow)
        Card(
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                // Header Badge
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFEFF6FF),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBFDBFE))
                        ) {
                            Text(
                                text = document.documentType.uppercase(),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E40AF),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        Text(
                            text = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(document.createdAt)),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF64748B)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Title
                    Text(
                        text = document.title,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E3A8A)
                        ),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(18.dp))
                    HorizontalDivider(thickness = 1.dp, color = Color(0xFFE2E8F0))
                    Spacer(modifier = Modifier.height(18.dp))
                }

                // Render all blocks
                items(document.blocks) { block ->
                    RenderBlock(block)
                    Spacer(modifier = Modifier.height(14.dp))
                }

                item {
                    Spacer(modifier = Modifier.height(30.dp))
                    HorizontalDivider(thickness = 1.dp, color = Color(0xFFE2E8F0))
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Document generated with ScanToWord AI • OpenXML Fidelity Engine",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF94A3B8),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }
    }
}

@Composable
fun RenderBlock(block: DocBlock) {
    when (block) {
        is DocBlock.Heading -> {
            val (fontSize, color) = when (block.level) {
                1 -> 22.sp to Color(0xFF1E3A8A)
                2 -> 18.sp to Color(0xFF1E40AF)
                else -> 16.sp to Color(0xFF334155)
            }
            val textAlign = when (block.alignment) {
                TextAlignment.CENTER -> TextAlign.Center
                TextAlignment.RIGHT -> TextAlign.End
                TextAlignment.JUSTIFY -> TextAlign.Justify
                else -> TextAlign.Start
            }
            Text(
                text = block.text,
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
                color = color,
                textAlign = textAlign,
                modifier = Modifier.fillMaxWidth()
            )
        }
        is DocBlock.Paragraph -> {
            val textAlign = when (block.alignment) {
                TextAlignment.CENTER -> TextAlign.Center
                TextAlignment.RIGHT -> TextAlign.End
                TextAlignment.JUSTIFY -> TextAlign.Justify
                else -> TextAlign.Start
            }
            val annotated = buildAnnotatedString {
                for (run in block.runs) {
                    val color = parseHex(run.colorHex) ?: Color(0xFF1E293B)
                    val bg = if (run.isHighlight) Color(0xFFFEF08A) else Color.Transparent
                    withStyle(
                        SpanStyle(
                            fontWeight = if (run.isBold) FontWeight.Bold else FontWeight.Normal,
                            fontStyle = if (run.isItalic) FontStyle.Italic else FontStyle.Normal,
                            textDecoration = if (run.isUnderline) TextDecoration.Underline else TextDecoration.None,
                            color = color,
                            background = bg
                        )
                    ) {
                        append(run.text)
                    }
                }
            }

            if (block.isQuoteOrCallout) {
                Surface(
                    color = Color(0xFFF8FAFC),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(10.dp)) {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(24.dp)
                                .background(Color(0xFF2563EB), RoundedCornerShape(2.dp))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = annotated,
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                            textAlign = textAlign
                        )
                    }
                }
            } else {
                Text(
                    text = annotated,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp,
                        lineHeight = 22.sp
                    ),
                    textAlign = textAlign,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = (block.indentationLevel * 16).dp)
                )
            }
        }
        is DocBlock.Table -> {
            RenderTableView(block)
        }
        is DocBlock.ListBlock -> {
            Column(modifier = Modifier.fillMaxWidth()) {
                block.items.forEachIndexed { index, item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        val bullet = if (block.isOrdered) "${index + 1}." else "•"
                        Text(
                            text = bullet,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E3A8A),
                            modifier = Modifier.width(24.dp)
                        )
                        Text(
                            text = item,
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                            color = Color(0xFF1E293B)
                        )
                    }
                }
            }
        }
        is DocBlock.KeyValueGrid -> {
            Surface(
                color = Color(0xFFF8FAFC),
                shape = RoundedCornerShape(6.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    block.entries.forEachIndexed { i, entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = entry.first,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF475569),
                                fontSize = 13.sp
                            )
                            Text(
                                text = entry.second,
                                fontWeight = FontWeight.Normal,
                                color = Color(0xFF0F172A),
                                fontSize = 13.sp
                            )
                        }
                        if (i < block.entries.size - 1) {
                            HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFE2E8F0))
                        }
                    }
                }
            }
        }
        is DocBlock.Callout -> {
            Surface(
                color = Color(0xFFEFF6FF),
                shape = RoundedCornerShape(6.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBFDBFE)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    if (!block.title.isNullOrBlank()) {
                        Text(
                            text = block.title,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E40AF),
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    Text(
                        text = block.text,
                        color = Color(0xFF1E293B),
                        fontSize = 13.sp
                    )
                }
            }
        }
        DocBlock.Divider -> {
            HorizontalDivider(thickness = 1.dp, color = Color(0xFFCBD5E1))
        }
    }
}

@Composable
fun RenderTableView(table: DocBlock.Table) {
    val scrollState = rememberScrollState()

    Surface(
        shape = RoundedCornerShape(6.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
        ) {
            // Header Row
            if (table.headers.isNotEmpty() && table.hasHeaderRow) {
                Row(
                    modifier = Modifier
                        .background(Color(0xFF1E3A8A))
                        .padding(vertical = 8.dp, horizontal = 4.dp)
                ) {
                    table.headers.forEach { header ->
                        Text(
                            text = header,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .width(130.dp)
                                .padding(horizontal = 8.dp)
                        )
                    }
                }
            }

            // Data Rows
            table.rows.forEachIndexed { rowIndex, row ->
                val bg = if (rowIndex % 2 == 0) Color(0xFFF8FAFC) else Color.White
                Row(
                    modifier = Modifier
                        .background(bg)
                        .padding(vertical = 8.dp, horizontal = 4.dp)
                ) {
                    row.forEach { cell ->
                        Text(
                            text = cell,
                            color = Color(0xFF1E293B),
                            fontSize = 12.sp,
                            modifier = Modifier
                                .width(130.dp)
                                .padding(horizontal = 8.dp)
                        )
                    }
                }
                if (rowIndex < table.rows.size - 1) {
                    HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFE2E8F0))
                }
            }
        }
    }
}

@Composable
fun StructureBlocksView(blocks: List<DocBlock>) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(blocks) { block ->
            BlockInspectorCard(block)
        }
    }
}

@Composable
fun BlockInspectorCard(block: DocBlock) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            val (badgeLabel, badgeColor) = when (block) {
                is DocBlock.Heading -> "HEADING (H${block.level})" to MaterialTheme.colorScheme.primary
                is DocBlock.Paragraph -> "PARAGRAPH (${block.runs.size} runs)" to MaterialTheme.colorScheme.secondary
                is DocBlock.Table -> "TABLE (${tableSummary(block)})" to Color(0xFF059669)
                is DocBlock.ListBlock -> (if (block.isOrdered) "NUMBERED LIST" else "BULLET LIST") to Color(0xFFD97706)
                is DocBlock.KeyValueGrid -> "KEY-VALUE (${block.entries.size} pairs)" to Color(0xFF7C3AED)
                is DocBlock.Callout -> "CALLOUT BOX" to Color(0xFF2563EB)
                DocBlock.Divider -> "DIVIDER" to MaterialTheme.colorScheme.outline
            }

            Surface(
                shape = RoundedCornerShape(4.dp),
                color = badgeColor.copy(alpha = 0.15f),
                modifier = Modifier.padding(bottom = 6.dp)
            ) {
                Text(
                    text = badgeLabel,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = badgeColor,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            when (block) {
                is DocBlock.Heading -> Text(block.text, fontWeight = FontWeight.Bold)
                is DocBlock.Paragraph -> Text(block.plainText, maxLines = 3)
                is DocBlock.Table -> Text(
                    "Headers: ${block.headers.joinToString(", ")}\nRows: ${block.rows.size}",
                    style = MaterialTheme.typography.bodySmall
                )
                is DocBlock.ListBlock -> Text(
                    block.items.joinToString("\n") { "• $it" },
                    style = MaterialTheme.typography.bodySmall
                )
                is DocBlock.KeyValueGrid -> Text(
                    block.entries.joinToString("; ") { "${it.first}: ${it.second}" },
                    style = MaterialTheme.typography.bodySmall
                )
                is DocBlock.Callout -> Text(block.text, style = MaterialTheme.typography.bodySmall)
                DocBlock.Divider -> Text("Horizontal rule / section separator", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun OriginalScanView(imagePath: String?) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.05f))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        if (!imagePath.isNullOrBlank() && File(imagePath).exists()) {
            AsyncImage(
                model = File(imagePath),
                contentDescription = "Original Scanned Document",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp))
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Original document image scan",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun tableSummary(table: DocBlock.Table): String {
    val cols = table.headers.size.coerceAtLeast(table.rows.firstOrNull()?.size ?: 0)
    return "$cols cols × ${table.rows.size} rows"
}

private fun parseHex(hex: String?): Color? {
    if (hex.isNullOrBlank()) return null
    return try {
        val clean = hex.removePrefix("#")
        Color(android.graphics.Color.parseColor("#$clean"))
    } catch (_: Exception) {
        null
    }
}
