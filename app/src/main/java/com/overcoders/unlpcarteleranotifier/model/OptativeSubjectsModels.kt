/** Agrupa los modelos de carrera y documento para materias optativas. */
package com.overcoders.unlpcarteleranotifier.model

enum class OptativeCareer(
    val slug: String,
    val shortName: String,
    val displayName: String,
) {
    SISTEMAS(
        slug = "sistemas",
        shortName = "LS",
        displayName = "Licenciatura en Sistemas"
    ),
    INFORMATICA(
        slug = "informatica",
        shortName = "LI",
        displayName = "Licenciatura en Informática"
    ),
}

data class OptativeSubjectsPage(
    val year: Int,
    val career: OptativeCareer,
    val url: String,
    val pageTitle: String,
    val contentHtml: String,
)
