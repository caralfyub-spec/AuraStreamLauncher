package com.atenea.pixelultralauncher

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date
import kotlin.math.abs

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
        name = "Pixel Material (Azul)",
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AuraStreamApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("aura_stream_prefs", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

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
    var showThemeDialog by remember { mutableStateOf(false) }
    var selectedAppForMenu by remember { mutableStateOf<AppItem?>(null) }

    var waveTouchY by remember { mutableStateOf<Float?>(null) }
    var waveBarHeight by remember { mutableStateOf(1) }
    var activeWaveChar by remember { mutableStateOf<Char?>(null) }

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
                    val defaultFavs = loadedApps.take(4).map { it.packageName }.toSet()
                    favoritePackages = defaultFavs
                    prefs.edit().putStringSet("favorites", defaultFavs).apply()
                }
            }
        }
    }

    val favoriteApps = remember(apps, favoritePackages) {
        apps.filter { it.packageName in favoritePackages }
    }

    val filteredApps = remember(apps, searchQuery) {
        if (searchQuery.isBlank()) apps
        else apps.filter { it.label.contains(searchQuery.trim(), ignoreCase = true) }
    }

    val letterIndexMap = remember(filteredApps) {
        val map = mutableMapOf<Char, Int>()
        val headerCount = 1
        filteredApps.forEachIndexed { index, app ->
            if (!map.containsKey(app.initial)) {
                map[app.initial] = index + headerCount
            }
        }
        map
    }

    fun handleWaveAt(y: Float) {
        if (waveBarHeight <= 0) return
        val clampedY = y.coerceIn(0f, waveBarHeight.toFloat())
        waveTouchY = clampedY
        val fraction = clampedY / waveBarHeight.toFloat()
        val index = (fraction * ALPHABET.size).toInt().coerceIn(0, ALPHABET.lastIndex)
        val char = ALPHABET[index]

        if (activeWaveChar != char) {
            activeWaveChar = char
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            letterIndexMap[char]?.let { targetIdx ->
                scope.launch {
                    listState.scrollToItem(targetIdx)
                }
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
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(end = 40.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (isSearching) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .background(currentTheme.surface, RoundedCornerShape(24.dp))
                            .border(1.dp, currentTheme.accent.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Buscar",
                            tint = currentTheme.accent,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = currentTheme.textPrimary,
                                fontSize = 16.sp
                            ),
                            cursorBrush = SolidColor(currentTheme.accent),
                            modifier = Modifier.weight(1f),
                            decorationBox = { innerTextField ->
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "Buscar app...",
                                        color = currentTheme.textSecondary,
                                        fontSize = 16.sp
                                    )
                                }
                                innerTextField()
                            }
                        )
                        IconButton(
                            onClick = {
                                searchQuery = ""
                                isSearching = false
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cerrar",
                                tint = currentTheme.textSecondary
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(currentTheme.surface)
                            .clickable { isSearching = true }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Buscar",
                            tint = currentTheme.accent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Aura Stream",
                            color = currentTheme.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        IconButton(
                            onClick = { showThemeDialog = true },
                            modifier = Modifier
                                .size(38.dp)
                                .background(currentTheme.surface, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = "Temas",
                                tint = currentTheme.accent,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = { context.startActivity(Intent(Settings.ACTION_SETTINGS)) },
                            modifier = Modifier
                                .size(38.dp)
                                .background(currentTheme.surface, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Ajustes",
                                tint = currentTheme.textPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                item(key = "header_section") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        if (searchQuery.isBlank()) {
                            Text(
                                text = currentTime,
                                color = currentTheme.textPrimary,
                                fontSize = 58.sp,
                                fontWeight = FontWeight.Light
                            )
                            Text(
                                text = currentDate,
                                color = currentTheme.accent,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(20.dp))

                            if (favoriteApps.isNotEmpty()) {
                                Text(
                                    text = "FAVORITOS",
                                    color = currentTheme.textSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.5.sp,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(currentTheme.surface, RoundedCornerShape(20.dp))
                                        .padding(vertical = 4.dp)
                                ) {
                                    favoriteApps.forEach { fav ->
                                        AppRow(
                                            app = fav,
                                            theme = currentTheme,
                                            isFavorite = true,
                                            onClick = { launchApp(context, fav.packageName) },
                                            onLongClick = { selectedAppForMenu = fav }
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }

                        Text(
                            text = if (searchQuery.isBlank()) "APLICACIONES" else "RESULTADOS",
                            color = currentTheme.textSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        )
                    }
                }

                items(
                    items = filteredApps,
                    key = { it.packageName }
                ) { app ->
                    val showLetter = filteredApps.firstOrNull { it.initial == app.initial }?.packageName == app.packageName
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        if (showLetter && searchQuery.isBlank()) {
                            Text(
                                text = app.initial.toString(),
                                color = currentTheme.accent,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 14.dp, top = 8.dp, bottom = 2.dp)
                            )
                        }
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

        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(42.dp)
                .padding(vertical = 40.dp)
                .onSizeChanged { waveBarHeight = it.height.coerceAtLeast(1) }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = { offset ->
                            handleWaveAt(offset.y)
                            tryAwaitRelease()
                            waveTouchY = null
                            activeWaveChar = null
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset -> handleWaveAt(offset.y) },
                        onDrag = { change, _ ->
                            change.consume()
                            handleWaveAt(change.position.y)
                        },
                        onDragEnd = {
                            waveTouchY = null
                            activeWaveChar = null
                        },
                        onDragCancel = {
                            waveTouchY = null
                            activeWaveChar = null
                        }
                    )
                },
            contentAlignment = Alignment.CenterEnd
        ) {
            Column(
                modifier = Modifier.fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ALPHABET.forEachIndexed { index, letter ->
                    val activeIdx = activeWaveChar?.let { ALPHABET.indexOf(it) }
                    val distance = if (activeIdx != null) abs(index - activeIdx) else 99

                    val offsetDp by animateDpAsState(
                        targetValue = when (distance) {
                            0 -> (-30).dp
                            1 -> (-18).dp
                            2 -> (-8).dp
                            else -> 0.dp
                        },
                        animationSpec = tween(50),
                        label = "wave"
                    )

                    val scale by animateFloatAsState(
                        targetValue = if (distance == 0) 1.5f else 1f,
                        animationSpec = tween(50),
                        label = "scale"
                    )

                    Box(
                        modifier = Modifier
                            .offset(x = offsetDp)
                            .scale(scale)
                            .width(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = letter.toString(),
                            color = if (distance == 0) currentTheme.accent else currentTheme.textSecondary.copy(alpha = 0.5f),
                            fontSize = 11.sp,
                            fontWeight = if (distance == 0) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = activeWaveChar != null,
            enter = fadeIn(tween(50)),
            exit = fadeOut(tween(100)),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 76.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(70.dp)
                    .background(currentTheme.surface, CircleShape)
                    .border(2.dp, currentTheme.accent, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = activeWaveChar?.toString() ?: "",
                    color = currentTheme.accent,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (showThemeDialog) {
            AlertDialog(
                onDismissRequest = { showThemeDialog = false },
                containerColor = currentTheme.surface,
                title = { Text("Elige un Tema", color = currentTheme.textPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AURA_THEMES.forEach { t ->
                            val isSelected = t.id == currentTheme.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) t.accent.copy(alpha = 0.2f) else Color.Transparent)
                                    .border(1.dp, if (isSelected) t.accent else Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                    .clickable {
                                        selectedThemeId = t.id
                                        prefs.edit().putString("theme_id", t.id).apply()
                                        showThemeDialog = false
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.size(20.dp).background(t.accent, CircleShape))
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

        selectedAppForMenu?.let { app ->
            val isFav = app.packageName in favoritePackages
            AlertDialog(
                onDismissRequest = { selectedAppForMenu = null },
                containerColor = currentTheme.surface,
                title = { Text(app.label, color = currentTheme.textPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
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
                            Text(if (isFav) "Quitar de Favoritos" else "Añadir a Favoritos ⭐", color = currentTheme.accent)
                        }
                        TextButton(
                            onClick = {
                                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", app.packageName, null)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                })
                                selectedAppForMenu = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Información de la app", color = currentTheme.textPrimary)
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
fun AppRow(app: AppItem, theme: LauncherTheme, isFavorite: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (app.icon != null) {
            Image(bitmap = app.icon.asImageBitmap(), contentDescription = null, modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)))
        } else {
            Box(modifier = Modifier.size(40.dp).background(theme.accent.copy(alpha = 0.2f), CircleShape))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(text = app.label, color = theme.textPrimary, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (isFavorite) {
            Icon(imageVector = Icons.Default.Star, contentDescription = null, tint = theme.accent, modifier = Modifier.size(14.dp))
        }
    }
}

fn launchApp(context: Context, packageName: String) {
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
    val canvas = Canvas(bmp)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bmp
}
