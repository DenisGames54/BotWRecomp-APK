package io.github.denisgames54.botw

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val Bg = Brush.linearGradient(listOf(Color(0xFF0B1A12), Color(0xFF12301F), Color(0xFF0A1410)))
private val CardBg = Color(0x99101A14)
private val Accent = Color(0xFF7FE08A)
private val AccentDark = Color(0xFF1F6B3A)
private val Muted = Color(0xFFB7C8BC)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hideSystemBars()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Accent)) { Launcher() }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}

@Composable
private fun Launcher() {
    val ctx = LocalContext.current
    var game by remember { mutableStateOf(Storage.savedGame(ctx)) }
    var settings by remember { mutableStateOf(BotwSettings()) }
    var hasAccess by remember { mutableStateOf(Storage.hasAllFilesAccess()) }
    var rpxStatus by remember { mutableStateOf<String?>(null) }
    var orient by remember { mutableStateOf(Storage.orientation(ctx)) }
    var affinity by remember { mutableStateOf(Storage.affinity(ctx)) }
    var crashLog by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    var showLegal by remember { mutableStateOf(!Storage.legalAccepted(ctx)) }
    val iniFile = remember { File(Storage.userRoot, "settings.ini") }

    // Refresh when coming back from the permission screen
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasAccess = Storage.hasAllFilesAccess()
                // The game writes settings.ini when you change something in its menu: reload it.
                if (hasAccess) settings = BotwSettings.load(iniFile)
                // Back from the game: save diagnostics and tell the user if it died abnormally.
                if (hasAccess && Storage.wasLaunched(ctx)) {
                    Storage.markLaunched(ctx, false)
                    scope.launch {
                        val (file, crashed) = withContext(Dispatchers.IO) { Diagnostics.save(ctx) to Diagnostics.lastGameCrashed(ctx) }
                        if (crashed) crashLog = file.path
                    }
                }
            }
        }
        lifecycle.addObserver(obs); onDispose { lifecycle.removeObserver(obs) }
    }

    LaunchedEffect(game, hasAccess) {
        val g = game
        rpxStatus = null
        if (g != null && hasAccess && Storage.looksValid(g)) {
            rpxStatus = "verifying..."
            rpxStatus = withContext(Dispatchers.IO) {
                if (Storage.sha256(Storage.rpx(g)) == Storage.RPX_SHA256) "ok" else "other version"
            }
        }
    }

    fun update(s: BotwSettings) {
        settings = s
        if (hasAccess) runCatching {
            iniFile.parentFile?.mkdirs(); iniFile.writeText(s.toIni())
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val p = uri?.let { Storage.treeUriToPath(it) }
        if (p != null) { game = p; Storage.saveGame(ctx, p) }
    }

    val valid = game != null && hasAccess && Storage.looksValid(game!!)

    Box(Modifier.fillMaxSize().background(Bg).safeDrawingPadding().padding(16.dp)) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // ---- left: identity + folder + play
            Column(
                Modifier.weight(0.38f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("THE LEGEND OF ZELDA", color = Muted, fontSize = 12.sp, letterSpacing = 3.sp)
                Text("BREATH OF THE WILD", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black)
                Text("Recompiled · Android", color = Accent, fontSize = 13.sp, letterSpacing = 2.sp)
                Spacer(Modifier.height(18.dp))
                PillButton("CHOOSE FOLDER", Modifier.fillMaxWidth()) {
                    if (!hasAccess) {
                        ctx.startActivity(
                            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${ctx.packageName}"))
                        )
                    } else picker.launch(null)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    when {
                        !hasAccess -> "Tap to grant \"All files access\" (the game is read straight from the folder, nothing is copied)."
                        game == null -> "Choose the folder that contains code/, content/ and meta/ from your own dump."
                        !Storage.looksValid(game!!) -> "Invalid folder: code/U-King.rpx, content/ or meta/ is missing.\n$game"
                        else -> game!!
                    },
                    color = if (hasAccess && game != null && !valid) Color(0xFFFFB86B) else Muted,
                    fontSize = 11.sp
                )
                when (rpxStatus) {
                    "ok" -> Text("✔ U-King.rpx v208 (USA 1.6.0) verified", color = Accent, fontSize = 11.sp)
                    "other version" -> Text("⚠ U-King.rpx is not v208: only USA v208 is supported", color = Color(0xFFFFB86B), fontSize = 11.sp)
                    "verifying..." -> Text("verifying U-King.rpx...", color = Muted, fontSize = 11.sp)
                }
                Spacer(Modifier.height(14.dp))
                PillButton("PLAY", Modifier.fillMaxWidth(), enabled = valid && rpxStatus == "ok") {
                    update(settings)
                    Storage.markLaunched(ctx, true)
                    ctx.startActivity(Intent(ctx, GameActivity::class.java).apply {
                        putExtra("BOTW_GAME", game)
                        putExtra("BOTW_USER_ROOT", Storage.userRoot.path)
                        putExtra("BOTW_ORIENTATION", orient)
                        putExtra("BOTW_AFFINITY", affinity)
                    })
                }
                Spacer(Modifier.height(10.dp))
                Text("About / legal notice", color = Muted, fontSize = 11.sp,
                    modifier = Modifier.clickable { showLegal = true })
                Spacer(Modifier.height(6.dp))
                Text("Save diagnostics", color = Muted, fontSize = 11.sp,
                    modifier = Modifier.clickable {
                        scope.launch {
                            val f = withContext(Dispatchers.IO) { Diagnostics.save(ctx) }
                            crashLog = f.path
                        }
                    })
            }

            // ---- right: options (1:1 with the runtime settings.ini)
            Column(Modifier.weight(0.62f).fillMaxHeight().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingsCards(settings, ::update)
                SCard("Orientation") {
                    Segmented(listOf("Landscape" to "landscape", "Portrait" to "portrait", "Auto" to "auto"), orient,
                        { orient = it; Storage.setOrientation(ctx, it) })
                }
                SCard("CPU affinity") {
                    Segmented(listOf("Auto (big cores, busiest thread on prime core)" to "auto", "Off" to "off"), affinity,
                        { affinity = it; Storage.setAffinity(ctx, it) })
                }
                Text("Tap an option: it is saved to settings.ini and applies on the next start. " +
                        "Resolution only changes after restarting the game. In game, the menu button opens the same options.",
                    color = Muted, fontSize = 11.sp)
            }
        }
    }

    crashLog?.let { path ->
        AlertDialog(
            onDismissRequest = { crashLog = null },
            confirmButton = { TextButton(onClick = { crashLog = null }) { Text("OK") } },
            title = { Text("Diagnostics saved") },
            text = { Text("The game process ended. A diagnostic report (exit reason, errors, runtime.log) was saved to:\n\n$path\n\nSend that file when reporting a problem.") }
        )
    }
    if (showLegal) LegalDialog { Storage.acceptLegal(ctx); showLegal = false }
}

