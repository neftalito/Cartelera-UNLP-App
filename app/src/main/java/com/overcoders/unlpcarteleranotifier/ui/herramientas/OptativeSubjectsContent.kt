/** Presenta los selectores y la tabla HTML de materias optativas. */
package com.overcoders.unlpcarteleranotifier.ui.herramientas

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.overcoders.unlpcarteleranotifier.model.OptativeCareer
import com.overcoders.unlpcarteleranotifier.ui.common.HtmlWebView
import com.overcoders.unlpcarteleranotifier.ui.common.LoadingContentBox
import com.overcoders.unlpcarteleranotifier.ui.common.keyedContentLoadingPhase

@Composable
internal fun OptativeSubjectsContent(
    state: OptativeSubjectsUiState,
    onCareerDropdownExpandedChange: (Boolean) -> Unit,
    onYearDropdownExpandedChange: (Boolean) -> Unit,
    onCareerSelected: (OptativeCareer) -> Unit,
    onYearSelected: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(if (state.isFullscreen) 0.dp else 16.dp),
    ) {
        if (!state.isFullscreen) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(1f),
            ) {
                OptativeCareerSelector(
                    selectedCareer = state.selectedCareer,
                    expanded = state.careerDropdownExpanded,
                    onExpandedChange = onCareerDropdownExpandedChange,
                    onCareerSelected = onCareerSelected,
                    modifier = Modifier.weight(3f),
                )

                Spacer(Modifier.width(8.dp))

                OptativeYearSelector(
                    years = state.years,
                    selectedYear = state.selectedYear,
                    expanded = state.yearDropdownExpanded,
                    onExpandedChange = onYearDropdownExpandedChange,
                    onYearSelected = onYearSelected,
                    modifier = Modifier.weight(2f),
                )
            }

            Spacer(Modifier.height(12.dp))
        }

        HerramientasWarning(
            warning = state.warning,
            isFullscreen = state.isFullscreen,
        )

        LoadingContentBox(
            phase = keyedContentLoadingPhase(
                isLoading = state.loading,
                resolvedKey = state.resolvedSelectedQuery,
                requestedKey = state.selectedQuery,
                hasError = state.error != null,
            ),
            initialText = "Cargando materias optativas…",
            refreshContentDescription = "Actualizando materias optativas",
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            val page = state.page
            when {
                page != null -> {
                    HtmlWebView(
                        html = page.contentHtml,
                        baseUrl = page.url,
                        fitContentWidth = true,
                        showScrollMoreHint = true,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                state.unavailableYear != null -> {
                    HerramientasStateMessage(
                        title = "Optativas no disponibles",
                        body = "No se encontró una tabla de materias optativas para " +
                            "${state.selectedCareer.displayName} en ${state.unavailableYear}.",
                    )
                }

                state.error != null -> {
                    HerramientasStateMessage(
                        title = null,
                        body = state.error,
                        isError = true,
                    )
                }

                else -> {
                    HerramientasStateMessage(
                        title = "Sin contenido",
                        body = "No hay una tabla de materias optativas para mostrar.",
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OptativeCareerSelector(
    selectedCareer: OptativeCareer,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onCareerSelected: (OptativeCareer) -> Unit,
    modifier: Modifier = Modifier,
) {
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selectedCareer.shortName,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text("Carrera") },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true)
                .fillMaxWidth(),
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
        ) {
            OptativeCareer.entries.forEach { career ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(career.shortName)
                            Text(
                                text = career.displayName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    onClick = { onCareerSelected(career) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OptativeYearSelector(
    years: List<Int>,
    selectedYear: Int,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onYearSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selectedYear.toString(),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text("Año") },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true)
                .fillMaxWidth(),
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
        ) {
            years.forEach { year ->
                DropdownMenuItem(
                    text = { Text(year.toString()) },
                    onClick = { onYearSelected(year) },
                )
            }
        }
    }
}
