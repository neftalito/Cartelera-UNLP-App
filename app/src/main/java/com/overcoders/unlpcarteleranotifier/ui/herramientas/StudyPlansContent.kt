/** Presenta selectores, estados y el documento HTML de planes de estudio. */
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
import com.overcoders.unlpcarteleranotifier.model.StudyCareer
import com.overcoders.unlpcarteleranotifier.model.StudyPlanSource
import com.overcoders.unlpcarteleranotifier.ui.common.HtmlWebView
import com.overcoders.unlpcarteleranotifier.ui.common.LoadingContentBox
import com.overcoders.unlpcarteleranotifier.ui.common.contentLoadingPhase
import com.overcoders.unlpcarteleranotifier.ui.common.keyedContentLoadingPhase

@Composable
internal fun StudyPlansContent(
    state: StudyPlansUiState,
    onCareerDropdownExpandedChange: (Boolean) -> Unit,
    onPlanDropdownExpandedChange: (Boolean) -> Unit,
    onCareerSelected: (StudyCareer) -> Unit,
    onPlanSelected: (StudyPlanSource) -> Unit,
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
                CareerSelector(
                    careerGroups = state.careerGroups,
                    selectedCareer = state.selectedCareerGroup?.career,
                    expanded = state.careerDropdownExpanded,
                    onExpandedChange = onCareerDropdownExpandedChange,
                    onCareerSelected = onCareerSelected,
                    modifier = Modifier.weight(3f),
                )

                Spacer(Modifier.width(8.dp))

                PlanSelector(
                    plans = state.selectedCareerGroup?.plans.orEmpty(),
                    selectedPlan = state.selectedPlanSource,
                    expanded = state.planDropdownExpanded,
                    onExpandedChange = onPlanDropdownExpandedChange,
                    onPlanSelected = onPlanSelected,
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
            phase = state.selectedPlanSource?.let { source ->
                keyedContentLoadingPhase(
                    isLoading = state.loading,
                    resolvedKey = state.resolvedPlanId,
                    requestedKey = source.id,
                    hasError = state.error != null,
                )
            } ?: contentLoadingPhase(
                isLoading = false,
                hasResolvedContent = true,
            ),
            initialText = state.selectedPlanSource?.let { source ->
                "Cargando plan ${source.planLabel}…"
            } ?: "Cargando plan de estudio…",
            refreshContentDescription = state.selectedPlanSource?.let { source ->
                "Actualizando plan ${source.planLabel}"
            } ?: "Actualizando plan de estudio",
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            val selectedPlan = state.selectedPlan
            when {
                state.error != null -> {
                    HerramientasStateMessage(
                        title = null,
                        body = state.error,
                        isError = true,
                    )
                }

                selectedPlan != null -> {
                    HtmlWebView(
                        html = selectedPlan.contentHtml,
                        baseUrl = selectedPlan.source.url,
                        fitContentWidth = true,
                        showScrollMoreHint = true,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                state.unavailablePlanId != null &&
                    state.unavailablePlanId == state.selectedPlanSource?.id -> {
                    HerramientasStateMessage(
                        title = "Plan no disponible",
                        body = "El plan de estudio seleccionado no está disponible.",
                    )
                }

                else -> {
                    HerramientasStateMessage(
                        title = "Sin contenido",
                        body = "No hay un plan de estudio disponible.",
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CareerSelector(
    careerGroups: List<StudyPlanCareerGroup>,
    selectedCareer: StudyCareer?,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onCareerSelected: (StudyCareer) -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabled = careerGroups.isNotEmpty()
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) onExpandedChange(it) },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selectedCareer?.shortName.orEmpty(),
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            singleLine = true,
            label = { Text("Carrera") },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = enabled)
                .fillMaxWidth(),
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
        ) {
            careerGroups.forEach { group ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(group.career.shortName)
                            Text(
                                text = group.career.displayName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    onClick = { onCareerSelected(group.career) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlanSelector(
    plans: List<StudyPlanSource>,
    selectedPlan: StudyPlanSource?,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onPlanSelected: (StudyPlanSource) -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabled = plans.isNotEmpty()
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) onExpandedChange(it) },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selectedPlan?.planLabel.orEmpty(),
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            singleLine = true,
            label = { Text("Plan") },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = enabled)
                .fillMaxWidth(),
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
        ) {
            plans.forEach { plan ->
                DropdownMenuItem(
                    text = { Text("Plan ${plan.planLabel}") },
                    onClick = { onPlanSelected(plan) },
                )
            }
        }
    }
}
