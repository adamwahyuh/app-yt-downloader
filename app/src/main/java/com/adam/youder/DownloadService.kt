package com.adam.youder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Environment
import android.os.IBinder
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class DownloadService : Service() {

    enum class State { RUNNING, DONE, FAILED }

    data class Status(
        val state: State,
        val title: String,
        val text: String,
        val percent: Int? = null,
        val file: Uri? = null,
        val mime: String? = null,
    )

    // Satu thread = antrian: link yang dikirim saat download berjalan akan menunggu giliran
    private val worker = Executors.newSingleThreadExecutor()
    private val pending = AtomicInteger(0)
    private val notifier by lazy { getSystemService(NotificationManager::class.java) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notifier.createNotificationChannel(NotificationChannel(CHANNEL, "Download", NotificationManager.IMPORTANCE_LOW))
        startForeground(ONGOING_ID, notification(Status(State.RUNNING, "Youder", "Menyiapkan…")),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val url = intent?.getStringExtra(EXTRA_URL) ?: return START_NOT_STICKY
        val audio = intent.getBooleanExtra(EXTRA_AUDIO, false)
        val quality = intent.getStringExtra(EXTRA_QUALITY).orEmpty()
        val folder = intent.getStringExtra(EXTRA_FOLDER)?.let(Uri::parse)
        pending.incrementAndGet()
        worker.execute {
            cancelled = false
            try {
                run(url, audio, quality, folder)
            } catch (e: Exception) {
                val reason = e.message?.lineSequence()?.lastOrNull { it.isNotBlank() } ?: e.javaClass.simpleName
                update(Status(State.FAILED, url, if (cancelled) "Dibatalkan" else "Gagal: $reason"))
            }
            if (pending.decrementAndGet() == 0) stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun run(url: String, audio: Boolean, quality: String, folder: Uri?) {
        var title = url
        update(Status(State.RUNNING, title, "Menyiapkan…"))
        if (!ready) {
            YoutubeDL.getInstance().init(application)
            FFmpeg.getInstance().init(application)
            // yt-dlp bawaan library cepat usang karena YouTube sering berubah; perbarui sekali tiap app dijalankan
            update(Status(State.RUNNING, title, "Memperbarui yt-dlp…"))
            runCatching { YoutubeDL.getInstance().updateYoutubeDL(application, YoutubeDL.UpdateChannel.STABLE) }
            ready = true
        }

        val dir = File(cacheDir, "job").apply { deleteRecursively(); mkdirs() }
        val request = YoutubeDLRequest(url).apply {
            addOption("--no-playlist")
            addOption("--no-mtime")
            addOption("-o", "${dir.absolutePath}/%(title).80B.%(ext)s")
            if (audio) {
                addOption("-f", "ba/b")
                addOption("-x")
                addOption("--audio-format", "mp3")
                addOption("--audio-quality", quality.substringBefore(" ") + "K")
                addOption("--embed-thumbnail")
                addOption("--add-metadata")
            } else {
                addOption("-f", videoFormat(quality))
                addOption("--merge-output-format", "mp4")
            }
        }

        update(Status(State.RUNNING, title, "Mengunduh…", 0))
        var processing = false
        YoutubeDL.getInstance().execute(request, PROCESS_ID) { percent, _, line ->
            if (title == url) DESTINATION.find(line)?.let { title = it.groupValues[1] }
            if (POST_PROCESS.any { line.startsWith(it) }) processing = true
            val p = percent.toInt().coerceIn(0, 100)
            val s = if (processing) Status(State.RUNNING, title, "Memproses…")
                else Status(State.RUNNING, title, "Mengunduh… $p%", p)
            if (s != status) update(s)
        }

        val file = dir.listFiles()?.maxByOrNull { it.length() } ?: error("File hasil tidak ditemukan")
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension) ?: "application/octet-stream"
        update(Status(State.RUNNING, title, "Menyimpan…"))
        val uri = save(file, mime, audio, folder)
        dir.deleteRecursively()
        update(Status(State.DONE, title, "Tersimpan di ${Folders.label(folder, audio)}", file = uri, mime = mime))
    }

    private fun save(file: File, mime: String, audio: Boolean, folder: Uri?): Uri {
        val uri = if (folder != null) {
            val dir = DocumentsContract.buildDocumentUriUsingTree(folder, DocumentsContract.getTreeDocumentId(folder))
            DocumentsContract.createDocument(contentResolver, dir, mime, file.name)
        } else {
            // Default: folder publik lewat MediaStore agar langsung muncul di galeri / pemutar musik
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH,
                    (if (audio) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_DOWNLOADS) + "/Youder")
            }
            contentResolver.insert(
                if (audio) MediaStore.Audio.Media.EXTERNAL_CONTENT_URI else MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        } ?: error("Tidak bisa membuat file di folder tujuan")

        try {
            contentResolver.openOutputStream(uri)!!.use { out -> file.inputStream().use { it.copyTo(out) } }
        } catch (e: Exception) {
            runCatching { if (folder != null) DocumentsContract.deleteDocument(contentResolver, uri) else contentResolver.delete(uri, null, null) }
            throw e
        }
        return uri
    }

    private fun update(s: Status) {
        status = s
        listener?.invoke(s)
        // Notifikasi hasil memakai id berbeda supaya tetap ada setelah service berhenti
        notifier.notify(if (s.state == State.RUNNING) ONGOING_ID else System.currentTimeMillis().toInt(), notification(s))
    }

    private fun notification(s: Status): Notification {
        val running = s.state == State.RUNNING
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(if (running) android.R.drawable.stat_sys_download else android.R.drawable.stat_sys_download_done)
            .setContentTitle(s.title)
            .setContentText(s.text)
            .setContentIntent(open)
            .setOngoing(running)
            .setAutoCancel(!running)
            .apply { if (running) setProgress(100, s.percent ?: 0, s.percent == null) }
            .build()
    }

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_AUDIO = "audio"
        const val EXTRA_QUALITY = "quality"
        const val EXTRA_FOLDER = "folder"
        private const val CHANNEL = "download"
        private const val ONGOING_ID = 1
        private const val PROCESS_ID = "youder"
        private val DESTINATION = Regex("""\[download\] Destination: .*/(.+?)(\.f\d+)?\.\w+$""")
        private val POST_PROCESS = listOf("[Merger]", "[ExtractAudio]", "[EmbedThumbnail]", "[Metadata]")

        // Status terakhir & pendengar UI; satu proses jadi cukup variabel statis
        @Volatile var status: Status? = null
        @Volatile var listener: ((Status) -> Unit)? = null
        @Volatile private var cancelled = false
        @Volatile private var ready = false

        fun cancel() {
            cancelled = true
            YoutubeDL.getInstance().destroyProcessById(PROCESS_ID)
        }

        // Utamakan mp4+m4a (tanpa re-encode), lalu fallback ke format apa pun di batas tinggi yang sama
        fun videoFormat(quality: String): String {
            val h = quality.removeSuffix("p").toIntOrNull() ?: return "bv*[ext=mp4]+ba[ext=m4a]/bv*+ba/b"
            return "bv*[height<=$h][ext=mp4]+ba[ext=m4a]/bv*[height<=$h]+ba/b[height<=$h]/b"
        }
    }
}
