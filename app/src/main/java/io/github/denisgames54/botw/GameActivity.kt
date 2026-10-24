package io.github.denisgames54.botw

import android.content.pm.ActivityInfo
import android.os.Bundle
import org.libsdl.app.SDLActivity

/**
 * Hosts the native runtime (libmain.so) through SDL3's SDLActivity. SDL creates the Vulkan window and
 * forwards touch / gamepad events; the launcher passes the folders and options as arguments, which
 * native/src/android_main.cpp consumes (--game, --user-root, --orientation, --affinity).
 */
class GameActivity : SDLActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Start in the right orientation from the first frame (SDL applies its own hint later, with the same meaning)
        requestedOrientation = when (intent.getStringExtra("BOTW_ORIENTATION")) {
            "portrait" -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            "auto" -> ActivityInfo.SCREEN_ORIENTATION_FULL_USER
            else -> ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
        }
        super.onCreate(savedInstanceState)
    }

    override fun getLibraries(): Array<String> = arrayOf("SDL3", "main")

    override fun getArguments(): Array<String> = arrayOf(
        "--game", intent.getStringExtra("BOTW_GAME") ?: "",
        "--user-root", intent.getStringExtra("BOTW_USER_ROOT") ?: "",
        "--orientation", intent.getStringExtra("BOTW_ORIENTATION") ?: "landscape",
        "--affinity", intent.getStringExtra("BOTW_AFFINITY") ?: "auto"
    )
}
