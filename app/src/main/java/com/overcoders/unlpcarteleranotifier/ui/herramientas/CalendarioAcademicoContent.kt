/** Presenta el selector y el documento HTML del calendario académico. */
package com.overcoders.unlpcarteleranotifier.ui.herramientas

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.overcoders.unlpcarteleranotifier.ui.common.HtmlWebView
import com.overcoders.unlpcarteleranotifier.ui.common.LoadingContentBox
import com.overcoders.unlpcarteleranotifier.ui.common.keyedContentLoadingPhase

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CalendarioAcademicoContent(
    state: CalendarioAcademicoUiState,
    onDropdownExpandedChange: (Boolean) -> Unit,
    onYearSelected: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(if (state.isFullscreen) 0.dp else 16.dp),
    ) {
        if (!state.isFullscreen) {
            ExposedDropdownMenuBox(
                expanded = state.dropdownExpanded,
                onExpandedChange = onDropdownExpandedChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(1f),
            ) {
                OutlinedTextField(
                    value = state.selectedYear.toString(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Año") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(
                            expanded = state.dropdownExpanded,
                        )
                    },
                    modifier = Modifier
                        .menuAnchor(
                            ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                            enabled = true,
                        )
                        .fillMaxWidth(),
                )

                ExposedDropdownMenu(
                    expanded = state.dropdownExpanded,
                    onDismissRequest = { onDropdownExpandedChange(false) },
                ) {
                    state.years.forEach { year ->
                        DropdownMenuItem(
                            text = { Text(year.toString()) },
                            onClick = { onYearSelected(year) },
                        )
                    }
                }
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
                resolvedKey = state.resolvedSelectedYear,
                requestedKey = state.selectedYear,
                hasError = state.error != null,
            ),
            initialText = "Cargando calendario académico ${state.selectedYear}…",
            refreshContentDescription = "Actualizando calendario académico ${state.selectedYear}",
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            val calendario = state.calendario
            when {
                calendario != null -> {
                    HtmlWebView(
                        html = calendario.contenidoHtml,
                        baseUrl = calendario.url,
                        fitContentWidth = true,
                        showScrollMoreHint = true,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                state.unavailableYear != null -> {
                    HerramientasStateMessage(
                        title = "Calendario ${state.unavailableYear} no disponible",
                        body = "El calendario del año seleccionado no está disponible.",
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
                        body = "No se encontró información para el año seleccionado.",
                    )
                }
            }
        }
    }
}
