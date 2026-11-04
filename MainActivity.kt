package com.atenea.pixelultralauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.os.SystemClock
import android.provider.AlarmClock
import android.provider.Settings
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt

// ==========================================
// MODELOS DE DATOS Y TEMAS BASE (INTACTOS)
// ==========================================

data class AppItem(
    val label: String,
    val packageName: String,
    val icon: ImageBitmap?,
    val initial: String
)

data class LauncherTheme(
    val id: String,
    val name: String,
    val bgColors: List<Color>,
    val accentColor: Color,
    val textColor: Color,
    val cardColor: Color
)

data class WallpaperStyle(
    val id: String,
    val name: String,
    val colors: List<Color>
)

val AURA_THEMES = listOf(
    LauncherTheme(
        id = "cyber_neon",
        name = "Cyber Neon",
        bgColors = listOf(Color(0xFF0A0E17), Color(0xFF141E30), Color(0xFF0F172A)),
        accentColor = Color(0xFF00F2FE),
        textColor = Color(0xFFF8FAFC),
        cardColor = Color(0x26FFFFFF)
    ),
    LauncherTheme(
        id = "minimal_dark",
        name = "Niagara Dark",
        bgColors = listOf(Color(0xFF000000), Color(0xFF0B0B0E), Color(0xFF121216)),
        accentColor = Color(0xFFA78BFA),
        textColor = Color(0xFFE2E8F0),
        cardColor = Color(0x1FFFFFFF)
    ),
    LauncherTheme(
        id = "emerald_forest",
        name = "Emerald Flow",
        bgColors = listOf(Color(0xFF041C15), Color(0xFF083326), Color(0xFF052018)),
        accentColor = Color(0xFF34D399),
        textColor = Color(0xFFECFDF5),
        cardColor = Color(0x22FFFFFF)
    ),
    LauncherTheme(
        id = "sunset_gold",
        name = "Sunset Aura",
        bgColors = listOf(Color(0xFF1E0B18), Color(0xFF381224), Color(0xFF140711)),
        accentColor = Color(0xFFFB923C),
        textColor = Color(0xFFFFF7ED),
        cardColor = Color(0x24FFFFFF)
    ),
    LauncherTheme(
        id = "frost_light",
        name = "Frost Crystal",
        bgColors = listOf(Color(0xFF1E293B), Color(0xFF334155), Color(0xFF0F172A)),
        accentColor = Color(0xFF38BDF8),
        textColor = Color(0xFFF1F5F9),
        cardColor = Color(0x28FFFFFF)
    )
)

val WALLPAPER_STYLES = listOf(
    WallpaperStyle("theme_default", "Del Tema", emptyList()),
    WallpaperStyle("amoled_pure", "Negro AMOLED", listOf(Color(0xFF000000), Color(0xFF000000), Color(0xFF050505))),
    WallpaperStyle("aurora_boreal", "Aurora Boreal", listOf(Color(0xFF031B29), Color(0xFF0B4F6C), Color(0xFF14532D), Color(0xFF090D16))),
    WallpaperStyle("crimson_dusk", "Atardecer Carmesí", listOf(Color(0xFF1A0622), Color(0xFF581C4F), Color(0xFF9A3412), Color(0xFF0F0514))),
    WallpaperStyle("deep_ocean", "Océano Abisal", listOf(Color(0xFF020617), Color(0xFF0F172A), Color(0xFF1E3A8A), Color(0xFF020617))),
    WallpaperStyle("violet_nebula", "Nebulosa Violeta", listOf(Color(0xFF0D041A), Color(0xFF2E1065), Color(0xFF4C1D95), Color(0xFF090314))),
    WallpaperStyle("custom_photo", "Foto de Galería", emptyList())
)

val WAVE_ALPHABET = listOf("★") + ('A'..'Z').map { it.toString() } + listOf("#")

// ==========================================
// ACTIVIDAD PRINCIPAL
// ==========================================

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                AuraStreamApp()
            }
        }
    }
}

// ==========================================
// COMPONENTE PRINCIPAL DEL LAUNCHER
// ==========================================

