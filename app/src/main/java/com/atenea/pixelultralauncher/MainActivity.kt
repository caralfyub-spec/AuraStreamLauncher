package com.atenea.pixelultralauncher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

data class AppInfo(
    val label: String,
    val packageName: String,
    val icon: ImageBitmap,
    val firstLetter: Char
)

enum class UltraTheme(
    val title: String,
    val primary: Color,
    val accent: Color,
    val cardBg: Color,
    val borderGlow: Color
) {
    SPATIAL_GLASS("Spatial Glass 3D", Color(0xFFE2F1FF), Color(0xFF64B5F6), Color(0x33FFFFFF), Color(0x9980D8FF)),
    CYBER_MATRIX("Cyber Matrix LED", Color(0xFFFF1744), Color(0xFFFFFFFF), Color(0xCC0D0D0D), Color(0xFFFF1744)),
    BIOLUM_OLED("Bioluminiscente OLED", Color(0xFF00E676), Color(0xFF1DE9B6), Color(0x99000000), Color(0xFF00E676)),
    CERAMIC("Relieve Cerámico", Color(0xFFFFF8E1), Color(0xFFFFB300), Color(0x443E2723), Color(0x88FFE082)),
    CHAMELEON("Camaleón Aurora", Color(0xFFE040FB), Color(0xFF18FFFF), Color(0x331A0033), Color(0xAAEA80FC))
}

enum class ClockStyle(val title: String) {
    PIXEL_GIANT("Pixel Lockscreen"),
    NOTHING_DOT("Matriz LED"),
    ANALOG_MINIMAL("Analógico Ultra"),
    TEXT_WORD("Texto Minimal")
}

enum class WavePosition(val title: String) {
    RIGHT("Derecha"), LEFT("Izquierda"), BOTH("Ambos Lados")
}

