package io.github.denisgames54.botw

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * Launcher language, as a BCP 47 tag. English texts are the keys: every Text() of the launcher goes through tr(),
 * which looks the text up for the current language and falls back to the English original.
 * To add a language: add its tag to Lang.options and a map like PT_BR below.
 */
object Lang {
    val options = listOf("en" to "English", "pt-BR" to "Português (Brasil)", "es" to "Español")

    var tag by mutableStateOf("en")
        private set

    fun init(ctx: Context) {
        val saved = Storage.language(ctx)
        tag = if (saved != null && options.any { it.first == saved }) saved else detect()
    }

    fun set(ctx: Context, newTag: String) {
        tag = newTag
        Storage.setLanguage(ctx, newTag)
    }

    private fun detect(): String {
        val l = Locale.getDefault()
        return when (l.language) {
            "pt" -> "pt-BR"
            "es" -> "es"
            else -> "en"
        }
    }
}

object Msg {
    const val GRANT = "Tap to grant \"All files access\" (the game is read straight from the folder, nothing is copied)."
    const val CHOOSE_DIR = "Choose the folder that contains code/, content/ and meta/ from your own dump."
    const val INVALID = "Invalid folder: code/U-King.rpx, content/ or meta/ is missing."
    const val HINT = "Tap an option: it is saved to settings.ini and applies on the next start. " +
            "Resolution only changes after restarting the game. In game, the menu button opens the same options."
    const val CRASH_BODY = "The game process ended. A diagnostic report (exit reason, errors, runtime.log) was saved to:\n\n%s\n\nSend that file when reporting a problem."
    const val CRASH_DRIVER = "A custom GPU driver is selected: if the game fails at start, try the system driver."
    const val LEGAL = "This project does NOT provide the game or any protected files (ROMs, keys, dumps or game code). " +
            "It is only a tool. You need your own legally obtained Wii U dump (USA, update 1.6.0 / v208). " +
            "The APK is generated on your own machine from your own dump and must not be distributed.\n\n" +
            "Unofficial project. Recompiler and runtime by Ishaan Kalra; the Vulkan renderer and H.264 decoder come from Cemu."
}

fun tr(key: String): String = when (Lang.tag) {
    "pt-BR" -> PT_BR[key]
    "es" -> ES[key]
    else -> null
} ?: key

