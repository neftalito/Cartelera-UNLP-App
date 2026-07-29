/** Resuelve estados de carga compartidos, incluidos filtros y paginación. */
package com.overcoders.unlpcarteleranotifier.ui.common

internal data class PaginatedLoadingState(
    val phase: ContentLoadingPhase,
    val showPaginationIndicator: Boolean,
)

internal fun resolvedContentLoadingPhase(
    isLoading: Boolean,
    hasResolvedContent: Boolean,
    hasError: Boolean,
): ContentLoadingPhase = contentLoadingPhase(
    isLoading = isLoading || (!hasResolvedContent && !hasError),
    hasResolvedContent = hasResolvedContent,
)

internal fun <T> keyedContentLoadingPhase(
    isLoading: Boolean,
    resolvedKey: T?,
    requestedKey: T,
    hasError: Boolean,
): ContentLoadingPhase = resolvedContentLoadingPhase(
    isLoading = isLoading,
    hasResolvedContent = resolvedKey == requestedKey,
    hasError = hasError,
)

internal fun <T> paginatedLoadingState(
    isContentLoading: Boolean,
    isAuxiliaryLoading: Boolean = false,
    isLoadingMore: Boolean,
    resolvedKey: T?,
    requestedKey: T,
    hasError: Boolean,
): PaginatedLoadingState {
    val hasResolvedContent = resolvedKey == requestedKey
    val isPrimaryLoading = isContentLoading || isAuxiliaryLoading
    return PaginatedLoadingState(
        phase = resolvedContentLoadingPhase(
            isLoading = isPrimaryLoading,
            hasResolvedContent = hasResolvedContent,
            hasError = hasError,
        ),
        showPaginationIndicator = isLoadingMore && hasResolvedContent && !isPrimaryLoading,
    )
}