class MainActivity : ComponentActivity(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var tiltX by mutableFloatStateOf(0f)
    private var tiltY by mutableFloatStateOf(0f)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        setContent {
            PixelUltraLauncherApp(tiltX = tiltX, tiltY = tiltY)
        }
    }

    override fun onResume() {
        super.onResume()
        sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onPause() {
        super.onPause()
        // Apagado inteligente a 0% en segundo plano para ahorrar bateria
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_GAME_ROTATION_VECTOR) {
            tiltX = (event.values[1] * 25f).coerceIn(-20f, 20f)
            tiltY = (event.values[0] * 25f).coerceIn(-20f, 20f)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}

@Composable
fun PixelUltraLauncherApp(tiltX: Float, tiltY: Float) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("ultra_prefs", Context.MODE_PRIVATE) }

    // Cache de aplicaciones en RAM (0 ms lag)
    var allApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var favoriteApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }

    // Estados de personalizacion libre
    var currentTheme by remember { mutableStateOf(UltraTheme.SPATIAL_GLASS) }
    var clockStyle by remember { mutableStateOf(ClockStyle.PIXEL_GIANT) }
    var waveSide by remember { mutableStateOf(WavePosition.RIGHT) }
    var appAlignment by remember { mutableIntStateOf(prefs.getInt("app_align", 0)) } // 0: Izq, 1: Centro, 2: Der
    var wallpaperPreset by remember { mutableIntStateOf(prefs.getInt("wall_preset", 0)) }
    var iconShaderMode by remember { mutableIntStateOf(0) } // 0: Cristal, 1: Monocromo, 2: Solo Texto

    // Coordenadas libres de Reloj y Lista de Apps (Arriba, Abajo, Izquierda, Derecha)
    var editMode by remember { mutableStateOf(false) }
    var clockOffsetX by remember { mutableFloatStateOf(prefs.getFloat("clock_x", 40f)) }
    var clockOffsetY by remember { mutableFloatStateOf(prefs.getFloat("clock_y", 110f)) }
    var appsOffsetX by remember { mutableFloatStateOf(prefs.getFloat("apps_x", 30f)) }
    var appsOffsetY by remember { mutableFloatStateOf(prefs.getFloat("apps_y", 420f)) }

    // Estados de la Ola Alfabetica, Buscador y Tutorial en Video Simulado
    var activeLetter by remember { mutableStateOf<Char?>(null) }
    var waveTouchY by remember { mutableFloatStateOf(0f) }
    var expandedSwipeApp by remember { mutableStateOf<String?>(null) }
    var showSettingsPanel by remember { mutableStateOf(false) }
    var showInteractiveTutorial by remember { mutableStateOf(false) }
    var showSearchSheet by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // Carga asincrona en hilo secundario Dispatchers.IO
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = pm.queryIntentActivities(intent, 0)
            val loaded = resolveInfos.mapNotNull { ri ->
                val pkg = ri.activityInfo.packageName
                if (pkg == context.packageName) return@mapNotNull null
                val label = ri.loadLabel(pm).toString()
                val drawable = ri.loadIcon(pm)
                val bmp = drawableToBitmap(drawable, 120)
                val first = label.firstOrNull()?.uppercaseChar() ?: '#'
                AppInfo(label, pkg, bmp.asImageBitmap(), if (first in 'A'..'Z') first else '#')
            }.sortedBy { it.label.lowercase() }

            allApps = loaded
            favoriteApps = loaded.take(7)
        }
    }

    // Reloj y Bateria en vivo
    var currentTime by remember { mutableStateOf(Calendar.getInstance()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = Calendar.getInstance()
            delay(1000L)
        }
    }

    val batteryPct = remember(currentTime) {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dragAmount ->
                    if (dragAmount > 35f && !editMode && activeLetter == null) {
                        showSearchSheet = true
                    }
                }
            }
    ) {
        // 1. FONDO DE PANTALLA 4K PROCEDURAL + PARALAJE 3D
        UltraProceduralWallpaper(
            theme = currentTheme,
            preset = wallpaperPreset,
            tiltX = tiltX,
            tiltY = tiltY
        )

        // 2. MODULO DE RELOJ (100% Movible Arriba/Abajo/Izquierda/Derecha)
        Box(
            modifier = Modifier
                .offset { IntOffset(clockOffsetX.roundToInt(), clockOffsetY.roundToInt()) }
                .pointerInput(editMode) {
                    if (editMode) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            clockOffsetX += dragAmount.x
                            clockOffsetY += dragAmount.y
                            prefs.edit()
                                .putFloat("clock_x", clockOffsetX)
                                .putFloat("clock_y", clockOffsetY)
                                .apply()
                        }
                    }
                }
                .then(
                    if (editMode) Modifier.border(2.dp, Color.Cyan, RoundedCornerShape(18.dp))
                    else Modifier
                )
                .padding(12.dp)
        ) {
            UltraClockModule(
                style = clockStyle,
                theme = currentTheme,
                calendar = currentTime,
                battery = batteryPct,
                tiltX = tiltX,
                tiltY = tiltY
            )
        }

        // 3. LISTA EN CASCADA ESTILO NIAGARA MEJORADA (100% Movible + Carpetas Swipe)
        val horizontalAlign = when (appAlignment) {
            1 -> Alignment.CenterHorizontally
            2 -> Alignment.End
            else -> Alignment.Start
        }

        Column(
            horizontalAlignment = horizontalAlign,
            modifier = Modifier
                .offset { IntOffset(appsOffsetX.roundToInt(), appsOffsetY.roundToInt()) }
                .widthIn(min = 220.dp, max = 320.dp)
                .pointerInput(editMode) {
                    if (editMode) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            appsOffsetX += dragAmount.x
                            appsOffsetY += dragAmount.y
                            prefs.edit()
                                .putFloat("apps_x", appsOffsetX)
                                .putFloat("apps_y", appsOffsetY)
                                .apply()
                        }
                    }
                }
                .then(
                    if (editMode) Modifier.border(2.dp, Color.Yellow, RoundedCornerShape(18.dp))
                    else Modifier
                )
                .padding(8.dp)
        ) {
            if (editMode) {
                Text(
                    "Arrástrame a cualquier lugar",
                    color = Color.Yellow,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }

            favoriteApps.forEachIndexed { index, app ->
                UltraFavoriteAppRow(
                    app = app,
                    theme = currentTheme,
                    iconShaderMode = iconShaderMode,
                    tiltX = tiltX,
                    isExpandedFolder = expandedSwipeApp == app.packageName,
                    relatedApps = allApps.drop(index * 2).take(3),
                    onLaunch = { launchPackage(context, it) },
                    onSwipeFolder = {
                        vibrateTick(context)
                        expandedSwipeApp = if (expandedSwipeApp == app.packageName) null else app.packageName
                    }
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
        }

        // 4. BARRA ALFABETICA CON EFECTO OLA MAGNETICA (Derecha, Izquierda o Ambos)
        if (waveSide == WavePosition.RIGHT || waveSide == WavePosition.BOTH) {
            AlphabetWaveBar(
                modifier = Modifier.align(Alignment.CenterEnd),
                isLeftSide = false,
                theme = currentTheme,
                activeLetter = activeLetter,
                touchY = waveTouchY,
                onLetterChange = { letter, yPos ->
                    if (activeLetter != letter && letter != null) vibrateTick(context)
                    activeLetter = letter
                    waveTouchY = yPos
                }
            )
        }

        if (waveSide == WavePosition.LEFT || waveSide == WavePosition.BOTH) {
            AlphabetWaveBar(
                modifier = Modifier.align(Alignment.CenterStart),
                isLeftSide = true,
                theme = currentTheme,
                activeLetter = activeLetter,
                touchY = waveTouchY,
                onLetterChange = { letter, yPos ->
                    if (activeLetter != letter && letter != null) vibrateTick(context)
                    activeLetter = letter
                    waveTouchY = yPos
                }
            )
        }

        // 5. POPUP DE LA OLA ALFABETICA EN TIEMPO REAL
        if (activeLetter != null) {
            val filtered = allApps.filter { it.firstLetter == activeLetter }
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xEE10141D)),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(280.dp)
                    .heightIn(max = 380.dp)
                    .border(2.dp, currentTheme.borderGlow, RoundedCornerShape(24.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = activeLetter.toString(),
                        fontSize = 42.sp,
                        fontWeight = FontWeight.Black,
                        color = currentTheme.primary
                    )
                    LazyColumn {
                        items(filtered) { item ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        activeLetter = null
                                        launchPackage(context, item.packageName)
                                    }
                                    .padding(vertical = 8.dp)
                            ) {
                                Image(
                                    bitmap = item.icon,
                                    contentDescription = item.label,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(item.label, color = Color.White, fontSize = 16.sp)
                            }
                        }
                    }
                }
            }
        }

        // 6. BARRA FLOTANTE DE CONTROL: MODO EDICION, TEMAS, FONDOS Y BOTON "?" TUTORIAL
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 36.dp)
                .clip(RoundedCornerShape(50))
                .background(Color(0xAA000000))
                .border(1.dp, currentTheme.borderGlow, RoundedCornerShape(50))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Boton Mover Libremente
            Text(
                text = if (editMode) "✓ Fijar" else "✥ Mover Todo",
                color = if (editMode) Color.Yellow else Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable {
                    vibrateTick(context)
                    editMode = !editMode
                }
            )
            Text("|", color = Color.Gray)
            // Boton Estudio de Temas y Relojes
            Text(
                text = "🎨 Estudio Ultra",
                color = currentTheme.primary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable {
                    vibrateTick(context)
                    showSettingsPanel = !showSettingsPanel
                }
            )
            Text("|", color = Color.Gray)
            // Boton Cambiar Fondo 4K al instante
            Text(
                text = "🖼️ Fondo",
                color = Color.White,
                fontSize = 13.sp,
                modifier = Modifier.clickable {
                    vibrateTick(context)
                    wallpaperPreset = (wallpaperPreset + 1) % 4
                    prefs.edit().putInt("wall_preset", wallpaperPreset).apply()
                }
            )
            Text("|", color = Color.Gray)
            // BOTON "?" VIDEO / GUIA ANIMADA INTERACTIVA
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(currentTheme.primary)
                    .clickable {
                        vibrateTick(context)
                        showInteractiveTutorial = true
                    }
            ) {
                Text("?", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 16.sp)
            }
        }

        // 7. PANEL DEL LABORATORIO DE TEMAS, RELOJES Y POSICIONES
        AnimatedVisibility(
            visible = showSettingsPanel,
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xF5121218)),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, currentTheme.borderGlow, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Laboratorio Pixel Ultra", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("Cerrar ✕", color = currentTheme.primary, modifier = Modifier.clickable { showSettingsPanel = false })
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    Text("1. Temas Categoría Superior (Con Giroscopio 3D):", color = Color.LightGray, fontSize = 13.sp)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        UltraTheme.entries.forEach { t ->
                            Button(
                                onClick = { currentTheme = t },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (currentTheme == t) t.primary else Color.DarkGray
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(t.title.take(12), color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Text("2. Estilo del Reloj:", color = Color.LightGray, fontSize = 13.sp)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        ClockStyle.entries.forEach { cs ->
                            OutlinedButton(
                                onClick = { clockStyle = cs },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(cs.title, color = if (clockStyle == cs) currentTheme.primary else Color.White, fontSize = 11.sp)
                            }
                        }
                    }

                    Text("3. Posición de Apps y Barra Alfabética:", color = Color.LightGray, fontSize = 13.sp)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        OutlinedButton(onClick = {
                            appAlignment = (appAlignment + 1) % 3
                            prefs.edit().putInt("app_align", appAlignment).apply()
                        }) {
                            val alignName = when (appAlignment) { 0 -> "Izquierda"; 1 -> "Centro"; else -> "Derecha" }
                            Text("Apps: $alignName", color = Color.White, fontSize = 11.sp)
                        }
                        OutlinedButton(onClick = {
                            waveSide = WavePosition.entries[(waveSide.ordinal + 1) % WavePosition.entries.size]
                        }) {
                            Text("Ola: ${waveSide.title}", color = Color.White, fontSize = 11.sp)
                        }
                        OutlinedButton(onClick = {
                            iconShaderMode = (iconShaderMode + 1) % 3
                        }) {
                            val modeTxt = when (iconShaderMode) { 0 -> "Cristal"; 1 -> "Neón Mono"; else -> "Solo Texto" }
                            Text("Iconos: $modeTxt", color = Color.White, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // 8. BUSCADOR RAPIDO DESLIZANDO HACIA ABAJO
        if (showSearchSheet) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xEE080B11))
                    .padding(24.dp)
            ) {
                Column {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth().padding(top = 28.dp)
                    ) {
                        Text("Buscador Instantáneo", color = currentTheme.primary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("Cerrar ✕", color = Color.White, modifier = Modifier.clickable { showSearchSheet = false })
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Escribe el nombre de una app...", color = Color.Gray) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    val searchResults = allApps.filter { it.label.contains(searchQuery, ignoreCase = true) }
                    LazyColumn {
                        items(searchResults) { item ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showSearchSheet = false
                                        launchPackage(context, item.packageName)
                                    }
                                    .padding(vertical = 10.dp)
                            ) {
                                Image(bitmap = item.icon, contentDescription = null, modifier = Modifier.size(40.dp))
                                Spacer(modifier = Modifier.width(14.dp))
                                Text(item.label, color = Color.White, fontSize = 17.sp)
                            }
                        }
                    }
                }
            }
        }

        // 9. SIMULADOR / VIDEO EXPLICATIVO ANIMADO (Al pulsar el boton "?")
        if (showInteractiveTutorial) {
            InteractiveVideoTutorialOverlay(
                theme = currentTheme,
                onClose = { showInteractiveTutorial = false }
            )
        }
    }
}

