package com.overcoders.unlpcarteleranotifier

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.overcoders.unlpcarteleranotifier.data.SettingsStore
import com.overcoders.unlpcarteleranotifier.data.SubscripcionesStore
import com.overcoders.unlpcarteleranotifier.model.Adjunto
import com.overcoders.unlpcarteleranotifier.model.CarteleraNotificationTarget
import com.overcoders.unlpcarteleranotifier.model.CursadaInfo
import com.overcoders.unlpcarteleranotifier.model.CursadaNotificationTarget
import com.overcoders.unlpcarteleranotifier.model.Mensaje
import com.overcoders.unlpcarteleranotifier.push.FirebaseTopicSyncManager
import com.overcoders.unlpcarteleranotifier.push.PushNotificationDispatcher
import com.overcoders.unlpcarteleranotifier.ui.AjustesScreen
import com.overcoders.unlpcarteleranotifier.ui.AulasScreen
import com.overcoders.unlpcarteleranotifier.ui.CursadasScreen
import com.overcoders.unlpcarteleranotifier.ui.HorariosScreen
import com.overcoders.unlpcarteleranotifier.ui.MateriasScreen
import com.overcoders.unlpcarteleranotifier.ui.SubscripcionesScreen
import com.overcoders.unlpcarteleranotifier.ui.theme.UNLPCarteleraNotifierTheme
import com.overcoders.unlpcarteleranotifier.worker.CursadasNotificationDispatcher
import com.overcoders.unlpcarteleranotifier.worker.NotificationDispatcher
import org.json.JSONArray

/**
 * Punto de entrada de la app.
 *
 * Su responsabilidad principal es traducir intents externos (por ejemplo, taps en
 * notificaciones) a un estado inicial que la UI de Compose pueda consumir de forma segura.
 */
