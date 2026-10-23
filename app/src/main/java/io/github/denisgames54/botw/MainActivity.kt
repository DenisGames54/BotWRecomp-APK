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
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Accent)) { Launcher() }
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
    var showLegal by remember { mutableStateOf(!Storage.legalAccepted(ctx)) }
    val iniFile = remember { File(Storage.userRoot, "settings.ini") }

    // Atualiza ao voltar do ajuste de permissao
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasAccess = Storage.hasAllFilesAccess()
                // O jogo grava o settings.ini quando voce muda algo no menu F1: recarrega.
                if (hasAccess) settings = BotwSettings.load(iniFile)
            }
        }
        lifecycle.addObserver(obs); onDispose { lifecycle.removeObserver(obs) }
    }

    LaunchedEffect(game, hasAccess) {
        val g = game
        rpxStatus = null
        if (g != null && hasAccess && Storage.looksValid(g)) {
            rpxStatus = "verificando..."
            rpxStatus = withContext(Dispatchers.IO) {
                if (Storage.sha256(Storage.rpx(g)) == Storage.RPX_SHA256) "ok" else "outra versao"
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
            // ---- esquerda: identidade + pasta + jogar
            Column(
                Modifier.weight(0.38f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("THE LEGEND OF ZELDA", color = Muted, fontSize = 12.sp, letterSpacing = 3.sp)
                Text("BREATH OF THE WILD", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black)
                Text("Recompiled · Android", color = Accent, fontSize = 13.sp, letterSpacing = 2.sp)
                Spacer(Modifier.height(18.dp))
                PillButton("ESCOLHER PASTA", Modifier.fillMaxWidth()) {
                    if (!hasAccess) {
                        ctx.startActivity(
                            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${ctx.packageName}"))
                        )
                    } else picker.launch(null)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    when {
                        !hasAccess -> "Toque para conceder \"Acesso a todos os arquivos\" (o jogo e lido direto da pasta, sem copia)."
                        game == null -> "Escolha a pasta que contem code/, content/ e meta/ do seu dump."
                        !Storage.looksValid(game!!) -> "Pasta invalida: faltam code/U-King.rpx, content/ ou meta/.\n$game"
                        else -> game!!
                    },
                    color = if (hasAccess && game != null && !valid) Color(0xFFFFB86B) else Muted,
                    fontSize = 11.sp
                )
                when (rpxStatus) {
                    "ok" -> Text("✔ U-King.rpx v208 (USA 1.6.0) verificado", color = Accent, fontSize = 11.sp)
                    "outra versao" -> Text("⚠ U-King.rpx diferente da v208: so a v208 USA e suportada", color = Color(0xFFFFB86B), fontSize = 11.sp)
                    "verificando..." -> Text("verificando U-King.rpx...", color = Muted, fontSize = 11.sp)
                }
                Spacer(Modifier.height(14.dp))
                PillButton("JOGAR", Modifier.fillMaxWidth(), enabled = valid && rpxStatus == "ok") {
                    update(settings)
                    ctx.startActivity(Intent(ctx, GameActivity::class.java).apply {
                        putExtra("BOTW_GAME", game)
                        putExtra("BOTW_USER_ROOT", Storage.userRoot.path)
                    })
                }
                Spacer(Modifier.height(10.dp))
                Text("Sobre / aviso legal", color = Muted, fontSize = 11.sp,
                    modifier = Modifier.clickable { showLegal = true })
            }

            // ---- direita: opcoes (1:1 com o settings.ini do runtime)
            Column(Modifier.weight(0.62f).fillMaxHeight().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingsCards(settings, ::update)
                Text("Toque numa opcao: ela e salva em settings.ini e vale no proximo inicio. " +
                        "A resolucao so muda ao reiniciar o jogo. Dentro do jogo, o botao de menu abre as mesmas opcoes.",
                    color = Muted, fontSize = 11.sp)
            }
        }
    }

    if (showLegal) LegalDialog { Storage.acceptLegal(ctx); showLegal = false }
}

@Composable
private fun SettingsCards(s: BotwSettings, set: (BotwSettings) -> Unit) {
    SCard("Resolucao de renderizacao") {
        Segmented(
            listOf("Auto" to 0f, "360p" to 0.5f, "540p" to 0.75f, "720p" to 1f,
                "1080p" to 1.5f, "1440p" to 2f, "1800p" to 2.5f, "2160p" to 3f),
            s.scale, { set(s.copy(scale = it)) }, near = { a, b -> kotlin.math.abs(a - b) < 0.01f })
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SCard("Taxa de quadros", Modifier.weight(1f)) {
            Segmented(listOf("30 (original)" to 30, "60" to 60), s.fps, { set(s.copy(fps = it)) })
        }
        SCard("Tela", Modifier.weight(1f)) {
            Segmented(listOf("16:9" to 0, "Esticar" to 1, "Preencher" to 2), s.display, { set(s.copy(display = it)) })
        }
    }
    SCard("Filtro de upscale") {
        Segmented(listOf("Bilinear" to 0, "Bicubic" to 1, "Hermite" to 2, "Nearest" to 3), s.filter, { set(s.copy(filter = it)) })
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SCard("Controles touch", Modifier.weight(1f)) {
            Segmented(listOf("Auto" to 0, "Ligado" to 1, "Desligado" to 2), s.touch, { set(s.copy(touch = it)) })
        }
        SCard("Botoes", Modifier.weight(1f)) {
            Segmented(listOf("A a direita" to 0, "A embaixo" to 1), s.layout, { set(s.copy(layout = it)) })
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ToggleCard("Contador de FPS", "mostra o FPS no jogo", s.showFps, { set(s.copy(showFps = it)) }, Modifier.weight(1f))
        ToggleCard("Giroscopio", "mira com o gyro do controle", s.gyro, { set(s.copy(gyro = it)) }, Modifier.weight(1f))
        ToggleCard("Vibracao", "rumble", s.rumble, { set(s.copy(rumble = it)) }, Modifier.weight(1f))
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
        confirmButton = { TextButton(onClick = onAccept) { Text("Entendi") } },
        title = { Text("Aviso legal") },
        text = {
            Text(
                "Este projeto NAO fornece o jogo nem nenhum arquivo protegido (ROMs, chaves, dumps ou codigo do jogo). " +
                        "E apenas uma ferramenta. Voce precisa do seu proprio dump legal do Wii U (USA, update 1.6.0 / v208). " +
                        "O APK e gerado na sua maquina a partir do seu dump e nao pode ser distribuido.\n\n" +
                        "Projeto nao oficial. Recompilador e runtime por Ishaan Kalra; renderer Vulkan e decoder H.264 vem do Cemu."
            )
        }
    )
}
