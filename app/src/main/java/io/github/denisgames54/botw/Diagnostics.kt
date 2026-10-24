package io.github.denisgames54.botw

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Collects what is needed to understand why the game process (":game") died:
 * how Android says it exited, this app's own logcat (errors + the "BotW" tag), and the tail of runtime.log.
 * Written to <user root>/diagnostics.txt.
 */
object Diagnostics {
    private fun reasonName(r: Int) = when (r) {
        ApplicationExitInfo.REASON_EXIT_SELF -> "EXIT_SELF"
        ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
        ApplicationExitInfo.REASON_CRASH -> "CRASH (Java)"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE"
        ApplicationExitInfo.REASON_ANR -> "ANR"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "INITIALIZATION_FAILURE"
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "PERMISSION_CHANGE"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE_USAGE"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED"
        ApplicationExitInfo.REASON_USER_STOPPED -> "USER_STOPPED"
        else -> "OTHER($r)"
    }

    private fun exits(ctx: Context): List<ApplicationExitInfo> {
        if (Build.VERSION.SDK_INT < 30) return emptyList()
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return runCatching { am.getHistoricalProcessExitReasons(ctx.packageName, 0, 8) }.getOrDefault(emptyList())
    }

    /** True when the most recent exit of the game process looks like a crash / kill (not a normal quit). */
    fun lastGameCrashed(ctx: Context): Boolean {
        val e = exits(ctx).firstOrNull { it.processName.endsWith(":game") } ?: return false
        return e.reason != ApplicationExitInfo.REASON_EXIT_SELF &&
                e.reason != ApplicationExitInfo.REASON_USER_REQUESTED &&
                e.reason != ApplicationExitInfo.REASON_USER_STOPPED
    }

    private fun logcat(): String = runCatching {
        val p = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "threadtime", "-t", "6000"))
        p.inputStream.bufferedReader().readLines().filter { line ->
            val f = line.trim().split(Regex("\\s+"), 6)
            if (f.size < 6) return@filter false
            val level = f[4]
            val tag = f[5].substringBefore(':').trim()
            level == "F" || level == "E" ||
                    tag.startsWith("BotW") || tag.startsWith("SDL") || tag.startsWith("libc") ||
                    tag.startsWith("DEBUG") || tag.startsWith("AndroidRuntime") || tag.startsWith("linker")
        }.takeLast(1500).joinToString("\n")
    }.getOrElse { "logcat failed: $it" }

    private fun tail(f: File, lines: Int): String =
        if (f.isFile) f.readLines().takeLast(lines).joinToString("\n") else "(missing: ${f.path})"

    fun save(ctx: Context): File {
        val sb = StringBuilder()
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        sb.append("BotWRecomp-APK diagnostics  ${fmt.format(Date())}\n")
        sb.append("Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}), ABI ${Build.SUPPORTED_ABIS.joinToString()}\n")
        sb.append("App: ${ctx.packageName}  game folder: ${Storage.savedGame(ctx)}\n\n")
        sb.append("=== Process exit reasons (newest first) ===\n")
        exits(ctx).forEach {
            sb.append("${fmt.format(Date(it.timestamp))}  ${it.processName}  ${reasonName(it.reason)}  status=${it.status}  ${it.description ?: ""}\n")
        }
        sb.append("\n=== logcat (errors + BotW/SDL/libc/DEBUG tags) ===\n").append(logcat())
        sb.append("\n\n=== runtime.log (last 200 lines) ===\n").append(tail(File(Storage.userRoot, "runtime.log"), 200))
        sb.append("\n\n=== settings.ini ===\n").append(tail(File(Storage.userRoot, "settings.ini"), 40)).append('\n')
        val out = File(Storage.userRoot, "diagnostics.txt")
        out.parentFile?.mkdirs()
        out.writeText(sb.toString())
        return out
    }
}
