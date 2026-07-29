/** Verifica la selección por catálogo y la decisión de red para un único plan. */
package com.overcoders.unlpcarteleranotifier.ui

import com.overcoders.unlpcarteleranotifier.data.CachedValue
import com.overcoders.unlpcarteleranotifier.data.ContentCachePolicy
import com.overcoders.unlpcarteleranotifier.data.StudyPlanCatalog
import com.overcoders.unlpcarteleranotifier.ui.herramientas.StudyPlanAvailability
import com.overcoders.unlpcarteleranotifier.ui.herramientas.StudyPlanSelection
import com.overcoders.unlpcarteleranotifier.ui.herramientas.reconcileStudyPlanSelection
import com.overcoders.unlpcarteleranotifier.ui.herramientas.shouldFetchStudyPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyPlanLoadingTest {
    private val available = StudyPlanCatalog.careers.map { catalog ->
        StudyPlanAvailability(
            careerShortName = catalog.career.shortName,
            planIds = catalog.plans.map { it.id },
        )
    }

    @Test
    fun keepsAConfiguredPlanSelectedBeforeItsDocumentIsDownloaded() {
        val source = StudyPlanCatalog.careers
            .first { it.plans.size > 1 }
            .plans[1]
        val requested = StudyPlanSelection(
            careerShortName = source.career.shortName,
            planId = source.id,
        )

        assertEquals(
            requested,
            reconcileStudyPlanSelection(requested, available),
        )
    }

    @Test
    fun fallsBackToTheFirstPlanOnlyWhenTheSavedPlanIsNoLongerConfigured() {
        val career = StudyPlanCatalog.careers.first { it.plans.isNotEmpty() }

        val result = reconcileStudyPlanSelection(
            requested = StudyPlanSelection(
                careerShortName = career.career.shortName,
                planId = "plan-eliminado",
            ),
            available = available,
        )

        assertEquals(
            StudyPlanSelection(
                careerShortName = career.career.shortName,
                planId = career.plans.first().id,
            ),
            result,
        )
    }

    @Test
    fun fallsBackToTheFirstCatalogEntryWhenTheSavedCareerWasRemoved() {
        val firstCareer = StudyPlanCatalog.careers.first()

        val result = reconcileStudyPlanSelection(
            requested = StudyPlanSelection(
                careerShortName = "CARRERA_ELIMINADA",
                planId = "plan-eliminado",
            ),
            available = available,
        )

        assertEquals(
            StudyPlanSelection(
                careerShortName = firstCareer.career.shortName,
                planId = firstCareer.plans.first().id,
            ),
            result,
        )
    }

    @Test
    fun usesAFreshCachedPlanWithoutNetwork() {
        val now = 1_000_000_000L
        val cached = CachedValue(
            value = "plan",
            savedAtEpochMillis = now - ContentCachePolicy.STUDY_PLANS_TTL_MILLIS,
        )

        assertFalse(
            shouldFetchStudyPlan(
                cached = cached,
                forceRefresh = false,
                nowEpochMillis = now,
            )
        )
    }

    @Test
    fun fetchesOnlyWhenTheSelectedPlanIsMissingStaleOrExplicitlyRefreshed() {
        val now = 1_000_000_000L
        val fresh = CachedValue(value = "plan", savedAtEpochMillis = now)
        val stale = CachedValue(
            value = "plan",
            savedAtEpochMillis = now - ContentCachePolicy.STUDY_PLANS_TTL_MILLIS - 1,
        )

        assertTrue(
            shouldFetchStudyPlan(
                cached = null,
                forceRefresh = false,
                nowEpochMillis = now,
            )
        )
        assertTrue(
            shouldFetchStudyPlan(
                cached = stale,
                forceRefresh = false,
                nowEpochMillis = now,
            )
        )
        assertTrue(
            shouldFetchStudyPlan(
                cached = fresh,
                forceRefresh = true,
                nowEpochMillis = now,
            )
        )
    }
}