@Composable
fun UltraProceduralWallpaper(theme: UltraTheme, preset: Int, tiltX: Float, tiltY: Float) {
    val infiniteTransition = rememberInfiniteTransition(label = "wallpaper")
    val animPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28f,
        animationSpec = infiniteRepeatable(tween(14000, easing = LinearEasing)),
        label = "phase"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        // Fondo base oscuro OLED / Espacial
        val bgTop = when (preset) {
            0 -> Color(0xFF05070F)
            1 -> Color(0xFF000000) // Negro Puro OLED
            2 -> Color(0xFF0D1B2A)
            else -> Color(0xFF1A0B2E)
        }
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(bgTop, Color(0xFF020205))
            )
        )

        // Esferas de luz 4K con paralaje 3D controlado por el giroscopio del Pixel
        val center1 = Offset(
            x = size.width * 0.3f + tiltX * 12f + cos(animPhase) * 60f,
            y = size.height * 0.25f + tiltY * 12f + sin(animPhase) * 50f
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(theme.primary.copy(alpha = 0.38f), Color.Transparent),
                center = center1,
                radius = size.width * 0.75f
            ),
            center = center1,
            radius = size.width * 0.75f
        )

        val center2 = Offset(
            x = size.width * 0.75f - tiltX * 15f + sin(animPhase) * 70f,
            y = size.height * 0.7f - tiltY * 15f + cos(animPhase) * 60f
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(theme.accent.copy(alpha = 0.28f), Color.Transparent),
                center = center2,
                radius = size.width * 0.65f
            ),
            center = center2,
            radius = size.width * 0.65f
        )
    }
}

