package com.adam.youder

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import com.adam.youder.DownloadService.State

class MainActivity : Activity() {

    private val videoQualities = listOf("360p", "480p", "720p", "1080p", "Terbaik")
    private val audioQualities = listOf("128 kbps", "192 kbps", "320 kbps")

    private lateinit var urlInput: EditText
    private lateinit var videoTab: RadioButton
    private lateinit var qualityGroup: RadioGroup
    private lateinit var folderLabel: TextView
    private lateinit var downloadBtn: Button
    private lateinit var card: View
    private lateinit var jobTitle: TextView
    private lateinit var jobStatus: TextView
    private lateinit var jobProgress: ProgressBar
    private lateinit var jobAction: Button
    private lateinit var jobClose: Button

    // Folder khusus untuk download berikutnya saja; null = pakai default dari Pengaturan
    private var oneOffFolder: Uri? = null
    private val isAudio get() = !videoTab.isChecked
    private val folder get() = oneOffFolder ?: Folders.default(this, isAudio)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        urlInput = findViewById(R.id.url)
        videoTab = findViewById(R.id.video)
        qualityGroup = findViewById(R.id.quality)
        folderLabel = findViewById(R.id.folder)
        downloadBtn = findViewById(R.id.download)
        card = findViewById(R.id.job_card)
        jobTitle = findViewById(R.id.job_title)
        jobStatus = findViewById(R.id.job_status)
        jobProgress = findViewById(R.id.job_progress)
        jobAction = findViewById(R.id.job_action)
        jobClose = findViewById(R.id.job_close)

        findViewById<RadioGroup>(R.id.type).setOnCheckedChangeListener { group, _ ->
            group.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            oneOffFolder = null
            fillQualities()
            showFolder()
        }
        qualityGroup.setOnCheckedChangeListener { group, _ -> group.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
        fillQualities()

        urlInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) { downloadBtn.isEnabled = findUrl(s.toString()) != null }
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
        })
        downloadBtn.isEnabled = false

        findViewById<View>(R.id.paste).setOnClickListener { pasteFromClipboard(force = true) }
        findViewById<View>(R.id.settings).setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        findViewById<View>(R.id.folder_row).setOnClickListener { startActivityForResult(Folders.pickIntent(), REQ_FOLDER) }
        downloadBtn.setOnClickListener { startDownload(it) }
        jobAction.setOnClickListener { onJobAction() }
        jobClose.setOnClickListener {
            DownloadService.status = null
            render(null)
        }

        requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 0)
        handleShare(intent)
    }

    override fun onResume() {
        super.onResume()
        showFolder() // default bisa berubah dari Pengaturan
        DownloadService.listener = { s -> runOnUiThread { render(s) } }
        render(DownloadService.status)
    }

    override fun onPause() {
        super.onPause()
        DownloadService.listener = null
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    // Clipboard hanya bisa dibaca saat jendela app sedang fokus (Android 10+)
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) pasteFromClipboard(force = false)
    }

    @Deprecated("Activity platform tanpa AndroidX")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_FOLDER && resultCode == RESULT_OK) {
            oneOffFolder = data?.data
            showFolder()
        }
    }

    private fun startDownload(button: View) {
        val url = findUrl(urlInput.text.toString()) ?: return
        button.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        startForegroundService(Intent(this, DownloadService::class.java)
            .putExtra(DownloadService.EXTRA_URL, url)
            .putExtra(DownloadService.EXTRA_AUDIO, isAudio)
            .putExtra(DownloadService.EXTRA_QUALITY, selectedQuality())
            .putExtra(DownloadService.EXTRA_FOLDER, folder?.toString()))
        urlInput.text.clear()
        urlInput.clearFocus()
        getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(urlInput.windowToken, 0)
        oneOffFolder = null
        showFolder()
    }

    private fun onJobAction() {
        val s = DownloadService.status ?: return
        when (s.state) {
            State.RUNNING -> DownloadService.cancel()
            State.DONE -> try {
                startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(s.file, s.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            } catch (e: ActivityNotFoundException) {
                jobStatus.text = "Tidak ada aplikasi untuk membuka file ini"
            }
            State.FAILED -> Unit
        }
    }

    private fun render(s: DownloadService.Status?) {
        card.visibility = if (s == null) View.GONE else View.VISIBLE
        if (s == null) return
        jobTitle.text = s.title
        jobStatus.text = s.text
        val running = s.state == State.RUNNING
        jobProgress.visibility = if (running) View.VISIBLE else View.GONE
        jobProgress.isIndeterminate = s.percent == null
        s.percent?.let { jobProgress.setProgress(it, true) }
        jobStatus.setTextColor(getColor(if (s.state == State.FAILED) R.color.accent else R.color.label2))
        jobAction.visibility = if (s.state == State.FAILED) View.GONE else View.VISIBLE
        jobAction.text = if (running) "Batalkan" else "Buka"
        jobClose.visibility = if (running) View.GONE else View.VISIBLE
    }

    private fun fillQualities() {
        val items = if (isAudio) audioQualities else videoQualities
        qualityGroup.removeAllViews()
        val gap = resources.getDimensionPixelSize(R.dimen.gap)
        items.forEachIndexed { i, label ->
            qualityGroup.addView(RadioButton(this, null, 0, R.style.Chip).apply {
                id = View.generateViewId()
                text = label
                tag = label
            }, RadioGroup.LayoutParams(RadioGroup.LayoutParams.WRAP_CONTENT, RadioGroup.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = gap
            })
            if (i == (if (isAudio) 1 else 2)) qualityGroup.check(qualityGroup.getChildAt(i).id)
        }
    }

    private fun selectedQuality(): String =
        findViewById<RadioButton>(qualityGroup.checkedRadioButtonId)?.tag as? String ?: "Terbaik"

    private fun showFolder() {
        folderLabel.text = Folders.label(folder, isAudio)
    }

    private fun pasteFromClipboard(force: Boolean) {
        if (!force && urlInput.text.isNotBlank()) return
        val clip = getSystemService(ClipboardManager::class.java).primaryClip ?: return
        findUrl(clip.getItemAt(0).text?.toString().orEmpty())?.let { urlInput.setText(it) }
    }

    private fun handleShare(intent: Intent) {
        if (intent.action != Intent.ACTION_SEND) return
        findUrl(intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty())?.let { urlInput.setText(it) }
    }

    companion object {
        private const val REQ_FOLDER = 1
        private val YT_URL = Regex("""https?://(www\.|m\.|music\.)?(youtube\.com|youtu\.be)/\S+""")

        fun findUrl(text: String): String? = YT_URL.find(text)?.value
    }
}
