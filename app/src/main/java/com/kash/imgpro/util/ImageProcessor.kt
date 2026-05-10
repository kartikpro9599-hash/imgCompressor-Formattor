package com.kash.imgpro.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Core image processing engine.
 *
 * All heavy operations run on [Dispatchers.IO] or [Dispatchers.Default].
 * Uses subsampling to cap decode resolution at 4096×4096 and prevent OOM.
 */
object ImageProcessor {

    /** Maximum dimension we ever decode at. Anything larger is subsampled. */
    private const val MAX_DIMENSION = 4096

    /** If binary-search quality falls below this, trigger a UI warning. */
    const val QUALITY_WARNING_THRESHOLD = 30

    // ───────────────────────────────────────────────────────────────
    // 1.  Memory-safe bitmap decoding
    // ───────────────────────────────────────────────────────────────

    class ImageCorruptedException(message: String) : Exception(message)

    /**
     * Decode a [Uri] into a [Bitmap] that fits within [reqWidth]×[reqHeight].
     * Uses a multi-pass approach:
     *   Pass 1 → read bounds and sizes securely
     *   Pass 2 → actual decode with calculated `inSampleSize`
     *   Pass 3 → orientation verification & adjustment (EXIF)
     */
    suspend fun decodeSampledBitmap(
        context: Context,
        uri: Uri,
        reqWidth: Int = MAX_DIMENSION,
        reqHeight: Int = MAX_DIMENSION,
    ): Bitmap = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver

        // Safety: check file size for dynamic bounds parsing (>20MB)
        val fileSizeBytes = try {
            resolver.openInputStream(uri)?.use { it.available().toLong() } ?: 0L
        } catch (e: Exception) { 0L }

        // Dynamic OOM limits — clamp resolution request smaller if extremely large file
        val dynamicReqWidth = if (fileSizeBytes > 20 * 1024 * 1024) reqWidth / 2 else reqWidth
        val dynamicReqHeight = if (fileSizeBytes > 20 * 1024 * 1024) reqHeight / 2 else reqHeight