@Composable
fun UltraClockModule(
    style: ClockStyle,
    theme: UltraTheme,
    calendar: Calendar,
    battery: Int,
    tiltX: Float,
    tiltY: Float
) {
    val hourStr = String.format("%02d", calendar.get(Calendar.HOUR_OF_DAY))
    val minStr = String.format("%02d", calendar.get(Calendar.MINUTE))
    val dateStr = SimpleDateFormat("EEEE, d MMM", Locale("es", "ES")).format(calendar.time).uppercase()

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(26.dp))
            .background(theme.cardBg)
            .border(
                width = 1.5.dp,
                brush = Brush.linearGradient(
                    colors = listOf(theme.borderGlow, Color.Transparent, theme.accent),
                    start = Offset(0f + tiltX * 5f, 0f + tiltY * 5f),
                    end = Offset(300f - tiltX * 5f, 300f - tiltY * 5f)
                ),
                shape = RoundedCornerShape(26.dp)
            )
            .padding(horizontal = 22.dp, vertical = 14.dp)
    ) {
        // Pixel Pill Integrada (Fecha + Bateria)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(dateStr, color = theme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(10.dp))
            Text("⚡ $battery%", color = Color.White, fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.height(4.dp))

        when (style) {
            ClockStyle.PIXEL_GIANT -> {
                Text(
                    text = "$hourStr:$minStr",
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
            }
            ClockStyle.NOTHING_DOT -> {
                Text(
                    text = "[$hourStr : $minStr]",
                    fontSize = 48.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold,
                    color = theme.primary
                )
            }
            ClockStyle.ANALOG_MINIMAL -> {
                Canvas(modifier = Modifier.size(110.dp).padding(8.dp)) {
                    drawCircle(color = theme.primary, style = Stroke(width = 5f))
                    val c = Offset(size.width / 2, size.height / 2)
                    val minAngle = (calendar.get(Calendar.MINUTE) * 6f - 90f) * (Math.PI / 180f)
                    val hrAngle = ((calendar.get(Calendar.HOUR) % 12) * 30f - 90f) * (Math.PI / 180f)
                    drawLine(
                        color = Color.White,
                        start = c,
                        end = Offset(c.x + cos(hrAngle).toFloat() * 28f, c.y + sin(hrAngle).toFloat() * 28f),
                        strokeWidth = 7f,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = theme.accent,
                        start = c,
                        end = Offset(c.x + cos(minAngle).toFloat() * 42f, c.y + sin(minAngle).toFloat() * 42f),
                        strokeWidth = 4f,
                        cap = StrokeCap.Round
                    )
                }
            }
            ClockStyle.TEXT_WORD -> {
                Text(
                    text = "SON LAS $hourStr\nY $minStr MINUTOS",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = theme.primary
                )
            }
        }
    }
}

