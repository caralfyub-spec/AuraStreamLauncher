package com.atenea.pixelultralauncher

import android.app.AlarmClock
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.provider.CalendarContract
import android.provider.Settings
import android.text.format.DateFormat
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Date
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

data class AppItem(
    val label: String,
    val packageName: String,
    val icon: Bitmap?,
    val initial: Char
)

data class LauncherTheme(
    val id: String,
    val name: String,
    val bgTop: Color,
    val bgBottom: Color,
    val accent: Color,
    val surface: Color,
    val textPrimary: Color,
    val textSecondary: Color
)

val AURA_THEMES = listOf(
    LauncherTheme(
        id = "oled",
        name = "OLED Pure Black",
        bgTop = Color(0xFF000000),
        bgBottom = Color(0xFF000000),
        accent = Color(0xFF00E5FF),
        surface = Color(0xFF111111),
        textPrimary = Color(0xFFFFFFFF),
        textSecondary = Color(0xFF888888)
    ),
    LauncherTheme(
        id = "cyber",
        name = "Aura Cyber (Neón)",
        bgTop = Color(0xFF090D16),
        bgBottom = Color(0xFF05070B),
        accent = Color(0xFF00E5FF),
        surface = Color(0xFF161F30),
        textPrimary = Color(0xFFFFFFFF),
        textSecondary = Color(0xFF94A3B8)
    ),
    LauncherTheme(
        id = "pixel",
        name = "Pixel Material",
        bgTop = Color(0xFF111827),
        bgBottom = Color(0xFF0B0F19),
        accent = Color(0xFFA8C7FA),
        surface = Color(0xFF1E293B),
        textPrimary = Color(0xFFF8FAFC),
        textSecondary = Color(0xFFCBD5E1)
    ),
    LauncherTheme(
        id = "emerald",
        name = "Bosque Esmeralda",
        bgTop = Color(0xFF062019),
        bgBottom = Color(0xFF03110D),
        accent = Color(0xFF34D399),
        surface = Color(0xFF0D3327),
        textPrimary = Color(0xFFECFDF5),
        textSecondary = Color(0xFFA7F3D0)
    )
)

val ALPHABET = ('A'..'Z').toList() + listOf('#')

// Letras de las esquinas que forman la estructura visible de la "C" en reposo
val CORNER_INDICES = setOf(0, 6, 13, 20, 25, 26) // A, G, N, U, Z, #

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {}
        })

        setContent {
            AuraStreamApp()
        }
    }
}

