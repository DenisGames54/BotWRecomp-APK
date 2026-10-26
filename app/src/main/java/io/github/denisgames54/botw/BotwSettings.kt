package io.github.denisgames54.botw

import java.io.File
import java.util.Locale

/**
 * Mirrors EXACTLY the keys of runtime/src/settings.cpp (settings.ini).
 * If the runtime gains or loses a key, update it here and in the UI cards.
 */
data class BotwSettings(
    val fps: Int = 60,            // 30 or 60
    val scale: Float = 1f,        // 0 = auto (fit the display); 0.5 .. 3
    val display: Int = 0,         // 0 fit, 1 stretch, 2 fill
    val filter: Int = 1,          // 0 bilinear, 1 bicubic, 2 hermite, 3 nearest
    val gyro: Boolean = true,
    val rumble: Boolean = true,
    val layout: Int = 0,          // 0 A on the right (Nintendo), 1 A at the bottom
    val showFps: Boolean = false,
    val volume: Int = 100,        // 0..100
    val touch: Int = 1,           // 0 auto, 1 on, 2 off
    val touchHidden: Boolean = false,
    val camSens: Float = 1f,      // right stick (camera) multiplier: 0.3 .. 3, 1 = game default
    val touchLayout: String = "", // on-screen control offsets, set by the in-game layout editor
) {
    fun toIni(): String {
        fun b(v: Boolean) = if (v) 1 else 0
        val sc = if (scale == scale.toInt().toFloat()) scale.toInt().toString()
        else String.format(Locale.US, "%s", scale.toString())
        return buildString {
            append("# BotWRecomp settings (also editable from the in-game menu: touchpad, L3+R3 or F1)\n")
            append("fps=$fps\nscale=$sc\ndisplay=$display\nfilter=$filter\n")
            append("gyro=${b(gyro)}\nrumble=${b(rumble)}\nlayout=$layout\n")
            append("show_fps=${b(showFps)}\nvolume=$volume\ntouch=$touch\ntouch_hidden=${b(touchHidden)}\n")
            append("cam_sens=$camSens\ntouch_layout=$touchLayout\n")
        }
    }

    companion object {
        /** Reads settings.ini (the game writes it when you change something in its menu). Missing file = defaults. */
        fun load(file: File): BotwSettings {
            var s = BotwSettings()
            if (!file.isFile) return s
            file.forEachLine { raw ->
                val line = raw.trim()
                if (line.startsWith("#")) return@forEachLine
                val i = line.indexOf('=')
                if (i < 0) return@forEachLine
                val k = line.substring(0, i)
                val v = line.substring(i + 1)
                val n = v.toIntOrNull()
                s = when (k) {
                    "fps" -> s.copy(fps = if ((n ?: 30) >= 60) 60 else 30)
                    "scale" -> s.copy(scale = v.toFloatOrNull() ?: s.scale)
                    "display" -> s.copy(display = (n ?: 0).coerceIn(0, 2))
                    "filter" -> s.copy(filter = (n ?: 1).coerceIn(0, 3))
                    "gyro" -> s.copy(gyro = n != 0)
                    "rumble" -> s.copy(rumble = n != 0)
                    "layout" -> s.copy(layout = if ((n ?: 0) != 0) 1 else 0)
                    "show_fps" -> s.copy(showFps = n != 0)
                    "volume" -> s.copy(volume = (n ?: 100).coerceIn(0, 100))
                    "touch" -> s.copy(touch = (n ?: 1).coerceIn(0, 2))
                    "touch_hidden" -> s.copy(touchHidden = n != 0)
                    "cam_sens" -> s.copy(camSens = (v.toFloatOrNull() ?: 1f).coerceIn(0.3f, 3f))
                    "touch_layout" -> s.copy(touchLayout = v)
                    else -> s
                }
            }
            return s
        }
    }
}
