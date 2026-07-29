/** Comprueba la salida temporizada y la cancelación con Atrás al abrir notificaciones. */
package com.overcoders.unlpcarteleranotifier.ui

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class NotificationOpeningStateInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun cancelAppearsAfterFiveSecondsAndLeavesTheOpeningState() {
        composeRule.mainClock.autoAdvance = false
        var cancelled by mutableStateOf(false)

        composeRule.setContent {
            MaterialTheme {
                NotificationOpeningState(onCancel = { cancelled = true })
            }
        }

        composeRule.onAllNodesWithText("Cancelar").assertCountEquals(0)
        composeRule.mainClock.advanceTimeBy(4_000L)
        composeRule.onAllNodesWithText("Cancelar").assertCountEquals(0)

        assertEquals(5_000L, NOTIFICATION_OPEN_CANCEL_DELAY_MILLIS)
        composeRule.mainClock.advanceTimeBy(1_200L)
        composeRule.onNodeWithText("Cancelar").assertIsDisplayed().performClick()

        composeRule.runOnIdle { assertTrue(cancelled) }
    }

    @Test
    fun aNewOpeningRestartsTheFiveSecondTimer() {
        composeRule.mainClock.autoAdvance = false
        var openingKey by mutableIntStateOf(1)

        composeRule.setContent {
            MaterialTheme {
                NotificationOpeningState(
                    openingKey = openingKey,
                    onCancel = {},
                )
            }
        }

        composeRule.mainClock.advanceTimeBy(4_000L)
        composeRule.runOnIdle { openingKey += 1 }
        composeRule.mainClock.advanceTimeBy(4_000L)
        composeRule.onAllNodesWithText("Cancelar").assertCountEquals(0)

        composeRule.mainClock.advanceTimeBy(1_200L)
        composeRule.onNodeWithText("Cancelar").assertIsDisplayed()
    }

    @Test
    fun backCancelsImmediately() {
        var cancelCount by mutableIntStateOf(0)
        var backDispatcher: OnBackPressedDispatcher? = null

        composeRule.setContent {
            backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
            MaterialTheme {
                NotificationOpeningState(onCancel = { cancelCount += 1 })
            }
        }

        composeRule.runOnIdle { requireNotNull(backDispatcher).onBackPressed() }

        composeRule.runOnIdle { assertEquals(1, cancelCount) }
    }
}
