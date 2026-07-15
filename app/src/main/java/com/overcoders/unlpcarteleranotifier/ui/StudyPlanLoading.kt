/**
 * Ordena y agrupa las fuentes de planes para actualizarlas con prioridad y concurrencia acotada.
 */
package com.overcoders.unlpcarteleranotifier.ui

import com.overcoders.unlpcarteleranotifier.model.StudyPlanSource

internal fun orderStudyPlanSourcesForRefresh(
    sources: List<StudyPlanSource>,
    selectedPlanId: String?,
): List<StudyPlanSource> = sources.sortedBy { source ->
    if (source.id == selectedPlanId) 0 else 1
}

internal const val STUDY_PLAN_MAX_CONCURRENT_REQUESTS = 3

internal data class StudyPlanAvailability(
    val careerShortName: String,
    val planIds: List<String>,
)

internal data class StudyPlanSelection(
    val careerShortName: String?,
    val planId: String?,
)

internal fun reconcileStudyPlanSelection(
    requested: StudyPlanSelection,
    available: List<StudyPlanAvailability>,
    loading: Boolean,
): StudyPlanSelection {
    if (loading) return requested

    val career = available.firstOrNull { it.careerShortName == requested.careerShortName }
        ?: available.firstOrNull()
        ?: return StudyPlanSelection(careerShortName = null, planId = null)
    val planId = requested.planId?.takeIf(career.planIds::contains)
        ?: career.planIds.firstOrNull()

    return StudyPlanSelection(
        careerShortName = career.careerShortName,
        planId = planId,
    )
}

internal fun studyPlanRefreshBatches(
    sources: List<StudyPlanSource>,
    selectedPlanId: String?,
    maxConcurrency: Int = STUDY_PLAN_MAX_CONCURRENT_REQUESTS,
): List<List<StudyPlanSource>> {
    require(maxConcurrency > 0)

    val orderedSources = orderStudyPlanSourcesForRefresh(sources, selectedPlanId)
    val selectedSource = orderedSources.firstOrNull { it.id == selectedPlanId }
    val remainingSources = if (selectedSource == null) {
        orderedSources
    } else {
        orderedSources.filterNot { it.id == selectedSource.id }
    }

    return buildList {
        if (selectedSource != null) {
            // El contenido visible queda disponible antes de iniciar el resto de las descargas.
            add(listOf(selectedSource))
        }
        addAll(remainingSources.chunked(maxConcurrency))
    }
}
