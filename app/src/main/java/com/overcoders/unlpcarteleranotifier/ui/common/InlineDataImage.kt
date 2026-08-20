/** Valida y calcula tamaños seguros para imágenes Base64 embebidas en HTML. */
package com.overcoders.unlpcarteleranotifier.ui.common

import kotlin.math.floor
import kotlin.math.sqrt

internal const val MAX_INLINE_IMAGE_BYTES = 2 * 1024 * 1024
internal const val MAX_INLINE_IMAGE_TOTAL_BYTES = 4 * 1024 * 1024
internal const val MAX_INLINE_IMAGES_PER_HTML = 4
internal const val MAX_INLINE_IMAGE_SIDE = 8_192
internal const val MAX_INLINE_IMAGE_SOURCE_PIXELS = 20_000_000L
internal const val MAX_INLINE_IMAGE_BITMAP_PIXELS = 2_000_000L
internal const val MAX_INLINE_IMAGE_TOTAL_BITMAP_PIXELS = 4_000_000L

internal data class InlineDataImagePayload(
    val mimeType: String,
    val encodedData: String,
    val estimatedDecodedBytes: Int,
)

internal data class InlineImageDimensions(
    val width: Int,
    val height: Int,
)

internal fun parseInlineDataImageSource(
    source: String,
    maxDecodedBytes: Int = MAX_INLINE_IMAGE_BYTES,
): InlineDataImagePayload? {
    if (maxDecodedBytes <= 0 || source.isEmpty()) return null

    val trimmed = source.trim()
    val commaIndex = trimmed.indexOf(',')
    if (commaIndex !in 1..64) return null

    val header = trimmed.substring(0, commaIndex)
    if (!header.regionMatches(0, "data:", 0, 5, ignoreCase = true)) return null

    val separatorIndex = header.indexOf(';', startIndex = 5)
    if (separatorIndex <= 5 || header.indexOf(';', separatorIndex + 1) >= 0) return null
    if (!header.substring(separatorIndex + 1).equals("base64", ignoreCase = true)) return null

    val mimeType = normalizeInlineImageMime(header.substring(5, separatorIndex)) ?: return null
    val encodedData = trimmed.substring(commaIndex + 1)
    val maxEncodedChars = minOf(
        Int.MAX_VALUE.toLong(),
        (maxDecodedBytes.toLong() + 2L) / 3L * 4L,
    ).toInt()
    if (encodedData.isEmpty() || encodedData.length > maxEncodedChars) return null

    val estimatedBytes = estimateStrictBase64DecodedBytes(encodedData) ?: return null
    if (estimatedBytes <= 0 || estimatedBytes > maxDecodedBytes) return null

    return InlineDataImagePayload(
        mimeType = mimeType,
        encodedData = encodedData,
        estimatedDecodedBytes = estimatedBytes,
    )
}

internal fun normalizeInlineImageMime(mimeType: String): String? =
    when (mimeType.lowercase()) {
        "image/png" -> "image/png"
        "image/jpeg" -> "image/jpeg"
        "image/webp" -> "image/webp"
        else -> null
    }

internal fun isValidInlineImageSourceDimensions(
    width: Int,
    height: Int,
    maxSide: Int = MAX_INLINE_IMAGE_SIDE,
    maxPixels: Long = MAX_INLINE_IMAGE_SOURCE_PIXELS,
): Boolean {
    if (width <= 0 || height <= 0 || maxSide <= 0 || maxPixels <= 0) return false
    if (width > maxSide || height > maxSide) return false
    return width.toLong() * height.toLong() <= maxPixels
}

internal fun fitInlineImageDimensions(
    sourceWidth: Int,
    sourceHeight: Int,
    maxWidth: Int,
    maxPixels: Long = MAX_INLINE_IMAGE_BITMAP_PIXELS,
): InlineImageDimensions? {
    if (sourceWidth <= 0 || sourceHeight <= 0 || maxWidth <= 0 || maxPixels <= 0) return null

    val sourcePixels = sourceWidth.toLong() * sourceHeight.toLong()
    val scale = minOf(
        1.0,
        maxWidth.toDouble() / sourceWidth.toDouble(),
        sqrt(maxPixels.toDouble() / sourcePixels.toDouble()),
    )
    if (!scale.isFinite() || scale <= 0.0) return null

    var targetWidth = floor(sourceWidth * scale).toInt().coerceAtLeast(1)
    var targetHeight = floor(sourceHeight * scale).toInt().coerceAtLeast(1)
    while (targetWidth.toLong() * targetHeight.toLong() > maxPixels) {
        if (targetWidth >= targetHeight && targetWidth > 1) {
            targetWidth--
        } else if (targetHeight > 1) {
            targetHeight--
        } else {
            return null
        }
    }

    return InlineImageDimensions(targetWidth, targetHeight)
}

