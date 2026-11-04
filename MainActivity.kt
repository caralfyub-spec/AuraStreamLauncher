package com.atenea.pixelultralauncher

import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.Settings
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

// ============================================================================
// MODELOS DE DATOS (MANTENIDOS Y AMPLIADOS SIN PERDER COMPATIBILIDAD)
// ============================================================================

data class AppItem(
    val label: String,
    val customLabel: String = "",
    val packageName: String,
    val icon: ImageBitmap?,
    val isFavorite: Boolean = false,
    val isHidden: Boolean = false,
    val note: String = ""
) {
    val displayName: String
        get() = if (customLabel.isNotBlank()) customLabel else label
}

data class LauncherTheme(
    val id: String,
    val name: String,
    val backgroundColor: Color,
    val secondaryBackground: Color,
    val accentColor: Color,
    val textColor: Color,
    val cardColor: Color
)

data class PopupFolder(
    val id: String,
    val name: String,
    val iconEmoji: String,
    val packageNames: List<String>
)

enum class WaveSide(val id: String, val label: String) {
    RIGHT("RIGHT", "Derecha"),
    LEFT("LEFT", "Izquierda"),
    BOTH("BOTH", "Ambos lados")
}

enum class WallpaperStyle(val id: String, val label: String) {
    THEME_SOLID("THEME_SOLID", "Color del Tema"),
    CUSTOM_PHOTO("CUSTOM_PHOTO", "Foto de Galería"),
    AURORA("AURORA", "Degradado Aurora"),
    COSMOS("COSMOS", "Noche Estrellada"),
    NEON_WAVES("NEON_WAVES", "Ondas Neón"),
    SUNSET("SUNSET", "Atardecer Minimal"),
    GEOMETRIC("GEOMETRIC", "Malla Geométrica"),
    SYSTEM_TRANSPARENT("SYSTEM_TRANSPARENT", "Fondo del Sistema")
}

enum class IconStyle(val id: String, val label: String) {
    DEFAULT("DEFAULT", "Original"),
    CIRCLE("CIRCLE", "Círculo"),
    ROUNDED("ROUNDED", "Cuadrado Redondeado"),
    TEARDROP("TEARDROP", "Lágrima"),
    MINIMAL_DOT("MINIMAL_DOT", "Puntos Minimalistas")
}

// ============================================================================
// TEMAS PREDEFINIDOS
// ============================================================================

val PRESET_THEMES = listOf(
    LauncherTheme(
        id = "niagara_dark",
        name = "Niagara Dark",
        backgroundColor = Color(0xFF0A0E17),
        secondaryBackground = Color(0xFF141C2B),
        accentColor = Color(0xFF64B5F6),
        textColor = Color(0xFFF5F7FA),
        cardColor = Color(0xFF1A2234)
    ),
    LauncherTheme(
        id = "amoled_pure",
        name = "AMOLED Puro",
        backgroundColor = Color(0xFF000000),
        secondaryBackground = Color(0xFF0D0D0D),
        accentColor = Color(0xFF00E676),
        textColor = Color(0xFFFFFFFF),
        cardColor = Color(0xFF121212)
    ),
    LauncherTheme(
        id = "cyber_violet",
        name = "Cyber Violeta",
        backgroundColor = Color(0xFF0F081E),
        secondaryBackground = Color(0xFF1E1136),
        accentColor = Color(0xFFE040FB),
        textColor = Color(0xFFF3E5F5),
        cardColor = Color(0xFF251542)
    ),
    LauncherTheme(
        id = "pixel_ocean",
        name = "Pixel Océano",
        backgroundColor = Color(0xFF07161B),
        secondaryBackground = Color(0xFF0E2A33),
        accentColor = Color(0xFF26C6DA),
        textColor = Color(0xFFE0F7FA),
        cardColor = Color(0xFF133540)
    ),
    LauncherTheme(
        id = "sunset_gold",
        name = "Atardecer Ámbar",
        backgroundColor = Color(0xFF180D0A),
        secondaryBackground = Color(0xFF2B1712),
        accentColor = Color(0xFFFFAB40),
        textColor = Color(0xFFFFF3E0),
        cardColor = Color(0xFF361D17)
    ),
    LauncherTheme(
        id = "minimal_snow",
        name = "Minimal Claro",
        backgroundColor = Color(0xFFF2F5F8),
        secondaryBackground = Color(0xFFE1E8F0),
        accentColor = Color(0xFF1976D2),
        textColor = Color(0xFF101418),
        cardColor = Color(0xFFFFFFFF)
    )
)

