/** Verifica la política de restauración del listado después de actualizar la cartelera global. */
package com.overcoders.unlpcarteleranotifier.ui.cartelera

import org.junit.Assert.assertEquals
import org.junit.Test

class MateriasFeedStateTest {
    @Test
    fun successfulRefreshWithNewAnnouncementsRequestsTopOfList() {
        val previous = restoration(
            targetOffset = 50,
            index = 23,
            scrollOffset = 37,
            pending = false,
        )

        val updated = listRestorationAfterSuccessfulGlobalRefresh(
            newAnnouncementCount = 5,
            loadedOffset = 20,
            current = previous,
        )

        assertEquals(
            restoration(
                targetOffset = 20,
                index = 0,
                scrollOffset = 0,
                pending = true,
            ),
            updated,
        )
    }

    @Test
    fun successfulRefreshWithoutNewAnnouncementsKeepsRestoration() {
        val previous = restoration(
            targetOffset = 50,
            index = 23,
            scrollOffset = 37,
            pending = false,
        )

        val updated = listRestorationAfterSuccessfulGlobalRefresh(
            newAnnouncementCount = 0,
            loadedOffset = 20,
            current = previous,
        )

        assertEquals(previous, updated)
    }

    private fun restoration(
        targetOffset: Int,
        index: Int,
        scrollOffset: Int,
        pending: Boolean,
    ) = MateriasListRestoration(
        targetOffset = targetOffset,
        index = index,
        scrollOffset = scrollOffset,
        pending = pending,
    )
}
