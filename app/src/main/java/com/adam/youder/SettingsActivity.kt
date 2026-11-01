package com.adam.youder

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView

class SettingsActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        findViewById<View>(R.id.back).setOnClickListener { finish() }
        findViewById<View>(R.id.video_row).setOnClickListener { startActivityForResult(Folders.pickIntent(), REQ_VIDEO) }
        findViewById<View>(R.id.audio_row).setOnClickListener { startActivityForResult(Folders.pickIntent(), REQ_AUDIO) }
        findViewById<View>(R.id.reset_row).setOnClickListener {
            Folders.setDefault(this, false, null)
            Folders.setDefault(this, true, null)
            show()
        }
        show()
    }

    @Deprecated("Activity platform tanpa AndroidX")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data ?: return
        if (resultCode != RESULT_OK) return
        Folders.setDefault(this, requestCode == REQ_AUDIO, uri)
        show()
    }

    private fun show() {
        findViewById<TextView>(R.id.video_folder).text = Folders.label(Folders.default(this, false), false)
        findViewById<TextView>(R.id.audio_folder).text = Folders.label(Folders.default(this, true), true)
    }

    companion object {
        private const val REQ_VIDEO = 1
        private const val REQ_AUDIO = 2
    }
}