@Composable
private fun SettingsCards(s: BotwSettings, set: (BotwSettings) -> Unit) {
    SCard("Render resolution") {
        Segmented(
            listOf("Auto" to 0f, "360p" to 0.5f, "540p" to 0.75f, "720p" to 1f,
                "1080p" to 1.5f, "1440p" to 2f, "1800p" to 2.5f, "2160p" to 3f),
            s.scale, { set(s.copy(scale = it)) }, near = { a, b -> kotlin.math.abs(a - b) < 0.01f })
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SCard("Frame rate", Modifier.weight(1f)) {
            Segmented(listOf("30 (original)" to 30, "60" to 60), s.fps, { set(s.copy(fps = it)) })
        }
        SCard("Display", Modifier.weight(1f)) {
            Segmented(listOf("16:9" to 0, "Stretch" to 1, "Fill" to 2), s.display, { set(s.copy(display = it)) })
        }
    }
    SCard("Upscale filter") {
        Segmented(listOf("Bilinear" to 0, "Bicubic" to 1, "Hermite" to 2, "Nearest" to 3), s.filter, { set(s.copy(filter = it)) })
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SCard("Touch controls", Modifier.weight(1f)) {
            Segmented(listOf("Auto" to 0, "On" to 1, "Off" to 2), s.touch, { set(s.copy(touch = it)) })
        }
        SCard("Buttons", Modifier.weight(1f)) {
            Segmented(listOf("A on the right" to 0, "A at the bottom" to 1), s.layout, { set(s.copy(layout = it)) })
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ToggleCard("FPS counter", "shows FPS in game", s.showFps, { set(s.copy(showFps = it)) }, Modifier.weight(1f))
        ToggleCard("Gyro aiming", "controller gyro aims", s.gyro, { set(s.copy(gyro = it)) }, Modifier.weight(1f))
        ToggleCard("Rumble", "controller vibration", s.rumble, { set(s.copy(rumble = it)) }, Modifier.weight(1f))
    }
    SCard("Volume: ${s.volume}%") {
        Slider(value = s.volume.toFloat(), onValueChange = { set(s.copy(volume = it.toInt())) }, valueRange = 0f..100f)
    }
}

@Composable
private fun SCard(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(CardBg)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp)); content()
    }
}

@Composable
private fun ToggleCard(title: String, sub: String, on: Boolean, change: (Boolean) -> Unit, modifier: Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(18.dp)).background(CardBg).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(sub, color = Muted, fontSize = 10.sp)
        }
        Switch(checked = on, onCheckedChange = change)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> Segmented(
    options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit,
    near: (T, T) -> Boolean = { a, b -> a == b }
) {
    FlowRow(
        Modifier.clip(RoundedCornerShape(14.dp)).background(Color(0x66000000)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { (label, value) ->
            val on = near(value, selected)
            Box(
                Modifier.clip(RoundedCornerShape(11.dp))
                    .background(if (on) Brush.horizontalGradient(listOf(Accent, Color(0xFF3FB36A))) else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent)))
                    .clickable { onSelect(value) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) { Text(label, color = if (on) Color(0xFF06210F) else Color.White, fontSize = 13.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal) }
        }
    }
}

@Composable
private fun PillButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(50))
            .background(Brush.horizontalGradient(if (enabled) listOf(Color(0xFF3FB36A), AccentDark) else listOf(Color(0xFF38403B), Color(0xFF2A302C))))
            .clickable(enabled = enabled, onClick = onClick).padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, color = if (enabled) Color.White else Color(0xFF8A948E), fontWeight = FontWeight.Black, letterSpacing = 3.sp) }
}

@Composable
private fun LegalDialog(onAccept: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        confirmButton = { TextButton(onClick = onAccept) { Text("Got it") } },
        title = { Text("Legal notice") },
        text = {
            Text(
                "This project does NOT provide the game or any protected files (ROMs, keys, dumps or game code). " +
                        "It is only a tool. You need your own legally obtained Wii U dump (USA, update 1.6.0 / v208). " +
                        "The APK is generated on your own machine from your own dump and must not be distributed.\n\n" +
                        "Unofficial project. Recompiler and runtime by Ishaan Kalra; the Vulkan renderer and H.264 decoder come from Cemu."
            )
        }
    )
}