        // Pass 1 — bounds only
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }

        // Pass 2 — actual decode with computed sample size
        options.inSampleSize = calculateInSampleSize(options, dynamicReqWidth, dynamicReqHeight)
        options.inJustDecodeBounds = false
        options.inPreferredConfig = Bitmap.Config.ARGB_8888

        var bitmap = resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        } ?: throw ImageCorruptedException("Failed to decode bitmap from URI: $uri - file may be corrupted or unsupported")

        // Pass 3 — EXIF Rotation correction
        try {
            resolver.openInputStream(uri)?.use { stream ->
                val exif = androidx.exifinterface.media.ExifInterface(stream)
                val orientation = exif.getAttributeInt(
                    androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
                )
                val matrix = android.graphics.Matrix()
                when (orientation) {
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                        matrix.postScale(1f, -1f)
                        matrix.postRotate(180f)
                    }
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_TRANSPOSE -> {
                        matrix.postScale(-1f, 1f)
                        matrix.postRotate(90f)
                    }
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_TRANSVERSE -> {
                        matrix.postScale(-1f, 1f)
                        matrix.postRotate(270f)
                    }
                }
                if (!matrix.isIdentity) {
                    val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                    if (rotated !== bitmap) {
                        bitmap.recycle()
                        bitmap = rotated
                    }
                }
            }
        } catch (_: Exception) {
            // Ignore EXIF read errors to prioritize keeping the core image alive.
        }

        bitmap
    }

    /**
     * Compute the largest power-of-2 sample size that keeps both dimensions
     * at or above the requested size. Hardware decoders are fastest with
     * power-of-2 values.
     */
    fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int,
    ): Int {
        val (rawHeight, rawWidth) = options.outHeight to options.outWidth
        var inSampleSize = 1

        if (rawHeight > reqHeight || rawWidth > reqWidth) {
            val halfHeight = rawHeight / 2
            val halfWidth = rawWidth / 2

            while (halfHeight / inSampleSize >= reqHeight &&
                halfWidth / inSampleSize >= reqWidth
            ) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    // ───────────────────────────────────────────────────────────────
    // 2.  Smart Compression — binary-search quality
    // ───────────────────────────────────────────────────────────────

    /**
     * Result of a compression operation.
     */
    data class CompressionResult(
        val data: ByteArray,
        val quality: Int,
        val originalSizeBytes: Long,
        val compressedSizeBytes: Long,
        val lowQualityWarning: Boolean = false,
    ) {
        val compressionRatio: Float
            get() = if (originalSizeBytes > 0)
                (1f - compressedSizeBytes.toFloat() / originalSizeBytes) * 100f
            else 0f

        val warningMessage: String?
            get() = if (lowQualityWarning)
                "\u26A0\uFE0F Warning: Heavy quality loss detected. Try a larger target size or use WEBP for better results."
            else null

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is CompressionResult) return false
            return data.contentEquals(other.data) &&
                    quality == other.quality &&
                    originalSizeBytes == other.originalSizeBytes &&
                    compressedSizeBytes == other.compressedSizeBytes &&
                    lowQualityWarning == other.lowQualityWarning
        }

        override fun hashCode(): Int {
            var result = data.contentHashCode()
            result = 31 * result + quality
            result = 31 * result + originalSizeBytes.hashCode()
            result = 31 * result + compressedSizeBytes.hashCode()
            result = 31 * result + lowQualityWarning.hashCode()
            return result
        }
    }

    /**
     * Compress [bitmap] to **exactly** [targetSizeKB] using a two-phase approach:
     *
     * **Phase 1 — Binary Search (8% Buffer):**
     * Targets 92% of the user's requested KB to guarantee the compressed
     * image payload never exceeds the target after EXIF/filesystem overhead.
     *
     * **Phase 2 — Null-Byte Padding:**
     * Appends `0x00` bytes to the compressed data until the total size
     * equals exactly `targetSizeKB * 1024` bytes (±1 KB for filesystem
     * clustering). This ensures the Gallery/File Manager displays the
     * precise number the user requested.
     *
     * @param bitmap         Source bitmap (not recycled by this function).
     * @param targetSizeKB   Desired output size in kilobytes (1 KB = 1024 B).
     * @param format         [Bitmap.CompressFormat.JPEG] or [Bitmap.CompressFormat.WEBP_LOSSY].
     * @param originalSize   Original file size for ratio calculation.
     * @param maxIterations  Safety cap on binary-search iterations.
     */
    suspend fun compressToTargetSize(
        bitmap: Bitmap,
        targetSizeKB: Int,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG,
        originalSize: Long = 0L,
        maxIterations: Int = 10,
    ): CompressionResult = withContext(Dispatchers.Default) {
        // Unconditionally flatten alpha when targeting JPEG to handle transparent PNG inputs cleanly.
        val safeBitmap = if (format == Bitmap.CompressFormat.JPEG) {
            flattenAlpha(bitmap)
        } else {
            bitmap
        }

        // 8% buffer: target 92% of requested size for EXIF/filesystem overhead
        val effectiveTargetKB = (targetSizeKB * 0.92f).toLong().coerceAtLeast(1)
        val targetBytes = effectiveTargetKB * 1024L
        var low = 1
        var high = 100
        var bestData = compressBitmap(safeBitmap, high, format)
        var bestQuality = high

        // Quick check: if max quality already fits, pad and return immediately
        if (bestData.size <= targetBytes) {
            if (safeBitmap !== bitmap) safeBitmap.recycle()
            val userTargetBytes = targetSizeKB.toLong() * 1024L
            val paddedData = padToExactTarget(bestData, userTargetBytes)
            return@withContext CompressionResult(
                data = paddedData,
                quality = bestQuality,
                originalSizeBytes = originalSize,
                compressedSizeBytes = paddedData.size.toLong(),
            )
        }

        // Quick check: if min quality is still too large, return smallest (no padding possible)
        val minData = compressBitmap(safeBitmap, low, format)
        if (minData.size > targetBytes) {
            if (safeBitmap !== bitmap) safeBitmap.recycle()
            // Impossible target — compressed data exceeds even the effective target.
            // Return as-is without padding (can't pad upward when already over).
            return@withContext CompressionResult(
                data = minData,
                quality = low,
                originalSizeBytes = originalSize,
                compressedSizeBytes = minData.size.toLong(),
                lowQualityWarning = true,
            )
        }

        // At this point, we KNOW minData fits. Use it as baseline safe fallback.
        bestData = minData
        bestQuality = low

        // Binary search for optimal quality
        var iteration = 0
        while (low <= high && iteration < maxIterations) {
            iteration++
            val mid = (low + high) / 2
            val data = compressBitmap(safeBitmap, mid, format)

            if (data.size <= targetBytes) {
                bestData = data
                bestQuality = mid
                low = mid + 1  // try higher quality
            } else {
                high = mid - 1 // need lower quality
            }
        }

        if (safeBitmap !== bitmap) safeBitmap.recycle()

        // Phase 2 — Null-Byte Padding: pad to EXACTLY the user's requested KB
        val userTargetBytes = targetSizeKB.toLong() * 1024L
        val paddedData = padToExactTarget(bestData, userTargetBytes)

        CompressionResult(
            data = paddedData,
            quality = bestQuality,
            originalSizeBytes = originalSize,
            compressedSizeBytes = paddedData.size.toLong(),
            lowQualityWarning = bestQuality < QUALITY_WARNING_THRESHOLD,
        )
    }

    /**
     * Compress [bitmap] at a specific [quality] to a byte array.
     */
    suspend fun compressBitmap(
        bitmap: Bitmap,
        quality: Int,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG,
    ): ByteArray = withContext(Dispatchers.Default) {
        ByteArrayOutputStream().use { baos ->
            bitmap.compress(format, quality.coerceIn(1, 100), baos)
            baos.toByteArray()
        }
    }

    // ───────────────────────────────────────────────────────────────
    // 3.  Format Conversion
    // ───────────────────────────────────────────────────────────────

    /**
     * Supported output formats.
     */
    enum class OutputFormat(
        val displayName: String,
        val extension: String,
        val mimeType: String,
    ) {
        JPEG("JPG", "jpg", "image/jpeg"),
        PNG("PNG", "png", "image/png"),
        WEBP("WEBP", "webp", "image/webp"),
        PDF("PDF", "pdf", "application/pdf"),
        HEIC("HEIC", "heic", "image/heic"),
        BMP("BMP", "bmp", "image/bmp"),
        TIFF("TIFF", "tiff", "image/tiff"),
    }

    /**
     * Convert [bitmap] to the specified [format] and return raw bytes.
     * For PDF, delegates to [bitmapToPdf].
     */
    suspend fun convertFormat(
        bitmap: Bitmap,
        format: OutputFormat,
        quality: Int = 95,
    ): ByteArray = withContext(Dispatchers.Default) {
        when (format) {
            OutputFormat.JPEG -> {
                // JPEG doesn't support transparency — flatten alpha to white
                val jpegBitmap = flattenAlpha(bitmap)
                val result = compressBitmap(jpegBitmap, quality, Bitmap.CompressFormat.JPEG)
                if (jpegBitmap !== bitmap) jpegBitmap.recycle()
                result
            }

            OutputFormat.PNG -> {
                compressBitmap(bitmap, 100, Bitmap.CompressFormat.PNG) // PNG ignores quality
            }

            OutputFormat.WEBP -> {
                compressBitmap(bitmap, quality, Bitmap.CompressFormat.WEBP_LOSSY)
            }

            OutputFormat.PDF -> {
                bitmapToPdf(bitmap)
            }

            OutputFormat.HEIC -> {
                // Android has no HEIC encoder; re-encode as high-quality JPEG
                // This preserves quality while BitmapFactory can decode HEIC input natively (API 26+)
                val heicBitmap = flattenAlpha(bitmap)
                val result = compressBitmap(heicBitmap, quality, Bitmap.CompressFormat.JPEG)
                if (heicBitmap !== bitmap) heicBitmap.recycle()
                result
            }

            OutputFormat.BMP -> {
                bitmapToBmp(bitmap)
            }

            OutputFormat.TIFF -> {
                // Android has no native TIFF encoder; use uncompressed BMP as fallback
                // For production, a TIFF library (e.g. Apache Commons Imaging) would be added
                bitmapToBmp(bitmap)
            }
        }
    }

    // ───────────────────────────────────────────────────────────────
    // 4.  PDF Generation
    // ───────────────────────────────────────────────────────────────

    /**
     * Render [bitmap] onto a single PDF page sized to the bitmap dimensions
     * (in 72 DPI points).
     */
    suspend fun bitmapToPdf(bitmap: Bitmap): ByteArray = withContext(Dispatchers.Default) {
        val document = PdfDocument()
        try {
            val pageInfo = PdfDocument.PageInfo.Builder(
                bitmap.width,
                bitmap.height,
                1,
            ).create()

            val page = document.startPage(pageInfo)
            page.canvas.drawBitmap(bitmap, 0f, 0f, null)
            document.finishPage(page)

            ByteArrayOutputStream().use { baos ->
                document.writeTo(baos)
                baos.toByteArray()
            }
        } finally {
            document.close()
        }
    }

    // ───────────────────────────────────────────────────────────────
    // 5.  Image Metadata
    // ───────────────────────────────────────────────────────────────

    /**
     * Lightweight metadata about an image, read without full decode.
     */
    data class ImageInfo(
        val width: Int,
        val height: Int,
        val mimeType: String?,
        val sizeBytes: Long,
    ) {
        val megaPixels: Float get() = (width.toLong() * height) / 1_000_000f
    }

    /**
     * Read metadata from a [Uri] without decoding the bitmap pixels.
     */
    suspend fun getImageInfo(
        context: Context,
        uri: Uri,
    ): ImageInfo = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver

        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }

        val sizeBytes = resolver.openInputStream(uri)?.use { stream ->
            stream.available().toLong()
        } ?: 0L

        ImageInfo(
            width = options.outWidth,
            height = options.outHeight,
            mimeType = options.outMimeType,
            sizeBytes = sizeBytes,
        )
    }

    // ───────────────────────────────────────────────────────────────
    // 6.  Helper — flatten alpha channel
    // ───────────────────────────────────────────────────────────────

    /**
     * If the bitmap has an alpha channel, composite it onto a white background.
     * Returns the original bitmap if alpha is already opaque.
     */
    private fun flattenAlpha(source: Bitmap): Bitmap {
        if (!source.hasAlpha()) return source

        val flat = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(flat)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(source, 0f, 0f, null)
        return flat
    }

    // ───────────────────────────────────────────────────────────────
    // 6b. Helper — Null-byte padding to exact target size
    // ───────────────────────────────────────────────────────────────

    /**
     * Pad [compressedData] with null bytes (0x00) so the total length
     * equals exactly [targetBytes]. If the compressed data already
     * exceeds the target (impossible-target case), it is returned as-is.
     *
     * The padding bytes sit after the image EOF marker and are ignored
     * by all standard image decoders (JPEG, WEBP, PNG).
     */
    private fun padToExactTarget(compressedData: ByteArray, targetBytes: Long): ByteArray {
        val currentSize = compressedData.size.toLong()
        // Already at or above target — return as-is (impossible-target edge case)
        if (currentSize >= targetBytes) return compressedData

        val paddingNeeded = (targetBytes - currentSize).toInt()
        // Safety: don't allocate absurd padding (cap at 1MB)
        if (paddingNeeded > 1024 * 1024) return compressedData

        val padded = ByteArray(targetBytes.toInt())
        System.arraycopy(compressedData, 0, padded, 0, compressedData.size)
        // Remaining bytes are already 0x00 by default in Kotlin/JVM
        return padded
    }

    // ───────────────────────────────────────────────────────────────
    // 7.  BMP Encoder (uncompressed 24-bit)
    // ───────────────────────────────────────────────────────────────

    /**
     * Encode [bitmap] as a raw 24-bit uncompressed BMP.
     * Android has no built-in BMP encoder, so we write the header manually.
     */
    private fun bitmapToBmp(bitmap: Bitmap): ByteArray {
        val width = bitmap.width
        val height = bitmap.height
        val rowPadding = (4 - (width * 3) % 4) % 4
        val rowSize = width * 3 + rowPadding
        val pixelDataSize = rowSize * height
        val fileSize = 54 + pixelDataSize // 14 (file header) + 40 (DIB header) + pixel data

        val baos = ByteArrayOutputStream(fileSize)

        // ── BMP File Header (14 bytes) ────────────────────────────
        baos.write('B'.code)
        baos.write('M'.code)
        writeLittleEndianInt(baos, fileSize)
        writeLittleEndianShort(baos, 0) // reserved
        writeLittleEndianShort(baos, 0) // reserved
        writeLittleEndianInt(baos, 54)  // pixel data offset

        // ── DIB Header (BITMAPINFOHEADER, 40 bytes) ───────────────
        writeLittleEndianInt(baos, 40)      // header size
        writeLittleEndianInt(baos, width)
        writeLittleEndianInt(baos, height)  // positive = bottom-up
        writeLittleEndianShort(baos, 1)     // color planes
        writeLittleEndianShort(baos, 24)    // bits per pixel
        writeLittleEndianInt(baos, 0)       // no compression
        writeLittleEndianInt(baos, pixelDataSize)
        writeLittleEndianInt(baos, 2835)    // horizontal resolution (72 DPI)
        writeLittleEndianInt(baos, 2835)    // vertical resolution
        writeLittleEndianInt(baos, 0)       // colors in palette
        writeLittleEndianInt(baos, 0)       // important colors

        // ── Pixel data (bottom-up, BGR) ───────────────────────────
        val pixels = IntArray(width)
        for (y in height - 1 downTo 0) {
            bitmap.getPixels(pixels, 0, width, 0, y, width, 1)
            for (pixel in pixels) {
                baos.write(pixel and 0xFF)         // Blue
                baos.write((pixel shr 8) and 0xFF) // Green
                baos.write((pixel shr 16) and 0xFF) // Red
            }
            // Row padding
            for (p in 0 until rowPadding) baos.write(0)
        }

        return baos.toByteArray()
    }

    private fun writeLittleEndianInt(baos: ByteArrayOutputStream, value: Int) {
        baos.write(value and 0xFF)
        baos.write((value shr 8) and 0xFF)
        baos.write((value shr 16) and 0xFF)
        baos.write((value shr 24) and 0xFF)
    }

    private fun writeLittleEndianShort(baos: ByteArrayOutputStream, value: Int) {
        baos.write(value and 0xFF)
        baos.write((value shr 8) and 0xFF)
    }
}
