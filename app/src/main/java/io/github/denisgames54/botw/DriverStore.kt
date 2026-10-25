package io.github.denisgames54.botw

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

data class GpuDriver(
    val id: String,
    val name: String,
    val vendor: String,
    val version: String,
    val library: String,
    val dir: File,
)

/**
 * Custom Vulkan drivers (e.g. Mesa Turnip for Adreno) imported from .zip packages.
 *
 * Package format (the one used by the Turnip builds shared for emulators): a zip with `meta.json`
 * (`name`, `vendor`, `driverVersion`, `libraryName`) and the driver library (`libraryName`, e.g. vulkan.ad07XX.so).
 * A zip without meta.json is accepted if it holds exactly one .so.
 *
 * Drivers are stored in the app's PRIVATE storage: Android refuses to dlopen libraries from shared storage.
 */
object DriverStore {
    private const val MAX_BYTES = 400L * 1024 * 1024

    fun root(ctx: Context) = File(ctx.filesDir, "gpu_drivers")

    fun list(ctx: Context): List<GpuDriver> =
        (root(ctx).listFiles() ?: emptyArray())
            .filter { it.isDirectory && !it.name.startsWith(".") }
            .mapNotNull { read(it) }
            .sortedBy { it.name.lowercase() }

    fun delete(ctx: Context, id: String) {
        val d = File(root(ctx), id)
        if (d.parentFile == root(ctx)) d.deleteRecursively()
    }

    private fun read(dir: File): GpuDriver? {
        var name = dir.name
        var vendor = "unknown"
        var version = ""
        var library = ""
        val meta = File(dir, "meta.json")
        if (meta.isFile) {
            try {
                val j = JSONObject(meta.readText())
                name = j.optString("name", name).ifBlank { name }
                vendor = j.optString("vendor", vendor)
                version = j.optString("driverVersion", "")
                library = j.optString("libraryName", "")
            } catch (_: Exception) {
            }
        }
        if (library.isEmpty()) {
            val sos = dir.listFiles { f -> f.isFile && f.name.endsWith(".so") } ?: emptyArray()
            if (sos.size == 1) library = sos[0].name
        }
        if (library.isEmpty() || library != File(library).name) return null
        if (!File(dir, library).isFile) return null
        return GpuDriver(dir.name, name, vendor, version, library, dir)
    }

    /** arm64 ELF shared object? (magic, 64-bit, little endian, e_machine = EM_AARCH64) */
    private fun checkElf(f: File) {
        val h = ByteArray(20)
        val n = f.inputStream().use { it.read(h) }
        val ok = n == 20 && h[0] == 0x7F.toByte() && h[1] == 'E'.code.toByte() && h[2] == 'L'.code.toByte() && h[3] == 'F'.code.toByte() &&
                h[4].toInt() == 2 && h[5].toInt() == 1 && h[18] == 0xB7.toByte() && h[19].toInt() == 0
        if (!ok) throw IllegalArgumentException("The driver library is not an arm64 ELF (.so) file")
    }

    private fun slug(s: String) = s.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(40).ifEmpty { "driver" }

    /** Imports a driver .zip chosen with the document picker. Throws IllegalArgumentException with a readable message. */
    fun import(ctx: Context, uri: Uri): GpuDriver {
        val base = root(ctx)
        base.mkdirs()
        val tmp = File(base, ".import-${System.nanoTime()}")
        tmp.mkdirs()
        try {
            var total = 0L
            val md = MessageDigest.getInstance("SHA-256")
            val input = ctx.contentResolver.openInputStream(uri) ?: throw IllegalArgumentException("Cannot open the selected file")
            input.use { raw ->
                ZipInputStream(raw.buffered()).use { zip ->
                    while (true) {
                        val e = zip.nextEntry ?: break
                        if (e.isDirectory) continue
                        val fileName = File(e.name).name  // flatten: any path inside the zip is ignored (zip-slip safe)
                        if (fileName.isEmpty() || fileName.startsWith(".")) continue
                        if (fileName != "meta.json" && !fileName.endsWith(".so")) continue  // keep only what the loader needs
                        FileOutputStream(File(tmp, fileName)).use { out ->
                            val buf = ByteArray(1 shl 16)
                            while (true) {
                                val n = zip.read(buf)
                                if (n < 0) break
                                total += n
                                if (total > MAX_BYTES) throw IllegalArgumentException("The archive is too large")
                                out.write(buf, 0, n)
                                md.update(buf, 0, n)
                            }
                        }
                    }
                }
            }
            val found = read(tmp) ?: throw IllegalArgumentException("Not a driver package: expected meta.json with libraryName, or a single .so")
            checkElf(File(tmp, found.library))
            val id = slug(found.name) + "-" + md.digest().take(4).joinToString("") { "%02x".format(it) }
            val dest = File(base, id)
            dest.deleteRecursively()
            if (!tmp.renameTo(dest)) throw IllegalArgumentException("Could not store the driver")
            return read(dest) ?: throw IllegalArgumentException("Could not read the stored driver")
        } finally {
            tmp.deleteRecursively()  // no-op after a successful rename
        }
    }

    /** ro.hardware.vulkan of this device ("adreno", "mali", ...); empty if unknown. */
    fun deviceVulkanHardware(): String = try {
        val p = Runtime.getRuntime().exec(arrayOf("getprop", "ro.hardware.vulkan"))
        p.inputStream.bufferedReader().readText().trim()
    } catch (_: Exception) {
        ""
    }
}
