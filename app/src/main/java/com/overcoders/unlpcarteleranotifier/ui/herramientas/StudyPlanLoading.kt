/** Resuelve la selección y decide si el único plan solicitado necesita red. */
package com.overcoders.unlpcarteleranotifier.ui.herramientas

import com.overcoders.unlpcarteleranotifier.data.CachedValue
import com.overcoders.unlpcarteleranotifier.data.ContentCachePolicy

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
): StudyPlanSelection {
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

internal fun shouldFetchStudyPlan(
    cached: CachedValue<*>?,
    forceRefresh: Boolean,
    nowEpochMillis: Long = System.currentTimeMillis(),
): Boolean = forceRefresh || cached?.isFresh(
    ttlMillis = ContentCachePolicy.STUDY_PLANS_TTL_MILLIS,
    nowEpochMillis = nowEpochMillis,
) != true
