/**
 * Verifica matching, paginación y restauración de destinos de cartelera.
 */
package com.overcoders.unlpcarteleranotifier.ui.cartelera

import com.overcoders.unlpcarteleranotifier.model.CarteleraNotificationTarget
import com.overcoders.unlpcarteleranotifier.model.Mensaje
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CarteleraNotificationMatcherTest {
    private val message = Mensaje(
        materia = "Algoritmos",
        titulo = "Parcial",
        cuerpoHtml = "",
        fecha = "13/07/2026",
        autor = "Cátedra",
        isAnulado = false,
        adjuntos = emptyList()
    )

    @Test
    fun matchesNormalizedSubjectAndOptionalAuthor() {
        val target = CarteleraNotificationTarget(
            materiaId = "1",
            materia = " algoritmos ",
            titulo = "Parcial",
            fecha = "13/07/2026",
            autor = "",
            resumen = "",
            isAnulado = false
        )

        assertEquals(message, findAnuncioForNotification(listOf(message), target))
    }

    @Test
    fun matchesFiveAnnouncementsObservedInCurrentNotificationBatch() {
        val targets = listOf(
            notificationTarget(
                materiaId = "64",
                materia = "Matemática 2",
                titulo = "Información cursada 2026",
                fecha = "03/08/2026 16:11",
                autor = "María Mercedes Olea",
            ),
            notificationTarget(
                materiaId = "295",
                materia = "Aprendizaje Automático Profundo (Deep Learning)",
                titulo = "Inicio de clases",
                fecha = "03/08/2026 15:28",
                autor = "Laura Lanzarini",
            ),
            notificationTarget(
                materiaId = "203",
                materia = "Conceptos y Aplicaciones de Big Data",
                titulo = "Comienzo de cursada",
                fecha = "03/08/2026 14:04",
                autor = "Waldo Hasperue",
            ),
            notificationTarget(
                materiaId = "344",
                materia = "Minería de Datos y Aprendizaje Automático",
                titulo = "Inicio de cursada",
                fecha = "03/08/2026 13:37",
                autor = "Franco Ronchetti",
            ),
            notificationTarget(
                materiaId = "61",
                materia = "Organización de Computadoras",
                titulo = "Resultados Mesa Final de Julio",
                fecha = "03/08/2026 11:42",
                autor = "Horacio Villagarcia",
            ),
        )
        val announcements = targets.map { target ->
            Mensaje(
                materia = target.materia,
                titulo = target.titulo,
                cuerpoHtml = "",
                fecha = target.fecha,
                autor = target.autor,
                isAnulado = target.isAnulado,
                adjuntos = emptyList(),
            )
        }

        targets.forEachIndexed { index, target ->
            assertEquals(
                announcements[index],
                findAnuncioForNotification(announcements, target),
            )
        }
    }

    @Test
    fun opensImageOnlyAnnouncementReceivedFromNotification() {
        val announcement = Mensaje(
            materia = "Organización de Computadoras",
            titulo = "Inicio de clases teóricas",
            cuerpoHtml = "<p><img src=\"data:image/png;base64,iVBORw0KGgo=\" /></p>",
            fecha = "15/08/2026 11:00",
            autor = "Javier Giacomantone",
            isAnulado = false,
            adjuntos = emptyList(),
        )
        val target = notificationTarget(
            materiaId = "61",
            materia = announcement.materia,
            titulo = announcement.titulo,
            fecha = announcement.fecha,
            autor = announcement.autor,
        )

        assertEquals(
            announcement,
            findAnuncioForNotification(listOf(announcement), target),
        )
    }

    @Test
    fun rejectsDifferentAuthorWhenTargetProvidesOne() {
        val target = CarteleraNotificationTarget(
            materiaId = "1",
            materia = "Algoritmos",
            titulo = "Parcial",
            fecha = "13/07/2026",
            autor = "Otra cátedra",
            resumen = "",
            isAnulado = false
        )

        assertNull(findAnuncioForNotification(listOf(message), target))
    }

    @Test
    fun localRestorationUsesExactAnnouncementKeyAndPreservesPage() {
        val other = message.copy(cuerpoHtml = "Otro contenido")
        val target = message.toLocalRestorationTarget(materiaId = "1", pageIndex = 8)

        assertEquals(message, findAnuncioForNotification(listOf(other, message), target))
        assertEquals(8, target.localSelectionRestoration()?.pageIndex)
    }

    @Test
    fun waitsForRefreshBeforeOpeningCachedAnnouncementWithStableIdentity() {
        val cancellationTarget = cancellationTargetForMessage()

        // Una anulación mantiene la identidad del anuncio, por lo que el cache viejo coincide.
        assertEquals(message, findAnuncioForNotification(listOf(message), cancellationTarget))
        assertFalse(
            canResolveCarteleraTargetFromCurrentAnnouncements(
                isLoading = true,
                refreshFailed = false,
                expectedFilterLoaded = false,
                targetRefreshCompleted = true,
            )
        )
    }

    @Test
    fun startsTargetRefreshBeforeUsingAlreadyLoadedAnnouncements() {
        assertFalse(
            canResolveCarteleraTargetFromCurrentAnnouncements(
                isLoading = false,
                refreshFailed = false,
                expectedFilterLoaded = true,
                targetRefreshCompleted = false,
            )
        )
    }

    @Test
    fun allowsCachedAnnouncementAsFallbackAfterRefreshFailure() {
        assertTrue(
            canResolveCarteleraTargetFromCurrentAnnouncements(
                isLoading = false,
                refreshFailed = true,
                expectedFilterLoaded = false,
                targetRefreshCompleted = true,
            )
        )
    }

    @Test
    fun resolvesAnnouncementAfterExpectedFilterRefreshCompletes() {
        assertTrue(
            canResolveCarteleraTargetFromCurrentAnnouncements(
                isLoading = false,
                refreshFailed = false,
                expectedFilterLoaded = true,
                targetRefreshCompleted = true,
            )
        )
    }

    @Test
    fun cancelledCurrentRefreshReturnsToRetryableState() {
        assertEquals(
            CarteleraTargetRefreshState.REQUIRED,
            notificationRefreshStateAfterCancellation(
                currentState = CarteleraTargetRefreshState.IN_PROGRESS,
                isLatestAttempt = true,
                targetStillPending = true,
            ),
        )
    }

    @Test
    fun staleCancellationDoesNotOverrideTheCurrentRefresh() {
        assertEquals(
            CarteleraTargetRefreshState.IN_PROGRESS,
            notificationRefreshStateAfterCancellation(
                currentState = CarteleraTargetRefreshState.IN_PROGRESS,
                isLatestAttempt = false,
                targetStillPending = true,
            ),
        )
    }

    @Test
    fun continuesNotificationSearchWhilePagesRemainWithinLimit() {
        assertEquals(
            NotificationSearchDecision.LOAD_NEXT_PAGE,
            notificationSearchDecision(
                hasMore = true,
                automaticallyLoadedPages = 4,
                maximumAutomaticPages = 5,
            )
        )
    }

    @Test
    fun stopsNotificationSearchAtAutomaticPageLimit() {
        assertEquals(
            NotificationSearchDecision.SEARCH_LIMIT_REACHED,
            notificationSearchDecision(
                hasMore = true,
                automaticallyLoadedPages = 5,
                maximumAutomaticPages = 5,
            )
        )
    }

    @Test
    fun reportsMissingNotificationWhenFeedIsExhausted() {
        assertEquals(
            NotificationSearchDecision.NOT_FOUND,
            notificationSearchDecision(
                hasMore = false,
                automaticallyLoadedPages = 0,
            )
        )
    }

    @Test
    fun retriesGloballyWhenFilteredFeedIsExhausted() {
        assertEquals(
            NotificationSearchDecision.RETRY_GLOBAL_FEED,
            notificationSearchDecision(
                hasMore = false,
                automaticallyLoadedPages = 0,
                searchedWithMateriaFilter = true,
                globalFallbackAttempted = false,
            )
        )
    }

    @Test
    fun retriesGloballyWhenFilteredSearchReachesAutomaticLimit() {
        assertEquals(
            NotificationSearchDecision.RETRY_GLOBAL_FEED,
            notificationSearchDecision(
                hasMore = true,
                automaticallyLoadedPages = 5,
                searchedWithMateriaFilter = true,
                globalFallbackAttempted = false,
                maximumAutomaticPages = 5,
            )
        )
    }

    @Test
    fun doesNotRetryGloballyAfterFallbackWasAlreadyAttempted() {
        assertEquals(
            NotificationSearchDecision.NOT_FOUND,
            notificationSearchDecision(
                hasMore = false,
                automaticallyLoadedPages = 0,
                searchedWithMateriaFilter = true,
                globalFallbackAttempted = true,
            )
        )
        assertEquals(
            NotificationSearchDecision.SEARCH_LIMIT_REACHED,
            notificationSearchDecision(
                hasMore = true,
                automaticallyLoadedPages = 5,
                searchedWithMateriaFilter = true,
                globalFallbackAttempted = true,
                maximumAutomaticPages = 5,
            )
        )
    }

    @Test
    fun doesNotUseGlobalFallbackForUnfilteredSearch() {
        assertEquals(
            NotificationSearchDecision.NOT_FOUND,
            notificationSearchDecision(
                hasMore = false,
                automaticallyLoadedPages = 0,
                searchedWithMateriaFilter = false,
                globalFallbackAttempted = false,
            )
        )
    }

    @Test
    fun doesNotUseGlobalFallbackForLocalSelectionRestoration() {
        assertEquals(
            NotificationSearchDecision.NOT_FOUND,
            notificationSearchDecision(
                hasMore = false,
                automaticallyLoadedPages = 0,
                searchedWithMateriaFilter = true,
                globalFallbackAttempted = false,
                isLocalRestoration = true,
            )
        )
    }

    @Test
    fun usesGlobalFeedWhenTargetHasNoKnownMateriaId() {
        assertNull(
            notificationTargetFilterId(
                targetMateriaId = null,
                availableMateriaIds = setOf("1"),
            )
        )
        assertNull(
            notificationTargetFilterId(
                targetMateriaId = "99",
                availableMateriaIds = setOf("1"),
            )
        )
        assertEquals(
            "1",
            notificationTargetFilterId(
                targetMateriaId = "1",
                availableMateriaIds = setOf("1"),
            )
        )
        assertNull(
            notificationTargetFilterId(
                targetMateriaId = "1",
                availableMateriaIds = setOf("1"),
                useGlobalFallback = true,
            )
        )
    }

    private fun cancellationTargetForMessage() = CarteleraNotificationTarget(
        materiaId = "1",
        materia = message.materia,
        titulo = message.titulo,
        fecha = message.fecha,
        autor = message.autor,
        resumen = "La publicación cambió",
        isAnulado = true,
    )

    private fun notificationTarget(
        materiaId: String,
        materia: String,
        titulo: String,
        fecha: String,
        autor: String,
    ) = CarteleraNotificationTarget(
        materiaId = materiaId,
        materia = materia,
        titulo = titulo,
        fecha = fecha,
        autor = autor,
        resumen = "",
        isAnulado = false,
    )
}
