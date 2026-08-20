/** Decodifica imágenes Base64 permitidas para HtmlCompat sin realizar accesos de red. */
package com.overcoders.unlpcarteleranotifier.ui.common

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.text.Html
import android.util.Base64
import androidx.core.graphics.drawable.toDrawable

internal class InlineDataImageGetter(
    private val resources: Resources,
    private val maxWidthPx: Int,
    private val maxImageBytes: Int = MAX_INLINE_IMAGE_BYTES,
    private val maxImages: Int = MAX_INLINE_IMAGES_PER_HTML,
    private val maxTotalBytes: Int = MAX_INLINE_IMAGE_TOTAL_BYTES,
    private val maxTotalBitmapPixels: Long = MAX_INLINE_IMAGE_TOTAL_BITMAP_PIXELS,
) : Html.ImageGetter {
    private val decodedBySource = mutableMapOf<String, Drawable?>()
    private var processedImages = 0
    private var processedBytes = 0L
    private var publishedBitmapPixels = 0L

    override fun getDrawable(source: String?): Drawable? {
        if (source == null || maxWidthPx <= 0) return null
        if (decodedBySource.containsKey(source)) return decodedBySource[source]

        val drawable = decodeWithinBudget(source)
        decodedBySource[source] = drawable
        return drawable
    }

    private fun decodeWithinBudget(source: String): Drawable? {
        val payload = parseInlineDataImageSource(source, maxImageBytes) ?: return null
        if (processedImages >= maxImages) return null
        if (processedBytes + payload.estimatedDecodedBytes > maxTotalBytes.toLong()) return null

        processedImages++
        processedBytes += payload.estimatedDecodedBytes

        val bytes = try {
            Base64.decode(payload.encodedData, Base64.NO_WRAP)
        } catch (_: IllegalArgumentException) {
            return null
        }
        if (bytes.size != payload.estimatedDecodedBytes || bytes.size > maxImageBytes) return null
        if (!matchesInlineImageSignature(payload.mimeType, bytes)) return null

        val remainingBitmapPixels = maxTotalBitmapPixels - publishedBitmapPixels
        if (remainingBitmapPixels <= 0) return null
        val drawable = decodeBitmapDrawable(
            declaredMimeType = payload.mimeType,
            bytes = bytes,
            maxBitmapPixels = remainingBitmapPixels,
        ) ?: return null
        val bitmapPixels = drawable.bitmap.width.toLong() * drawable.bitmap.height.toLong()
        if (publishedBitmapPixels + bitmapPixels > maxTotalBitmapPixels) {
            drawable.bitmap.recycle()
            return null
        }

        publishedBitmapPixels += bitmapPixels
        return drawable
    }

    private fun decodeBitmapDrawable(
        declaredMimeType: String,
        bytes: ByteArray,
        maxBitmapPixels: Long,
    ): BitmapDrawable? {
        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOptions)

        val actualMimeType = boundsOptions.outMimeType
            ?.let(::normalizeInlineImageMime)
            ?: return null
        if (actualMimeType != declaredMimeType) return null
        if (!isValidInlineImageSourceDimensions(boundsOptions.outWidth, boundsOptions.outHeight)) {
            return null
        }

        val targetDimensions = fitInlineImageDimensions(
            sourceWidth = boundsOptions.outWidth,
            sourceHeight = boundsOptions.outHeight,
            maxWidth = maxWidthPx,
        ) ?: return null
        val sampleSize = calculateInlineImageSampleSize(
            sourceWidth = boundsOptions.outWidth,
            sourceHeight = boundsOptions.outHeight,
            targetWidth = targetDimensions.width,
            targetHeight = targetDimensions.height,
            maxDecodedPixels = minOf(MAX_INLINE_IMAGE_BITMAP_PIXELS, maxBitmapPixels),
        )
        val estimatedBitmapPixels = estimatedSampledImagePixels(
            sourceWidth = boundsOptions.outWidth,
            sourceHeight = boundsOptions.outHeight,
            sampleSize = sampleSize,
        )
        if (estimatedBitmapPixels > maxBitmapPixels) return null

        var decodedBitmap: Bitmap? = null
        return try {
            decodedBitmap = BitmapFactory.decodeByteArray(
                bytes,
                0,
                bytes.size,
                BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inScaled = false
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                },
            ) ?: return null

            val decodedPixels = decodedBitmap.width.toLong() * decodedBitmap.height.toLong()
            if (decodedPixels > MAX_INLINE_IMAGE_BITMAP_PIXELS || decodedPixels > maxBitmapPixels) {
                decodedBitmap.recycle()
                return null
            }

            decodedBitmap.toDrawable(resources).apply {
                setBounds(0, 0, targetDimensions.width, targetDimensions.height)
                isFilterBitmap = true
            }
        } catch (_: IllegalArgumentException) {
            decodedBitmap?.takeUnless(Bitmap::isRecycled)?.recycle()
            null
        } catch (_: OutOfMemoryError) {
            decodedBitmap?.takeUnless(Bitmap::isRecycled)?.recycle()
            null
        }
    }
}
