package com.overcoders.unlpcarteleranotifier.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.overcoders.unlpcarteleranotifier.model.MateriaCatalogItem

@Stable
class MateriasFilterState(
    expanded: Boolean = false,
    query: String = "",
    selected: MateriaCatalogItem? = null
) {
    var expanded by mutableStateOf(expanded)
    var query by mutableStateOf(query)
    var selected by mutableStateOf(selected)
}

@Composable
fun rememberMateriasFilterState(): MateriasFilterState {
    return remember { MateriasFilterState() }
}
