/**
 * Orquesta la navegación Compose, la barra superior y los estados globales de la app.
 */
package com.overcoders.unlpcarteleranotifier.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.overcoders.unlpcarteleranotifier.HeaderAction
import com.overcoders.unlpcarteleranotifier.HeaderActionPlacement
import com.overcoders.unlpcarteleranotifier.data.SettingsStore
import com.overcoders.unlpcarteleranotifier.data.SubscripcionesStore
import com.overcoders.unlpcarteleranotifier.model.AvisoNotificationTarget
import com.overcoders.unlpcarteleranotifier.model.CarteleraNotificationTarget
import com.overcoders.unlpcarteleranotifier.model.CursadaNotificationTarget
import com.overcoders.unlpcarteleranotifier.push.FirebaseTopicSyncManager
import com.overcoders.unlpcarteleranotifier.push.NotificationOpenKind
import com.overcoders.unlpcarteleranotifier.ui.AjustesScreen
import com.overcoders.unlpcarteleranotifier.ui.AulasScreen
import com.overcoders.unlpcarteleranotifier.ui.CalendarioAcademicoScreen
import com.overcoders.unlpcarteleranotifier.ui.CursadasScreen
import com.overcoders.unlpcarteleranotifier.ui.EventualReservationsScreen
import com.overcoders.unlpcarteleranotifier.ui.HorariosScreen
import com.overcoders.unlpcarteleranotifier.ui.MateriasScreen
import com.overcoders.unlpcarteleranotifier.ui.OptativeSubjectsScreen
import com.overcoders.unlpcarteleranotifier.ui.StudyPlansScreen
import com.overcoders.unlpcarteleranotifier.ui.SubscripcionesScreen
import com.overcoders.unlpcarteleranotifier.ui.common.openExternalUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Orquesta la navegación principal y conserva el contexto mínimo necesario para
 * restaurar la UI después de recreaciones de actividad o aperturas desde notificaciones.
 */
