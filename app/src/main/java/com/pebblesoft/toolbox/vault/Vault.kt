package com.pebblesoft.toolbox.vault

import android.content.Context
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

/**
 * Where the evidence lives.
 *
 * Everything here obeys two project laws at once. PRIVACY: the bytes are
 * encrypted with a key held in the device's keystore and sit in app-private
 * storage, so nothing reaches the gallery, a file manager, or a backup.
 * THE INSPECTION TEST: the directory name says nothing, the file names are
 * opaque, and an abuser scrolling the phone finds no trace.
 *
 * The seal is taken at close, before anything else may read the file — see
 * [Seal]. A recording whose hash no longer matches its seal is no longer
 * evidence, and the app says so rather than pretending.
 */
class Vault(private val context: Context) {

    private val dir: File by lazy {
        File(context.filesDir, "store").apply { if (!exists()) mkdirs() }
    }

    /** Decrypted copies for playback and sharing live here, and are wiped on demand. */
    private val scratch: File by lazy {
        File(context.cacheDir, "open").apply { if (!exists()) mkdirs() }
    }

    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private fun encrypted(name: String): EncryptedFile =
        EncryptedFile.Builder(
            context,
            File(dir, name),
            masterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB,
        ).build()

    fun exists(name: String): Boolean = File(dir, name).exists()

    fun sizeOf(name: String): Long = File(dir, name).length()

    /**
     * Open a sealed slot for writing.
     *
     * The caller writes and closes the stream; [seal] is then taken on what
     * actually landed on disk, which is the only thing a court would ever see.
     */
    fun openWrite(name: String): OutputStream {
        File(dir, name).takeIf { it.exists() }?.delete()
        return encrypted(name).openFileOutput()
    }

    fun openRead(name: String): InputStream = encrypted(name).openFileInput()

    fun writeText(name: String, text: String) {
        openWrite(name).use { it.write(text.toByteArray()) }
    }

    fun readText(name: String): String = openRead(name).use { it.readBytes().decodeToString() }

    /** The seal over the stored bytes, taken right after the file is closed. */
    fun seal(name: String): Seal = Seal.over(File(dir, name))

    /** True when the file on disk still matches the seal it was given. */
    fun verify(name: String, seal: Seal): Boolean = Seal.over(File(dir, name)).sha256 == seal.sha256

    /**
     * Put a readable copy in the scratch area — the only way to hand the bytes
     * to a media player or a share sheet, and always a COPY, never the vault
     * file itself. Call [purgeScratch] the moment it is no longer on screen.
     */
    fun decryptToScratch(name: String, extension: String): File {
        val out = File(scratch, "${name.substringBeforeLast('.')}.$extension")
        openRead(name).use { input -> out.outputStream().use { input.copyTo(it) } }
        return out
    }

    fun purgeScratch() {
        scratch.listFiles()?.forEach { it.delete() }
    }

    fun delete(name: String) {
        File(dir, name).delete()
    }
}

/**
 * A file's fingerprint and the moment it was taken.
 *
 * This is what turns a recording into evidence: the hash proves the bytes were
 * never altered after [takenAt]. It is computed over the stored (encrypted)
 * bytes, because those are the bytes that exist on the device.
 */
data class Seal(val sha256: String, val takenAt: Long) {

    companion object {
        fun over(file: File): Seal {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return Seal(digest.digest().joinToString("") { "%02x".format(it) }, file.lastModified())
        }
    }
}
