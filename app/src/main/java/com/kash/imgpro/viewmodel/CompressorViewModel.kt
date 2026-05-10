package com.kash.imgpro.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kash.imgpro.util.FileUtils
import com.kash.imgpro.util.ImageProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * UI state for the Smart Compressor screen.
 *
 * Performance notes:
 * - [sourceBitmap] is capped at 4096×4096 for full-res compression.
 * - [previewBitmap] is a separate 1024×1024 downsampled copy used only
 *   by the Before/After Canvas to keep GPU usage low on weak devices.
 * - [compressedPreviewBitmap] is decoded from the compression result
 *   bytes, also downsampled to 1024×1024 max.
 * - Slider movement updates [targetSizeKB] locally only; no compression
 *   is triggered until the user taps "Compress Image".
 */
data class CompressorUiState(
    val status: Status = Status.Idle,
    val sourceUri: Uri? = null,
    val sourceBitmap: Bitmap? = null,
    val previewBitmap: Bitmap? = null,             // 1024px max preview for slider
    val compressedPreviewBitmap: Bitmap? = null,    // decoded from result bytes
    val sourceFormatName: String = "Unknown",
    val imageInfo: ImageProcessor.ImageInfo? = null,
    val quality: Int = 80,
    val targetSizeKB: Int = 200,
    val compressFormat: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG,
    val compressionResult: ImageProcessor.CompressionResult? = null,
    val progress: Float = 0f,
    val errorMessage: String? = null,
    val isTargetImpossible: Boolean = false,
    val savedUri: Uri? = null,
) {
    enum class Status { Idle, Loading, Processing, Success, Error }

    val formatDisplayName: String
        get() = when (compressFormat) {
            Bitmap.CompressFormat.JPEG -> "JPG"
            Bitmap.CompressFormat.WEBP_LOSSY -> "WEBP"
            Bitmap.CompressFormat.PNG -> "PNG"
            else -> "JPG"
        }
}

/**
 * ViewModel for the Smart Compressor feature.
 *
 * Performance-first design:
 * - All image processing runs on [Dispatchers.Default] / [Dispatchers.IO]
 * - Bitmap recycling on every state transition (new pick, reset, onCleared)
 * - Preview bitmaps kept at 1024px max to reduce GPU texture upload cost
 */
class CompressorViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(CompressorUiState())
    val uiState: StateFlow<CompressorUiState> = _uiState.asStateFlow()

    private val context get() = getApplication<Application>()

    companion object {
        /** Preview resolution cap — keeps GPU memory low on budget devices. */
        private const val PREVIEW_MAX_DIM = 1024
    }

    /**
     * Load an image from [uri], subsample it for memory safety,
     * and build a low-res preview for the comparison slider.
     */
    fun loadImage(uri: Uri) {
        // Recycle previous bitmaps before loading new ones
        recycleBitmaps()

        viewModelScope.launch {
            _uiState.update { it.copy(status = CompressorUiState.Status.Loading, sourceUri = uri, errorMessage = null) }
            try {
                // Read metadata first (no pixel decode)
                val info = ImageProcessor.getImageInfo(context, uri)

                // Detect source format name
                val mimeType = FileUtils.getMimeType(context, uri)
                val sourceFormatName = FileUtils.mimeToOutputFormat(mimeType)?.displayName ?: "Unknown"

                // Decode full-res subsampled bitmap (capped at 4096×4096)
                val bitmap = ImageProcessor.decodeSampledBitmap(context, uri)

                // Decode low-res preview (capped at 1024×1024) for Canvas slider
                val preview = ImageProcessor.decodeSampledBitmap(
                    context, uri, PREVIEW_MAX_DIM, PREVIEW_MAX_DIM
                )

                // Get actual file size from content resolver
                val fileSize = FileUtils.getFileSize(context, uri)
                val updatedInfo = info.copy(sizeBytes = fileSize)

                _uiState.update {
                    it.copy(
                        status = CompressorUiState.Status.Idle,
                        sourceBitmap = bitmap,
                        previewBitmap = preview,
                        sourceFormatName = sourceFormatName,
                        imageInfo = updatedInfo,
                        compressionResult = null,
                        compressedPreviewBitmap = null,
                        savedUri = null,
                        targetSizeKB = (fileSize / 1024 / 2).toInt().coerceAtLeast(10),
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        status = CompressorUiState.Status.Error,
                        errorMessage = e.message ?: "Failed to load image",
                    )
                }
            }
        }
    }

    /** Update quality slider value. */
    fun setQuality(quality: Int) {
        _uiState.update { it.copy(quality = quality.coerceIn(1, 100)) }
    }

    /**
     * Update target file size in KB.
     * This ONLY updates the display value — no compression is triggered.
     * Evaluates strictly against a 5% baseline logic to flag physically impossible targets.
     */
    fun setTargetSizeKB(sizeKB: Int) {
        val effectiveSize = sizeKB.coerceAtLeast(1)
        val originalSize = _uiState.value.imageInfo?.sizeBytes ?: 0L
        val isImpossible = originalSize > 0 && effectiveSize * 1024L < originalSize * 0.05

        _uiState.update { it.copy(targetSizeKB = effectiveSize, isTargetImpossible = isImpossible) }
    }

    /** Update compression format. */
    fun setCompressFormat(format: Bitmap.CompressFormat) {
        _uiState.update { it.copy(compressFormat = format) }
    }

    /**
     * Run binary-search compression to hit the target file size.
     * Also builds a downsampled preview from the result bytes for
     * the Before/After Canvas.
     */
    fun compress() {
        if (_uiState.value.status == CompressorUiState.Status.Processing) return

        val bitmap = _uiState.value.sourceBitmap ?: return
        val info = _uiState.value.imageInfo ?: return

        // 1. Guard check storage space requirement before trying to compress heavily
        //    (Assumes worst case: new output might be similar size to original + mem cache)
        if (!FileUtils.hasEnoughStorageSpace(info.sizeBytes * 2)) {
            _uiState.update { 
                it.copy(
                    status = CompressorUiState.Status.Error, 
                    errorMessage = "Storage Full: Not enough space available to safely process image."
                ) 
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(status = CompressorUiState.Status.Processing, progress = 0.1f) }

            try {
                _uiState.update { it.copy(progress = 0.3f) }

                val result = ImageProcessor.compressToTargetSize(
                    bitmap = bitmap,
                    targetSizeKB = _uiState.value.targetSizeKB,
                    format = _uiState.value.compressFormat,
                    originalSize = info.sizeBytes,
                )

                _uiState.update { it.copy(progress = 0.8f) }

                // Decode a low-res preview from compressed bytes on background thread
                val compressedPreview = withContext(Dispatchers.Default) {
                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(result.data, 0, result.data.size, opts)

                    opts.inSampleSize = ImageProcessor.calculateInSampleSize(
                        opts, PREVIEW_MAX_DIM, PREVIEW_MAX_DIM
                    )
                    opts.inJustDecodeBounds = false
                    opts.inPreferredConfig = Bitmap.Config.ARGB_8888

                    BitmapFactory.decodeByteArray(result.data, 0, result.data.size, opts)
                }

                // Recycle old compressed preview
                _uiState.value.compressedPreviewBitmap?.recycle()

                _uiState.update {
                    it.copy(
                        status = CompressorUiState.Status.Success,
                        compressionResult = result,
                        compressedPreviewBitmap = compressedPreview,
                        progress = 1f,
                    )
                }
            } catch (e: ImageProcessor.ImageCorruptedException) {
                _uiState.update {
                    it.copy(
                        status = CompressorUiState.Status.Error,
                        errorMessage = "Decode Failed: The selected image format is corrupted or unsupported.",
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        status = CompressorUiState.Status.Error,
                        errorMessage = e.message ?: "Compression failed",
                    )
                }
            }
        }
    }

    /**
     * Save the compressed image to the device gallery via MediaStore.
     */
    fun saveToGallery() {
        val result = _uiState.value.compressionResult ?: return

        viewModelScope.launch {
            try {
                val extension = _uiState.value.formatDisplayName.lowercase()
                val mimeType = "image/$extension"
                val displayName = FileUtils.generateUniqueFileName("imgpro_compressed")

                val savedUri = FileUtils.saveToMediaStore(
                    context = context,
                    data = result.data,
                    displayName = displayName,
                    mimeType = mimeType,
                    extension = extension,
                )

                _uiState.update { it.copy(savedUri = savedUri) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        status = CompressorUiState.Status.Error,
                        errorMessage = "Failed to save: ${e.message}",
                    )
                }
            }
        }
    }

    /** Reset to initial state for a new image. Recycles all bitmaps. */
    fun reset() {
        recycleBitmaps()
        _uiState.value = CompressorUiState()
    }

    override fun onCleared() {
        super.onCleared()
        recycleBitmaps()
    }

    /** Explicitly recycle all held bitmaps to free native memory immediately. */
    private fun recycleBitmaps() {
        val state = _uiState.value
        state.sourceBitmap?.recycle()
        state.previewBitmap?.recycle()
        state.compressedPreviewBitmap?.recycle()
    }
}
