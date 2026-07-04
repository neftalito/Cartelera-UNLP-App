package com.overcoders.unlpcarteleranotifier.ui

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@SuppressLint("FrequentlyChangingValue")
@Composable
fun ScrollMoreHint(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    text: String = "↓ Deslizá para ver más",
    showDelayMillis: Long = 250
) {
    val shouldShowHint = scrollState.maxValue > 0 && scrollState.value < scrollState.maxValue
    var stableVisible by remember { mutableStateOf(false) }

    LaunchedEffect(shouldShowHint, showDelayMillis) {
        if (shouldShowHint) {
            delay(showDelayMillis)
            if (scrollState.maxValue > 0 && scrollState.value < scrollState.maxValue) {
                stableVisible = true
            }
        } else {
            stableVisible = false
        }
    }

    AnimatedVisibility(
        visible = stableVisible,
        modifier = modifier
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@SuppressLint("FrequentlyChangingValue")
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
    var stableVisible by remember { mutableStateOf(false) }

    LaunchedEffect(shouldShowHint, showDelayMillis) {
        if (shouldShowHint) {
            delay(showDelayMillis)
            @Suppress("KotlinConstantConditions")
            if (shouldShowHint) {
                stableVisible = true
            }
        } else {
            stableVisible = false
        }
    }

    AnimatedVisibility(
        visible = stableVisible,
        modifier = modifier
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}
