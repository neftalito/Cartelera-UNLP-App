/** Agrupa carreras, fuentes y documentos de los planes de estudio. */
package com.overcoders.unlpcarteleranotifier.model

enum class StudyCareer(
    val shortName: String,
    val displayName: String,
    val degreeTitle: String,
) {
    ATIC(
        shortName = "ATIC",
        displayName = "Analista en Tecnologías de la Información y la Comunicación",
        degreeTitle = "Analista en Tecnologías de la Información y la Comunicación"
    ),
    APU(
        shortName = "APU",
        displayName = "Analista Programador Universitario",
        degreeTitle = "Analista Programador Universitario"
    ),
    LS(
        shortName = "LS",
        displayName = "Licenciatura en Sistemas",
        degreeTitle = "Licenciado/a en Sistemas"
    ),
    LI(
        shortName = "LI",
        displayName = "Licenciatura en Informática",
        degreeTitle = "Licenciado/a en Informática"
    ),
}

data class StudyPlanSource(
    val career: StudyCareer,
    val planLabel: String,
    val url: String,
) {
    val id: String = "${career.shortName}_$planLabel"
}

data class StudyPlanDocument(
    val source: StudyPlanSource,
    val pageTitle: String,
    val degreeTitle: String,
    val subjectCount: Int,
    val contentHtml: String,
)
