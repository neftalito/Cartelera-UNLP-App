/** Muestra una ayuda flotante cuando un contenido desplazable continúa fuera de pantalla. */
package com.overcoders.unlpcarteleranotifier.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun ScrollMoreHint(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    text: String = "↓ Deslizá para ver más",
    showDelayMillis: Long = 250
) {
    val shouldShowHint by remember(scrollState) {
        derivedStateOf {
            scrollState.maxValue > 0 && scrollState.value < scrollState.maxValue
        }
    }
    DelayedScrollMoreHint(
        shouldShowHint = shouldShowHint,
        modifier = modifier,
        text = text,
        showDelayMillis = showDelayMillis
    )
}

@Composable
fun ScrollMoreHint(
    listState: LazyListState,
    modifier: Modifier = Modifier,
    text: String = "↓ Deslizá para ver más",
    showDelayMillis: Long = 250
) {
    val shouldShowHint by remember(listState) {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf false

            val hasMoreItemsBelow = lastVisible.index < totalItems - 1
            val lastVisibleItemClipped =
                (lastVisible.offset + lastVisible.size) > layoutInfo.viewportEndOffset

            hasMoreItemsBelow || lastVisibleItemClipped
        }
    }
    DelayedScrollMoreHint(
        shouldShowHint = shouldShowHint,
        modifier = modifier,
        text = text,
        showDelayMillis = showDelayMillis
    )
}

@Composable
fun ScrollMoreHint(
    shouldShowHint: Boolean,
    modifier: Modifier = Modifier,
    text: String = "↓ Deslizá para ver más",
    showDelayMillis: Long = 250,
) {
    DelayedScrollMoreHint(
        shouldShowHint = shouldShowHint,
        modifier = modifier,
        text = text,
        showDelayMillis = showDelayMillis,
    )
}

@Composable
private fun DelayedScrollMoreHint(
    shouldShowHint: Boolean,
    modifier: Modifier,
    text: String,
    showDelayMillis: Long,
) {
    var stableVisible by remember { mutableStateOf(false) }

    LaunchedEffect(shouldShowHint, showDelayMillis) {
        if (shouldShowHint) {
            delay(showDelayMillis)
            stableVisible = true
        } else {
            stableVisible = false
        }
    }

    AnimatedVisibility(
        visible = stableVisible,
        modifier = modifier
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            )
        }
    }
}
