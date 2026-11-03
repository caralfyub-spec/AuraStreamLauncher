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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Date

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
    val textPrimary: Color
)

val AURA_THEMES = listOf(
    LauncherTheme("oled", "OLED Pure Black", Color(0xFF000000), Color(0xFF000000), Color(0xFF00E5FF), Color(0xFF111111), Color(0xFFFFFFFF)),
    LauncherTheme("cyber", "Aura Cyber (Neón)", Color(0xFF090D16), Color(0xFF05070B), Color(0xFF00E5FF), Color(0xFF161F30), Color(0xFFFFFFFF)),
    LauncherTheme("pixel", "Pixel Material", Color(0xFF111827), Color(0xFF0B0F19), Color(0xFFA8C7FA), Color(0xFF1E293B), Color(0xFFF8FAFC)),
    LauncherTheme("emerald", "Bosque Esmeralda", Color(0xFF062019), Color(0xFF03110D), Color(0xFF34D399), Color(0xFF0D3327), Color(0xFFECFDF5))
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

    var selectedThemeId by remember {
        mutableStateOf(prefs.getString("theme_id", "oled") ?: "oled")
    }
    val currentTheme = remember(selectedThemeId) {
        AURA_THEMES.find { it.id == selectedThemeId } ?: AURA_THEMES.first()
    }

    var apps by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var favoritePackages by remember {
        mutableStateOf(prefs.getStringSet("favorites", emptySet()) ?: emptySet())
    }
    var selectedAppForMenu by remember { mutableStateOf<AppItem?>(null) }
    var showThemeDialog by remember { mutableStateOf(false) }

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

    val displayedApps = remember(apps, searchQuery) {
        if (searchQuery.isBlank()) apps else apps.filter { it.label.contains(searchQuery.trim(), ignoreCase = true) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors = listOf(currentTheme.bgTop, currentTheme.bgBottom)))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Cabecera superior: Reloj y Botón de Temas
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        text = currentTime,
                        color = currentTheme.textPrimary,
                        fontSize = 58.sp,
                        fontWeight = FontWeight.Light,
                        letterSpacing = (-2).sp
                    )
                    Text(
                        text = currentDate,
                        color = currentTheme.accent,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                IconButton(
                    onClick = { showThemeDialog = true },
                    modifier = Modifier
                        .size(40.dp)
                        .background(currentTheme.surface, CircleShape)
                        .border(1.dp, currentTheme.accent.copy(alpha = 0.5f), CircleShape)
                ) {
                    Text(text = "🎨", fontSize = 18.sp)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Barra de búsqueda rápida
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(currentTheme.surface, RoundedCornerShape(24.dp))
                    .border(1.dp, currentTheme.accent.copy(alpha = 0.4f), RoundedCornerShape(24.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    singleLine = true,
                    textStyle = TextStyle(color = currentTheme.textPrimary, fontSize = 16.sp),
                    cursorBrush = SolidColor(currentTheme.accent),
                    modifier = Modifier.weight(1f),
                    decorationBox = { innerTextField ->
                        if (searchQuery.isEmpty()) {
                            Text(text = "Buscar aplicación...", color = Color.Gray, fontSize = 16.sp)
                        }
                        innerTextField()
                    }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (searchQuery.isBlank()) {
                    if (favoriteApps.isNotEmpty()) {
                        item {
                            Text(
                                text = "FAVORITOS",
                                color = Color.Gray,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
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
                } else {
                    item {
                        Text(
                            text = "TODAS LAS APLICACIONES",
                            color = currentTheme.accent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
                        )
                    }
                    items(displayedApps, key = { "search_${it.packageName}" }) { app ->
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

        // Diálogo selector de Temas
        if (showThemeDialog) {
            AlertDialog(
                onDismissRequest = { showThemeDialog = false },
                containerColor = currentTheme.surface,
                title = { Text("Personalizar Temas", color = currentTheme.textPrimary, fontWeight = FontWeight.Bold) },
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
                                Box(modifier = Modifier.size(16.dp).background(t.accent, CircleShape))
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

        // Menú flotante al mantener pulsado
        selectedAppForMenu?.let { app ->
            val isFav = app.packageName in favoritePackages
            AlertDialog(
                onDismissRequest = { selectedAppForMenu = null },
                containerColor = currentTheme.surface,
                title = { Text(app.label, color = currentTheme.textPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                            Text(if (isFav) "⭐ Quitar de Favoritos" else "⭐ Añadir a Favoritos", color = currentTheme.accent)
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
                            Text("ℹ️ Información de la app", color = currentTheme.textPrimary)
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
                            Text("🗑️ Desinstalar app", color = Color(0xFFFF5252))
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { selectedAppForMenu = null }) {
                        Text("Cancelar", color = Color.Gray)
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
            .background(theme.surface)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
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
            Text(text = "★", color = theme.accent, fontSize = 16.sp)
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
    val canvas = Canvas(bmp)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bmp
}