private val PT_BR = mapOf(
    "Render resolution" to "Resolução de renderização", "Frame rate" to "Taxa de quadros", "Display" to "Tela",
    "Stretch" to "Esticar", "Fill" to "Preencher", "Upscale filter" to "Filtro de upscale",
    "Touch controls" to "Controles de toque", "On" to "Ligado", "Off" to "Desligado", "Buttons" to "Botões",
    "A on the right" to "A à direita", "A at the bottom" to "A embaixo",
    "FPS counter" to "Contador de FPS", "shows FPS in game" to "mostra o FPS no jogo",
    "Gyro aiming" to "Mira com giroscópio", "controller gyro aims" to "o giroscópio do controle mira",
    "Rumble" to "Vibração", "controller vibration" to "vibração do controle", "Volume" to "Volume",
    "Camera sensitivity" to "Sensibilidade da câmera",
    "Right stick: touch drag and physical controller. 1.0x is the game's own feel." to "Analógico direito: arrasto na tela e controle físico. 1.0x é como o jogo já vem.",
    "To move the on-screen controls, open the in-game menu (touch menu button or L3+R3) and choose Edit touch layout." to "Para mover os controles na tela, abra o menu do jogo (botão de menu ou L3+R3) e escolha Edit touch layout.",
    "Orientation" to "Orientação", "Landscape" to "Paisagem", "Portrait" to "Retrato",
    "CPU affinity" to "Afinidade de CPU",
    "Auto (big cores, busiest thread on prime core)" to "Auto (núcleos grandes, thread mais ocupada no núcleo principal)",
    "GPU driver" to "Driver de GPU", "System driver" to "Driver do sistema", "phone default" to "padrão do celular",
    "IMPORT DRIVER (.ZIP)" to "IMPORTAR DRIVER (.ZIP)",
    "Driver environment (KEY=VALUE per line)" to "Variáveis de ambiente do driver (CHAVE=VALOR por linha)",
    "Takes effect on the next start. Drivers are kept in the app's private storage." to "Vale na próxima partida. Os drivers ficam no armazenamento privado do app.",
    "Changing the driver clears the shader cache." to "Trocar o driver apaga o cache de shaders.",
    "Device" to "Aparelho", "CHOOSE FOLDER" to "ESCOLHER PASTA", "PLAY" to "JOGAR",
    "About / legal notice" to "Sobre / aviso legal", "Save diagnostics" to "Salvar diagnóstico",
    "Legal notice" to "Aviso legal", "Got it" to "Entendi", "Diagnostics saved" to "Diagnóstico salvo",
    "Use system driver" to "Usar driver do sistema",
    "verifying U-King.rpx..." to "verificando U-King.rpx...", "verifying..." to "verificando...",
    "✔ U-King.rpx v208 (USA 1.6.0) verified" to "✔ U-King.rpx v208 (USA 1.6.0) verificado",
    "⚠ U-King.rpx is not v208: only USA v208 is supported" to "⚠ U-King.rpx não é a v208: só a v208 USA é suportada",
    "Importing..." to "Importando...", "Imported %s and selected it." to "%s importado e selecionado.",
    "Import failed: %s" to "Falha ao importar: %s",
    "Cannot open the selected file" to "Não foi possível abrir o arquivo selecionado",
    "The archive is too large" to "O arquivo é grande demais",
    "Not a driver package: expected meta.json with libraryName, or a single .so" to "Não é um pacote de driver: esperado meta.json com libraryName, ou um único .so",
    "The driver library is not an arm64 ELF (.so) file" to "A biblioteca do driver não é um arquivo ELF arm64 (.so)",
    "Could not store the driver" to "Não foi possível guardar o driver",
    "Could not read the stored driver" to "Não foi possível ler o driver guardado",
    "This driver needs Android API %d or newer (this device: %d)." to "Este driver exige Android API %d ou mais novo (este aparelho: %d).",
    "This is an Adreno driver, but the device reports a different GPU." to "Este é um driver Adreno, mas o aparelho informa outra GPU.",
    "This is a Samsung Xclipse driver, but the device has an Adreno GPU." to "Este é um driver Samsung Xclipse, mas o aparelho tem uma GPU Adreno.",
    "Xclipse drivers only work on Exynos phones with an Xclipse GPU (920, 530, 940, 540, 550, 950, 960)." to "Drivers Xclipse só funcionam em celulares Exynos com GPU Xclipse (920, 530, 940, 540, 550, 950, 960).",
    "This is a Mali driver, but the device has an Adreno GPU." to "Este é um driver Mali, mas o aparelho tem uma GPU Adreno.",
    "Mali drivers here target Job Manager GPUs (Bifrost G31-G76, Valhall G57-G78); newer CSF GPUs (G610, G710, G715, Immortalis) are not supported." to "Os drivers Mali aqui são para GPUs Job Manager (Bifrost G31-G76, Valhall G57-G78); as GPUs CSF mais novas (G610, G710, G715, Immortalis) não são suportadas.",
    "Unknown driver type: the app cannot tell which GPU it is for." to "Tipo de driver desconhecido: o app não sabe para qual GPU ele serve.",
    Msg.GRANT to "Toque para conceder \"Acesso a todos os arquivos\" (o jogo é lido direto da pasta, nada é copiado).",
    Msg.CHOOSE_DIR to "Escolha a pasta que contém code/, content/ e meta/ do seu próprio dump.",
    Msg.INVALID to "Pasta inválida: faltam code/U-King.rpx, content/ ou meta/.",
    Msg.HINT to "Toque numa opção: ela é salva em settings.ini e vale na próxima partida. A resolução só muda ao reiniciar o jogo. Dentro do jogo, o botão de menu abre as mesmas opções.",
    Msg.CRASH_BODY to "O processo do jogo terminou. Um relatório de diagnóstico (motivo da saída, erros, runtime.log) foi salvo em:\n\n%s\n\nEnvie esse arquivo ao relatar um problema.",
    Msg.CRASH_DRIVER to "Um driver de GPU personalizado está selecionado: se o jogo falhar ao iniciar, tente o driver do sistema.",
    Msg.LEGAL to "Este projeto NÃO fornece o jogo nem nenhum arquivo protegido (ROMs, chaves, dumps ou código do jogo). É apenas uma ferramenta. Você precisa do seu próprio dump do Wii U, obtido legalmente (USA, update 1.6.0 / v208). O APK é gerado na sua própria máquina a partir do seu dump e não pode ser distribuído.\n\nProjeto não oficial. Recompilador e runtime de Ishaan Kalra; o renderizador Vulkan e o decodificador H.264 vêm do Cemu.",
)

