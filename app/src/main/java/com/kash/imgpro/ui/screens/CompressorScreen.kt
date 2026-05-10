package com.kash.imgpro.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.kash.imgpro.ui.components.GlassmorphicCard
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
import com.kash.imgpro.util.FileUtils
import com.kash.imgpro.viewmodel.CompressorUiState
import com.kash.imgpro.viewmodel.CompressorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompressorScreen(
    onNavigateBack: () -> Unit,
    viewModel: CompressorViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    // Local slider state — decoupled from ViewModel to avoid recomposition storms
    var localTargetSizeKB by remember(state.targetSizeKB) { mutableIntStateOf(state.targetSizeKB) }

    // Derived max for slider range — only recomputes when imageInfo changes
    val sliderMax by remember(state.imageInfo) {
        derivedStateOf {
            ((state.imageInfo?.sizeBytes ?: 2048000) / 1024f).coerceAtLeast(50f)
        }
    }

    // Photo picker
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        uri?.let { viewModel.loadImage(it) }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { error ->
            snackbarHostState.showSnackbar(error)
        }
    }

    val isProcessing = state.status == CompressorUiState.Status.Processing ||
            state.status == CompressorUiState.Status.Loading

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
                    onNavigateBack() // Simplest cancel is popping out of scope terminating viewModelScope jobs 
                }) {
                    Text("Yes, Cancel", color = ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) { 
                    Text("No", color = ElectricCyan) 
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
                        tint = ElectricCyan,
                    )
                }
                Text(
                    text = "Smart Compressor",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }

            // ── LinearProgressIndicator — only during actual background work
            AnimatedVisibility(visible = isProcessing) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = ElectricCyan,
                    trackColor = DarkSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Image picker / preview ────────────────────────────────
            if (state.sourceBitmap == null) {
                // Empty state — pick button
                GlassmorphicCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        photoPickerLauncher.launch(
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
                            imageVector = Icons.Rounded.Image,
                            contentDescription = "Select Image",
                            tint = ElectricCyan,
                            modifier = Modifier.size(48.dp),
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Tap to select an image",
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextSecondary,
                        )
                    }
                }
            } else {
                // ── Source Badge ──────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "SOURCE: ${state.sourceFormatName}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = SurfaceTone,
                        modifier = Modifier
                            .background(SurfaceTone.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                            .border(1.dp, SurfaceTone.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                    state.imageInfo?.let { info ->
                        Text(
                            text = FileUtils.formatFileSize(info.sizeBytes),
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ── Before/After Comparison Slider or plain preview ───
                val previewOriginal = state.previewBitmap
                val previewCompressed = state.compressedPreviewBitmap
                if (previewOriginal != null && previewCompressed != null) {
                    BeforeAfterSlider(
                        originalBitmap = previewOriginal,
                        compressedBitmap = previewCompressed,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .border(1.dp, GlassBorder, RoundedCornerShape(16.dp)),
                    )
                } else {
                    // Plain image preview (before compression)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .border(1.dp, GlassBorder, RoundedCornerShape(16.dp)),
                    ) {
                        AsyncImage(
                            model = state.sourceUri,
                            contentDescription = "Selected image",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }

                // Image info chips
                state.imageInfo?.let { info ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        InfoChip("${info.width} × ${info.height}")
                        InfoChip(FileUtils.formatFileSize(info.sizeBytes))
                        InfoChip("%.1fMP".format(info.megaPixels))
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ── Format selection ──────────────────────────────────
                Text(
                    text = "Output Format",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FormatChip("JPG", state.compressFormat == Bitmap.CompressFormat.JPEG) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.setCompressFormat(Bitmap.CompressFormat.JPEG)
                    }
                    FormatChip("WEBP", state.compressFormat == Bitmap.CompressFormat.WEBP_LOSSY) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.setCompressFormat(Bitmap.CompressFormat.WEBP_LOSSY)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ── Target size slider — LOCAL state, no VM call on drag ──
                Text(
                    text = "Target Size: $localTargetSizeKB KB",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary,
                )
                Spacer(modifier = Modifier.height(4.dp))
                
                AnimatedVisibility(visible = state.isTargetImpossible) {
                    Text(
                        text = "⚠️ Target too low: Target size mathematically unreachable (less than 5% of original). Output will hit quality floor.",
                        color = WarningAmber,
                        style = MaterialTheme.typography.bodySmall,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                
                val isSliderEnabled = state.status == CompressorUiState.Status.Idle

                Slider(
                    value = localTargetSizeKB.toFloat(),
                    onValueChange = { newVal ->
                        val intVal = newVal.toInt()
                        if (intVal != localTargetSizeKB) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        localTargetSizeKB = intVal
                    },
                    onValueChangeFinished = {
                        // Only commit to ViewModel when drag ends
                        viewModel.setTargetSizeKB(localTargetSizeKB)
                    },
                    enabled = isSliderEnabled,
                    valueRange = 10f..sliderMax,
                    colors = SliderDefaults.colors(
                        thumbColor = if (isSliderEnabled) ElectricCyan else TextTertiary,
                        activeTrackColor = if (isSliderEnabled) ElectricCyan else TextTertiary,
                        inactiveTrackColor = DarkSurfaceVariant,
                        disabledThumbColor = TextTertiary.copy(alpha = 0.5f),
                        disabledActiveTrackColor = TextTertiary.copy(alpha = 0.4f),
                        disabledInactiveTrackColor = DarkSurfaceVariant.copy(alpha = 0.4f),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(24.dp))

                // ── Primary Action Button ─────────────────────────────
                GlassmorphicCard(
                    modifier = Modifier.fillMaxWidth(),
                    accentColor = if (state.isTargetImpossible && !isProcessing) WarningAmber else ElectricCyan,
                    secondaryAccent = VividViolet,
                    onClick = if (!isProcessing && state.status != CompressorUiState.Status.Success) {
                        {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.compress()
                        }
                    } else null,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (isProcessing) {
                            // Spinning "Chakri" — M3 indeterminate spinner
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = ElectricCyan,
                                strokeWidth = 3.dp,
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                "Compressing…",
                                style = MaterialTheme.typography.titleMedium,
                                color = ElectricCyan,
                            )
                        } else {
                            Icon(
                                Icons.Rounded.Compress,
                                contentDescription = "Compress",
                                tint = ElectricCyan,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Compress Image",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = ElectricCyan,
                            )
                        }
                    }
                }

                // ── Results ───────────────────────────────────────────
                AnimatedVisibility(
                    visible = state.compressionResult != null,
                    enter = fadeIn() + slideInVertically { it / 2 },
                    exit = fadeOut(),
                ) {
                    state.compressionResult?.let { result ->
                        Column {
                            Spacer(modifier = Modifier.height(20.dp))

                            // ── Result Summary Card ──────────────────
                            GlassmorphicCard(
                                modifier = Modifier.fillMaxWidth(),
                                accentColor = ElectricCyan,
                                secondaryAccent = VividViolet,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "Reduced ${FileUtils.formatFileSize(result.originalSizeBytes)} ➔ ${FileUtils.formatFileSize(result.compressedSizeBytes)}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = ElectricCyan,
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "%.1f%% smaller at ${result.quality}%% quality".format(result.compressionRatio),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary,
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // ── Detailed Stats (dual-unit display) ───
                            GlassmorphicCard(
                                modifier = Modifier.fillMaxWidth(),
                                accentColor = if (result.lowQualityWarning) WarningAmber else SuccessGreen,
                                secondaryAccent = ElectricCyan,
                            ) {
                                Column {
                                    Text(
                                        text = "Compression Details",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (result.lowQualityWarning) WarningAmber else SuccessGreen,
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))

                                    StatRow("Original", FileUtils.formatFileSizeDual(result.originalSizeBytes))
                                    StatRow("Target Size", "${state.targetSizeKB} KB")
                                    StatRow("Saved", "%.1f%%".format(result.compressionRatio))
                                    StatRow("Quality", "${result.quality}%")

                                    // ── Quality warning ───────────────
                                    result.warningMessage?.let { warning ->
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = warning,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = WarningAmber,
                                            lineHeight = 18.sp,
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // ── Save button with success message ─────
                            GlassmorphicCard(
                                modifier = Modifier.fillMaxWidth(),
                                accentColor = SuccessGreen,
                                secondaryAccent = ElectricCyan,
                                onClick = if (state.savedUri == null) {
                                    {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        viewModel.saveToGallery()
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
                                            if (state.savedUri != null) Icons.Rounded.CheckCircle else Icons.Rounded.SaveAlt,
                                            contentDescription = "Save",
                                            tint = SuccessGreen,
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (state.savedUri != null)
                                                "✅ Success! Your image is ready."
                                            else "Save to Gallery",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = SuccessGreen,
                                        )
                                    }

                                    // Post-save instructions + path transparency
                                    if (state.savedUri != null) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "Open your Gallery or Photos app and look for the \"ImgPro\" folder.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextSecondary,
                                            lineHeight = 16.sp,
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Saved at: Pictures/ImgPro",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextTertiary,
                                        )
                                    }
                                }
                            }
                            // ── View Image Button ─────────────────────
                            if (state.savedUri != null) {
                                Spacer(modifier = Modifier.height(16.dp))
                                GlassmorphicCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    accentColor = ElectricCyan,
                                    secondaryAccent = VividViolet,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(state.savedUri, "image/*")
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
                                            contentDescription = "View Image",
                                            tint = ElectricCyan,
                                            modifier = Modifier.size(20.dp),
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "✅ View Image",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = ElectricCyan,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Reset ─────────────────────────────────────────────

                // New image button
                if (state.sourceBitmap != null) {
                    Spacer(modifier = Modifier.height(16.dp))
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
                                "Choose Another Image",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            } // Closes else block
        } // Closes Column
        
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
        )
    } // Closes Box
} // Closes CompressorScreen

// =====================================================================
// Before/After Comparison Slider — GPU-accelerated
// =====================================================================

/**
 * Draggable before/after comparison slider using downsampled preview bitmaps.
 * Uses [graphicsLayer] to offload compositing to the GPU and prevent
 * unnecessary recompositions during drag.
 */
@Composable
private fun BeforeAfterSlider(
    originalBitmap: Bitmap,
    compressedBitmap: Bitmap,
    modifier: Modifier = Modifier,
) {
    var dividerFraction by remember { mutableFloatStateOf(0.5f) }
    var canvasWidth by remember { mutableFloatStateOf(1f) }

    // Convert bitmaps to ImageBitmap once and cache
    val originalImage = remember(originalBitmap) { originalBitmap.asImageBitmap() }
    val compressedImage = remember(compressedBitmap) { compressedBitmap.asImageBitmap() }

    Box(
        modifier = modifier
            .graphicsLayer {
                // Force GPU compositing — avoids software canvas overhead
                compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
            },
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .onSizeChanged { size -> canvasWidth = size.width.toFloat() }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { _, dragAmount ->
                        dividerFraction = (dividerFraction + dragAmount / canvasWidth)
                            .coerceIn(0.05f, 0.95f)
                    }
                },
        ) {
            val dividerX = size.width * dividerFraction

            // Draw compressed (right/full background)
            drawImage(
                image = compressedImage,
                dstSize = IntSize(size.width.toInt(), size.height.toInt()),
            )

            // Draw original (clipped to left of divider)
            clipRect(right = dividerX) {
                drawImage(
                    image = originalImage,
                    dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                )
            }

            // Divider line
            drawLine(
                color = Color.White,
                start = Offset(dividerX, 0f),
                end = Offset(dividerX, size.height),
                strokeWidth = 3f,
            )

            // Divider handle circle
            drawCircle(
                color = Color.White,
                radius = 14f,
                center = Offset(dividerX, size.height / 2f),
            )
            drawCircle(
                color = Color(0xFF00E5FF),
                radius = 10f,
                center = Offset(dividerX, size.height / 2f),
            )
        }

        // Labels
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Original",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .background(DeepCharcoal.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
            Text(
                text = "Target Size",
                style = MaterialTheme.typography.labelSmall,
                color = ElectricCyan,
                modifier = Modifier
                    .background(DeepCharcoal.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

// ── Supporting Composables ────────────────────────────────────────

@Composable
private fun InfoChip(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
        color = TextTertiary,
        modifier = Modifier
            .background(DarkSurfaceVariant, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormatChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontWeight = FontWeight.Medium) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = ElectricCyan.copy(alpha = 0.15f),
            selectedLabelColor = ElectricCyan,
            containerColor = DarkSurfaceVariant,
            labelColor = TextSecondary,
        ),
        border = FilterChipDefaults.filterChipBorder(
            borderColor = DarkSurfaceVariant,
            selectedBorderColor = ElectricCyan.copy(alpha = 0.5f),
            enabled = true,
            selected = selected,
        ),
    )
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
