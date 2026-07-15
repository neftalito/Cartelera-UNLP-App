/** Verifica en Android que las claves de anuncios puedan guardarse en estado. */
package com.overcoders.unlpcarteleranotifier.ui

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.overcoders.unlpcarteleranotifier.model.Mensaje
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnuncioLazyListKeyInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun announcementKeyRestoresLazyItemState() {
        val announcement = Mensaje(
            materia = "Matemática 0",
            titulo = "Final - Mesa de julio",
            cuerpoHtml = "",
            fecha = "2026-07-13",
            autor = "Cátedra",
            isAnulado = false,
            adjuntos = emptyList(),
        )
        val restorationTester = StateRestorationTester(composeRule)

        restorationTester.setContent {
            LazyColumn {
                items(
                    items = listOf(announcement),
                    key = Mensaje::anuncioSaveableKey,
                ) {
                    var openingCount by rememberSaveable { mutableIntStateOf(0) }
                    Button(
                        onClick = { openingCount += 1 },
                        modifier = Modifier.testTag(ANNOUNCEMENT_TEST_TAG),
                    ) {
                        Text("Aperturas: $openingCount")
                    }
                }
            }
        }

        composeRule.onNodeWithTag(ANNOUNCEMENT_TEST_TAG).performClick()
        composeRule.onNodeWithText("Aperturas: 1").assertIsDisplayed()

        restorationTester.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithText("Aperturas: 1").assertIsDisplayed()
    }

    private companion object {
        const val ANNOUNCEMENT_TEST_TAG = "announcement"
    }
}
