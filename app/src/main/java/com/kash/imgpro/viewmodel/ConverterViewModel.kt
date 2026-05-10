package com.kash.imgpro.viewmodel

import android.app.Application
import android.graphics.Bitmap
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
 * State for a single image being converted.
 */
data class ConvertItem(
    val uri: Uri,
    val bitmap: Bitmap? = null,
    val sourceFormat: String = "Unknown",
    val sourceMimeType: String? = null,
    val originalSizeBytes: Long = 0L,
    val convertedData: ByteArray? = null,
    val isProcessed: Boolean = false,
    val savedUri: Uri? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ConvertItem) return false
        return uri == other.uri && sourceFormat == other.sourceFormat &&
                isProcessed == other.isProcessed && savedUri == other.savedUri
    }

    override fun hashCode(): Int {
        var result = uri.hashCode()
        result = 31 * result + sourceFormat.hashCode()
        result = 31 * result + isProcessed.hashCode()
        return result
    }
}

/**
 * UI state for the Format Converter screen.
 */
data class ConverterUiState(
    val status: Status = Status.Idle,
    val items: List<ConvertItem> = emptyList(),
    val targetFormat: ImageProcessor.OutputFormat = ImageProcessor.OutputFormat.PNG,
    val currentIndex: Int = 0,
    val progress: Float = 0f,
    val errorMessage: String? = null,
    val allSaved: Boolean = false,
    val isLinearProgressVisible: Boolean = false,
) {
    enum class Status { Idle, Loading, Processing, Success, Error }

    val totalItems: Int get() = items.size
    val processedCount: Int get() = items.count { it.isProcessed }

    /** All unique source formats among the loaded images. */
    val allSourceFormats: Set<String>
        get() = items.map { it.sourceFormat }.toSet()

    /** Dominant source format for badge display. */
    val dominantSourceFormat: String?
        get() = items
            .groupBy { it.sourceFormat }
            .maxByOrNull { it.value.size }
            ?.key

    /** True when images have more than one distinct source format. */
    val hasFormatConflict: Boolean
        get() = allSourceFormats.size > 1

    /** Warning message shown when a format conflict is detected. */
    val conflictWarning: String?
        get() = if (hasFormatConflict) {
            "⚠\uFE0F Conflict: Diverse source formats detected (${allSourceFormats.joinToString(", ")}). Output must be a new format (e.g., WEBP, PDF)."
        } else null

    /** Result summary text, e.g. "Converted JPG ➔ PNG" */
    val resultSummary: String?
        get() = if (status == Status.Success && dominantSourceFormat != null) {
            "Converted $dominantSourceFormat ➔ ${targetFormat.displayName}"
        } else null

    /** First successfully saved PDF Uri for viewing. */
    val pdfUri: Uri?
        get() = if (allSaved && targetFormat == ImageProcessor.OutputFormat.PDF) {
            items.firstOrNull { it.savedUri != null }?.savedUri
        } else null

    /** First successfully saved Image Uri for viewing (non-PDF output only). */
    val savedImageUri: Uri?
        get() = if (allSaved && targetFormat != ImageProcessor.OutputFormat.PDF) {
            items.firstOrNull { it.savedUri != null }?.savedUri
        } else null
}

/**
 * ViewModel for the Format Converter feature.
 *
 * Performance-first:
 * - All processing on [Dispatchers.Default]
 * - Explicit bitmap recycling on reset/onCleared and on new load
 * - LinearProgressIndicator visibility tied to actual background work
 */
class ConverterViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ConverterUiState())
    val uiState: StateFlow<ConverterUiState> = _uiState.asStateFlow()

    private val context get() = getApplication<Application>()

    /**
     * Load one or more images from [uris] — replaces any existing selection.
     * Recycles any previously held bitmaps first.
     */
    fun loadImages(uris: List<Uri>) {
        // Recycle old bitmaps
        recycleAllBitmaps()

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    status = ConverterUiState.Status.Loading,
                    errorMessage = null,
                    allSaved = false,
                    isLinearProgressVisible = true,
                )
            }

            try {
                val items = withContext(Dispatchers.IO) {
                    uris.map { uri ->
                        val mimeType = FileUtils.getMimeType(context, uri)
                        val sourceFormat = FileUtils.mimeToOutputFormat(mimeType)?.displayName ?: "Unknown"
                        val bitmap = ImageProcessor.decodeSampledBitmap(context, uri)
                        val fileSize = FileUtils.getFileSize(context, uri)

                        ConvertItem(
                            uri = uri,
                            bitmap = bitmap,
                            sourceFormat = sourceFormat,
                            sourceMimeType = mimeType,
                            originalSizeBytes = fileSize,
                        )
                    }
                }

                _uiState.update {
                    it.copy(
                        status = ConverterUiState.Status.Idle,
                        items = items,
                        isLinearProgressVisible = false,
                    )
                }
            } catch (e: ImageProcessor.ImageCorruptedException) {
                _uiState.update {
                    it.copy(
                        status = ConverterUiState.Status.Error,
                        errorMessage = "Decode Failed: Selected image is corrupted or unsupported.",
                        isLinearProgressVisible = false,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        status = ConverterUiState.Status.Error,
                        errorMessage = e.message ?: "Failed to load images",
                        isLinearProgressVisible = false,
                    )
                }
            }
        }
    }

    /**
     * Add more images to the existing selection without replacing.
     * Appends to current items list (capped at 10 total).
     */
    fun addImages(uris: List<Uri>) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    status = ConverterUiState.Status.Loading,
                    errorMessage = null,
                    isLinearProgressVisible = true,
                )
            }

            try {
                val existingUris = _uiState.value.items.map { it.uri }.toSet()
                val newUris = uris.filter { it !in existingUris }
                    .take(10 - _uiState.value.items.size) // cap at 10

                val newItems = withContext(Dispatchers.IO) {
                    newUris.map { uri ->
                        val mimeType = FileUtils.getMimeType(context, uri)
                        val sourceFormat = FileUtils.mimeToOutputFormat(mimeType)?.displayName ?: "Unknown"
                        val bitmap = ImageProcessor.decodeSampledBitmap(context, uri)
                        val fileSize = FileUtils.getFileSize(context, uri)

                        ConvertItem(
                            uri = uri,
                            bitmap = bitmap,
                            sourceFormat = sourceFormat,
                            sourceMimeType = mimeType,
                            originalSizeBytes = fileSize,
                        )
                    }
                }

                _uiState.update {
                    it.copy(
                        status = ConverterUiState.Status.Idle,
                        items = it.items + newItems,
                        isLinearProgressVisible = false,
                    )
                }
            } catch (e: ImageProcessor.ImageCorruptedException) {
                _uiState.update {
                    it.copy(
                        status = ConverterUiState.Status.Error,
                        errorMessage = "Decode Failed: Selected image is corrupted.",
                        isLinearProgressVisible = false,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        status = ConverterUiState.Status.Error,
                        errorMessage = e.message ?: "Failed to add images",
                        isLinearProgressVisible = false,
                    )
                }
            }
        }
    }

    /** Set the output format. */
    fun setTargetFormat(format: ImageProcessor.OutputFormat) {
        _uiState.update { it.copy(targetFormat = format) }
    }

    /**
     * Convert all loaded images to the selected target format.
     * Updates progress per-item. All processing on Dispatchers.Default.
     */
    fun convertAll() {
        if (_uiState.value.status == ConverterUiState.Status.Processing) return

        val items = _uiState.value.items
        if (items.isEmpty()) return

        // 1. Guard check storage space requirement before trying to process heavily
        val totalBytes = items.sumOf { it.originalSizeBytes }
        if (!FileUtils.hasEnoughStorageSpace(totalBytes * 2)) {
            _uiState.update { 
                it.copy(
                    status = ConverterUiState.Status.Error, 
                    errorMessage = "Storage Full: Not enough space available to batch process safely."
                ) 
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    status = ConverterUiState.Status.Processing,
                    progress = 0f,
                    currentIndex = 0,
                    isLinearProgressVisible = true,
                )
            }

            try {
                val total = items.size
                val convertedItems = mutableListOf<ConvertItem>()

                items.forEachIndexed { index, item ->
                    _uiState.update {
                        it.copy(currentIndex = index, progress = index.toFloat() / total)
                    }

                    val bitmap = item.bitmap ?: throw IllegalStateException("Bitmap not loaded for ${item.uri}")

                    val data = ImageProcessor.convertFormat(
                        bitmap = bitmap,
                        format = _uiState.value.targetFormat,
                    )

                    convertedItems.add(item.copy(convertedData = data, isProcessed = true))
                }

                _uiState.update {
                    it.copy(
                        status = ConverterUiState.Status.Success,
                        items = convertedItems,
                        progress = 1f,
                        isLinearProgressVisible = false,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        status = ConverterUiState.Status.Error,
                        errorMessage = e.message ?: "Conversion failed",
                        isLinearProgressVisible = false,
                    )
                }
            }
        }
    }

    /**
     * Save all converted images to the gallery.
     */
    fun saveAll() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLinearProgressVisible = true) }
            try {
                val format = _uiState.value.targetFormat
                val updatedItems = _uiState.value.items.mapIndexed { index, item ->
                    if (item.convertedData != null) {
                        val displayName = FileUtils.generateUniqueFileName("imgpro_converted_${index + 1}")
                        val savedUri = FileUtils.saveToMediaStore(
                            context = context,
                            data = item.convertedData,
                            displayName = displayName,
                            mimeType = format.mimeType,
                            extension = format.extension,
                        )
                        item.copy(savedUri = savedUri)
                    } else {
                        item
                    }
                }

                _uiState.update {
                    it.copy(items = updatedItems, allSaved = true, isLinearProgressVisible = false)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        status = ConverterUiState.Status.Error,
                        errorMessage = "Failed to save: ${e.message}",
                        isLinearProgressVisible = false,
                    )
                }
            }
        }
    }

    /** Save a single item by index. */
    fun saveItem(index: Int) {
        viewModelScope.launch {
            try {
                val item = _uiState.value.items.getOrNull(index) ?: return@launch
                val data = item.convertedData ?: return@launch
                val format = _uiState.value.targetFormat

                val displayName = FileUtils.generateUniqueFileName("ImgPro_converted")
                val savedUri = FileUtils.saveToMediaStore(
                    context = context,
                    data = data,
                    displayName = displayName,
                    mimeType = format.mimeType,
                    extension = format.extension,
                )

                _uiState.update { state ->
                    val updated = state.items.toMutableList()
                    updated[index] = item.copy(savedUri = savedUri)
                    state.copy(items = updated)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        status = ConverterUiState.Status.Error,
                        errorMessage = "Failed to save item: ${e.message}",
                    )
                }
            }
        }
    }

    /** Reset to initial state. Recycles all bitmaps. */
    fun reset() {
        recycleAllBitmaps()
        _uiState.value = ConverterUiState()
    }

    override fun onCleared() {
        super.onCleared()
        recycleAllBitmaps()
    }

    /** Explicitly recycle all held bitmaps to free native memory. */
    private fun recycleAllBitmaps() {
        _uiState.value.items.forEach { it.bitmap?.recycle() }
    }
}
