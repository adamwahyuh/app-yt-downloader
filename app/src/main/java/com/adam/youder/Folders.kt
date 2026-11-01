package com.adam.youder

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract

// Folder simpan: null = default MediaStore (Download/Youder atau Music/Youder), selain itu URI folder pilihan (SAF)
object Folders {
    private const val FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

    private fun prefs(c: Context) = c.getSharedPreferences("youder", Context.MODE_PRIVATE)
    private fun key(audio: Boolean) = if (audio) "audio_dir" else "video_dir"

    fun default(c: Context, audio: Boolean): Uri? = prefs(c).getString(key(audio), null)?.let(Uri::parse)

    fun setDefault(c: Context, audio: Boolean, uri: Uri?) {
        val old = default(c, audio)
        if (uri != null) c.contentResolver.takePersistableUriPermission(uri, FLAGS)
        prefs(c).edit().putString(key(audio), uri?.toString()).apply()
        // Lepas izin folder lama kalau tidak dipakai lagi (Android membatasi jumlah izin tersimpan)
        if (old != null && old != uri && old != default(c, !audio)) {
            runCatching { c.contentResolver.releasePersistableUriPermission(old, FLAGS) }
        }
    }

    fun label(uri: Uri?, audio: Boolean): String =
        if (uri == null) (if (audio) "Music/Youder" else "Download/Youder")
        else DocumentsContract.getTreeDocumentId(uri).substringAfter(':').ifEmpty { "Memori internal" }

    fun pickIntent() = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(FLAGS or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
}