@Composable
fun UltraFavoriteAppRow(
    app: AppInfo,
    theme: UltraTheme,
    iconShaderMode: Int,
    tiltX: Float,
    isExpandedFolder: Boolean,
    relatedApps: List<AppInfo>,
    onLaunch: (String) -> Unit,
    onSwipeFolder: () -> Unit
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(theme.cardBg)
                .border(
                    width = 1.dp,
                    brush = Brush.horizontalGradient(
                        listOf(theme.borderGlow.copy(alpha = 0.7f), Color.Transparent)
                    ),
                    shape = RoundedCornerShape(20.dp)
                )
                .clickable { onLaunch(app.packageName) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, dragAmount ->
                        if (dragAmount > 28f) {
                            change.consume()
                            onSwipeFolder()
                        }
                    }
                }
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            if (iconShaderMode != 2) {
                val colorFilter = if (iconShaderMode == 1) {
                    ColorFilter.tint(theme.primary, BlendMode.SrcIn)
                } else null

                Image(
                    bitmap = app.icon,
                    contentDescription = app.label,
                    colorFilter = colorFilter,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                )
                Spacer(modifier = Modifier.width(14.dp))
            }

            Text(
                text = app.label,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        // Mini-burbuja de carpeta al deslizar a la derecha sobre un icono
        AnimatedVisibility(visible = isExpandedFolder) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .padding(top = 6.dp, start = 20.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xDD1A1F2C))
                    .border(1.dp, theme.primary, RoundedCornerShape(16.dp))
                    .padding(10.dp)
            ) {
                relatedApps.forEach { subApp ->
                    Image(
                        bitmap = subApp.icon,
                        contentDescription = subApp.label,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .clickable { onLaunch(subApp.packageName) }
                    )
                }
            }
        }
    }
}

