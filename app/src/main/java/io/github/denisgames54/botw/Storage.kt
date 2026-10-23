package io.github.denisgames54.botw

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import java.io.File
import java.security.MessageDigest

object Storage {
    /** SHA-256 do U-King.rpx USA v208 (update 1.6.0), conforme o README do projeto. */
    const val RPX_SHA256 = "ba58da5b95ce929e005d058ceb08b9b2788d1ab2bbc8a6c189bbadca0bb34d30"

    /** Saves/cache/log/settings ficam fora do app: sobrevivem a desinstalar e dá para copiar do Debian. */
    val userRoot: File get() = File(Environment.getExternalStorageDirectory(), "BotWRecomp/user")

    fun hasAllFilesAccess() = Environment.isExternalStorageManager()

    /** content://...tree/primary:Download/game  ->  /storage/emulated/0/Download/game */
    fun treeUriToPath(uri: Uri): String? {
        val id = DocumentsContract.getTreeDocumentId(uri) // "primary:Download/game" ou "ABCD-1234:Games/botw"
        val vol = id.substringBefore(':')
        val rel = id.substringAfter(':', "")
        val base = if (vol == "primary") Environment.getExternalStorageDirectory().path else "/storage/$vol"
        return if (rel.isEmpty()) base else "$base/$rel"
    }

    fun rpx(gameDir: String) = File(gameDir, "code/U-King.rpx")

    fun looksValid(gameDir: String): Boolean =
        rpx(gameDir).isFile && File(gameDir, "content").isDirectory && File(gameDir, "meta").isDirectory

    fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { ins ->
            val buf = ByteArray(1 shl 20)
            while (true) { val n = ins.read(buf); if (n < 0) break; md.update(buf, 0, n) }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private const val PREFS = "botw"
    fun savedGame(c: Context): String? = c.getSharedPreferences(PREFS, 0).getString("game", null)
    fun saveGame(c: Context, path: String) = c.getSharedPreferences(PREFS, 0).edit().putString("game", path).apply()
    fun legalAccepted(c: Context) = c.getSharedPreferences(PREFS, 0).getBoolean("legal", false)
    fun acceptLegal(c: Context) = c.getSharedPreferences(PREFS, 0).edit().putBoolean("legal", true).apply()
}
