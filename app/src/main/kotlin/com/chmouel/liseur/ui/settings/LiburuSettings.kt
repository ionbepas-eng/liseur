package com.chmouel.liseur.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Row
import kotlinx.coroutines.launch
import com.chmouel.liseur.container
import com.chmouel.liseur.R
import com.chmouel.liseur.ui.library.styles.LiburuPrefs
import com.chmouel.liseur.ui.theme.LiburuAppearance

/** Liburuville's own settings, at the top of the screen as on the web: appearance, goals, name. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LiburuSettings(onOpenReadAloud: () -> Unit = {}) {
    val context = LocalContext.current
    LiburuAppearance.init(context)
    val look by LiburuAppearance.look.collectAsState()
    LiburuDriveSettings()

    SettingsGroup(stringResource(R.string.liburu_appearance)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.liburu_background), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Swatch(
                    color = null,
                    label = stringResource(R.string.liburu_bg_auto),
                    selected = look.background == null,
                    onClick = { LiburuAppearance.setBackground(null) },
                )
                LiburuAppearance.backgrounds.forEach { (key, color) ->
                    Swatch(color, swatchName(key), look.background == key) { LiburuAppearance.setBackground(key) }
                }
            }
            Text(
                stringResource(R.string.liburu_wallpaper),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 8.dp),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                WallpaperSwatch(null, stringResource(R.string.liburu_wall_none), look.wallpaper == null) {
                    LiburuAppearance.setWallpaper(null)
                }
                LiburuAppearance.wallpapers.forEach { wall ->
                    WallpaperSwatch(wall.colors, swatchName(wall.key), look.wallpaper == wall.key) {
                        LiburuAppearance.setWallpaper(wall.key)
                    }
                }
                // «Tu imagen»: a photo from the gallery, on this device only.
                val pick = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()) { uri ->
                    if (uri != null && !LiburuAppearance.setImage(context, uri)) android.widget.Toast.makeText(context, "No he podido abrir esa imagen", android.widget.Toast.LENGTH_SHORT).show()
                }
                val v by LiburuAppearance.imageVersion.collectAsState()
                val file = LiburuAppearance.imageFile(context)
                val shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
                val selected = look.wallpaper == LiburuAppearance.IMAGE
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(68.dp).clickable {
                    pick.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) {
                    Box(
                        Modifier.size(width = 60.dp, height = 84.dp)
                            .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, shape)
                            .padding(if (selected) 4.dp else 1.dp)
                            .clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (file.exists()) coil3.compose.AsyncImage(
                            model = remember(v) { coil3.request.ImageRequest.Builder(context).data(file).memoryCacheKey("wallthumb-$v").build() },
                            contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                        ) else Text("＋", style = MaterialTheme.typography.headlineMedium)
                    }
                    Text("Tu imagen", style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal), maxLines = 1, modifier = Modifier.padding(top = 4.dp))
                }
            }
            if (look.wallpaper == LiburuAppearance.IMAGE) {
                var dim by remember { mutableStateOf(LiburuAppearance.dim()) }
                Text("Atenuar la imagen", style = MaterialTheme.typography.bodyMedium)
                androidx.compose.material3.Slider(dim, { dim = it; LiburuAppearance.setDim(it) }, valueRange = 0f..0.75f)
                Text("«Tu imagen» se guarda solo en este dispositivo. Tócala de nuevo para cambiarla.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                stringResource(R.string.liburu_accent),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 8.dp),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LiburuAppearance.accents.forEach { (key, _) ->
                    // Each swatch as it will look on this background (harmonised, or the wallpaper's own for Automático).
                    Swatch(LiburuAppearance.accentColor(look.copy(accent = key)), swatchName(key), look.accent == key) { LiburuAppearance.setAccent(key) }
                }
            }
            // Liburuville: Home's layout on a tablet or a phone held sideways (≥ 600 dp).
            Text("Inicio en pantallas grandes", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            val homeLayout by com.chmouel.liseur.ui.library.styles.LiburuPrefs.homeLayout.collectAsState()
            androidx.compose.runtime.LaunchedEffect(Unit) { com.chmouel.liseur.ui.library.styles.LiburuPrefs.loadHomeLayout(context) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("stack" to "Normal", "columns" to "A · Dos columnas", "band" to "B · Banda", "tiles" to "C · Fichas").forEach { (k, label) ->
                    androidx.compose.material3.FilterChip(
                        selected = homeLayout == k,
                        onClick = { com.chmouel.liseur.ui.library.styles.LiburuPrefs.setHomeLayout(context, k) },
                        label = { Text(label) },
                    )
                }
            }
            Text("Se aplica en la tablet y con el móvil en horizontal; en vertical el móvil no tiene ancho para columnas.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    SettingsGroup(stringResource(R.string.liburu_goals_settings)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberField(stringResource(R.string.liburu_goal_minutes), LiburuPrefs.dailyGoal(context)) {
                LiburuPrefs.setDailyGoal(context, it)
            }
            NumberField(stringResource(R.string.liburu_goal_books), LiburuPrefs.yearGoal(context)) {
                LiburuPrefs.setYearGoal(context, it)
            }
            var name by remember { mutableStateOf(LiburuPrefs.name(context).orEmpty()) }
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    LiburuPrefs.setName(context, it.trim().ifEmpty { null })
                },
                label = { Text(stringResource(R.string.liburu_your_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    LiburuDeviceSettings()
    LiburuDictionarySettings()
    LiburuReadAloudSettings(onOpenReadAloud)
    LiburuHardcoverSettings()
    LiburuNewsSettings()
}

/** «Google Drive», as on the web: who is connected, the last sync and Sincronizar. */
@Composable
private fun LiburuDriveSettings() {
    val context = LocalContext.current
    val c = context.container
    val server by remember { c.database.remoteServerDao().observe() }.collectAsState(null)
    val last by c.driveStateSync.lastSyncAt.collectAsState()
    var busy by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    SettingsGroup("Google Drive") {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (server != null) "Conectado" else "Sin conectar", style = MaterialTheme.typography.titleMedium)
                    Text(server?.accountId ?: "Conecta tu cuenta en «Biblioteca» → Añadir → servidor", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Sincronización", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (last > 0) "Última: " + java.time.Instant.ofEpochMilli(last).atZone(java.time.ZoneId.systemDefault()).toLocalTime().toString().take(5) else "—",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                androidx.compose.material3.OutlinedButton(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        runCatching { c.remoteCatalog.refresh() }
                        c.driveStateSync.syncNow(force = true)
                        busy = false
                    }
                }) { Text(if (busy) "Sincronizando…" else "Sincronizar") }
            }
        }
    }
}