@Composable
fun AlphabetWaveBar(
    modifier: Modifier,
    isLeftSide: Boolean,
    theme: UltraTheme,
    activeLetter: Char?,
    touchY: Float,
    onLetterChange: (Char?, Float) -> Unit
) {
    val alphabet = remember { ('A'..'Z').toList() + '#' }
    var barHeight by remember { mutableFloatStateOf(1f) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly,
        modifier = modifier
            .fillMaxHeight(0.72f)
            .width(44.dp)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        val index = ((offset.y / barHeight) * alphabet.size).toInt().coerceIn(0, alphabet.lastIndex)
                        onLetterChange(alphabet[index], offset.y)
                    },
                    onVerticalDrag = { change, _ ->
                        val y = change.position.y
                        val index = ((y / barHeight) * alphabet.size).toInt().coerceIn(0, alphabet.lastIndex)
                        onLetterChange(alphabet[index], y)
                    },
                    onDragEnd = { onLetterChange(null, 0f) },
                    onDragCancel = { onLetterChange(null, 0f) }
                )
            }
    ) {
        alphabet.forEachIndexed { idx, letter ->
            // Calculo matematico del efecto Ola (Wave Scroller)
            val isSelected = activeLetter == letter
            val activeIdx = alphabet.indexOf(activeLetter)
            val dist = if (activeIdx >= 0) abs(idx - activeIdx) else 99
            val waveShift = when (dist) {
                0 -> 34f
                1 -> 22f
                2 -> 10f
                else -> 0f
            } * if (isLeftSide) 1f else -1f

            Text(
                text = letter.toString(),
                color = if (isSelected) theme.primary else Color.White.copy(alpha = 0.7f),
                fontSize = if (isSelected) 18.sp else 11.sp,
                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium,
                modifier = Modifier.offset(x = waveShift.dp)
            )
        }
    }
}

