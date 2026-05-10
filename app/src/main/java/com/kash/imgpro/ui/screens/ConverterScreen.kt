package com.kash.imgpro.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.kash.imgpro.ui.components.GlassmorphicCard
import com.kash.imgpro.ui.theme.CoralPink
import com.kash.imgpro.ui.theme.DarkSurfaceVariant
import com.kash.imgpro.ui.theme.DeepCharcoal
import com.kash.imgpro.ui.theme.ElectricCyan
import com.kash.imgpro.ui.theme.ErrorRed
import com.kash.imgpro.ui.theme.GlassBorder
import com.kash.imgpro.ui.theme.SuccessGreen
import com.kash.imgpro.ui.theme.SurfaceTone
import com.kash.imgpro.ui.theme.TextSecondary
import com.kash.imgpro.ui.theme.TextTertiary
import com.kash.imgpro.ui.theme.VividViolet
import com.kash.imgpro.ui.theme.WarningAmber
import com.kash.imgpro.util.ImageProcessor
import com.kash.imgpro.viewmodel.ConverterUiState
import com.kash.imgpro.viewmodel.ConverterViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConverterScreen(
    onNavigateBack: () -> Unit,
    viewModel: ConverterViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    // Derived — all unique source format names (e.g. {"JPG", "PNG"})
    val allSourceFormats by remember(state.items) {
        derivedStateOf { state.allSourceFormats }
    }

    val dominantSourceFormat by remember(state.items) {
        derivedStateOf { state.dominantSourceFormat }
    }

    // Multi-image picker (replace)
    val multiPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10),
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.loadImages(uris)
        }
    }

    // Add-image picker (append to existing)
    val addPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10),
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.addImages(uris)
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { error ->
            snackbarHostState.showSnackbar(error)
        }
    }

    val isProcessing = state.status == ConverterUiState.Status.Processing ||
            state.status == ConverterUiState.Status.Loading

    var showCancelDialog: Boolean by remember { mutableStateOf(false) }

    BackHandler(enabled = isProcessing) {
        showCancelDialog = true
    }

    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text("Cancel Process?") },
            text = { Text("Are you sure you want to cancel the active conversion process?", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    showCancelDialog = false
                    onNavigateBack()
                }) {
                    Text("Yes, Cancel", color = ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) {
                    Text("No", color = VividViolet)
                }
            },
            containerColor = DeepCharcoal,
            titleContentColor = SurfaceTone,
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DeepCharcoal)
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            // ── Top bar ───────────────────────────────────────────────
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                IconButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onNavigateBack()
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = VividViolet,
                    )
                }
                Text(
                    text = "Format Converter",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }

            // ── Top progress indicator ────────────────────────────────
            AnimatedVisibility(visible = state.isLinearProgressVisible) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = VividViolet,
                    trackColor = DarkSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Image picker (empty state) ────────────────────────────
            if (state.items.isEmpty()) {
                GlassmorphicCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    accentColor = VividViolet,
                    secondaryAccent = CoralPink,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        multiPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Collections,
                            contentDescription = "Pick Images",
                            tint = VividViolet,
                            modifier = Modifier.size(48.dp),
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Tap to select images (up to 10)",
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextSecondary,
                        )
                    }
                }
            } else {
                // ── Source Badge ───────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "SOURCE: ${dominantSourceFormat ?: "MIXED"}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = SurfaceTone,
                        modifier = Modifier
                            .background(SurfaceTone.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                            .border(1.dp, SurfaceTone.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                    Text(
                        text = "${state.totalItems} image${if (state.totalItems > 1) "s" else ""}",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary,
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ── Conflict Warning Card ─────────────────────────────
                AnimatedVisibility(visible = state.hasFormatConflict) {
                    state.conflictWarning?.let { warning ->
                        Column {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(WarningAmber.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                                    .border(1.dp, WarningAmber.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                    .padding(14.dp),
                            ) {
                                Text(
                                    text = warning,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = WarningAmber,
                                    lineHeight = 18.sp,
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                }

                // ── Selected image thumbnails ─────────────────────────
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(((state.items.size / 3 + 1) * 120).coerceAtMost(360).dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(0.dp),
                ) {
                    itemsIndexed(state.items) { index, item ->
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, GlassBorder, RoundedCornerShape(12.dp)),
                        ) {
                            AsyncImage(
                                model = item.uri,
                                contentDescription = "Image ${index + 1}",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )

                            // Source format badge
                            Text(
                                text = item.sourceFormat,
                                style = MaterialTheme.typography.labelSmall,
                                color = VividViolet,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(4.dp)
                                    .background(
                                        DeepCharcoal.copy(alpha = 0.8f),
                                        RoundedCornerShape(4.dp),
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            )

                            // Processed checkmark
                            if (item.isProcessed) {
                                Icon(
                                    Icons.Rounded.CheckCircle,
                                    contentDescription = "Done",
                                    tint = SuccessGreen,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .size(20.dp),
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ── Add Image button ──────────────────────────────────
                if (state.items.size < 10 && state.status != ConverterUiState.Status.Success) {
                    GlassmorphicCard(
                        modifier = Modifier.fillMaxWidth(),
                        accentColor = VividViolet.copy(alpha = 0.6f),
                        secondaryAccent = TextTertiary,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            addPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Rounded.Add,
                                contentDescription = "Add more images",
                                tint = VividViolet,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Add More Images",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = VividViolet,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ── Target format selection (scrollable row) ──────────
                Text(
                    text = "Convert To",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary,
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ImageProcessor.OutputFormat.entries.forEach { format ->
                        val isSuccess = state.status == ConverterUiState.Status.Success
                        val isBlockedBySource = allSourceFormats.any {
                            it.equals(format.displayName, ignoreCase = true)
                        }
                        val isDisabled = isBlockedBySource || isSuccess

                        FilterChip(
                            selected = state.targetFormat == format,
                            onClick = {
                                if (!isDisabled) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.setTargetFormat(format)
                                }
                            },
                            enabled = !isDisabled,
                            label = {
                                Text(
                                    text = if (isBlockedBySource) "${format.displayName} ✕" else format.displayName,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = VividViolet.copy(alpha = 0.15f),
                                selectedLabelColor = VividViolet,
                                containerColor = DarkSurfaceVariant,
                                labelColor = TextSecondary,
                                disabledContainerColor = DarkSurfaceVariant.copy(alpha = 0.4f),
                                disabledLabelColor = TextTertiary.copy(alpha = 0.5f),
                                disabledSelectedContainerColor = DarkSurfaceVariant.copy(alpha = 0.4f),
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                borderColor = DarkSurfaceVariant,
                                selectedBorderColor = VividViolet.copy(alpha = 0.5f),
                                disabledBorderColor = DarkSurfaceVariant.copy(alpha = 0.3f),
                                disabledSelectedBorderColor = DarkSurfaceVariant.copy(alpha = 0.3f),
                                enabled = !isDisabled,
                                selected = state.targetFormat == format,
                            ),
                        )
                    }
                }

                // Hint — show which source formats are blocked
                if (allSourceFormats.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Source format${if (allSourceFormats.size > 1) "s" else ""} (${allSourceFormats.joinToString(", ")}) disabled",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary.copy(alpha = 0.6f),
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // ── Convert button with Chakri spinner ────────────────
                val isButtonDisabled = isProcessing || state.items.isEmpty()

                GlassmorphicCard(
                    modifier = Modifier.fillMaxWidth(),
                    accentColor = VividViolet,
                    secondaryAccent = CoralPink,
                    onClick = if (!isButtonDisabled && state.status != ConverterUiState.Status.Success) {
                        {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.convertAll()
                        }
                    } else null,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = VividViolet,
                                strokeWidth = 3.dp,
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                "Converting ${state.currentIndex + 1}/${state.totalItems}…",
                                style = MaterialTheme.typography.titleMedium,
                                color = VividViolet,
                            )
                        } else {
                            Icon(
                                Icons.Rounded.SwapHoriz,
                                contentDescription = "Convert",
                                tint = VividViolet,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (state.status == ConverterUiState.Status.Success) "Conversion Complete ✓" else "Convert All",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (state.status == ConverterUiState.Status.Success) SuccessGreen else VividViolet,
                            )
                        }
                    }
                }

                // ── Result Summary Card ─────────────────────────────
                AnimatedVisibility(
                    visible = state.status == ConverterUiState.Status.Success,
                    enter = fadeIn() + slideInVertically { it / 2 },
                    exit = fadeOut(),
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))

                        // Summary: "Converted JPG ➔ PNG"
                        state.resultSummary?.let { summary ->
                            GlassmorphicCard(
                                modifier = Modifier.fillMaxWidth(),
                                accentColor = ElectricCyan,
                                secondaryAccent = VividViolet,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = summary,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = ElectricCyan,
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${state.processedCount} file${if (state.processedCount > 1) "s" else ""} converted successfully",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary,
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Save All button with success message
                        GlassmorphicCard(
                            modifier = Modifier.fillMaxWidth(),
                            accentColor = SuccessGreen,
                            secondaryAccent = ElectricCyan,
                            onClick = if (!state.allSaved) {
                                {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.saveAll()
                                }
                            } else null,
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        if (state.allSaved) Icons.Rounded.CheckCircle else Icons.Rounded.SaveAlt,
                                        contentDescription = "Save All",
                                        tint = SuccessGreen,
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    val isPdf = state.targetFormat == ImageProcessor.OutputFormat.PDF
                                    Text(
                                        text = if (state.allSaved)
                                            if (isPdf) "✅ Your PDF is ready" else "✅ Success! Your images are ready."
                                        else if (isPdf) "Save this Document" else "Save All to Gallery",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = SuccessGreen,
                                    )
                                }

                                // Post-save instructions + path transparency
                                if (state.allSaved) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    val isPdfSaved = state.targetFormat == ImageProcessor.OutputFormat.PDF
                                    val saveDir = if (isPdfSaved) "Documents/ImgPro" else "Pictures/ImgPro"

                                    Text(
                                        text = if (isPdfSaved) "Open your file manager and look for the \"ImgPro\" folder."
                                               else "Open your Gallery or Photos app and look for the \"ImgPro\" folder.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary,
                                        lineHeight = 16.sp,
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Saved at: $saveDir",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextTertiary,
                                    )
                                }
                            }
                        }

                        // ── View Result Button (PDF or Image) ─────────
                        val viewUri = state.pdfUri ?: state.savedImageUri
                        if (viewUri != null) {
                            Spacer(modifier = Modifier.height(16.dp))
                            val isPdfView = state.pdfUri != null
                            val viewMimeType = if (isPdfView) "application/pdf" else "image/*"
                            val viewLabel = if (isPdfView) "✅ View PDF" else "✅ View Image"
                            val viewDesc = if (isPdfView) "View PDF" else "View Image"

                            GlassmorphicCard(
                                modifier = Modifier.fillMaxWidth(),
                                accentColor = ElectricCyan,
                                secondaryAccent = VividViolet,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(viewUri, viewMimeType)
                                        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Open with"))
                                },
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Rounded.OpenInNew,
                                        contentDescription = viewDesc,
                                        tint = ElectricCyan,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = viewLabel,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = ElectricCyan,
                                    )
                                }
                            }
                        }
                    } // Closes Column inside AnimatedVisibility
                } // Closes AnimatedVisibility

                Spacer(modifier = Modifier.height(16.dp))

                // ── Reset ─────────────────────────────────────────────
                GlassmorphicCard(
                    modifier = Modifier.fillMaxWidth(),
                    accentColor = TextTertiary,
                    secondaryAccent = TextTertiary,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.reset()
                    },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            "Choose Different Images",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            } // Closes else block
        } // Closes Column

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding(),
        )
    } // Closes Box
} // Closes ConverterScreen