/** «Este dispositivo»: its name in sessions, and taking your data out (CSV, Babelio). */
@Composable
private fun LiburuDeviceSettings() {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var babelio by remember { mutableStateOf<String?>(null) }
    SettingsGroup("Este dispositivo") {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            var dev by remember { mutableStateOf(context.getSharedPreferences("liburu", 0).getString("deviceName", android.os.Build.MODEL).orEmpty()) }
            OutlinedTextField(
                dev, { dev = it; context.getSharedPreferences("liburu", 0).edit().putString("deviceName", it.trim()).apply() },
                label = { Text("Nombre (aparece en tus sesiones)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Exportar mis datos", style = MaterialTheme.typography.titleMedium)
                    Text("Sesiones y libros en CSV", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                androidx.compose.material3.OutlinedButton(onClick = { scope.launch { LiburuExport.csv(context) } }) { Text("Exportar") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Babelio", style = MaterialTheme.typography.titleMedium)
                    Text(babelio ?: "Tu biblioteca en el formato de importación de Babelio (ISBN, estado, nota, estanterías)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                androidx.compose.material3.OutlinedButton(onClick = { scope.launch { babelio = LiburuExport.babelio(context) } }) { Text("Exportar") }
            }
        }
    }
}

@Composable
internal fun LiburuHardcoverSettings() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("liburu", android.content.Context.MODE_PRIVATE) }
    var on by remember { mutableStateOf(prefs.getBoolean("hcSync", true)) }
    SettingsGroup("Hardcover") {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Marcar mis lecturas en Hardcover", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Al empezar, terminar o abandonar un libro aquí, se marca también en tu perfil de Hardcover, con sus fechas (a través de tu puente).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                androidx.compose.material3.Switch(checked = on, onCheckedChange = {
                    on = it
                    prefs.edit().putBoolean("hcSync", it).apply()
                })
            }
            prefs.getString("hcLast", null)?.let {
                Text("Último envío: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun LiburuNewsSettings() {
    val context = LocalContext.current
    var feeds by remember { mutableStateOf(com.chmouel.liseur.ui.library.styles.LiburuNews.feeds(context)) }
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    SettingsGroup("Actualidad literaria") {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "Noticias y reseñas que aparecen en Inicio. Añade cualquier feed RSS de una revista, blog o sección de cultura.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            feeds.forEach { feed ->
                androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(feed.first, style = MaterialTheme.typography.titleSmall)
                        Text(feed.second, style = MaterialTheme.typography.bodySmall, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    androidx.compose.material3.OutlinedButton(onClick = {
                        feeds = feeds - feed
                        com.chmouel.liseur.ui.library.styles.LiburuNews.setFeeds(context, feeds)
                    }) { Text("Quitar") }
                }
            }
            OutlinedTextField(name, { name = it }, label = { Text("Nombre (p. ej. El Cultural)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(url, { url = it }, label = { Text("https://…/rss") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            androidx.compose.material3.Button(
                enabled = url.trim().startsWith("http"),
                onClick = {
                    feeds = feeds + ((name.trim().ifEmpty { android.net.Uri.parse(url.trim()).host ?: "Feed" }) to url.trim())
                    com.chmouel.liseur.ui.library.styles.LiburuNews.setFeeds(context, feeds)
                    name = ""; url = ""
                },
            ) { Text("Añadir") }
        }
    }
}

@Composable
private fun NumberField(label: String, initial: Int, onValue: (Int) -> Unit) {
    var text by remember { mutableStateOf(initial.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { value ->
            text = value.filter(Char::isDigit).take(4)
            text.toIntOrNull()?.takeIf { it > 0 }?.let(onValue)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Swatch(color: Color?, label: String, selected: Boolean, onClick: () -> Unit) {
    val ring = MaterialTheme.colorScheme.primary
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(56.dp).clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .size(40.dp)
                .border(if (selected) 3.dp else 1.dp, if (selected) ring else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                .padding(if (selected) 5.dp else 1.dp)
                .background(color ?: MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (color == null) Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(20.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal),
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun WallpaperSwatch(colors: List<Color>?, label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(68.dp).clickable(onClick = onClick)) {
        Box(
            Modifier
                .size(width = 60.dp, height = 84.dp)
                .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, shape)
                .padding(if (selected) 4.dp else 1.dp)
                .background(
                    if (colors != null) androidx.compose.ui.graphics.Brush.verticalGradient(colors)
                    else androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.surfaceContainerHigh),
                    shape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (colors == null) Text("×", style = MaterialTheme.typography.headlineMedium)
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal),
            maxLines = 1,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun swatchName(key: String): String = stringResource(
    when (key) {
        "crema" -> R.string.liburu_c_crema
        "papel" -> R.string.liburu_c_papel
        "blanco" -> R.string.liburu_c_blanco
        "arena" -> R.string.liburu_c_arena
        "salvia" -> R.string.liburu_c_salvia
        "rosa" -> R.string.liburu_c_rosa
        "niebla" -> R.string.liburu_c_niebla
        "grafito" -> R.string.liburu_c_grafito
        "noche" -> R.string.liburu_c_noche
        "terracota" -> R.string.liburu_c_terracota
        "azul" -> R.string.liburu_c_azul
        "verde" -> R.string.liburu_c_verde
        "morado" -> R.string.liburu_c_morado
        "atardecer" -> R.string.liburu_w_atardecer
        "lavanda" -> R.string.liburu_w_lavanda
        "oceano" -> R.string.liburu_w_oceano
        "pradera" -> R.string.liburu_w_pradera
        "bosque" -> R.string.liburu_w_bosque
        "medianoche" -> R.string.liburu_w_medianoche
        "auto" -> R.string.liburu_c_auto
        else -> R.string.liburu_c_tinta
    },
)


/** «Diccionario», as on the web: where the words you save go. */
@Composable
private fun LiburuDictionarySettings() {
    val context = LocalContext.current
    val v by com.chmouel.liseur.data.gdrive.LiburuShared.version.collectAsState()
    val n = remember(v) { com.chmouel.liseur.data.gdrive.LiburuShared.vocab(context).size }
    SettingsGroup("Diccionario") {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Tu vocabulario", style = MaterialTheme.typography.titleMedium)
            Text("$n ${if (n == 1) "palabra guardada" else "palabras guardadas"} para repasar. Guárdalas con «Guardar» en la definición mientras lees, y repásalas en Biblioteca › Notas › Vocabulario.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Se comparten con la web: lo que guardas en un sitio aparece en el otro.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Inglés → español sin conexión", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            Text("Incluido en la app: unas 81.000 palabras inglesas con su traducción, del Wikcionario (CC BY-SA, vía doozan/spanish_data). Aparece en la definición al marcar una palabra en un libro en inglés. Las definiciones de cada idioma se descargan en Ajustes › Lectura › Diccionarios sin conexión.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}


/** «Lectura en voz alta», as on the web: which voice reads, and where to change it. */
@Composable
private fun LiburuReadAloudSettings(onOpen: () -> Unit) {
    val context = LocalContext.current
    SettingsGroup("Lectura en voz alta") {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Pulsa ▶ dentro de un libro para escucharlo; sigue sonando con la pantalla bloqueada y desde la notificación.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Voces y velocidad", style = MaterialTheme.typography.titleMedium)
                    Text("Las del teléfono (Samsung o Google) o voces en la nube con tu clave", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                androidx.compose.material3.OutlinedButton(onClick = onOpen) { Text("Configurar") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Voces del sistema", style = MaterialTheme.typography.titleMedium)
                    Text("Descarga voces en español o inglés de alta calidad", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                androidx.compose.material3.OutlinedButton(onClick = {
                    runCatching { context.startActivity(android.content.Intent("com.android.settings.TTS_SETTINGS").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }) { Text("Abrir") }
            }
        }
    }
}
