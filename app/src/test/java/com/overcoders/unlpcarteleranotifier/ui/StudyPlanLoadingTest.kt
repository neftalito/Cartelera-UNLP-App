/**
 * Verifica el orden de actualización y la resolución exclusiva de estados de planes.
 */
package com.overcoders.unlpcarteleranotifier.ui

import com.overcoders.unlpcarteleranotifier.model.StudyCareer
import com.overcoders.unlpcarteleranotifier.model.StudyPlanSource
import org.junit.Assert.assertEquals
import org.junit.Test

class StudyPlanLoadingTest {
    @Test
    fun refreshesSelectedPlanFirstWithoutReorderingTheRest() {
        val first = source("2021")
        val selected = source("2015")
        val last = source("2003")

        val result = orderStudyPlanSourcesForRefresh(
            sources = listOf(first, selected, last),
            selectedPlanId = selected.id
        )

        assertEquals(listOf(selected, first, last), result)
    }

    @Test
    fun refreshesTheSelectedPlanBeforeBoundedConcurrentBatches() {
        val sources = listOf(
            source("2021"),
            source("2015"),
            source("2003"),
            source("1995"),
            source("1986"),
        )

        val batches = studyPlanRefreshBatches(
            sources = sources,
            selectedPlanId = sources[1].id,
            maxConcurrency = 2,
        )

        assertEquals(listOf(sources[1]), batches.first())
        assertEquals(listOf(sources[0], sources[2]), batches[1])
        assertEquals(listOf(sources[3], sources[4]), batches[2])
        assertEquals(sources.toSet(), batches.flatten().toSet())
    }

    @Test
    fun boundsEveryBatchWhenTheSelectedPlanDoesNotNeedRefresh() {
        val sources = listOf(
            source("2021"),
            source("2015"),
            source("2003"),
            source("1995"),
        )

        val batches = studyPlanRefreshBatches(
            sources = sources,
            selectedPlanId = "already-fresh",
            maxConcurrency = 3,
        )

        assertEquals(listOf(sources.take(3), sources.drop(3)), batches)
        assertEquals(3, batches.maxOf { it.size })
    }

    @Test
    fun keepsContentWithoutWarningWhenEveryRefreshSucceeds() {
        assertEquals(
            StudyPlansLoadOutcome.CONTENT,
            outcome(documents = 8, attempted = 8),
        )
    }

    @Test
    fun warnsOnceWhenSomeSourcesAreUnavailableButContentExists() {
        assertEquals(
            StudyPlansLoadOutcome.CONTENT_WITH_WARNING,
            outcome(documents = 7, attempted = 8, unavailable = 1),
        )
    }

    @Test
    fun warnsOnceWhenCachedContentSurvivesAnOperationalFailure() {
        assertEquals(
            StudyPlansLoadOutcome.CONTENT_WITH_WARNING,
            outcome(documents = 2, attempted = 2, operationalFailures = 2),
        )
    }

    @Test
    fun reportsOneErrorWhenNoDocumentCouldBeLoaded() {
        assertEquals(
            StudyPlansLoadOutcome.ERROR,
            outcome(documents = 0, attempted = 8, operationalFailures = 8),
        )
    }

    @Test
    fun operationalFailureWinsOverUnavailableWhenNothingCouldBeLoaded() {
        assertEquals(
            StudyPlansLoadOutcome.ERROR,
            outcome(documents = 0, attempted = 8, unavailable = 7, operationalFailures = 1),
        )
    }

    @Test
    fun reportsOneEmptyStateWhenEverySourceIsUnavailable() {
        assertEquals(
            StudyPlansLoadOutcome.EMPTY,
            outcome(documents = 0, attempted = 8, unavailable = 8),
        )
    }

    @Test
    fun reportsOneEmptyStateWhenThereAreNoConfiguredSources() {
        assertEquals(
            StudyPlansLoadOutcome.EMPTY,
            outcome(documents = 0, attempted = 0),
        )
    }

    @Test
    fun keepsRequestedSelectionWhileItsPrioritizedPlanIsStillLoading() {
        val requested = StudyPlanSelection(careerShortName = "LS", planId = "ls-2021")

        val result = reconcileStudyPlanSelection(
            requested = requested,
            available = listOf(
                StudyPlanAvailability(
                    careerShortName = "LI",
                    planIds = listOf("li-2015"),
                )
            ),
            loading = true,
        )

        assertEquals(requested, result)
    }

    @Test
    fun fallsBackOnlyAfterTheRequestedPlanFinishedLoadingAsUnavailable() {
        val result = reconcileStudyPlanSelection(
            requested = StudyPlanSelection(careerShortName = "LS", planId = "ls-2021"),
            available = listOf(
                StudyPlanAvailability(
                    careerShortName = "LI",
                    planIds = listOf("li-2015"),
                )
            ),
            loading = false,
        )

        assertEquals(
            StudyPlanSelection(careerShortName = "LI", planId = "li-2015"),
            result,
        )
    }

    private fun outcome(
        documents: Int,
        attempted: Int,
        unavailable: Int = 0,
        operationalFailures: Int = 0,
    ) = studyPlansLoadOutcome(
        documentCount = documents,
        attemptedSourceCount = attempted,
        unavailableCount = unavailable,
        operationalFailureCount = operationalFailures,
    )

    private fun source(plan: String) = StudyPlanSource(
        career = StudyCareer.LS,
        planLabel = plan,
        url = "https://example.com/$plan"
    )
}