@Composable
fun AuraStreamApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val prefs = remember { context.getSharedPreferences("aura_prefs", Context.MODE_PRIVATE) }

    // --- Estados de Temas y Fondos ---
    var currentThemeId by remember {
        mutableStateOf(prefs.getString("theme_id", AURA_THEMES.first().id) ?: AURA_THEMES.first().id)
    }
    val currentTheme = remember(currentThemeId) {
        AURA_THEMES.find { it.id == currentThemeId } ?: AURA_THEMES.first()
    }
    var wallpaperStyleId by remember {
        mutableStateOf(prefs.getString("wallpaper_style_id", "theme_default") ?: "theme_default")
    }
    var wallpaperDim by remember {
        mutableStateOf(prefs.getFloat("wallpaper_dim", 0.35f))
    }
    var customWallpaperBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    // --- Estados de Configuración Niagara ---
    var isWaveOnLeft by remember {
        mutableStateOf(prefs.getBoolean("wave_on_left", false))
    }
    var favoritesOnlyHome by remember {
        mutableStateOf(prefs.getBoolean("favorites_only_home", true))
    }
    var showMediaWidget by remember {
        mutableStateOf(prefs.getBoolean("show_media_widget", false))
    }
    var showNiagaraButton by remember {
        mutableStateOf(prefs.getBoolean("show_niagara_button", true))
    }
    var agendaNote by remember {
        mutableStateOf(prefs.getString("agenda_note", "Toca para añadir nota o recordatorio") ?: "Toca para añadir nota o recordatorio")
    }

    // --- Estados de Aplicaciones, Favoritos, Ocultas, Alias y Carpetas ---
    var allApps by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var favoritePackages by remember {
        mutableStateOf(prefs.getStringSet("favorites", emptySet())?.toSet() ?: emptySet())
    }
    var hiddenPackages by remember {
        mutableStateOf(prefs.getStringSet("hidden_apps", emptySet())?.toSet() ?: emptySet())
    }
    var customNamesMap by remember {
        mutableStateOf(loadStringMap(prefs, "custom_app_names"))
    }
    var foldersMap by remember {
        mutableStateOf(loadFoldersMap(prefs))
    }

    // --- Estados de Navegación y UI ---
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var viewingAllApps by remember { mutableStateOf(false) }
    var selectedLetter by remember { mutableStateOf<String?>(null) }
    var isTouchActive by remember { mutableStateOf(false) }
    var touchY by remember { mutableStateOf(0f) }

    // --- Estados de Diálogos y Menús ---
    var selectedAppForMenu by remember { mutableStateOf<AppItem?>(null) }
    var appToRename by remember { mutableStateOf<AppItem?>(null) }
    var appToAssignFolder by remember { mutableStateOf<AppItem?>(null) }
    var openedFolderName by remember { mutableStateOf<String?>(null) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showHiddenAppsDialog by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showAgendaDialog by remember { mutableStateOf(false) }

    // --- Estados de Reloj y Batería ---
    var currentTime by remember { mutableStateOf("") }
    var currentDate by remember { mutableStateOf("") }
    var batteryLevel by remember { mutableStateOf(100) }
    var isCharging by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    // Selector de fotos de galería (guarda copia local para persistencia total)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val destFile = File(context.filesDir, "aura_custom_wallpaper.jpg")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    val bmp = decodeSampledBitmapFromFile(destFile, 1080, 2400)
                    withContext(Dispatchers.Main) {
                        if (bmp != null) {
                            customWallpaperBitmap = bmp.asImageBitmap()
                            wallpaperStyleId = "custom_photo"
                            prefs.edit().putString("wallpaper_style_id", "custom_photo").apply()
                        } else {
                            Toast.makeText(context, "No se pudo cargar la imagen", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Error al guardar el fondo", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // Cargar foto guardada si existe al iniciar
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val destFile = File(context.filesDir, "aura_custom_wallpaper.jpg")
            if (destFile.exists()) {
                val bmp = decodeSampledBitmapFromFile(destFile, 1080, 2400)
                if (bmp != null) {
                    withContext(Dispatchers.Main) {
                        customWallpaperBitmap = bmp.asImageBitmap()
                    }
                }
            }
        }
    }

    // Reloj en tiempo real
    LaunchedEffect(Unit) {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dateFormat = SimpleDateFormat("EEEE, d 'de' MMMM", Locale("es", "ES"))
        while (true) {
            val now = Date()
            currentTime = timeFormat.format(now)
            currentDate = dateFormat.format(now).replaceFirstChar { it.uppercase() }
            delay(1000L)
        }
    }

    // Receptor de batería en tiempo real
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent != null) {
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    if (level >= 0 && scale > 0) {
                        batteryLevel = (level * 100) / scale
                    }
                    isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                            status == BatteryManager.BATTERY_STATUS_FULL
                }
            }
        }
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {}
        }
    }

    // Carga de aplicaciones instaladas
    fun reloadApps() {
        scope.launch(Dispatchers.IO) {
            val pm = context.packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
            val loaded = resolveInfos.mapNotNull { info ->
                val pkg = info.activityInfo.packageName
                if (pkg == context.packageName) return@mapNotNull null
                val rawLabel = info.loadLabel(pm)?.toString() ?: pkg
                val displayLabel = customNamesMap[pkg] ?: rawLabel
                val iconDrawable = try { info.loadIcon(pm) } catch (_: Exception) { null }
                val iconBitmap = iconDrawable?.let { drawableToBitmap(it).asImageBitmap() }
                val firstChar = displayLabel.trim().firstOrNull()?.uppercaseChar() ?: '#'
                val initial = if (firstChar in 'A'..'Z') firstChar.toString() else "#"
                AppItem(
                    label = displayLabel,
                    packageName = pkg,
                    icon = iconBitmap,
                    initial = initial
                )
            }.distinctBy { it.packageName }
                .sortedBy { it.label.lowercase(Locale.getDefault()) }

            withContext(Dispatchers.Main) {
                allApps = loaded
            }
        }
    }

    LaunchedEffect(customNamesMap) {
        reloadApps()
    }

    // Escuchar instalaciones/desinstalaciones de paquetes
    DisposableEffect(context) {
        val pkgReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                reloadApps()
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }
        context.registerReceiver(pkgReceiver, filter)
        onDispose {
            try { context.unregisterReceiver(pkgReceiver) } catch (_: Exception) {}
        }
    }

    // Botón Atrás vuelve a la vista limpia de Favoritos
    BackHandler(enabled = isSearching || viewingAllApps || selectedLetter != null) {
        isSearching = false
        searchQuery = ""
        viewingAllApps = false
        selectedLetter = null
    }

    // Filtrado de aplicaciones visibles
    val visibleApps = remember(allApps, hiddenPackages) {
        allApps.filter { it.packageName !in hiddenPackages }
    }

    val favoriteApps = remember(visibleApps, favoritePackages) {
        visibleApps.filter { it.packageName in favoritePackages }
    }

    val displayedApps = remember(
        visibleApps,
        favoriteApps,
        searchQuery,
        isSearching,
        favoritesOnlyHome,
        viewingAllApps
    ) {
        when {
            isSearching && searchQuery.isNotBlank() -> {
                visibleApps.filter {
                    it.label.contains(searchQuery.trim(), ignoreCase = true)
                }
            }
            favoritesOnlyHome && !viewingAllApps -> {
                if (favoriteApps.isNotEmpty()) favoriteApps else visibleApps.take(8)
            }
            else -> visibleApps
        }
    }

    // Función para desplazar la lista al deslizar por la línea ondulante
    fun onWaveLetterSelected(letter: String) {
        selectedLetter = letter
        if (letter == "★") {
            viewingAllApps = false
            scope.launch { listState.scrollToItem(0) }
        } else {
            viewingAllApps = true
            val targetList = visibleApps
            val index = targetList.indexOfFirst {
                it.initial.equals(letter, ignoreCase = true) ||
                        (letter != "#" && it.initial > letter)
            }
            if (index >= 0) {
                scope.launch {
                    // +1 por la cabecera en LazyColumn
                    listState.scrollToItem(index + 1)
                }
            }
        }
    }

    // Selección del fondo activo
    val activeBgColors = remember(currentTheme, wallpaperStyleId) {
        val style = WALLPAPER_STYLES.find { it.id == wallpaperStyleId }
        if (style != null && style.colors.isNotEmpty()) style.colors else currentTheme.bgColors
    }

    // ==========================================
    // CONTENEDOR PRINCIPAL DE PANTALLA
    // ==========================================
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(activeBgColors))
    ) {
        // 1. Foto personalizada de fondo si está activa
        if (wallpaperStyleId == "custom_photo" && customWallpaperBitmap != null) {
            Image(
                bitmap = customWallpaperBitmap!!,
                contentDescription = "Fondo de pantalla personalizado",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = wallpaperDim))
            )
        }

        // 2. Contenido Principal (con margen dinámico según el lado de la línea alfabética)
        val startPad = if (isWaveOnLeft) 52.dp else 20.dp
        val endPad = if (isWaveOnLeft) 20.dp else 52.dp

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(start = startPad, end = endPad),
            contentPadding = PaddingValues(top = 48.dp, bottom = 100.dp)
        ) {
            // --- CABECERA NIAGARA (Reloj, Fecha, Batería, Agenda, Multimedia, Carpetas) ---
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 18.dp)
                ) {
                    // Fila superior: Reloj +
