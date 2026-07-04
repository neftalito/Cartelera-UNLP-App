package com.overcoders.unlpcarteleranotifier.data

import com.overcoders.unlpcarteleranotifier.model.MateriaCatalogItem

private val firstTermRegex = "\\(\\s*primero\\s*\\)".toRegex(RegexOption.IGNORE_CASE)
private val secondTermRegex = "\\(\\s*segundo\\s*\\)".toRegex(RegexOption.IGNORE_CASE)

fun MateriaCatalogItem.withFriendlyTermName(): MateriaCatalogItem {
    val updatedName = nombre
        .replace(firstTermRegex, "(Primer semestre)")
        .replace(secondTermRegex, "(Segundo semestre)")

    if (updatedName == nombre) return this
    return copy(nombre = updatedName)
}
