package com.schoolsync.parent.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Downscale and re-encode a picked image before upload.
 *
 * ── Why this exists ──────────────────────────────────────────────────────────
 * The Parent app had no image compressor. The only one in the codebase is the
 * Teacher app's StoryVideoCompressor, which handles video and is in the wrong
 * app besides. A modern phone camera produces 4–12 MB JPEGs; a parent attaching
 * three photos of a fee receipt over a school-gate connection would otherwise
 * upload ~30 MB, and Storage costs are per-tenant across every school.
 *
 * ── Why it decodes twice ─────────────────────────────────────────────────────
 * Pass one reads bounds ONLY (`inJustDecodeBounds`), so a 12 MP image never
 * lands in memory at full size. Pass two decodes with `inSampleSize`, which the
 * platform applies during decode. Decoding first and scaling after is the
 * obvious version and is how this OOMs on a cheap device.
 *
 * ── EXIF ─────────────────────────────────────────────────────────────────────
 * Rotation is read from EXIF and baked into the pixels, then the rest of the
 * metadata is dropped by re-encoding. That is deliberate on both counts: a
 * sideways receipt is unreadable, and the discarded metadata includes GPS
 * coordinates. Uploading a parent's home location with a photo of a bus pass is
 * a disclosure nobody asked for and nobody would notice.
 */
object ImageCompressor {

    /** Longest edge after downscale. Comfortably readable for a document photo. */
    private const val MAX_DIMENSION = 1600

    /** JPEG quality. 82 is the knee — below it, text in a receipt starts to mush. */
    private const val QUALITY = 82

    /** Hard ceiling per attachment, enforced after encoding. */
    const val MAX_BYTES = 2 * 1024 * 1024   // 2 MB

    /** What the caller gets back. [bytes] is ready to upload as image/jpeg. */
    data class Result(
        val bytes: ByteArray,
        val width: Int,
        val height: Int,
        val originalBytes: Long
    ) {
        // ByteArray in a data class needs these; the generated ones compare by
        // reference and would silently break any equality check.
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Result) return false
            return bytes.contentEquals(other.bytes) &&
                width == other.width && height == other.height &&
                originalBytes == other.originalBytes
        }

        override fun hashCode(): Int {
            var h = bytes.contentHashCode()
            h = 31 * h + width
            h = 31 * h + height
            h = 31 * h + originalBytes.hashCode()
            return h
        }
    }

    /**
     * Compress [uri] to a JPEG under [MAX_BYTES].
     *
     * Returns a failure rather than throwing, so a single unreadable pick does
     * not take down a compose screen the parent has already typed into.
     */
    suspend fun compress(context: Context, uri: Uri): kotlin.Result<Result> =
        withContext(Dispatchers.IO) {
            try {
                val resolver = context.contentResolver

                val originalBytes = runCatching {
                    resolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
                }.getOrNull() ?: -1L

                // Pass 1 — bounds only. Nothing is allocated for the pixels.
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                    ?: return@withContext kotlin.Result.failure(
                        IllegalStateException("Could not open that image.")
                    )
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                    return@withContext kotlin.Result.failure(
                        IllegalStateException("That file is not an image we can read.")
                    )
                }

                // Pass 2 — decode subsampled.
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight)
                }
                var bmp = resolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, opts)
                } ?: return@withContext kotlin.Result.failure(
                    IllegalStateException("Could not read that image.")
                )

                bmp = applyExifRotation(context, uri, bmp)
                bmp = scaleToMax(bmp)

                // Encode, stepping quality down only if the cap is still missed.
                // Most photos clear 2 MB on the first pass; the loop is for the
                // occasional dense, noisy image.
                var quality = QUALITY
                var out = ByteArrayOutputStream()
                bmp.compress(Bitmap.CompressFormat.JPEG, quality, out)
                while (out.size() > MAX_BYTES && quality > 45) {
                    quality -= 12
                    out = ByteArrayOutputStream()
                    bmp.compress(Bitmap.CompressFormat.JPEG, quality, out)
                }

                val w = bmp.width
                val h = bmp.height
                bmp.recycle()

                if (out.size() > MAX_BYTES) {
                    return@withContext kotlin.Result.failure(
                        IllegalStateException("That image is too large even after compressing. Try a smaller photo.")
                    )
                }

                kotlin.Result.success(
                    Result(bytes = out.toByteArray(), width = w, height = h, originalBytes = originalBytes)
                )
            } catch (e: OutOfMemoryError) {
                // Genuinely possible on a low-RAM device with a huge panorama.
                // Caught by name because it is an Error, not an Exception.
                kotlin.Result.failure(IllegalStateException("Not enough memory to process that image."))
            } catch (e: Exception) {
                kotlin.Result.failure(e)
            }
        }

    /** Largest power-of-two subsample that still leaves us above MAX_DIMENSION. */
    private fun sampleSizeFor(width: Int, height: Int): Int {
        var sample = 1
        var longest = max(width, height)
        while (longest / 2 >= MAX_DIMENSION) {
            longest /= 2
            sample *= 2
        }
        return sample
    }

    private fun scaleToMax(src: Bitmap): Bitmap {
        val longest = max(src.width, src.height)
        if (longest <= MAX_DIMENSION) return src
        val ratio = MAX_DIMENSION.toFloat() / longest
        val w = (src.width * ratio).roundToInt().coerceAtLeast(1)
        val h = (src.height * ratio).roundToInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(src, w, h, true)
        if (scaled !== src) src.recycle()
        return scaled
    }

    /**
     * Bake EXIF orientation into the pixels.
     *
     * Re-encoding drops EXIF, so an unrotated result would display sideways
     * everywhere it is later shown. Failure here is non-fatal — an upright
     * photo with no rotation applied beats no photo at all.
     */
    private fun applyExifRotation(context: Context, uri: Uri, src: Bitmap): Bitmap {
        val degrees = try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                when (ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
                )) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        } catch (e: Exception) {
            0f
        }
        if (degrees == 0f) return src

        return try {
            val m = Matrix().apply { postRotate(degrees) }
            val rotated = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
            if (rotated !== src) src.recycle()
            rotated
        } catch (e: OutOfMemoryError) {
            src
        }
    }
}