@PreviewScreenSizes
@Composable
fun UNLPCarteleraNotifierApp(
    initialCarteleraTarget: CarteleraNotificationTarget? = null,
    initialCursadaTarget: CursadaNotificationTarget? = null,
    initialAvisoTarget: AvisoNotificationTarget? = null,
    notificationOpenEventId: Long = 0L,
    activeNotificationKind: NotificationOpenKind? = null,
    onCarteleraTargetConsumed: () -> Unit = {},
    onCursadaTargetConsumed: () -> Unit = {},
    onAvisoTargetConsumed: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.CARTELERA) }
    var selectedCategory by rememberSaveable { mutableStateOf(MainCategory.MATERIAS) }
    var pendingPagerDestination by remember { mutableStateOf<AppDestinations?>(null) }
    val navigationHistory = rememberSaveable(
        saver = listSaver(
            save = { history -> history.map { state -> "${state.destination.name}|${state.category.name}" } },
            restore = { saved ->
                val restoredHistory = mutableStateListOf<NavigationState>()
                saved.forEach { savedState ->
                    val parts = savedState.split("|")
                    if (parts.size != 2) return@forEach
                    val destination = AppDestinations.entries.find { it.name == parts[0] }
                        ?: return@forEach
                    val category = MainCategory.entries.find { it.name == parts[1] }
                        ?: return@forEach
                    restoredHistory.add(
                        NavigationState(
                            destination = destination,
                            category = category
                        )
                    )
                }
                restoredHistory
            }
        )
    ) { mutableStateListOf<NavigationState>() }
    val subscriptionsFlow = remember(context) { SubscripcionesStore.subscripcionesFlow(context) }
    val notifyAllFlow = remember(context) {
        SettingsStore.notifyAllFlow(context).map<Boolean, Boolean?> { it }
    }
    val subscriptasIds by subscriptionsFlow.collectAsStateWithLifecycle(initialValue = emptySet())
    val notifyAllPreference by notifyAllFlow.collectAsStateWithLifecycle(initialValue = null)
    val notifyAll = notifyAllPreference == true
    var showSubscriptionsBlockedDialog by remember { mutableStateOf(false) }
    var showReviewPromptDialog by rememberSaveable { mutableStateOf(false) }
    var appOpenRegistered by rememberSaveable { mutableStateOf(false) }
    var suppressReviewPromptForNotificationOpen by rememberSaveable {
        mutableStateOf(
            initialCarteleraTarget != null ||
                initialCursadaTarget != null ||
                initialAvisoTarget != null
        )
    }
    var pendingAvisoTarget by rememberSaveable { mutableStateOf(initialAvisoTarget) }
    var highlightNotifyAllRequest by remember { mutableIntStateOf(0) }
    val colorScheme = MaterialTheme.colorScheme
    var headerActionsByDestination by remember {
        mutableStateOf<Map<AppDestinations, List<HeaderAction>>>(emptyMap())
    }
    var titleByDestination by remember {
        mutableStateOf<Map<AppDestinations, String>>(emptyMap())
    }
    var fullscreenDetailByDestination by remember {
        mutableStateOf<Map<AppDestinations, Boolean>>(emptyMap())
    }

    fun updateHeaderActions(destination: AppDestinations, actions: List<HeaderAction>) {
        val updated = headerActionsByDestination.toMutableMap()
        if (actions.isEmpty()) {
            updated.remove(destination)
        } else {
            updated[destination] = actions
        }
        headerActionsByDestination = updated
    }

    fun updateTitle(destination: AppDestinations, title: String?) {
        val updated = titleByDestination.toMutableMap()
        if (title.isNullOrBlank()) {
            updated.remove(destination)
        } else {
            updated[destination] = title
        }
        titleByDestination = updated
    }

    fun updateFullscreenDetail(destination: AppDestinations, isFullscreen: Boolean) {
        val updated = fullscreenDetailByDestination.toMutableMap()
        if (isFullscreen) {
            updated[destination] = true
        } else {
            updated.remove(destination)
        }
        fullscreenDetailByDestination = updated
    }

    fun navigateTo(
        destination: AppDestinations,
        category: MainCategory = selectedCategory,
        addToHistory: Boolean = true,
        syncPager: Boolean = true,
    ) {
        val sameState = destination == currentDestination && category == selectedCategory
        if (sameState) return
        if (addToHistory) {
            if (navigationHistory.size >= MAX_HISTORY_SIZE) {
                navigationHistory.removeAt(0)
            }
            navigationHistory.add(
                NavigationState(
                    destination = currentDestination,
                    category = selectedCategory
                )
            )
        }
        currentDestination = destination
        selectedCategory = category
        pendingPagerDestination = if (syncPager) destination else null
    }

    fun isNavigationDisabled(destination: AppDestinations): Boolean {
        val isSubscriptions = destination == AppDestinations.SUBSCRIPCIONES
        return isSubscriptions && notifyAll
    }

    fun navigateToValidFallbackFromBlockedSubscriptions() {
        while (navigationHistory.isNotEmpty()) {
            val previousState = navigationHistory.removeAt(navigationHistory.lastIndex)
            if (isNavigationDisabled(previousState.destination)) {
                continue
            }
            navigateTo(
                destination = previousState.destination,
                category = previousState.category,
                addToHistory = false
            )
            return
        }

        navigateTo(
            destination = AppDestinations.CARTELERA,
            category = MainCategory.MATERIAS,
            addToHistory = false
        )
    }

    LaunchedEffect(initialCarteleraTarget) {
        if (initialCarteleraTarget != null) {
            suppressReviewPromptForNotificationOpen = true
            showSubscriptionsBlockedDialog = false
            pendingAvisoTarget = null
            navigateTo(
                destination = AppDestinations.CARTELERA,
                category = MainCategory.MATERIAS,
                addToHistory = false
            )
        }
    }

    LaunchedEffect(initialCursadaTarget) {
        if (initialCursadaTarget != null) {
            suppressReviewPromptForNotificationOpen = true
            showSubscriptionsBlockedDialog = false
            pendingAvisoTarget = null
            navigateTo(
                destination = AppDestinations.CURSADAS,
                category = MainCategory.MATERIAS,
                addToHistory = false
            )
        }
    }

    LaunchedEffect(initialAvisoTarget) {
        if (initialAvisoTarget != null) {
            suppressReviewPromptForNotificationOpen = true
            showSubscriptionsBlockedDialog = false
            pendingAvisoTarget = initialAvisoTarget
        }
    }

    LaunchedEffect(Unit) {
        if (!appOpenRegistered) {
            appOpenRegistered = true
            try {
                val shouldShowReviewPrompt =
                    SettingsStore.registerAppOpenAndShouldShowReviewPrompt(context)
                if (shouldShowReviewPrompt) {
                    showReviewPromptDialog = true
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // El prompt de reseña es opcional y no debe impedir abrir la aplicación.
            }
        }
    }

    LaunchedEffect(notifyAllPreference, subscriptasIds) {
        if (notifyAllPreference == null) return@LaunchedEffect
        // Los valores sólo disparan el trabajo: el manager vuelve a leer DataStore
        // dentro de un scope de aplicación que no se cancela al recomponer la UI.
        FirebaseTopicSyncManager.requestSync(context)
    }

    LaunchedEffect(notifyAll) {
        if (!notifyAll) {
            showSubscriptionsBlockedDialog = false
        }
    }

    fun consumeReviewPrompt() {
        showReviewPromptDialog = false
        scope.launch {
            try {
                SettingsStore.markReviewPromptShown(context)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Si no puede persistirse, el prompt podrá ofrecerse en otra apertura.
            }
        }
    }

    if (pendingAvisoTarget != null) {
        AvisoNotificationDialog(
            target = pendingAvisoTarget!!,
            onDismiss = {
                pendingAvisoTarget = null
                onAvisoTargetConsumed()
            }
        )
    } else if (showSubscriptionsBlockedDialog) {
        SubscriptionsBlockedDialog(
            onDismiss = {
                if (isNavigationDisabled(currentDestination)) {
                    navigateToValidFallbackFromBlockedSubscriptions()
                }
                @Suppress("AssignedValueIsNeverRead")
                showSubscriptionsBlockedDialog = false
            },
            onOpenSettings = {
                highlightNotifyAllRequest += 1
                navigateTo(
                    destination = AppDestinations.AJUSTES,
                    category = MainCategory.AJUSTES
                )
                @Suppress("AssignedValueIsNeverRead")
                showSubscriptionsBlockedDialog = false
            }
        )
    } else if (showReviewPromptDialog && !suppressReviewPromptForNotificationOpen) {
        ReviewPromptDialog(
            onDismiss = {
                consumeReviewPrompt()
            },
            onConfirm = {
                context.openExternalUrl(
                    "https://play.google.com/store/apps/details?id=com.overcoders.unlpcarteleranotifier"
                )
                consumeReviewPrompt()
            }
        )
    }

    BackHandler(enabled = navigationHistory.isNotEmpty()) {
        while (navigationHistory.isNotEmpty()) {
            val previousState = navigationHistory.removeAt(navigationHistory.lastIndex)
            if (isNavigationDisabled(previousState.destination)) {
                @Suppress("AssignedValueIsNeverRead")
                showSubscriptionsBlockedDialog = true
                continue
            }
            navigateTo(
                destination = previousState.destination,
                category = previousState.category,
                addToHistory = false
            )
            return@BackHandler
        }
    }

    val currentCategoryDestinations = selectedCategory.destinations
    val selectedTabIndex = currentCategoryDestinations.indexOf(currentDestination)
        .takeIf { it >= 0 }
        ?: 0

    val pagerState = rememberPagerState(
        initialPage = selectedTabIndex,
        pageCount = { currentCategoryDestinations.size }
    )

    val headerDestination by remember(pagerState, currentCategoryDestinations, currentDestination) {
        derivedStateOf {
            val destinationIndex = if (pagerState.currentPageOffsetFraction == 0f) {
                pagerState.currentPage
            } else {
                pagerState.targetPage
            }

            currentCategoryDestinations.getOrNull(destinationIndex) ?: currentDestination
        }
    }

    val headerActions = headerActionsByDestination[headerDestination].orEmpty()
    val currentTitle = titleByDestination[headerDestination] ?: headerDestination.label
    val isFullscreenDetail = fullscreenDetailByDestination[headerDestination] == true
    val leadingHeaderAction = if (isFullscreenDetail) {
        headerActions.firstOrNull { it.placement == HeaderActionPlacement.Leading }
    } else {
        null
    }
    val trailingHeaderActions = if (leadingHeaderAction != null) {
        headerActions.filterNot { it === leadingHeaderAction }
    } else {
        headerActions
    }

    LaunchedEffect(selectedCategory, selectedTabIndex) {
        if (pagerState.currentPage != selectedTabIndex) {
            pagerState.scrollToPage(selectedTabIndex)
        }
    }

    LaunchedEffect(pagerState, selectedCategory, notifyAll) {
        snapshotFlow { pagerState.settledPage }
            .collect { page ->
                val destination = currentCategoryDestinations.getOrNull(page) ?: return@collect
                val pendingDestination = pendingPagerDestination
                if (pendingDestination != null && destination != pendingDestination) {
                    return@collect
                }
                if (isNavigationDisabled(destination)) {
                    @Suppress("AssignedValueIsNeverRead")
                    showSubscriptionsBlockedDialog = true
                    val fallbackPage = currentCategoryDestinations.indexOf(currentDestination)
                        .takeIf { it >= 0 }
                        ?: 0
                    if (pagerState.currentPage != fallbackPage) {
                        pagerState.scrollToPage(fallbackPage)
                    }
                } else {
                    if (pendingDestination == destination) {
                        pendingPagerDestination = null
                    }
                    if (destination != currentDestination) {
                        navigateTo(destination = destination, syncPager = false)
                    }
                }
            }
    }

    val density = LocalDensity.current
    val navBarBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val fullNavBarHeight = 80.dp + navBarBottomInset

    val chromeVisible = !isFullscreenDetail

    val tabsAlpha by animateFloatAsState(
        targetValue = if (chromeVisible && currentCategoryDestinations.size > 1) 1f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "tabsAlpha"
    )
    val tabsHeight by animateDpAsState(
        targetValue = if (chromeVisible && currentCategoryDestinations.size > 1) 48.dp else 0.dp,
        animationSpec = tween(durationMillis = 220),
        label = "tabsHeight"
    )
    val tabsTranslationY by animateFloatAsState(
        targetValue = if (chromeVisible && currentCategoryDestinations.size > 1) 0f else -32f,
        animationSpec = tween(durationMillis = 220),
        label = "tabsTranslationY"
    )

    val bottomBarAlpha by animateFloatAsState(
        targetValue = if (chromeVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "bottomBarAlpha"
    )
    val bottomBarHeight by animateDpAsState(
        // Aunque se oculte la navegación, el slot conserva el inset del sistema para que el
        // contenido fullscreen nunca quede debajo de la barra de navegación del dispositivo.
        targetValue = if (chromeVisible) fullNavBarHeight else navBarBottomInset,
        animationSpec = tween(durationMillis = 220),
        label = "bottomBarHeight"
    )
    val bottomBarTranslationY by animateFloatAsState(
        targetValue = if (chromeVisible) 0f else with(density) { fullNavBarHeight.toPx() },
        animationSpec = tween(durationMillis = 220),
        label = "bottomBarTranslationY"
    )

    val contentInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = contentInsets,
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(bottomBarHeight)
            ) {
                NavigationBar(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = bottomBarAlpha
                            translationY = bottomBarTranslationY
                        }
                ) {
                    MainCategory.entries.forEach { category ->
                        NavigationBarItem(
                            selected = selectedCategory == category,
                            onClick = {
                                if (chromeVisible) {
                                    navigateTo(
                                        destination = category.initialDestination,
                                        category = category,
                                        addToHistory = selectedCategory != category
                                    )
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = category.icon,
                                    contentDescription = null
                                )
                            },
                            label = { Text(category.label) },
                            enabled = chromeVisible
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                val leadingTitleInset = if (leadingHeaderAction != null) 56.dp else 0.dp
                val trailingTitleInset = (trailingHeaderActions.size * 48).dp

                if (leadingHeaderAction != null) {
                    IconButton(
                        onClick = leadingHeaderAction.onClick,
                        enabled = leadingHeaderAction.enabled,
                        modifier = Modifier.align(Alignment.CenterStart)
                    ) {
                        Icon(
                            imageVector = leadingHeaderAction.icon,
                            contentDescription = leadingHeaderAction.contentDescription
                        )
                    }
                }

                Text(
                    text = currentTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Start,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = leadingTitleInset,
                            end = trailingTitleInset
                        )
                        .align(Alignment.CenterStart)
                )

                HeaderActionsRow(
                    actions = trailingHeaderActions,
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(tabsHeight)
            ) {
                if (currentCategoryDestinations.size > 1) {
                    SecondaryScrollableTabRow(
                        selectedTabIndex = selectedTabIndex,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                alpha = tabsAlpha
                                translationY = tabsTranslationY
                            }
                    ) {
                        currentCategoryDestinations.forEachIndexed { index, destination ->
                            val isSubscriptions = destination == AppDestinations.SUBSCRIPCIONES
                            val isDisabled = isSubscriptions && notifyAll
                            val tabColor =
                                if (isDisabled) {
                                    colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                } else {
                                    colorScheme.onSurface
                                }

                            Tab(
                                selected = index == selectedTabIndex,
                                onClick = {
                                    if (!chromeVisible) return@Tab
                                    if (isDisabled) {
                                        @Suppress("AssignedValueIsNeverRead")
                                        showSubscriptionsBlockedDialog = true
                                    } else {
                                        navigateTo(destination = destination)
                                    }
                                },
                                enabled = chromeVisible,
                                text = {
                                    Text(
                                        text = destination.label,
                                        color = tabColor
                                    )
                                }
                            )
                        }
                    }
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clipToBounds(),
                userScrollEnabled = currentCategoryDestinations.size > 1 && !isFullscreenDetail
            ) { page ->
                val destination = currentCategoryDestinations.getOrNull(page) ?: return@HorizontalPager
                Box(Modifier.fillMaxSize()) {
                    AppDestinationContent(
                        destination = destination,
                        notifyAll = notifyAll,
                        initialCarteleraTarget = initialCarteleraTarget,
                        initialCursadaTarget = initialCursadaTarget,
                        notificationOpenEventId = notificationOpenEventId,
                        activeNotificationKind = activeNotificationKind,
                        onCarteleraTargetConsumed = onCarteleraTargetConsumed,
                        onCursadaTargetConsumed = onCursadaTargetConsumed,
                        highlightNotifyAllRequest = highlightNotifyAllRequest,
                        onHighlightNotifyAllConsumed = { highlightNotifyAllRequest = 0 },
                        onHeaderActionsChange = { updateHeaderActions(destination, it) },
                        onTitleChange = { updateTitle(destination, it) },
                        onFullscreenDetailChange = { updateFullscreenDetail(destination, it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AppDestinationContent(
    destination: AppDestinations,
    notifyAll: Boolean,
    initialCarteleraTarget: CarteleraNotificationTarget?,
    initialCursadaTarget: CursadaNotificationTarget?,
    notificationOpenEventId: Long,
    activeNotificationKind: NotificationOpenKind?,
    onCarteleraTargetConsumed: () -> Unit,
    onCursadaTargetConsumed: () -> Unit,
    highlightNotifyAllRequest: Int,
    onHighlightNotifyAllConsumed: () -> Unit,
    onHeaderActionsChange: (List<HeaderAction>) -> Unit,
    onTitleChange: (String?) -> Unit,
    onFullscreenDetailChange: (Boolean) -> Unit,
) {
    when (destination) {
        AppDestinations.CARTELERA -> MateriasScreen(
            initialTarget = initialCarteleraTarget,
            notificationOpenEventId = notificationOpenEventId,
            activeNotificationKind = activeNotificationKind,
            onInitialTargetConsumed = onCarteleraTargetConsumed,
            onTitleChange = onTitleChange,
            onFullscreenDetailChange = onFullscreenDetailChange,
            onHeaderActionsChange = onHeaderActionsChange
        )

        AppDestinations.CURSADAS -> CursadasScreen(
            initialTarget = initialCursadaTarget,
            notificationOpenEventId = notificationOpenEventId,
            activeNotificationKind = activeNotificationKind,
            onInitialTargetConsumed = onCursadaTargetConsumed,
            onTitleChange = onTitleChange,
            onFullscreenDetailChange = onFullscreenDetailChange,
            onHeaderActionsChange = onHeaderActionsChange
        )

        AppDestinations.SUBSCRIPCIONES -> {
            if (notifyAll) {
                Box(Modifier.fillMaxSize())
            } else {
                SubscripcionesScreen(
                    onHeaderActionsChange = onHeaderActionsChange
                )
            }
        }

        AppDestinations.HORARIOS -> HorariosScreen(
            onHeaderActionsChange = onHeaderActionsChange
        )

        AppDestinations.AULAS -> AulasScreen(
            onHeaderActionsChange = onHeaderActionsChange
        )

        AppDestinations.RESERVAS_EVENTUALES -> EventualReservationsScreen(
            onHeaderActionsChange = onHeaderActionsChange
        )

        AppDestinations.PLANES_DE_ESTUDIO -> StudyPlansScreen(
            onFullscreenDetailChange = onFullscreenDetailChange,
            onHeaderActionsChange = onHeaderActionsChange
        )

        AppDestinations.MATERIAS_OPTATIVAS -> OptativeSubjectsScreen(
            onFullscreenDetailChange = onFullscreenDetailChange,
            onHeaderActionsChange = onHeaderActionsChange
        )

        AppDestinations.CALENDARIO_ACADEMICO -> CalendarioAcademicoScreen(
            onFullscreenDetailChange = onFullscreenDetailChange,
            onHeaderActionsChange = onHeaderActionsChange
        )

        AppDestinations.AJUSTES -> AjustesScreen(
            highlightNotifyAllTrigger = highlightNotifyAllRequest,
            onHighlightNotifyAllConsumed = onHighlightNotifyAllConsumed,
            onHeaderActionsChange = onHeaderActionsChange
        )
    }
}
