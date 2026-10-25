package io.github.denisgames54.botw

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Everything needed to understand why the game process (":game") died, written to <user root>/diagnostics.txt:
 * device / memory / thermal state, how Android says the process exited (+ native crash trace), the game folder and
 * user folder contents, launcher settings, this app's logcat (filtered, crash buffer and raw tail) and runtime.log.
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

    private fun runLines(vararg cmd: String): List<String> = runCatching {
        Runtime.getRuntime().exec(arrayOf(*cmd)).inputStream.bufferedReader().readLines()
    }.getOrElse { listOf("failed: $it") }

    private fun logcatFiltered(): String = runLines("logcat", "-d", "-v", "threadtime", "-t", "8000").filter { line ->
        val f = line.trim().split(Regex("\\s+"), 6)
        if (f.size < 6) return@filter false
        val level = f[4]
        val tag = f[5].substringBefore(':').trim()
        level == "F" || level == "E" || level == "W" && (tag.startsWith("libc") || tag.startsWith("linker")) ||
                tag.startsWith("BotW") || tag.startsWith("SDL") || tag.startsWith("libc") ||
                tag.startsWith("DEBUG") || tag.startsWith("AndroidRuntime") || tag.startsWith("linker") ||
                tag.startsWith("hook_impl") || tag.startsWith("vulkan") || tag.startsWith("adreno") || tag.startsWith("RADV") ||
                tag.startsWith("Mesa") || tag.startsWith("PanVK") || tag.startsWith("XPROF")
    }.takeLast(2000).joinToString("\n")

    private fun tail(f: File, lines: Int): String =
        if (f.isFile) f.readLines().takeLast(lines).joinToString("\n") else "(missing: ${f.path})"

    private fun head(f: File, lines: Int): String =
        if (f.isFile) f.readLines().take(lines).joinToString("\n") else "(missing: ${f.path})"

    /** Printable strings (>= 6 chars) of the native crash trace (a tombstone protobuf); also saved raw next to the report. */
    private fun tombstoneStrings(info: ApplicationExitInfo): String = runCatching {
        val bytes = info.traceInputStream?.use { it.readBytes() } ?: return@runCatching "(no trace attached)"
        File(Storage.userRoot, "last-tombstone.pb").writeBytes(bytes)
        val out = ArrayList<String>()
        val cur = StringBuilder()
        for (b in bytes) {
            val c = b.toInt() and 0xFF
            if (c in 0x20..0x7E) cur.append(c.toChar()) else { if (cur.length >= 6) out.add(cur.toString()); cur.setLength(0) }
        }
        if (cur.length >= 6) out.add(cur.toString())
        "(${bytes.size} bytes, raw copy: last-tombstone.pb)\n" + out.distinct().take(400).joinToString("\n")
    }.getOrElse { "(could not read the trace: $it)" }

    private fun sizeOf(f: File) = if (f.isFile) "${f.length()} B" else "missing"

    private fun deviceSection(ctx: Context, sb: StringBuilder) {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        sb.append("BotWRecomp-APK diagnostics  ${fmt.format(Date())}\n")
        val pi = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0) }.getOrNull()
        sb.append("App: ${ctx.packageName} ${pi?.versionName} (code ${pi?.longVersionCode})  build ${Build.TYPE}\n")
        sb.append("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE}, board ${Build.BOARD}, hardware ${Build.HARDWARE})\n")
        sb.append("Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}), patch ${Build.VERSION.SECURITY_PATCH}, ABI ${Build.SUPPORTED_ABIS.joinToString()}\n")
        sb.append("Fingerprint: ${Build.FINGERPRINT}\n")
        if (Build.VERSION.SDK_INT >= 31) sb.append("SoC: ${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}\n")
        sb.append("Device GPU: ${DriverStore.deviceGpu()}\n")
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        sb.append("RAM: total ${mi.totalMem shr 20} MB, available ${mi.availMem shr 20} MB, low-memory threshold ${mi.threshold shr 20} MB, lowMemory=${mi.lowMemory}\n")
        runCatching {
            val st = StatFs(Environment.getExternalStorageDirectory().path)
            sb.append("Storage: free ${st.availableBytes shr 20} MB of ${st.totalBytes shr 20} MB\n")
        }
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        val thermal = listOf("NONE", "LIGHT", "MODERATE", "SEVERE", "CRITICAL", "EMERGENCY", "SHUTDOWN")
        sb.append("Thermal status: ${thermal.getOrElse(pm.currentThermalStatus) { "?" }}  power-save=${pm.isPowerSaveMode}\n")
        ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let { b ->
            sb.append("Battery: ${b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)}%  ${b.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10.0} C  plugged=${b.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)}\n")
        }
        val dm = ctx.resources.displayMetrics
        sb.append("Display: ${dm.widthPixels}x${dm.heightPixels} px, ${dm.densityDpi} dpi\n")
        val cpus = (0 until Runtime.getRuntime().availableProcessors()).joinToString(" ") { c ->
            fun r(p: String) = runCatching { File("/sys/devices/system/cpu/cpu$c/$p").readText().trim() }.getOrDefault("?")
            "cpu$c(cap ${r("cpu_capacity")}, max ${r("cpufreq/cpuinfo_max_freq")})"
        }
        sb.append("CPUs: $cpus\n")
        runCatching { File("/proc/cpuinfo").readLines().firstOrNull { it.startsWith("Features") } }.getOrNull()?.let { sb.append("$it\n") }
        sb.append("\n")
    }

    private fun gameSection(ctx: Context, sb: StringBuilder) {
        val game = Storage.savedGame(ctx)
        sb.append("=== Game folder ===\n")
        sb.append("path: $game   all-files-access: ${Storage.hasAllFilesAccess()}\n")
        if (game != null) {
            val g = File(game)
            sb.append("exists: ${g.isDirectory}  entries: ${g.list()?.sorted()?.joinToString()}\n")
            sb.append("code/U-King.rpx: ${sizeOf(Storage.rpx(game))}\n")
            sb.append("content/: ${File(g, "content").list()?.size ?: "missing"} entries   meta/: ${File(g, "meta").list()?.size ?: "missing"} entries\n")
        }
        sb.append("\n=== Launcher settings ===\n")
        sb.append("language ${Lang.tag}, orientation ${Storage.orientation(ctx)}, CPU affinity ${Storage.affinity(ctx)}\n")
        val drv = DriverStore.list(ctx).firstOrNull { it.id == Storage.driverId(ctx) }
        sb.append("GPU driver: ${if (drv == null) "system" else "${drv.name} [${drv.family.label}] lib ${drv.library} minApi ${drv.minApi} vendor ${drv.vendor} ${drv.version}"}\n")
        if (drv != null) sb.append("Driver environment: ${Storage.driverEnv(ctx, drv.id).replace("\n", " ; ")}\n")
        sb.append("Imported drivers: ${DriverStore.list(ctx).joinToString { "${it.id} (${it.family.label})" }.ifEmpty { "none" }}\n")
        sb.append("\n=== User folder (${Storage.userRoot.path}) ===\n")
        val files = Storage.userRoot.listFiles()?.sortedBy { it.name }
        if (files == null) sb.append("(missing)\n") else files.forEach {
            sb.append("${if (it.isDirectory) "dir " else "file"} ${it.name}  ${if (it.isDirectory) "${it.list()?.size ?: 0} entries" else "${it.length()} B"}\n")
        }
        sb.append("\n=== Native libraries (${ctx.applicationInfo.nativeLibraryDir}) ===\n")
        File(ctx.applicationInfo.nativeLibraryDir).listFiles()?.sortedBy { it.name }?.forEach { sb.append("${it.name}  ${it.length()} B\n") }
        sb.append("\n")
    }

    fun save(ctx: Context): File {
        val sb = StringBuilder()
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        deviceSection(ctx, sb)
        gameSection(ctx, sb)
        sb.append("=== Process exit reasons (newest first) ===\n")
        val all = exits(ctx)
        all.forEach {
            sb.append("${fmt.format(Date(it.timestamp))}  ${it.processName}  ${reasonName(it.reason)}  status=${it.status}  pss=${it.pss} KB  rss=${it.rss} KB  importance=${it.importance}  ${it.description ?: ""}\n")
        }
        all.firstOrNull { it.processName.endsWith(":game") && it.reason == ApplicationExitInfo.REASON_CRASH_NATIVE }?.let {
            sb.append("\n=== Native crash trace of the last game crash (strings) ===\n").append(tombstoneStrings(it)).append("\n")
        }
        sb.append("\n=== logcat: errors and BotW / SDL / libc / DEBUG / hook / driver tags ===\n").append(logcatFiltered())
        sb.append("\n\n=== logcat: crash buffer ===\n").append(runLines("logcat", "-b", "crash", "-d", "-v", "threadtime", "-t", "300").joinToString("\n"))
        sb.append("\n\n=== logcat: raw tail (last 300 lines of this app) ===\n").append(runLines("logcat", "-d", "-v", "threadtime", "-t", "300").joinToString("\n"))
        val rl = File(Storage.userRoot, "runtime.log")
        sb.append("\n\n=== runtime.log: start (first 80 lines) ===\n").append(head(rl, 80))
        sb.append("\n\n=== runtime.log: end (last 400 lines) ===\n").append(tail(rl, 400))
        sb.append("\n\n=== settings.ini ===\n").append(tail(File(Storage.userRoot, "settings.ini"), 40)).append('\n')
        val out = File(Storage.userRoot, "diagnostics.txt")
        out.parentFile?.mkdirs()
        out.writeText(sb.toString())
        return out
    }
}