@Composable
fun InteractiveVideoTutorialOverlay(theme: UltraTheme, onClose: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    val infinite = rememberInfiniteTransition(label = "tutorial_finger")
    val fingerProgress by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing)),
        label = "finger"
    )

    val titles = listOf(
        "1. La Ola Alfabética a 120 Hz",
        "2. Posición 100% Libre (Mover Todo)",
        "3. Carpetas Ocultas Deslizando",
        "4. Giroscopio 3D y Temas Superiores"
    )
    val descriptions = listOf(
        "Desliza tu pulgar por el borde derecho o izquierdo. Las letras se curvan en forma de ola con vibración háptica para abrir cualquier app en 0 milisegundos.",
        "Toca '✥ Mover Todo' abajo. Podrás arrastrar el reloj y la lista de aplicaciones a cualquier rincón (arriba, abajo, izquierda o derecha).",
        "Desliza el dedo hacia la derecha sobre cualquiera de tus aplicaciones favoritas para desplegar su cápsula de apps secundarias.",
        "Inclina tu Google Pixel en la mano: verás cómo el cristal y la luz del fondo 4K se mueven en 3D gracias a la aceleración por GPU."
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xEE060911))
            .padding(24.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize()
        ) {
            Spacer(modifier = Modifier.height(36.dp))
            Text(
                "Guía Visual Animada",
                color = theme.primary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Pantalla de "Video en Vivo" con el dedo virtual animado
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color(0xFF101726))
                    .border(2.dp, theme.borderGlow, RoundedCornerShape(28.dp))
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val fingerPos = when (step) {
                        0 -> Offset(size.width * 0.85f, size.height * (0.2f + fingerProgress * 0.6f))
                        1 -> Offset(size.width * (0.25f + fingerProgress * 0.45f), size.height * (0.25f + fingerProgress * 0.4f))
                        2 -> Offset(size.width * (0.2f + fingerProgress * 0.5f), size.height * 0.5f)
                        else -> Offset(size.width * 0.5f + cos(fingerProgress * 6.28f) * 80f, size.height * 0.5f)
                    }

                    // Dibujar rastro luminoso del gesto
                    drawCircle(
                        color = theme.primary.copy(alpha = 0.35f),
                        radius = 48f,
                        center = fingerPos
                    )
                    // Dedo virtual interactivo
                    drawCircle(
                        color = Color.White,
                        radius = 24f,
                        center = fingerPos
                    )
                }

                Text(
                    text = "DEMO EN VIVO",
                    color = theme.accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
                )
            }

            Spacer(modifier = Modifier.height(22.dp))
            Text(titles[step], color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(10.dp))
            Text(descriptions[step], color = Color.LightGray, fontSize = 15.sp)

            Spacer(modifier = Modifier.weight(1f))

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(onClick = { step = (step + 1) % titles.size }) {
                    Text("Siguiente Demo ➔", color = theme.primary)
                }
                Button(
                    onClick = onClose,
                    colors = ButtonDefaults.buttonColors(containerColor = theme.primary)
                ) {
                    Text("¡Entendido, probar!", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

private fun drawableToBitmap(drawable: Drawable, sizePx: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, sizePx, sizePx)
    drawable.draw(canvas)
    return bitmap
}

private fun launchPackage(context: Context, packageName: String) {
    val intent = context.packageManager.getLaunchIntentForPackage(packageName)
    if (intent != null) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

private fun vibrateTick(context: Context) {
    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
    }
}