private val ES = mapOf(
    "Render resolution" to "Resolución de renderizado", "Frame rate" to "Velocidad de fotogramas", "Display" to "Pantalla",
    "Stretch" to "Estirar", "Fill" to "Rellenar", "Upscale filter" to "Filtro de escalado",
    "Touch controls" to "Controles táctiles", "On" to "Activado", "Off" to "Desactivado", "Buttons" to "Botones",
    "A on the right" to "A a la derecha", "A at the bottom" to "A abajo",
    "FPS counter" to "Contador de FPS", "shows FPS in game" to "muestra los FPS en el juego",
    "Gyro aiming" to "Apuntado con giroscopio", "controller gyro aims" to "el giroscopio del mando apunta",
    "Rumble" to "Vibración", "controller vibration" to "vibración del mando", "Volume" to "Volumen",
    "Camera sensitivity" to "Sensibilidad de la cámara",
    "Right stick: touch drag and physical controller. 1.0x is the game's own feel." to "Stick derecho: arrastre en pantalla y mando físico. 1.0x es como viene el juego.",
    "To move the on-screen controls, open the in-game menu (touch menu button or L3+R3) and choose Edit touch layout." to "Para mover los controles en pantalla, abre el menú del juego (botón de menú o L3+R3) y elige Edit touch layout.",
    "Orientation" to "Orientación", "Landscape" to "Horizontal", "Portrait" to "Vertical",
    "CPU affinity" to "Afinidad de CPU",
    "Auto (big cores, busiest thread on prime core)" to "Auto (núcleos grandes, hilo más ocupado en el núcleo principal)",
    "GPU driver" to "Controlador de GPU", "System driver" to "Controlador del sistema", "phone default" to "predeterminado del teléfono",
    "IMPORT DRIVER (.ZIP)" to "IMPORTAR CONTROLADOR (.ZIP)",
    "Driver environment (KEY=VALUE per line)" to "Variables de entorno del controlador (CLAVE=VALOR por línea)",
    "Takes effect on the next start. Drivers are kept in the app's private storage." to "Se aplica en el próximo inicio. Los controladores se guardan en el almacenamiento privado de la app.",
    "Changing the driver clears the shader cache." to "Cambiar el controlador borra la caché de shaders.",
    "Device" to "Dispositivo", "CHOOSE FOLDER" to "ELEGIR CARPETA", "PLAY" to "JUGAR",
    "About / legal notice" to "Acerca de / aviso legal", "Save diagnostics" to "Guardar diagnóstico",
    "Legal notice" to "Aviso legal", "Got it" to "Entendido", "Diagnostics saved" to "Diagnóstico guardado",
    "Use system driver" to "Usar controlador del sistema",
    "verifying U-King.rpx..." to "verificando U-King.rpx...", "verifying..." to "verificando...",
    "✔ U-King.rpx v208 (USA 1.6.0) verified" to "✔ U-King.rpx v208 (USA 1.6.0) verificado",
    "⚠ U-King.rpx is not v208: only USA v208 is supported" to "⚠ U-King.rpx no es la v208: solo se admite la v208 USA",
    "Importing..." to "Importando...", "Imported %s and selected it." to "%s importado y seleccionado.",
    "Import failed: %s" to "Error al importar: %s",
    "Cannot open the selected file" to "No se pudo abrir el archivo seleccionado",
    "The archive is too large" to "El archivo es demasiado grande",
    "Not a driver package: expected meta.json with libraryName, or a single .so" to "No es un paquete de controlador: se esperaba meta.json con libraryName, o un único .so",
    "The driver library is not an arm64 ELF (.so) file" to "La biblioteca del controlador no es un archivo ELF arm64 (.so)",
    "Could not store the driver" to "No se pudo guardar el controlador",
    "Could not read the stored driver" to "No se pudo leer el controlador guardado",
    "This driver needs Android API %d or newer (this device: %d)." to "Este controlador requiere Android API %d o superior (este dispositivo: %d).",
    "This is an Adreno driver, but the device reports a different GPU." to "Este es un controlador Adreno, pero el dispositivo informa otra GPU.",
    "This is a Samsung Xclipse driver, but the device has an Adreno GPU." to "Este es un controlador Samsung Xclipse, pero el dispositivo tiene una GPU Adreno.",
    "Xclipse drivers only work on Exynos phones with an Xclipse GPU (920, 530, 940, 540, 550, 950, 960)." to "Los controladores Xclipse solo funcionan en teléfonos Exynos con GPU Xclipse (920, 530, 940, 540, 550, 950, 960).",
    "This is a Mali driver, but the device has an Adreno GPU." to "Este es un controlador Mali, pero el dispositivo tiene una GPU Adreno.",
    "Mali drivers here target Job Manager GPUs (Bifrost G31-G76, Valhall G57-G78); newer CSF GPUs (G610, G710, G715, Immortalis) are not supported." to "Los controladores Mali de aquí son para GPU Job Manager (Bifrost G31-G76, Valhall G57-G78); las GPU CSF más nuevas (G610, G710, G715, Immortalis) no son compatibles.",
    "Unknown driver type: the app cannot tell which GPU it is for." to "Tipo de controlador desconocido: la app no sabe para qué GPU es.",
    Msg.GRANT to "Toca para conceder \"Acceso a todos los archivos\" (el juego se lee directamente de la carpeta, no se copia nada).",
    Msg.CHOOSE_DIR to "Elige la carpeta que contiene code/, content/ y meta/ de tu propio volcado.",
    Msg.INVALID to "Carpeta no válida: faltan code/U-King.rpx, content/ o meta/.",
    Msg.HINT to "Toca una opción: se guarda en settings.ini y se aplica en el próximo inicio. La resolución solo cambia al reiniciar el juego. Dentro del juego, el botón de menú abre las mismas opciones.",
    Msg.CRASH_BODY to "El proceso del juego terminó. Se guardó un informe de diagnóstico (motivo de salida, errores, runtime.log) en:\n\n%s\n\nEnvía ese archivo al reportar un problema.",
    Msg.CRASH_DRIVER to "Hay un controlador de GPU personalizado seleccionado: si el juego falla al iniciar, prueba el controlador del sistema.",
    Msg.LEGAL to "Este proyecto NO proporciona el juego ni ningún archivo protegido (ROM, claves, volcados o código del juego). Es solo una herramienta. Necesitas tu propio volcado de Wii U obtenido legalmente (USA, update 1.6.0 / v208). El APK se genera en tu propio equipo a partir de tu volcado y no puede distribuirse.\n\nProyecto no oficial. Recompilador y runtime de Ishaan Kalra; el renderizador Vulkan y el decodificador H.264 provienen de Cemu.",
)