val ALPHABET_INDEX = listOf(
    "★", "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M",
    "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z", "#"
)

// ============================================================================
// ACTIVIDAD PRINCIPAL
// ============================================================================

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AuraStreamApp()
        }
    }
}

// ============================================================================
// COMPOSABLE PRINCIPAL DEL LAUNCHER (AURA STREAM / NIAGARA)
// ============================================================================

@Composable
fun AuraStreamApp() {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("aura_stream_prefs", Context.MODE_PRIVATE) }

    // --- Estados persistentes en SharedPreferences ---
    var currentThemeId by remember {
        mutableStateOf(prefs.getString("selected_theme", "niagara_dark") ?: "niagara_dark")
    }
    var favoritePackages by remember {
        mutableStateOf(
            prefs.getString("favorite_packages_ordered", null)
                ?.split(",")
                ?.filter { it.isNotBlank() }
                ?: prefs.getStringSet("favorite_packages", emptySet())?.toList()
                ?: emptyList()
        )
    }
    var hiddenPackages by remember {
        mutableStateOf(prefs.getStringSet("hidden_packages", emptySet()) ?: emptySet())
    }
    var waveSide by remember {
        val savedSide = prefs.getString("wave_side", WaveSide.RIGHT.id) ?: WaveSide.RIGHT.id
        mutableStateOf(WaveSide.values().find { it.id == savedSide } ?: WaveSide.RIGHT)
    }
    var wallpaperStyle by remember {
        val savedWp = prefs.getString("wallpaper_style", WallpaperStyle.AURORA.id) ?: WallpaperStyle.AURORA.id
        mutableStateOf(WallpaperStyle.values().find { it.id == savedWp } ?: WallpaperStyle.AURORA)
    }
    var customWallpaperUri by remember {
        mutableStateOf(prefs.getString("custom_wallpaper_uri", null))
    }
    var wallpaperDim by remember {
        mutableStateOf(prefs.getFloat("wallpaper_dim", 0.35f))
    }
    var iconStyle by remember {
        val savedIcon = prefs.getString("icon_style", IconStyle.DEFAULT.id) ?: IconStyle.DEFAULT.id
        mutableStateOf(IconStyle.values().find { it.id == savedIcon } ?: IconStyle.DEFAULT)
    }
    var monochromeIcons by remember {
        mutableStateOf(prefs.getBoolean("monochrome_icons", false))
    }
    var iconSizeDp by remember {
        mutableStateOf(prefs.getInt("icon_size_dp", 42))
    }
    var showMediaWidget by remember {
        mutableStateOf(prefs.getBoolean("show_media_widget", true))
    }
    var showStatusPill by remember {
        mutableStateOf(prefs.getBoolean("show_status_pill", true))
    }
    var homeAgendaNote by remember {
        mutableStateOf(prefs.getString("home_agenda_note", "") ?: "")
    }
    var customAliases by remember {
        mutableStateOf(loadKeyValueMap(prefs, "app_aliases"))
    }
    var appNotes by remember {
        mutableStateOf(loadKeyValueMap(prefs, "app_notes"))
    }
    var popupFolders by remember {
        mutableStateOf(loadPopupFolders(prefs))
    }

    val currentTheme = remember(currentThemeId) {
        PRESET_THEMES.find { it.id == currentThemeId } ?: PRESET_THEMES.first()
    }

    // --- Estado de imagen de fondo personalizada ---
    var customWallpaperBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(customWallpaperUri) {
        val uriStr = customWallpaperUri
        if (!uriStr.isNullOrBlank()) {
            val bmp = withContext(Dispatchers.IO) {
                loadSafeBitmapFromUri(context, Uri.parse(uriStr))
            }
            customWallpaperBitmap = bmp
        } else {
            customWallpaperBitmap = null
        }
    }

    // Selector de fotos de la galería con permiso persistente
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) { }
            val uriString = uri.toString()
            customWallpaperUri = uriString
            wallpaperStyle = WallpaperStyle.CUSTOM_PHOTO
            prefs.edit()
                .putString("custom_wallpaper_uri", uriString)
                .putString("wallpaper_style", WallpaperStyle.CUSTOM_PHOTO.id)
                .apply()
        }
    }

    // --- Lista de aplicaciones instaladas ---
    var rawApps by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var isLoadingApps by remember { mutableStateOf(true) }
    var reloadTrigger by remember { mutableStateOf(0) }

    LaunchedEffect(reloadTrigger) {
        isLoadingApps = true
        val loaded = withContext(Dispatchers.IO) {
            loadInstalledApps(context)
        }
        rawApps = loaded
        if (favoritePackages.isEmpty() && loaded.isNotEmpty()) {
            val initialFavs = loaded.take(6).map { it.packageName }
            favoritePackages = initialFavs
            saveFavoritesOrder(prefs, initialFavs)
        }
        isLoadingApps = false
    }

    val allApps = remember(rawApps, favoritePackages, hiddenPackages, customAliases, appNotes) {
        rawApps.map { app ->
            app.copy(
                customLabel = customAliases[app.packageName] ?: "",
                isFavorite = favoritePackages.contains(app.packageName),
                isHidden = hiddenPackages.contains(app.packageName),
                note = appNotes[app.packageName] ?: ""
            )
        }.sortedBy { it.displayName.lowercase(Locale.getDefault()) }
    }

    val visibleApps = remember(allApps) {
        allApps.filter { !it.isHidden }
    }

    val favoriteApps = remember(allApps, favoritePackages) {
        favoritePackages.mapNotNull { pkg -> allApps.find { it.packageName == pkg && !it.isHidden } }
    }

    // --- Estados de navegación, búsqueda, diálogos y ola alfabética ---
    val listState = rememberLazyListState()
    var isSearching by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedLetterFilter by remember { mutableStateOf<String?>(null) }

    var selectedAppForMenu by remember { mutableStateOf<AppItem?>(null) }
    var selectedAppForPopup by remember { mutableStateOf<AppItem?>(null) }
    var activePopupFolder by remember { mutableStateOf<PopupFolder?>(null) }

    var showThemeDialog by remember { mutableStateOf(false) }
    var showHiddenAppsDialog by remember { mutableStateOf(false) }
    var showFolderManagerDialog by remember { mutableStateOf(false) }
    var showAgendaNoteDialog by remember { mutableStateOf(false) }

    // Estado de la barra alfabética ondulante
    var isWaveDragging by remember { mutableStateOf(false) }
    var activeDragSide by remember { mutableStateOf(WaveSide.RIGHT) }
    var waveTouchYRatio by remember { mutableStateOf(0.5f) }
    var currentWaveLetter by remember { mutableStateOf<String?>(null) }

    // Reloj y batería en tiempo real
    var currentTime by remember { mutableStateOf("") }
    var currentDate by remember { mutableStateOf("") }
    var batteryLevel by remember { mutableStateOf(100) }
    var isCharging by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dateFmt = SimpleDateFormat("EEEE, d 'de' MMMM", Locale("es", "ES"))
        while (true) {
            val now = Date()
            currentTime = timeFmt.format(now)
            currentDate = dateFmt.format(now).replaceFirstChar { it.uppercase() }

            val bmIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = bmIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = bmIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val status = bmIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            if (level >= 0 && scale > 0) {
                batteryLevel = (level * 100) / scale
            }
            isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

            delay(5000)
        }
    }

    // Manejo del botón Atrás para volver siempre al inicio limpio
    BackHandler(enabled = isSearching || selectedLetterFilter != null || showThemeDialog || selectedAppForMenu != null || selectedAppForPopup != null) {
        when {
            selectedAppForMenu != null -> selectedAppForMenu = null
            selectedAppForPopup != null -> selectedAppForPopup = null
            showThemeDialog -> showThemeDialog = false
            isSearching -> {
                isSearching = false
                searchQuery = ""
                focusManager.clearFocus()
            }
            selectedLetterFilter != null -> {
                selectedLetterFilter = null
                coroutineScope.launch { listState.animateScrollToItem(0) }
            }
        }
    }

    // Agrupación de apps por inicial para salto instantáneo en la ola alfabética
    val groupedApps = remember(visibleApps) {
        visibleApps.groupBy { app ->
            val first = app.displayName.firstOrNull()?.uppercaseChar() ?: '#'
            if (first in 'A'..'Z') first.toString() else "#"
        }
    }

    // Cálculo de índices de cada letra en el LazyColumn
    val letterScrollIndexMap = remember(groupedApps, popupFolders, favoriteApps) {
        val map = mutableMapOf<String, Int>()
        map["★"] = 0
        var runningIndex = 1 // 0 es la cabecera (Reloj + Carpetas + Favoritos)
        ALPHABET_INDEX.drop(1).forEach { letter ->
            val appsInLetter = groupedApps[letter]
            if (!appsInLetter.isNullOrEmpty()) {
                map[letter] = runningIndex
                runningIndex += 1 + appsInLetter.size // Cabecera de letra + items
            }
        }
        map
    }

    fun onAlphabetLetterSelected(letter: String) {
        if (currentWaveLetter != letter) {
            currentWaveLetter = letter
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            if (letter == "★") {
                selectedLetterFilter = null
                coroutineScope.launch { listState.scrollToItem(0) }
            } else {
                val targetIdx = letterScrollIndexMap[letter]
                if (targetIdx != null) {
                    coroutineScope.launch { listState.scrollToItem(targetIdx) }
                }
            }
        }
    }

    // Funciones de ayuda para actualizar preferencias
    fun toggleFavorite(app: AppItem) {
        val mutable = favoritePackages.toMutableList()
        if (mutable.contains(app.packageName)) {
            mutable.remove(app.packageName)
        } else {
            mutable.add(app.packageName)
        }
        favoritePackages = mutable
        saveFavoritesOrder(prefs, mutable)
    }

    fun moveFavorite(packageName: String, moveUp: Boolean) {
        val mutable = favoritePackages.toMutableList()
        val idx = mutable.indexOf(packageName)
        if (idx == -1) return
        val newIdx = if (moveUp) idx - 1 else idx + 1
        if (newIdx in mutable.indices) {
            Collections.swap(mutable, idx, newIdx)
            favoritePackages = mutable
            saveFavoritesOrder(prefs, mutable)
        }
    }

    fun toggleHiddenApp(packageName: String) {
        val mutable = hiddenPackages.toMutableSet()
        if (mutable.contains(packageName)) mutable.remove(packageName) else mutable.add(packageName)
        hiddenPackages = mutable
        prefs.edit().putStringSet("hidden_packages", mutable).apply()
    }

    fun saveAppAlias(packageName: String, newAlias: String) {
        val mutable = customAliases.toMutableMap()
        if (newAlias.isBlank()) mutable.remove(packageName) else mutable[packageName] = newAlias.trim()
        customAliases = mutable
        saveKeyValueMap(prefs, "app_aliases", mutable)
    }

    fun saveAppNote(packageName: String, newNote: String) {
        val mutable = appNotes.toMutableMap()
        if (newNote.isBlank()) mutable.remove(packageName) else mutable[packageName] = newNote.trim()
        appNotes = mutable
        saveKeyValueMap(prefs, "app_notes", mutable)
    }

    fun cycleWaveSide() {
        val next = when (waveSide) {
            WaveSide.RIGHT -> WaveSide.LEFT
            WaveSide.LEFT -> WaveSide.BOTH
            WaveSide.BOTH -> WaveSide.RIGHT
        }
        waveSide = next
        prefs.edit().putString("wave_side", next.id).apply()
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    // ========================================================================
    // INTERFAZ PRINCIPAL
    // ========================================================================

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (wallpaperStyle == WallpaperStyle.SYSTEM_TRANSPARENT) Color.Transparent
                else currentTheme.backgroundColor
            )
    ) {
        // 1. CAPA DE FONDO DE PANTALLA (FOTOS, FONDOS GENERATIVOS O TEMA)
        LauncherWallpaperLayer(
            wallpaperStyle = wallpaperStyle,
            theme = currentTheme,
            customBitmap = customWallpaperBitmap,
            dimAmount = wallpaperDim
        )

        // 2. CONTENIDO CENTRAL (RELOJ, WIDGETS NIAGARA, FAVORITOS Y LISTA DE APPS)
        val startPadding = if (waveSide == WaveSide.LEFT || waveSide == WaveSide.BOTH) 46.dp else 20.dp
        val endPadding = if (waveSide == WaveSide.RIGHT || waveSide == WaveSide.BOTH) 46.dp else 20.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(start = startPadding, end = endPadding)
        ) {
            // Barra superior minimalista de acciones rápidas
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Píldora de estado / batería
                if (showStatusPill) {
                    Surface(
                        color = currentTheme.cardColor.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.clickable {
                            openSystemBatteryOrSettings(context)
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isCharging) "⚡ $batteryLevel%" else "🔋 $batteryLevel%",
                                color = currentTheme.textColor.copy(alpha = 0.9f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            if (selectedLetterFilter != null) {
                                Text(
                                    text = "  •  Filtro: $selectedLetterFilter ✕",
                                    color = currentTheme.accentColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable { selectedLetterFilter = null }
                                )
                            }
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.width(8.dp))
                }

                // Botones rápidos: Cambiar lado de la ola (⇄), Buscar (🔍), Fondos/Ajustes (🎨)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    QuickTopChip(
                        text = if (waveSide == WaveSide.LEFT) "⫷ Izq" else if (waveSide == WaveSide.RIGHT) "Der ⫸" else "⫷ Ambos ⫸",
                        theme = currentTheme,
                        onClick = { cycleWaveSide() }
                    )
                    QuickTopChip(
                        text = "🔍",
                        theme = currentTheme,
                        onClick = { isSearching = !isSearching }
                    )
                    QuickTopChip(
                        text = "🎨",
                        theme = currentTheme,
                        onClick = { showThemeDialog = true }
                    )
                }
            }

            // Panel desplegable de Búsqueda Universal + Calculadora Instantánea
            AnimatedVisibility(visible = isSearching) {
                NiagaraSearchAndCalculatorBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    allApps = visibleApps,
                    theme = currentTheme,
                    iconStyle = iconStyle,
                    monochromeIcons = monochromeIcons,
                    iconSizeDp = iconSizeDp,
                    onLaunchApp = { app ->
                        launchApp(context, app.packageName)
                        isSearching = false
                        searchQuery = ""
                    },
                    onLongPressApp = { app -> selectedAppForMenu = app },
                    onClose = {
                        isSearching = false
                        searchQuery = ""
                        focusManager.clearFocus()
                    }
                )
            }

            // Lista principal estilo Niagara (Cabecera con Reloj + Favoritos + Lista A-Z)
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 84.dp)
            ) {
                // SECCIÓN 0: CABECERA NIAGARA (Reloj, Agenda, Multimedia, Carpetas y Favoritos)
                item(key = "niagara_home_header") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp, bottom = 18.dp)
                    ) {
                        // Reloj interactivo (abre Alarma/Reloj)
                        Text(
                            text = currentTime.ifEmpty { "12:00" },
                            color = currentTheme.textColor,
                            fontSize = 56.sp,
                            fontWeight = FontWeight.Light,
                            letterSpacing = (-1.5).sp,
                            modifier = Modifier.clickable { openSystemClock(context) }
                        )

                        // Fecha interactiva + Nota de Agenda rápida (abre Calendario o Nota)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
                        ) {
                            Text(
                                text = currentDate,
                                color = currentTheme.accentColor,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.clickable { openSystemCalendar(context) }
                            )
                            Text(
                                text = "•",
                                color = currentTheme.textColor.copy(alpha = 0.4f),
                                fontSize = 14.sp
                            )
                            Text(
                                text = if (homeAgendaNote.isBlank()) "+ Nota rápida" else "📌 $homeAgendaNote",
                                color = currentTheme.textColor.copy(alpha = 0.75f),
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.clickable { showAgendaNoteDialog = true }
                            )
                        }

                        // Widget Reproductor Multimedia estilo Niagara (si está activo)
                        if (showMediaWidget) {
                            NiagaraMediaControlPill(context = context, theme = currentTheme)
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        // Carpetas Pop-up en Inicio (estilo Niagara)
                        if (popupFolders.isNotEmpty()) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp)
                            ) {
                                items(popupFolders, key = { it.id }) { folder ->
                                    Surface(
                                        color = currentTheme.cardColor.copy(alpha = 0.8f),
                                        shape = RoundedCornerShape(16.dp),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            currentTheme.accentColor.copy(alpha = 0.3f)
                                        ),
                                        modifier = Modifier.clickable {
                                            activePopupFolder = folder
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(text = folder.iconEmoji, fontSize = 16.sp)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = folder.name,
                                                color = currentTheme.textColor,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "(${folder.packageNames.size})",
                                                color = currentTheme.accentColor,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Sección de Apps Favoritas
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "★ FAVORITOS",
                                color = currentTheme.accentColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp
                            )
                            Text(
                                text = "Desliza → en una app para Pop-up",
                                color = currentTheme.textColor.copy(alpha = 0.45f),
                                fontSize = 11.sp
                            )
                        }

                        if (favoriteApps.isEmpty()) {
                            Text(
                                text = "Mantén pulsada cualquier app de la lista para añadirla a tus favoritos.",
                                color = currentTheme.textColor.copy(alpha = 0.6f),
                                fontSize = 13.sp,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        } else {
                            favoriteApps.forEach { favApp ->
                                NiagaraAppRow(
                                    app = favApp,
                                    theme = currentTheme,
                                    iconStyle = iconStyle,
                                    monochromeIcons = monochromeIcons,
                                    iconSizeDp = iconSizeDp,
                                    isFavoriteSection = true,
                                    onClick = { launchApp(context, favApp.packageName) },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        selectedAppForMenu = favApp
                                    },
                                    onSwipeRightOrPopup = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        selectedAppForPopup = favApp
                                    }
                                )
                            }
                        }

                        Divider(
                            color = currentTheme.textColor.copy(alpha = 0.12f),
                            modifier = Modifier.padding(top = 14.dp)
                        )
                    }
                }

                // SECCIÓN A-Z: TODAS LAS APLICACIONES AGRUPADAS POR LETRA
                val lettersToDisplay = if (selectedLetterFilter != null) {
                    listOf(selectedLetterFilter!!)
                } else {
                    ALPHABET_INDEX.drop(1)
                }

                lettersToDisplay.forEach { letter ->
                    val appsForLetter = groupedApps[letter]
                    if (!appsForLetter.isNullOrEmpty()) {
                        item(key = "header_$letter") {
                            Text(
                                text = letter,
                                color = currentTheme.accentColor,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(top = 14.dp, bottom = 4.dp, start = 6.dp)
                            )
                        }
                        items(
                            items = appsForLetter,
                            key = { "app_${it.packageName}" }
                        ) { app ->
                            NiagaraAppRow(
                                app = app,
                                theme = currentTheme,
                                iconStyle = iconStyle,
                                monochromeIcons = monochromeIcons,
                                iconSizeDp = iconSizeDp,
                                isFavoriteSection = false,
                                onClick = { launchApp(context, app.packageName) },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    selectedAppForMenu = app
                                },
                                onSwipeRightOrPopup = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    selectedAppForPopup = app
                                }
                            )
                        }
                    }
                }
            }
        }

        // 3. BARRA ALFABÉTICA RECTA CON OLA DINÁMICA ONDULANTE (IZQUIERDA, DERECHA O AMBOS)
        if (waveSide == WaveSide.RIGHT || waveSide == WaveSide.BOTH) {
            StraightWavingAlphabetBar(
                letters = ALPHABET_INDEX,
                isLeftSide = false,
                isDraggingThisSide = isWaveDragging && activeDragSide == WaveSide.RIGHT,
                touchYRatio = waveTouchYRatio,
                activeLetter = currentWaveLetter,
                theme = currentTheme,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .padding(top = 72.dp, bottom = 72.dp),
                onDragStart = { ratio ->
                    isWaveDragging = true
                    activeDragSide = WaveSide.RIGHT
                    waveTouchYRatio = ratio
                    val idx = (ratio * ALPHABET_INDEX.size).toInt().coerceIn(0, ALPHABET_INDEX.lastIndex)
                    onAlphabetLetterSelected(ALPHABET_INDEX[idx])
                },
                onDragMove = { ratio ->
                    waveTouchYRatio = ratio
                    val idx = (ratio * ALPHABET_INDEX.size).toInt().coerceIn(0, ALPHABET_INDEX.lastIndex)
                    onAlphabetLetterSelected(ALPHABET_INDEX[idx])
                },
                onDragEnd = {
                    isWaveDragging = false
                    currentWaveLetter = null
                }
            )
        }

        if (waveSide == WaveSide.LEFT || waveSide == WaveSide.BOTH) {
            StraightWavingAlphabetBar(
                letters = ALPHABET_INDEX,
                isLeftSide = true,
                isDraggingThisSide = isWaveDragging && activeDragSide == WaveSide.LEFT,
                touchYRatio = waveTouchYRatio,
                activeLetter = currentWaveLetter,
                theme = currentTheme,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .padding(top = 72.dp, bottom = 72.dp),
                onDragStart = { ratio ->
                    isWaveDragging = true
                    activeDragSide = WaveSide.LEFT
                    waveTouchYRatio = ratio
                    val idx = (ratio * ALPHABET_INDEX.size).toInt().coerceIn(0, ALPHABET_INDEX.lastIndex)
                    onAlphabetLetterSelected(ALPHABET_INDEX[idx])
                },
                onDragMove = { ratio ->
                    waveTouchYRatio = ratio
                    val idx = (ratio * ALPHABET_INDEX.size).toInt().coerceIn(0, ALPHABET_INDEX.lastIndex)
                    onAlphabetLetterSelected(ALPHABET_INDEX[idx])
                },
                onDragEnd = {
                    isWaveDragging = false
                    currentWaveLetter = null
                }
            )
        }

        // 4. BURBUJA FLOTANTE GRANDE DE LETRA ACTIVA AL DESLIZAR LA OLA
        AnimatedVisibility(
            visible = isWaveDragging && currentWaveLetter != null,
            enter = fadeIn(tween(100)),
            exit = fadeOut(tween(150)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(currentTheme.accentColor.copy(alpha = 0.92f))
                    .border(2.dp, currentTheme.textColor.copy(alpha = 0.6f), CircleShape)
            ) {
                Text(
                    text = currentWaveLetter ?: "",
                    color = currentTheme.backgroundColor,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        // 5. NIAGARA BUTTON (BOTÓN INFERIOR ICÓNICO DE NIAGARA)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 12.dp)
        ) {
            NiagaraBottomCoinButton(
                theme = currentTheme,
                onTap = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    isSearching = !isSearching
                },
                onLongPress = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showThemeDialog = true
                }
            )
        }
    }

    // ========================================================================
    // DIÁLOGOS Y VENTANAS EMERGENTES (POP-UPS NIAGARA, AJUSTES Y MENÚS)
    // ========================================================================

    // Menú contextual completo al mantener pulsada una aplicación
    selectedAppForMenu?.let { app ->
        AppContextMenuDialog(
            app = app,
            theme = currentTheme,
            canMoveUp = favoritePackages.indexOf(app.packageName) > 0,
            canMoveDown = favoritePackages.indexOf(app.packageName) in 0 until (favoritePackages.size - 1),
            onDismiss = { selectedAppForMenu = null },
            onToggleFavorite = {
                toggleFavorite(app)
                selectedAppForMenu = null
            },
            onMoveFavoriteUp = {
                moveFavorite(app.packageName, true)
