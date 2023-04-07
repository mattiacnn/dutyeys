package com.dutyventures.dutyeyes.activity

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.dutyventures.dutyeyes.R
import com.dutyventures.dutyeyes.data.StreamManager
import com.dutyventures.dutyeyes.model.DutyStream
import com.github.tbouron.shakedetector.library.ShakeDetector
import com.otaliastudios.zoom.ZoomLayout
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer

class VLCStreamActivity : AppCompatActivity() {
    companion object {
        private const val USE_TEXTURE_VIEW = false
        private const val ENABLE_SUBTITLES = false
        private var playingStream: DutyStream? = null

        private var libVLC: LibVLC? = null
        private var mediaPlayer: MediaPlayer? = null
    }

    private lateinit var emptyText: View
    private lateinit var videoLayout: View
    private lateinit var loading: View
    private lateinit var retry: View
    private lateinit var zoom: View
    var lastWidth = -1


    private fun updateUi() {
        val hasStream = StreamManager.getCurrentStream() != null
        zoom.isVisible = hasStream
        emptyText.isVisible = !hasStream
    }

    private val globalListener = ViewTreeObserver.OnGlobalLayoutListener {
        if (lastWidth == zoom.width) {
            return@OnGlobalLayoutListener
        }
        lastWidth = zoom.width
        val params = videoLayout.layoutParams
        params.height = zoom.height
        params.width = zoom.width
        videoLayout.layoutParams = params
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        StreamManager.load()
        setContentView(R.layout.activity_vlcstream)
        // ui
        emptyText = findViewById(R.id.empty_text)
        videoLayout = findViewById(R.id.view_vlc_layout)
        loading = findViewById(R.id.loading)
        retry = findViewById(R.id.retry)
        zoom = findViewById<ZoomLayout>(R.id.zoom)
        updateUi()
        // listeners
        findViewById<View>(R.id.settings).setOnClickListener { openSettings() }
        zoom.viewTreeObserver.addOnGlobalLayoutListener(globalListener)
        retry.setOnClickListener {
            Log.e("EyesDuty", "Retry clicked")
            ExoStreamActivity.LOGS += "\nRetry clicked\n"
            releasePlayer()
            startPlayer()
        }
        ShakeDetector.create(
            this
        ) {
            nextStream()
        }
    }

    private fun releasePlayer() {
        if (libVLC == null) {
            return
        }
        libVLC?.release()
        libVLC = null
    }

    private fun startPlayer() {
        val stream = StreamManager.getCurrentStream() ?: return
        val uri = Uri.parse(stream.url)
        libVLC = LibVLC(this, ArrayList<String>().apply {
            add("--no-drop-late-frames")
            add("--no-skip-frames")
            add("--rtsp-tcp")
            add("-vvv")
        })
        mediaPlayer = MediaPlayer(libVLC)
        mediaPlayer?.attachViews(
            findViewById(R.id.view_vlc_layout),
            null,
            ENABLE_SUBTITLES,
            USE_TEXTURE_VIEW
        )
        try {
            Media(libVLC, uri).apply {
                setHWDecoderEnabled(true, false)
                // addOption(":network-caching=150");
                // addOption(":clock-jitter=0");
                // addOption(":clock-synchro=0");
                mediaPlayer?.media = this
            }.release()
            mediaPlayer?.play()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun nextStream() {
        if (StreamManager.nextStream()) {
            releasePlayer()
            startPlayer()
        }
    }

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        ShakeDetector.destroy()
        releasePlayer()
    }

    override fun onResume() {
        super.onResume()
        updateUi()
        ShakeDetector.start()
        startPlayer()
    }

    override fun onStop() {
        super.onStop()
        ShakeDetector.stop()
    }

}