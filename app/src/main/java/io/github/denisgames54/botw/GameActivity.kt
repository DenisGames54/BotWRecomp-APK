package io.github.denisgames54.botw

import org.libsdl.app.SDLActivity

/**
 * Hosts the native runtime (libmain.so) through SDL3's SDLActivity. SDL creates the Vulkan window and
 * forwards touch / gamepad events; the launcher passes the game folder and the user folder as arguments,
 * which native/src/android_main.cpp turns into --game / BOTW_USER_ROOT.
 */
class GameActivity : SDLActivity() {
    override fun getLibraries(): Array<String> = arrayOf("SDL3", "main")

    override fun getArguments(): Array<String> = arrayOf(
        "--game", intent.getStringExtra("BOTW_GAME") ?: "",
        "--user-root", intent.getStringExtra("BOTW_USER_ROOT") ?: ""
    )
}
