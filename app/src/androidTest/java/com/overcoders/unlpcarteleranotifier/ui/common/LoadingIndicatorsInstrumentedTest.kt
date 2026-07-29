/** Comprueba la representación Compose compartida para cada situación de carga. */
package com.overcoders.unlpcarteleranotifier.ui.common

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test

class LoadingIndicatorsInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun idleShowsContentWithoutLoadingIndicators() {
        composeRule.setContent {
            MaterialTheme {
                LoadingContentBox(
                    phase = ContentLoadingPhase.IDLE,
                    initialText = "Cargando contenido…",
                    refreshContentDescription = "Actualizando contenido",
                    modifier = Modifier.size(240.dp),
                ) {
                    Text("Contenido", Modifier.testTag("content"))
                }
            }
        }

        composeRule.onNodeWithTag("content").assertIsDisplayed()
        composeRule.onAllNodesWithTag(INITIAL_LOADING_TEST_TAG).assertCountEquals(0)
        composeRule.onAllNodesWithTag(REFRESH_LOADING_TEST_TAG).assertCountEquals(0)
    }

    @Test
    fun initialLoadingReplacesContent() {
        composeRule.setContent {
            MaterialTheme {
                LoadingContentBox(
                    phase = ContentLoadingPhase.INITIAL,
                    initialText = "Cargando contenido…",
                    refreshContentDescription = "Actualizando contenido",
                    modifier = Modifier.size(240.dp),
                ) {
                    Text("Contenido", Modifier.testTag("content"))
                }
            }
        }

        composeRule.onNodeWithTag(INITIAL_LOADING_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(INITIAL_LOADING_INDICATOR_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Cargando contenido…").assertIsDisplayed()
        composeRule.onAllNodesWithTag("content").assertCountEquals(0)
        composeRule.onAllNodesWithTag(REFRESH_LOADING_TEST_TAG).assertCountEquals(0)
    }

    @Test
    fun refreshingKeepsContentAndShowsOneBar() {
        composeRule.setContent {
            MaterialTheme {
                LoadingContentBox(
                    phase = ContentLoadingPhase.REFRESHING,
                    initialText = "Cargando contenido…",
                    refreshContentDescription = "Actualizando contenido",
                    modifier = Modifier.size(240.dp),
                ) {
                    Text("Contenido", Modifier.testTag("content"))
                }
            }
        }

        composeRule.onNodeWithTag("content").assertIsDisplayed()
        composeRule.onNodeWithTag(REFRESH_LOADING_TEST_TAG).assertIsDisplayed()
        composeRule.onAllNodesWithTag(REFRESH_LOADING_TEST_TAG).assertCountEquals(1)
        composeRule.onAllNodesWithTag(INITIAL_LOADING_TEST_TAG).assertCountEquals(0)
    }

    @Test
    fun paginationUsesItsOwnCompactIndicator() {
        composeRule.setContent {
            MaterialTheme {
                PaginationLoadingIndicator(text = "Cargando más contenido…")
            }
        }

        composeRule.onNodeWithTag(PAGINATION_LOADING_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(PAGINATION_LOADING_INDICATOR_TEST_TAG)
            .assertIsDisplayed()
            .assertWidthIsEqualTo(24.dp)
            .assertHeightIsEqualTo(24.dp)
        composeRule.onNodeWithText("Cargando más contenido…").assertIsDisplayed()
    }
}
