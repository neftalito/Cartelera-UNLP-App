/** Estandariza la presentación de cargas iniciales, actualizaciones y paginación. */
package com.overcoders.unlpcarteleranotifier.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

internal enum class ContentLoadingPhase {
    IDLE,
    INITIAL,
    REFRESHING,
}

internal fun contentLoadingPhase(
    isLoading: Boolean,
    hasResolvedContent: Boolean,
): ContentLoadingPhase = when {
    !isLoading -> ContentLoadingPhase.IDLE
    hasResolvedContent -> ContentLoadingPhase.REFRESHING
    else -> ContentLoadingPhase.INITIAL
}

@Composable
internal fun LoadingContentBox(
    phase: ContentLoadingPhase,
    initialText: String,
    refreshContentDescription: String,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        if (phase == ContentLoadingPhase.INITIAL) {
            CenteredLoadingState(
                text = initialText,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            content()
        }

        if (phase == ContentLoadingPhase.REFRESHING) {
            LinearProgressIndicator(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .zIndex(1f)
                    .semantics { contentDescription = refreshContentDescription }
                    .testTag(REFRESH_LOADING_TEST_TAG),
            )
        }
    }
}

@Composable
internal fun CenteredLoadingState(
    text: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .testTag(INITIAL_LOADING_TEST_TAG)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.testTag(INITIAL_LOADING_INDICATOR_TEST_TAG),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun PaginationLoadingIndicator(
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .testTag(PAGINATION_LOADING_TEST_TAG),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier
                .size(24.dp)
                .testTag(PAGINATION_LOADING_INDICATOR_TEST_TAG),
            strokeWidth = 3.dp,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

internal const val INITIAL_LOADING_TEST_TAG = "initial-loading"
internal const val INITIAL_LOADING_INDICATOR_TEST_TAG = "initial-loading-indicator"
internal const val REFRESH_LOADING_TEST_TAG = "refresh-loading"
internal const val PAGINATION_LOADING_TEST_TAG = "pagination-loading"
internal const val PAGINATION_LOADING_INDICATOR_TEST_TAG = "pagination-loading-indicator"
