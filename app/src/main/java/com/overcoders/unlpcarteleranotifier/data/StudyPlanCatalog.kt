/** Declara el catálogo estable de fuentes disponibles para planes de estudio. */
package com.overcoders.unlpcarteleranotifier.data

import com.overcoders.unlpcarteleranotifier.model.StudyCareer
import com.overcoders.unlpcarteleranotifier.model.StudyPlanSource

data class StudyPlanCareerCatalog(
    val career: StudyCareer,
    val plans: List<StudyPlanSource>,
)

object StudyPlanCatalog {
    val careers: List<StudyPlanCareerCatalog> = listOf(
        StudyPlanCareerCatalog(
            career = StudyCareer.ATIC,
            plans = listOf(
                StudyPlanSource(
                    career = StudyCareer.ATIC,
                    planLabel = "2021",
                    url = "https://www.info.unlp.edu.ar/analista-en-tecnologias-de-la-informacion-y-la-comunicacion-plan-2021/"
                ),
                StudyPlanSource(
                    career = StudyCareer.ATIC,
                    planLabel = "2017",
                    url = "https://www.info.unlp.edu.ar/plan-2017-analista-en-tic/"
                )
            )
        ),
        StudyPlanCareerCatalog(
            career = StudyCareer.APU,
            plans = listOf(
                StudyPlanSource(
                    career = StudyCareer.APU,
                    planLabel = "2021",
                    url = "https://www.info.unlp.edu.ar/analista-programador-universitario-plan-2021/"
                ),
                StudyPlanSource(
                    career = StudyCareer.APU,
                    planLabel = "2015",
                    url = "https://www.info.unlp.edu.ar/plan-2015-analista-programador-universitario/"
                )
            )
        ),
        StudyPlanCareerCatalog(
            career = StudyCareer.LS,
            plans = listOf(
                StudyPlanSource(
                    career = StudyCareer.LS,
                    planLabel = "2021",
                    url = "https://www.info.unlp.edu.ar/licenciatura-en-sistemas-plan-2021/"
                ),
                StudyPlanSource(
                    career = StudyCareer.LS,
                    planLabel = "2015",
                    url = "https://www.info.unlp.edu.ar/carreras-gradoarticulo/plan-2015-licenciatura-en-sistema/"
                )
            )
        ),
        StudyPlanCareerCatalog(
            career = StudyCareer.LI,
            plans = listOf(
                StudyPlanSource(
                    career = StudyCareer.LI,
                    planLabel = "2021",
                    url = "https://www.info.unlp.edu.ar/licenciatura-en-informatica-plan-2021/"
                ),
                StudyPlanSource(
                    career = StudyCareer.LI,
                    planLabel = "2015",
                    url = "https://www.info.unlp.edu.ar/carreras-gradoarticulo/2015linuevo/"
                )
            )
        )
    )

    val sources: List<StudyPlanSource> = careers.flatMap { it.plans }
}
