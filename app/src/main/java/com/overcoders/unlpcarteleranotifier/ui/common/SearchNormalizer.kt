/** Normaliza texto y acentos para búsquedas locales consistentes. */
package com.overcoders.unlpcarteleranotifier.ui.common

import java.text.Normalizer
import java.util.Locale

private val combiningMarks = Regex("\\p{M}+")
private val repeatedWhitespace = Regex("\\s+")

internal fun String.normalizeForSearch(): String =
    Normalizer.normalize(replace('\u00A0', ' '), Normalizer.Form.NFD)
        .replace(combiningMarks, "")
        .lowercase(Locale.ROOT)
        .replace(repeatedWhitespace, " ")
        .trim()
