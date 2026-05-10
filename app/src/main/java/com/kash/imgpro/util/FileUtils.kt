package com.kash.imgpro.util

import android.app.RecoverableSecurityException
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream

/**
 * File I/O utilities — MediaStore integration, file size helpers, MIME type
 * resolution.  All disk/IO operations run on [Dispatchers.IO].
 */
object FileUtils {

    /** Sub-folder inside Pictures/ where all outputs are saved. */
    private const val APP_FOLDER = "ImgPro"

    /** Extracted error handling block */
    suspend fun <T> runStorageSafe(block: suspend () -> T): T? {
        return try {
            block()
        } catch (e: SecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                // Handled in UI normally via startIntentSenderForResult
                throw e
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    /** 
     * Verify if the system has enough disk space to process [requiredBytes].
     * Leaves a 50MB safety buffer.
     */
    fun hasEnoughStorageSpace(requiredBytes: Long): Boolean {
        return try {
            val statFs = StatFs(Environment.getDataDirectory().path)
            val availableBytes = statFs.availableBlocksLong * statFs.blockSizeLong
            availableBytes > (requiredBytes + (50 * 1024 * 1024))
        } catch (e: Exception) {
            true // Fallback gracefully if we can't read statFs
        }
    }

    // ───────────────────────────────────────────────────────────────
    // 1.  Save to MediaStore (Gallery)
    // ───────────────────────────────────────────────────────────────

    /**
     * Save [data] (raw bytes) to the device gallery via [MediaStore].
     *
     * @param context      Application context.
     * @param data         Raw image/PDF bytes to write.
     * @param displayName  File name without extension (e.g. "photo_compressed").
     * @param mimeType     MIME type (e.g. "image/jpeg", "application/pdf").
     * @param extension    File extension without dot (e.g. "jpg").
     * @return The content [Uri] of the saved file, or `null` on failure.
     */
    suspend fun saveToMediaStore(
        context: Context,
        data: ByteArray,
        displayName: String,
        mimeType: String,
        extension: String,
    ): Uri? = runStorageSafe {
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver

            val isImage = mimeType.startsWith("image/")

            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "$displayName.$extension")
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val directory = if (isImage) Environment.DIRECTORY_PICTURES else Environment.DIRECTORY_DOCUMENTS
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        "$directory/$APP_FOLDER"
                    )
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            // Choose the correct collection based on MIME type
            val collection = if (isImage) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }
            } else {
                // PDFs and other non-image types → Documents
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Files.getContentUri("external")
                }
            }

            val uri = resolver.insert(collection, contentValues) ?: return@withContext null

            try {
                resolver.openOutputStream(uri)?.use { outputStream ->
                    ByteArrayInputStream(data).use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

                // Mark file as ready (Android Q+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val updateValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.IS_PENDING, 0)
                    }
                    resolver.update(uri, updateValues, null, null)
                } else {
                    // Fallback for API < 29 using MediaScannerConnection
                    context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA))
                            MediaScannerConnection.scanFile(context, arrayOf(path), arrayOf(mimeType), null)
                        }
                    }
                }

                uri
            } catch (e: Exception) {
                // Clean up broken insert
                resolver.delete(uri, null, null)
                null
            }
        }
    }

    /**
     * Save a [Bitmap] directly to the gallery. Convenience wrapper around
     * the byte-array version.
     */
    suspend fun saveBitmapToMediaStore(
        context: Context,
        bitmap: Bitmap,
        displayName: String,
        format: ImageProcessor.OutputFormat,
        quality: Int = 95,
    ): Uri? {
        val data = ImageProcessor.convertFormat(bitmap, format, quality)
        return saveToMediaStore(
            context = context,
            data = data,
            displayName = displayName,
            mimeType = format.mimeType,
            extension = format.extension,
        )
    }

    // ───────────────────────────────────────────────────────────────
    // 2.  File size helpers
    // ───────────────────────────────────────────────────────────────

    /**
     * Get the byte size of a content [uri].
     */
    suspend fun getFileSize(context: Context, uri: Uri): Long =
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.available().toLong()
                } ?: 0L
            } catch (_: Exception) {
                0L
            }
        }

    /**
     * Format a byte count into a human-readable string.
     * Strict 1024-based binary units (1 KB = 1024 B, 1 MB = 1024 KB).
     *
     * Examples: `"1.2 KB"`, `"3.5 MB"`, `"128 B"`
     */
    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
            bytes < 1024L * 1024 * 1024 -> "%.2f MB".format(bytes / (1024.0 * 1024.0))
            else -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }

    /**
     * Format a byte count showing **both** units for transparency.
     * Used in the Compression Details card.
     *
     * Examples:
     * - `"1.56 MB (1597 KB)"` when ≥ 1 MB
     * - `"456 KB"` when < 1 MB
     * - `"128 B"` when < 1 KB
     */
    fun formatFileSizeDual(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> {
                val kb = bytes / 1024
                "$kb KB"
            }
            else -> {
                val kb = bytes / 1024
                val mb = bytes / (1024.0 * 1024.0)
                "%.2f MB (%d KB)".format(mb, kb)
            }
        }
    }

    // ───────────────────────────────────────────────────────────────
    // 3.  MIME type resolution
    // ───────────────────────────────────────────────────────────────

    /**
     * Resolve the MIME type of a content [uri] via [android.content.ContentResolver].
     */
    suspend fun getMimeType(context: Context, uri: Uri): String? =
        withContext(Dispatchers.IO) {
            context.contentResolver.getType(uri)
        }

    /**
     * Map a MIME type string to an [ImageProcessor.OutputFormat], if recognized.
     */
    fun mimeToOutputFormat(mime: String?): ImageProcessor.OutputFormat? {
        return when {
            mime == null -> null
            mime.contains("jpeg") || mime.contains("jpg") -> ImageProcessor.OutputFormat.JPEG
            mime.contains("png") -> ImageProcessor.OutputFormat.PNG
            mime.contains("webp") -> ImageProcessor.OutputFormat.WEBP
            mime.contains("pdf") -> ImageProcessor.OutputFormat.PDF
            mime.contains("heic") || mime.contains("heif") -> ImageProcessor.OutputFormat.HEIC
            mime.contains("bmp") || mime.contains("x-ms-bmp") -> ImageProcessor.OutputFormat.BMP
            mime.contains("tiff") || mime.contains("tif") -> ImageProcessor.OutputFormat.TIFF
            else -> null
        }
    }

    // ───────────────────────────────────────────────────────────────
    // 4.  Display name generation
    // ───────────────────────────────────────────────────────────────

    /**
     * Generate a highly sanitized unique display name for a saved file.
     * Prevents duplicate overwrites safely without requiring storage read checks on older APIs.
     */
    fun generateUniqueFileName(prefix: String = "imgpro", originalName: String? = null): String {
        val timestamp = System.currentTimeMillis()
        val safePrefix = prefix.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").lowercase()
        return if (originalName != null) {
            val safeOriginal = originalName.substringBeforeLast(".")
                .replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
            "${safePrefix}_${safeOriginal}_$timestamp"
        } else {
            "${safePrefix}_$timestamp"
        }
    }
}