internal fun calculateInlineImageSampleSize(
    sourceWidth: Int,
    sourceHeight: Int,
    targetWidth: Int,
    targetHeight: Int,
    maxDecodedPixels: Long = MAX_INLINE_IMAGE_BITMAP_PIXELS,
): Int {
    if (
        sourceWidth <= 0 || sourceHeight <= 0 || targetWidth <= 0 || targetHeight <= 0 ||
        maxDecodedPixels <= 0
    ) {
        return 1
    }

    var targetSampleSize = 1
    while (
        sourceWidth / (targetSampleSize * 2) >= targetWidth &&
        sourceHeight / (targetSampleSize * 2) >= targetHeight
    ) {
        targetSampleSize *= 2
    }

    var pixelSampleSize = 1
    while (estimatedSampledImagePixels(sourceWidth, sourceHeight, pixelSampleSize) > maxDecodedPixels) {
        pixelSampleSize *= 2
    }
    return maxOf(targetSampleSize, pixelSampleSize)
}

internal fun estimatedSampledImagePixels(
    sourceWidth: Int,
    sourceHeight: Int,
    sampleSize: Int,
): Long {
    if (sourceWidth <= 0 || sourceHeight <= 0 || sampleSize <= 0) return Long.MAX_VALUE
    val sampledWidth = (sourceWidth.toLong() + sampleSize - 1L) / sampleSize
    val sampledHeight = (sourceHeight.toLong() + sampleSize - 1L) / sampleSize
    return sampledWidth * sampledHeight
}

internal fun matchesInlineImageSignature(mimeType: String, bytes: ByteArray): Boolean =
    when (mimeType) {
        "image/png" -> bytes.startsWith(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        "image/jpeg" -> bytes.startsWith(0xFF, 0xD8, 0xFF)
        "image/webp" ->
            bytes.startsWith(0x52, 0x49, 0x46, 0x46) &&
                bytes.matchesAt(8, 0x57, 0x45, 0x42, 0x50)
        else -> false
    }

private fun estimateStrictBase64DecodedBytes(encodedData: String): Int? {
    val firstPaddingIndex = encodedData.indexOf('=').let { index ->
        if (index >= 0) index else encodedData.length
    }
    if (firstPaddingIndex == 0) return null

    for (index in 0 until firstPaddingIndex) {
        val char = encodedData[index]
        val valid = char in 'A'..'Z' || char in 'a'..'z' || char in '0'..'9' ||
            char == '+' || char == '/'
        if (!valid) return null
    }

    val paddingCount = encodedData.length - firstPaddingIndex
    if (paddingCount > 2) return null
    for (index in firstPaddingIndex until encodedData.length) {
        if (encodedData[index] != '=') return null
    }

    val remainder = firstPaddingIndex % 4
    if (paddingCount > 0) {
        if (encodedData.length % 4 != 0) return null
        if (paddingCount == 1 && remainder != 3) return null
        if (paddingCount == 2 && remainder != 2) return null
    } else if (remainder == 1) {
        return null
    }

    val decodedBytes = firstPaddingIndex.toLong() / 4L * 3L + when (remainder) {
        2 -> 1L
        3 -> 2L
        else -> 0L
    }
    return decodedBytes.takeIf { it <= Int.MAX_VALUE }?.toInt()
}

private fun ByteArray.startsWith(vararg expected: Int): Boolean = matchesAt(0, *expected)

private fun ByteArray.matchesAt(offset: Int, vararg expected: Int): Boolean {
    if (offset < 0 || size - offset < expected.size) return false
    return expected.indices.all { index ->
        this[offset + index].toInt() and 0xFF == expected[index]
    }
}