// Calcula la posición exacta (X, Y) de cada letra formando una "C" real en pantalla
fun calculateCPoint(index: Int, total: Int, width: Float, height: Float): Offset {
    val t = index.toFloat() / (total - 1).coerceAtLeast(1).toFloat()
    val topY = height * 0.17f
    val bottomY = height * 0.85f
    val y = topY + t * (bottomY - topY)

    // Curva en forma de C: las esquinas superior e inferior se abren hacia adentro y el centro se curva hacia el borde izquierdo
    val curveDepth = sin(t * PI).toFloat()
    val minX = width * 0.045f
    val maxX = width * 0.24f
    val x = maxX - curveDepth * (maxX - minX)

    return Offset(x, y)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AuraStreamApp() {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val prefs = remember { context.getSharedPreferences("aura_stream_prefs", Context.MODE_PRIVATE) }

    var selectedThemeId by remember {
        mutableStateOf(prefs.getString("theme_id", "oled") ?: "oled")
    }
    val currentTheme = remember(selectedThemeId) {
        AURA_THEMES.find { it.id == selectedThemeId } ?: AURA_THEMES.first()
    }

    var favoritePackages by remember {
        mutableStateOf(prefs.getStringSet("favorites", emptySet()) ?: emptySet())
    }

    var apps by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var showAllApps by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var selectedAppForMenu by remember { mutableStateOf<AppItem?>(null) }

    var activeCategoryChar by remember { mutableStateOf<Char?>(null) }
    var activeWaveIndex by remember { mutableStateOf<Int?>(null) }
    var containerSize by remember { mutableStateOf(IntSize(1080, 2400)) }

    val listState = rememberLazyListState()

    var currentTime by remember { mutableStateOf("") }
    var currentDate by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (true) {
            val now = Date()
            currentTime = DateFormat.format("HH:mm", now).toString()
            currentDate = DateFormat.format("EEEE, d MMMM", now).toString()
                .replaceFirstChar { it.uppercase() }
            delay(1000L)
        }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
            val loadedApps = resolveInfos.mapNotNull { ri ->
                val pkg = ri.activityInfo.packageName
                if (pkg == context.packageName) return@mapNotNull null
                val label = ri.loadLabel(pm)?.toString()?.trim() ?: pkg
                val firstChar = label.firstOrNull()?.uppercaseChar() ?: '#'
                val initial = if (firstChar in 'A'..'Z') firstChar else '#'
                val drawable = ri.loadIcon(pm)
                AppItem(
                    label = label,
                    packageName = pkg,
                    icon = drawableToBitmap(drawable),
                    initial = initial
                )
            }.sortedBy { it.label.lowercase() }

            withContext(Dispatchers.Main) {
                apps = loadedApps
                if (favoritePackages.isEmpty() && loadedApps.isNotEmpty()) {
                    val defaultFavs = loadedApps.take(6).map { it.packageName }.toSet()
                    favoritePackages = defaultFavs
                    prefs.edit().putStringSet("favorites", defaultFavs).apply()
                }
            }
        }
    }

    val favoriteApps = remember(apps, favoritePackages) {
        favoritePackages.mapNotNull { pkg -> apps.find { it.packageName == pkg } }
    }

    val displayedApps = remember(apps, searchQuery, activeCategoryChar, showAllApps) {
        when {
            searchQuery.isNotBlank() -> apps.filter { it.label.contains(searchQuery.trim(), ignoreCase = true) }
            activeCategoryChar != null -> apps.filter { it.initial == activeCategoryChar }
            showAllApps -> apps
            else -> emptyList()
        }
    }

    val cPoints = remember(containerSize) {
        val w = containerSize.width.toFloat().coerceAtLeast(1f)
        val h = containerSize.height.toFloat().coerceAtLeast(1f)
        ALPHABET.indices.map { i -> calculateCPoint(i, ALPHABET.size, w, h) }
    }

    fun updateCWaveFromTouch(touch: Offset) {
        if (cPoints.isEmpty()) return
        var closestIndex = 0
        var minDist = Float.MAX_VALUE
        cPoints.forEachIndexed { idx, pt ->
            val d = hypot(touch.x - pt.x, touch.y - pt.y)
            if (d < minDist) {
                minDist = d
                closestIndex = idx
            }
        }
        // Radio de activación generoso alrededor de la curva C
        val maxThreshold = containerSize.width * 0.42f
        if (minDist <= maxThreshold) {
            if (activeWaveIndex != closestIndex) {
                activeWaveIndex = closestIndex
                activeCategoryChar = ALPHABET[closestIndex]
                isSearching = false
                showAllApps = false
                searchQuery = ""
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(currentTheme.bgTop, currentTheme.bgBottom)
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .onSizeChanged { containerSize = it }
    ) {
        // 1. LÍNEA GUÍA SUTIL EN FORMA DE "C" + ESTELA LUMINOSA AL PASAR EL DEDO
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (cPoints.size > 1) {
                val guidePath = Path().apply {
                    moveTo(cPoints.first().x, cPoints.first().y)
                    for (i in 1 until cPoints.size) {
                        val prev = cPoints[i - 1]
                        val curr = cPoints[i]
                        val midX = (prev.x + curr.x) / 2f
                        val midY = (prev.y + curr.y) / 2f
                        quadraticBezierTo(prev.x, prev.y, midX, midY)
                    }
                    lineTo(cPoints.last().x, cPoints.last().y)
                }

                // Línea guía continua, fina y sutil para no perderse
                drawPath(
                    path = guidePath,
                    color = currentTheme.accent.copy(alpha = 0.18f),
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )

                // Pequeños puntos guía en las esquinas de la C
                CORNER_INDICES.forEach { cornerIdx ->
                    if (cornerIdx in cPoints.indices) {
                        drawCircle(
                            color = currentTheme.accent.copy(alpha = 0.28f),
                            radius = 3.dp.toPx(),
                            center = cPoints[cornerIdx]
                        )
                    }
                }

                // Resplandor mágico en el tramo de la curva C que estás pisando
                activeWaveIndex?.let { activeIdx ->
                    val startIdx = (activeIdx - 2).coerceAtLeast(0)
                    val endIdx = (activeIdx + 2).coerceAtMost(cPoints.lastIndex)
                    val glowPath = Path().apply {
                        moveTo(cPoints[startIdx].x, cPoints[startIdx].y)
                        for (i in (startIdx + 1)..endIdx) {
                            lineTo(cPoints[i].x, cPoints[i].y)
                        }
                    }
                    drawPath(
                        path = glowPath,
                        color = currentTheme.accent.copy(alpha = 0.75f),
                        style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }
        }

        // 2. CONTENIDO PRINCIPAL (RELOJ NIAGARA, BUSCADOR Y LISTA DE APPS)
        // Desplazado ligeramente a la derecha para convivir con la curva C izquierda
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 68.dp, end = 20.dp, top = 12.dp, bottom = 12.dp)
        ) {
            // Cabecera con Reloj Interactivo y Botones de Tema / Ajustes
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        text = currentTime,
                        color = currentTheme.textPrimary,
                        fontSize = 54.sp,
                        fontWeight = FontWeight.Light,
                        letterSpacing = (-2).sp,
                        modifier = Modifier.clickable {
                            try {
                                context.startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                })
                            } catch (_: Exception) {}
                        }
                    )
                    Text(
                        text = currentDate,
                        color = currentTheme.accent,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable {
                            try {
                                context.startActivity(Intent(Intent.ACTION_MAIN).apply {
                                    addCategory(Intent.CATEGORY_APP_CALENDAR)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                })
                            } catch (_: Exception) {}
                        }
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Botón de Temas (sin dependencias externas que rompan Gradle)
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(currentTheme.surface)
                            .border(1.dp, currentTheme.accent.copy(alpha = 0.5f), CircleShape)
                            .clickable { showThemeDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "🎨", fontSize = 16.sp)
                    }

                    IconButton(
                        onClick = { showSettingsDialog = true },
                        modifier = Modifier
                            .size(38.dp)
                            .background(currentTheme.surface, CircleShape)
                            .border(1.dp, currentTheme.textSecondary.copy(alpha = 0.2f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Ajustes",
                            tint = currentTheme.textPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Barra de búsqueda minimalista
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(currentTheme.surface, RoundedCornerShape(24.dp))
                    .border(1.dp, currentTheme.accent.copy(alpha = 0.35f), RoundedCornerShape(24.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = currentTheme.accent,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                BasicTextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        isSearching = it.isNotBlank()
                        if (it.isNotBlank()) activeCategoryChar = null
                    },
                    singleLine = true,
                    textStyle = TextStyle(color = currentTheme.textPrimary, fontSize = 15.sp),
                    cursorBrush = SolidColor(currentTheme.accent),
                    modifier = Modifier.weight(1f),
                    decorationBox = { innerTextField ->
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Buscar app o pisa la curva C...",
                                color = currentTheme.textSecondary,
                                fontSize = 14.sp
                            )
                        }
                        innerTextField()
                    }
                )
                if (searchQuery.isNotBlank()) {
                    IconButton(
                        onClick = {
                            searchQuery = ""
                            isSearching = false
                        },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            tint = currentTheme.textSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Lista dinámica estilo Niagara
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 64.dp)
            ) {
                if (!isSearching && activeCategoryChar == null && !showAllApps) {
                    if (favoriteApps.isNotEmpty()) {
                        item(key = "fav_header") {
                            Text(
                                text = "FAVORITOS",
                                color = currentTheme.textSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.5.sp,
                                modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
                            )
                        }
                        items(favoriteApps, key = { "fav_${it.packageName}" }) { app ->
                            AppRow(
                                app = app,
                                theme = currentTheme,
                                isFavorite = true,
                                onClick = { launchApp(context, app.packageName) },
                                onLongClick = { selectedAppForMenu = app }
                            )
                        }
                    }

                    item(key = "all_apps_button") {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(currentTheme.surface.copy(alpha = 0.6f))
                                .clickable { showAllApps = true }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Ver todas las aplicaciones (${apps.size})",
                                color = currentTheme.textSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "➔",
                                color = currentTheme.accent,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    item(key = "filter_header") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = when {
                                    searchQuery.isNotBlank() -> "RESULTADOS"
                                    activeCategoryChar != null -> "LETRA $activeCategoryChar"
                                    else -> "TODAS LAS APPS"
                                },
                                color = currentTheme.accent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.5.sp
                            )
                            TextButton(
                                onClick = {
                                    searchQuery = ""
                                    isSearching = false
                                    activeCategoryChar = null
                                    showAllApps = false
                                }
                            ) {
                                Text("Inicio", color = currentTheme.textSecondary, fontSize = 12.sp)
                            }
                        }
                    }

                    if (displayedApps.isEmpty()) {
                        item(key = "empty_state") {
                            Text(
                                text = "Sin aplicaciones con esta inicial",
                                color = currentTheme.textSecondary,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(vertical = 20.dp)
                            )
                        }
                    } else {
                        items(displayedApps, key = { "list_${it.packageName}" }) { app ->
                            AppRow(
                                app = app,
                                theme = currentTheme,
                                isFavorite = app.packageName in favoritePackages,
                                onClick = { launchApp(context, app.packageName) },
                                onLongClick = { selectedAppForMenu = app }
                            )
                        }
                    }
                }
            }
        }

        // 3. LETRAS DE LA CURVA "C" MÁGICA (ESQUINAS VISIBLES + APARICIÓN/DESAPARICIÓN AL PISARLAS)
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(110.dp)
                .align(Alignment.CenterStart)
                .pointerInput(containerSize) {
                    detectTapGestures(
                        onPress = { offset ->
                            updateCWaveFromTouch(offset)
                            tryAwaitRelease()
                            activeWaveIndex = null
                        }
                    )
                }
                .pointerInput(containerSize) {
                    detectDragGestures(
                        onDragStart = { offset -> updateCWaveFromTouch(offset) },
                        onDrag = { change, _ ->
                            change.consume()
                            updateCWaveFromTouch(change.position)
                        },
                        onDragEnd = { activeWaveIndex = null },
                        onDragCancel = { activeWaveIndex = null }
                    )
                }
        ) {
            cPoints.forEachIndexed { index, point ->
                val isCorner = index in CORNER_INDICES
                val dist = activeWaveIndex?.let { abs(index - it) } ?: 99

                // Visibilidad mágica:
                // - En reposo: SOLO se ven las letras de las esquinas que forman la C
                // - Al mover el dedo: aparecen las letras que vas pisando y desaparecen al pasar
                val targetAlpha = when {
                    dist == 0 -> 1f
                    dist == 1 -> 0.85f
                    dist == 2 -> 0.45f
                    isCorner -> 0.48f
                    else -> 0f
                }

                val targetScale = when (dist) {
                    0 -> 1.75f
                    1 -> 1.3f
                    2 -> 1.08f
                    else -> 1f
                }

                // Empuje suave hacia la derecha cuando el dedo pisa la letra para que se vea perfecta
                val pushRightPx = when (dist) {
                    0 -> with(density) { 28.dp.toPx() }
                    1 -> with(density) { 16.dp.toPx() }
                    2 -> with(density) { 6.dp.toPx() }
                    else -> 0f
                }

                val animatedAlpha by animateFloatAsState(
                    targetValue = targetAlpha,
                    animationSpec = tween(durationMillis = 120)
                )
                val animatedScale by animateFloatAsState(
                    targetValue = targetScale,
                    animationSpec = spring(dampingRatio = 0.55f, stiffness = 450f)
                )
                val animatedPushX by animateFloatAsState(
                    targetValue = pushRightPx,
                    animationSpec = spring(dampingRatio = 0.6f, stiffness = 450f)
                )

                if (animatedAlpha > 0.02f) {
                    val boxSizePx = with(density) { 28.dp.toPx() }
                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    x = (point.x - boxSizePx / 2f + animatedPushX).roundToInt(),
                                    y = (point.y - boxSizePx / 2f).roundToInt()
                                )
                            }
                            .size(28.dp)
                            .scale(animatedScale)
                            .alpha(animatedAlpha)
                            .background(
                                color = if (dist == 0) currentTheme.surface else Color.Transparent,
                                shape = CircleShape
                            )
                            .then(
                                if (dist == 0) Modifier.border(1.5.dp, currentTheme.accent, CircleShape)
                                else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = ALPHABET[index].toString(),
                            color = if (dist == 0 || isCorner) currentTheme.accent else currentTheme.textPrimary,
                            fontSize = if (isCorner && dist > 1) 12.sp else 13.sp,
                            fontWeight = if (dist == 0 || isCorner) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        // 4. BURBUJA FLOTANTE CENTRAL CUANDO PISAS LAS LETRAS DE LA CURVA "C"
        AnimatedVisibility(
            visible = activeWaveIndex != null,
            enter = fadeIn(tween(40)),
            exit = fadeOut(tween(120)),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(78.dp)
                    .background(currentTheme.surface.copy(alpha = 0.94f), CircleShape)
                    .border(2.dp, currentTheme.accent, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = activeWaveIndex?.let { ALPHABET[it].toString() } ?: "",
                    color = currentTheme.accent,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Selector de Temas
        if (showThemeDialog) {
            AlertDialog(
                onDismissRequest = { showThemeDialog = false },
                containerColor = currentTheme.surface,
                title = {
                    Text("Personalizar Temas", color = currentTheme.textPrimary, fontWeight = FontWeight.Bold)
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AURA_THEMES.forEach { t ->
                            val isSelected = t.id == currentTheme.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) t.accent.copy(alpha = 0.2f) else Color.Transparent)
                                    .border(
                                        1.dp,
                                        if (isSelected) t.accent else Color.Gray.copy(alpha = 0.3f),
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable {
                                        selectedThemeId = t.id
                                        prefs.edit().putString("theme_id", t.id).apply()
                                        showThemeDialog = false
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.size(18.dp).background(t.accent, CircleShape))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(text = t.name, color = currentTheme.textPrimary, fontSize = 15.sp)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showThemeDialog = false }) {
                        Text("Cerrar", color = currentTheme.accent)
                    }
                }
            )
        }

        // Ajustes del Launcher
        if (showSettingsDialog) {
            AlertDialog(
                onDismissRequest = { showSettingsDialog = false },
                containerColor = currentTheme.surface,
                title = {
                    Text("Ajustes de Aura Stream", color = currentTheme.textPrimary, fontWeight = FontWeight.Bold)
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        TextButton(
                            onClick = {
                                context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
                                showSettingsDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Establecer como Launcher predeterminado", color = currentTheme.accent, fontSize = 15.sp)
                        }
                        TextButton(
                            onClick = {
                                context.startActivity(Intent(Settings.ACTION_SETTINGS))
                                showSettingsDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Ajustes del sistema Android", color = currentTheme.textPrimary, fontSize = 15.sp)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSettingsDialog = false }) {
                        Text("Cerrar", color = currentTheme.textSecondary)
                    }
                }
            )
        }

        // Menú Contextual al mantener pulsada una app
        selectedAppForMenu?.let { app ->
            val isFav = app.packageName in favoritePackages
            AlertDialog(
                onDismissRequest = { selectedAppForMenu = null },
                containerColor = currentTheme.surface,
                title = {
                    Text(app.label, color = currentTheme.textPrimary, fontWeight = FontWeight.Bold)
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(
                            onClick = {
                                val updated = favoritePackages.toMutableSet()
                                if (isFav) updated.remove(app.packageName) else updated.add(app.packageName)
                                favoritePackages = updated
                                prefs.edit().putStringSet("favorites", updated).apply()
                                selectedAppForMenu = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = currentTheme.accent,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = if (isFav) "Quitar de Favoritos" else "Añadir a Favoritos",
                                    color = currentTheme.accent,
                                    fontSize = 15.sp
                                )
                            }
                        }
                        TextButton(
                            onClick = {
                                context.startActivity(
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.fromParts("package", app.packageName, null)
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                )
                                selectedAppForMenu = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = currentTheme.textPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Información de la app", color = currentTheme.textPrimary, fontSize = 15.sp)
                            }
                        }
                        TextButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_DELETE).apply {
                                    data = Uri.parse("package:${app.packageName}")
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                                selectedAppForMenu = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = Color(0xFFFF5252),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Desinstalar app", color = Color(0xFFFF5252), fontSize = 15.sp)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { selectedAppForMenu = null }) {
                        Text("Cancelar", color = currentTheme.textSecondary)
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppRow(
    app: AppItem,
    theme: LauncherTheme,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(theme.surface.copy(alpha = 0.85f))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (app.icon != null) {
            Image(
                bitmap = app.icon.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
            )
        } else {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(theme.accent.copy(alpha = 0.2f), CircleShape)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = app.label,
            color = theme.textPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (isFavorite) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = theme.accent,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

fun launchApp(context: Context, packageName: String) {
    context.packageManager.getLaunchIntentForPackage(packageName)?.let {
        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(it)
    }
}

fun drawableToBitmap(drawable: Drawable): Bitmap {
    if (drawable is BitmapDrawable && drawable.bitmap != null) return drawable.bitmap
    val w = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 96
    val h = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 96
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bmp)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bmp
}