class MainActivity : ComponentActivity() {
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    private val pendingNotificationMessage = mutableStateOf<Mensaje?>(null)
    private val pendingCarteleraTarget = mutableStateOf<CarteleraNotificationTarget?>(null)
    private val pendingCursadaNotification = mutableStateOf<CursadaInfo?>(null)
    private val pendingCursadaTarget = mutableStateOf<CursadaNotificationTarget?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        updateNotificationPayload(intent)
        enableEdgeToEdge()
        setContent {
            UNLPCarteleraNotifierTheme {
                val message by pendingNotificationMessage
                val carteleraTarget by pendingCarteleraTarget
                val cursada by pendingCursadaNotification
                val cursadaTarget by pendingCursadaTarget
                UNLPCarteleraNotifierApp(
                    initialNotificationMessage = message,
                    initialCarteleraTarget = carteleraTarget,
                    initialCursada = cursada,
                    initialCursadaTarget = cursadaTarget,
                    onNotificationMessageConsumed = { pendingNotificationMessage.value = null },
                    onCarteleraTargetConsumed = { pendingCarteleraTarget.value = null },
                    onCursadaConsumed = { pendingCursadaNotification.value = null },
                    onCursadaTargetConsumed = { pendingCursadaTarget.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        updateNotificationPayload(intent)
    }

    override fun onStart() {
        super.onStart()
        checkAndRequestRequiredPermissions()
    }

    private fun checkAndRequestRequiredPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return
        }
        val hasPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun updateNotificationPayload(intent: Intent?) {
        pendingNotificationMessage.value = mensajeFromIntent(intent)
        // Nuevo flujo push: si no vino el anuncio legado completo, guardamos sólo el target
        // mínimo para que la pantalla busque y enfoque el contenido correcto al cargar.
        pendingCarteleraTarget.value = if (pendingNotificationMessage.value == null) {
            PushNotificationDispatcher.carteleraTargetFromIntent(intent)
        } else {
            null
        }
        pendingCursadaNotification.value = cursadaFromIntent(intent)
        pendingCursadaTarget.value = if (pendingCursadaNotification.value == null) {
            PushNotificationDispatcher.cursadaTargetFromIntent(intent)
        } else {
            null
        }
    }

    // Compatibilidad transitoria: reconstruye el mensaje completo desde extras del esquema
    // anterior. Se puede eliminar cuando ya no haga falta abrir notificaciones legacy.
    private fun mensajeFromIntent(intent: Intent?): Mensaje? {
        if (intent == null) return null
        if (
            intent.getStringExtra(CursadasNotificationDispatcher.EXTRA_TYPE) ==
            CursadasNotificationDispatcher.TYPE_CURSADA
        ) {
            return null
        }
        val titulo = intent.getStringExtra(NotificationDispatcher.EXTRA_TITULO) ?: return null
        val materia = intent.getStringExtra(NotificationDispatcher.EXTRA_MATERIA) ?: return null
        val cuerpoHtml =
            intent.getStringExtra(NotificationDispatcher.EXTRA_CUERPO_HTML) ?: return null
        val fecha = intent.getStringExtra(NotificationDispatcher.EXTRA_FECHA) ?: return null
        val autor = intent.getStringExtra(NotificationDispatcher.EXTRA_AUTOR) ?: return null
        val anulado = intent.getBooleanExtra(NotificationDispatcher.EXTRA_ANULADO, false)
        val adjuntos = parseAdjuntosFromIntent(intent)
        return Mensaje(
            materia = materia,
            titulo = titulo,
            cuerpoHtml = cuerpoHtml,
            fecha = fecha,
            autor = autor,
            isAnulado = anulado,
            adjuntos = adjuntos
        )
    }

    private fun parseAdjuntosFromIntent(intent: Intent): List<Adjunto> {
        val serializedAdjuntos = intent.getStringExtra(NotificationDispatcher.EXTRA_ADJUNTOS)
            ?: return emptyList()

        return try {
            val adjuntosArray = JSONArray(serializedAdjuntos)
            buildList(adjuntosArray.length()) {
                for (index in 0 until adjuntosArray.length()) {
                    val adjunto = adjuntosArray.optJSONObject(index) ?: continue
                    val nombre = adjunto.optString("nombre")
                    val publicPath = adjunto.optString("publicPath")
                    if (nombre.isNotEmpty() && publicPath.isNotEmpty()) {
                        add(Adjunto(nombre = nombre, publicPath = publicPath))
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    // Compatibilidad transitoria: abre notificaciones viejas de cursadas hasta que ya no
    // queden pendientes en instalaciones actualizadas.
    private fun cursadaFromIntent(intent: Intent?): CursadaInfo? {
        if (intent == null) return null
        if (
            intent.getStringExtra(CursadasNotificationDispatcher.EXTRA_TYPE) !=
            CursadasNotificationDispatcher.TYPE_CURSADA
        ) {
            return null
        }
        val materia = intent.getStringExtra(CursadasNotificationDispatcher.EXTRA_MATERIA) ?: return null
        val inicio = intent.getStringExtra(CursadasNotificationDispatcher.EXTRA_INICIO).orEmpty()
        val horarios = intent.getStringExtra(CursadasNotificationDispatcher.EXTRA_HORARIOS).orEmpty()
        val fecha =
            intent.getStringExtra(CursadasNotificationDispatcher.EXTRA_FECHA_MODIFICACION).orEmpty()

        return CursadaInfo(
            materia = materia,
            inicioCursadaHtml = inicio,
            horariosCursadaHtml = horarios,
            ultimaModificacion = fecha,
            ultimaModificacionEpochMillis = null
        )
    }
}

/**
 * Orquesta la navegación principal y conserva el contexto mínimo necesario para
 * restaurar la UI después de recreaciones de actividad o aperturas desde notificaciones.
 */
@PreviewScreenSizes
@Composable
fun UNLPCarteleraNotifierApp(
    initialNotificationMessage: Mensaje? = null,
    initialCarteleraTarget: CarteleraNotificationTarget? = null,
    initialCursada: CursadaInfo? = null,
    initialCursadaTarget: CursadaNotificationTarget? = null,
    onNotificationMessageConsumed: () -> Unit = {},
    onCarteleraTargetConsumed: () -> Unit = {},
    onCursadaConsumed: () -> Unit = {},
    onCursadaTargetConsumed: () -> Unit = {}
) {
    val context = LocalContext.current
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.CARTELERA) }
    var selectedCategory by rememberSaveable { mutableStateOf(MainCategory.MATERIAS) }
    var pendingPagerDestination by remember { mutableStateOf<AppDestinations?>(null) }
    // Guardamos sólo destino y categoría. Cada pantalla recompone su estado interno al volver,
    // así evitamos serializar árboles de estado más pesados o acoplados a Compose.
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
    val subscriptasIds by SubscripcionesStore.subscripcionesFlow(context)
        .collectAsState(initial = emptySet())
    val notifyAll by SettingsStore.notifyAllFlow(context).collectAsState(initial = true)
    var showSubscriptionsBlockedDialog by remember { mutableStateOf(false) }
    var showDevelopmentWarningDialog by remember { mutableStateOf(false) }
    var showReviewPromptDialog by remember { mutableStateOf(false) }
    var highlightNotifyAllRequest by remember { mutableIntStateOf(0) }
    val colorScheme = MaterialTheme.colorScheme
    // Cada pantalla publica su propio encabezado. El contenedor los conserva por destino
    // para restaurarlos correctamente al navegar hacia atrás.
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

    // Si el usuario activa "notificar todas", la pantalla de suscripciones deja de tener
    // sentido. Este helper rebobina el historial hasta encontrar una pantalla válida.
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

    LaunchedEffect(initialNotificationMessage) {
        if (initialNotificationMessage != null) {
            navigateTo(
                destination = AppDestinations.CARTELERA,
                category = MainCategory.MATERIAS,
                addToHistory = false
            )
        }
    }

    LaunchedEffect(initialCarteleraTarget) {
        if (initialCarteleraTarget != null) {
            navigateTo(
                destination = AppDestinations.CARTELERA,
                category = MainCategory.MATERIAS,
                addToHistory = false
            )
        }
    }

    LaunchedEffect(initialCursada) {
        if (initialCursada != null) {
            navigateTo(
                destination = AppDestinations.CURSADAS,
                category = MainCategory.MATERIAS,
                addToHistory = false
            )
        }
    }

    LaunchedEffect(initialCursadaTarget) {
        if (initialCursadaTarget != null) {
            navigateTo(
                destination = AppDestinations.CURSADAS,
                category = MainCategory.MATERIAS,
                addToHistory = false
            )
        }
    }

    // Son diálogos que deben dispararse una sola vez por instalación o por hito de uso,
    // por eso se apoyan en flags persistidos en SettingsStore.
    LaunchedEffect(Unit) {
        val hasSeenWarning = SettingsStore.hasSeenDevelopmentWarning(context)
        if (!hasSeenWarning) {
            showDevelopmentWarningDialog = true
            SettingsStore.setHasSeenDevelopmentWarning(context, true)
        }

        val shouldShowReviewPrompt = SettingsStore.registerAppOpenAndShouldShowReviewPrompt(context)
        if (shouldShowReviewPrompt) {
            showReviewPromptDialog = true
        }
    }

    LaunchedEffect(notifyAll, subscriptasIds) {
        // El servidor decide qué push mandar; el cliente sólo mantiene actualizado
        // el conjunto de topics que representa las preferencias del usuario.
        // Mientras sigan entrando instalaciones desde versiones previas, este paso
        // también funciona como migración automática. Cuando toda la base esté en
        // la versión nueva, esta compatibilidad ya no debería hacer falta.
        FirebaseTopicSyncManager.sync(context)
    }

    if (showDevelopmentWarningDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                @Suppress("AssignedValueIsNeverRead")
                showDevelopmentWarningDialog = false
            },
            title = { Text("Atención") },
            text = {
                Text(
                    "Esta aplicación está en desarrollo, por lo que puede contener errores.\n" +
                    "Ante cualquier error, hay un botón de reportes debajo de todo en Ajustes para reportarlo.\n" +
                    "Es recomendable también desactivar las optimizaciones de batería, así la app puede funcionar en segundo plano y obtener correctamente las novedades."
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        @Suppress("AssignedValueIsNeverRead")
                        showDevelopmentWarningDialog = false
                    }
                ) {
                    Text("Entendido")
                }
            }
        )
    }

    if (showReviewPromptDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                @Suppress("AssignedValueIsNeverRead")
                showReviewPromptDialog = false
            },
            title = { Text("¿Te gusta la app?") },
            text = {
                Text(
                    "Si te resulta útil, podés dejar una reseña en Google Play para apoyar."
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            "https://play.google.com/store/apps/details?id=com.overcoders.unlpcarteleranotifier".toUri()
                        )
                        context.startActivity(intent)
                        @Suppress("AssignedValueIsNeverRead")
                        showReviewPromptDialog = false
                    }
                ) {
                    Text("Dejar reseña")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        @Suppress("AssignedValueIsNeverRead")
                        showReviewPromptDialog = false
                    }
                ) {
                    Text("Cerrar")
                }
            }
        )
    }

    if (showSubscriptionsBlockedDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                if (isNavigationDisabled(currentDestination)) {
                    navigateToValidFallbackFromBlockedSubscriptions()
                }
                @Suppress("AssignedValueIsNeverRead")
                showSubscriptionsBlockedDialog = false
            },
            title = { Text("Suscripciones deshabilitadas") },
            text = {
                Text(
                    "Activaste las notificaciones para todas las materias. " +
                        "Desactivá esa opción en Ajustes para gestionar tus suscripciones."
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        highlightNotifyAllRequest += 1
                        navigateTo(
                            destination = AppDestinations.AJUSTES,
                            category = MainCategory.AJUSTES
                        )
                        @Suppress("AssignedValueIsNeverRead")
                        showSubscriptionsBlockedDialog = false
                    }
                ) {
                    Text("Ir a Ajustes")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        if (isNavigationDisabled(currentDestination)) {
                            navigateToValidFallbackFromBlockedSubscriptions()
                        }
                        @Suppress("AssignedValueIsNeverRead")
                        showSubscriptionsBlockedDialog = false
                    }
                ) {
                    Text("Cerrar")
                }
            }
        )
    }

    // El historial puede contener destinos que quedaron bloqueados por cambios de ajustes.
    // Recorremos hacia atrás hasta encontrar uno accesible para no dejar la navegación rota.
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
        headerActions.firstOrNull { it.contentDescription == "Volver" }
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

    LaunchedEffect(pagerState, selectedCategory) {
        snapshotFlow { pagerState.settledPage }
            .collect { page ->
                val destination = currentCategoryDestinations.getOrNull(page) ?: return@collect
                val pendingDestination = pendingPagerDestination
                // Ignoramos la página asentada anterior mientras una navegación programática
                // todavía está llevando el pager al destino pedido por una notificación o tab.
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
        targetValue = if (chromeVisible) fullNavBarHeight else 0.dp,
        animationSpec = tween(durationMillis = 220),
        label = "bottomBarHeight"
    )
    val bottomBarTranslationY by animateFloatAsState(
        targetValue = if (chromeVisible) 0f else with(density) { fullNavBarHeight.toPx() },
        animationSpec = tween(durationMillis = 220),
        label = "bottomBarTranslationY"
    )

    val contentInsets = if (chromeVisible) {
        WindowInsets.systemBars.union(WindowInsets.displayCutout)
    } else {
        WindowInsets.systemBars
            .only(
                WindowInsetsSides.Top +
                    WindowInsetsSides.Start +
                    WindowInsetsSides.End
            )
            .union(WindowInsets.displayCutout)
    }

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
                                    contentDescription = category.label
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
        androidx.compose.foundation.layout.Column(
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
                                enabled = chromeVisible && !isDisabled,
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
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = currentCategoryDestinations.size > 1 && !isFullscreenDetail
            ) { page ->
                val destination = currentCategoryDestinations.getOrNull(page) ?: return@HorizontalPager

                Box(Modifier.fillMaxSize()) {
                    when (destination) {
                        AppDestinations.CARTELERA -> MateriasScreen(
                            initialSelected = initialNotificationMessage,
                            initialTarget = initialCarteleraTarget,
                            onInitialSelectedConsumed = onNotificationMessageConsumed,
                            onInitialTargetConsumed = onCarteleraTargetConsumed,
                            onTitleChange = { updateTitle(AppDestinations.CARTELERA, it) },
                            onFullscreenDetailChange = {
                                updateFullscreenDetail(AppDestinations.CARTELERA, it)
                            },
                            onHeaderActionsChange = {
                                updateHeaderActions(AppDestinations.CARTELERA, it)
                            }
                        )

                        AppDestinations.CURSADAS -> CursadasScreen(
                            initialSelected = initialCursada,
                            initialTarget = initialCursadaTarget,
                            onInitialSelectedConsumed = onCursadaConsumed,
                            onInitialTargetConsumed = onCursadaTargetConsumed,
                            onTitleChange = { updateTitle(AppDestinations.CURSADAS, it) },
                            onFullscreenDetailChange = {
                                updateFullscreenDetail(AppDestinations.CURSADAS, it)
                            },
                            onHeaderActionsChange = {
                                updateHeaderActions(AppDestinations.CURSADAS, it)
                            }
                        )

                        AppDestinations.SUBSCRIPCIONES -> {
                            if (notifyAll) {
                                Box(Modifier.fillMaxSize())
                            } else {
                                SubscripcionesScreen(
                                    onHeaderActionsChange = {
                                        updateHeaderActions(AppDestinations.SUBSCRIPCIONES, it)
                                    }
                                )
                            }
                        }

                        AppDestinations.HORARIOS -> HorariosScreen(
                            onHeaderActionsChange = {
                                updateHeaderActions(AppDestinations.HORARIOS, it)
                            }
                        )

                        AppDestinations.AULAS -> AulasScreen(
                            onHeaderActionsChange = {
                                updateHeaderActions(AppDestinations.AULAS, it)
                            }
                        )

                        AppDestinations.AJUSTES -> AjustesScreen(
                            highlightNotifyAllTrigger = highlightNotifyAllRequest,
                            onHighlightNotifyAllConsumed = { highlightNotifyAllRequest = 0 },
                            onHeaderActionsChange = {
                                updateHeaderActions(AppDestinations.AJUSTES, it)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderActionsRow(
    actions: List<HeaderAction>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        actions.forEach { action ->
            IconButton(onClick = action.onClick, enabled = action.enabled) {
                Icon(
                    imageVector = action.icon,
                    contentDescription = action.contentDescription
                )
            }
        }
    }
}

private const val MAX_HISTORY_SIZE = 20

private data class NavigationState(
    val destination: AppDestinations,
    val category: MainCategory,
)

enum class MainCategory(
    val label: String,
    val icon: ImageVector,
    val destinations: List<AppDestinations>,
    val initialDestination: AppDestinations,
) {
    MATERIAS(
        label = "Materias",
        icon = Icons.Default.EventAvailable,
        destinations = listOf(
            AppDestinations.CARTELERA,
            AppDestinations.CURSADAS,
            AppDestinations.SUBSCRIPCIONES
        ),
        initialDestination = AppDestinations.CARTELERA
    ),
    HERRAMIENTAS(
        label = "Herramientas",
        icon = Icons.Default.Build,
        destinations = listOf(
            AppDestinations.AULAS,
            AppDestinations.HORARIOS
        ),
        initialDestination = AppDestinations.AULAS
    ),
    AJUSTES(
        label = "Ajustes",
        icon = Icons.Default.Settings,
        destinations = listOf(AppDestinations.AJUSTES),
        initialDestination = AppDestinations.AJUSTES
    ),
}

enum class AppDestinations {
    CARTELERA,
    CURSADAS,
    SUBSCRIPCIONES,
    HORARIOS,
    AULAS,
    AJUSTES,
}

private val AppDestinations.label: String
    get() = when (this) {
        AppDestinations.CARTELERA -> "Cartelera"
        AppDestinations.CURSADAS -> "Cursadas"
        AppDestinations.SUBSCRIPCIONES -> "Suscripciones"
        AppDestinations.HORARIOS -> "Reservas de aulas"
        AppDestinations.AULAS -> "Estado de aulas"
        AppDestinations.AJUSTES -> "Ajustes"
    }
