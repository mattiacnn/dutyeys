package com.dutyventures.dutyeyes.activity

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.dutyventures.dutyeyes.R
import com.dutyventures.dutyeyes.data.DutySensorListener
import com.dutyventures.dutyeyes.data.StreamManager
import com.dutyventures.dutyeyes.model.DutyStream
import com.github.tbouron.shakedetector.library.ShakeDetector
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.rtsp.RtspMediaSource
import com.google.android.exoplayer2.util.EventLogger
import com.google.android.exoplayer2.video.VideoSize
import com.otaliastudios.zoom.ZoomSurfaceView
import kotlin.math.abs
import kotlin.properties.Delegates
import android.graphics.drawable.ColorDrawable
import android.view.ViewGroup
import android.widget.Button
import com.dutyventures.dutyeyes.ui.DutyStreamAdapter
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlin.random.Random


class ExoStreamActivity : AppCompatActivity(), Player.Listener {

    companion object {
        var LOGS = ""
        private var player: ExoPlayer? = null
        private var playingStream: DutyStream? = null
    }

    private lateinit var emptyText: View
    private lateinit var surface: ZoomSurfaceView
    private lateinit var loading: View
    private lateinit var retry: View
    private lateinit var fab: FloatingActionButton

    private var sensor: Sensor? = null
    private var sensorManager: SensorManager? = null

    private fun setupSurface() {
        surface.setBackgroundColor(Color.BLACK)
        surface.addCallback(object : ZoomSurfaceView.Callback {
            override fun onZoomSurfaceCreated(view: ZoomSurfaceView) {
                Log.e("EyesDuty", "Zoom surface created")
                player?.setVideoSurface(view.surface)
            }

            override fun onZoomSurfaceDestroyed(view: ZoomSurfaceView) {}
        })
    }

    private fun createExoPlayerInstanceIfNeeded() {
        Log.e("EyesDuty", "createExoPlayerInstanceIfNeeded called")
        if (player != null) {
            Log.e("EyesDuty", "Won't create player, we already have it")
            return
        }
        Log.e("EyesDuty", "Creating new player")

        val player = ExoPlayer.Builder(this).build()
        player.playWhenReady = true
        player.addAnalyticsListener(EventLogger())
        player.addListener(this)
        Companion.player = player
    }

    private fun updateUi() {
        val hasStream = StreamManager.getCurrentStream() != null
        surface.isVisible = hasStream
        emptyText.isVisible = !hasStream
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        LOGS = ""

        StreamManager.load()
        setContentView(R.layout.activity_exo_stream)
        // ui
        emptyText = findViewById(R.id.empty_text)
        surface = findViewById(R.id.surface_view)
        loading = findViewById(R.id.loading)
        retry = findViewById(R.id.retry)
        updateUi()
        setupSurface()

        findViewById<TextView>(R.id.gyro)
        fab = findViewById<FloatingActionButton>(R.id.floatingActionButton)

        // listeners
        findViewById<View>(R.id.settings).setOnClickListener { openSettings() }
        retry.setOnClickListener {
            Log.e("EyesDuty", "Retry clicked")
            LOGS += "\nRetry clicked\n"
            stopPlaying()
            playCurrentStreamIfNotPlaying()
        }
        ShakeDetector.create(
            this
        ) {
            // run functuion here
        }

        setupGyro()
    }

    private fun setupGyro() {
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager? ?: return
        sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
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
        stopPlaying()
    }

    override fun onResume() {
        super.onResume()
        if (sensor != null) {
            sensorManager?.registerListener(sensorListener, sensor, 2 * 1000 * 1000)
        }
        updateUi()
        ShakeDetector.start()
        playCurrentStreamIfNotPlaying()
    }

    override fun onPause() {
        super.onPause()
        sensor
        sensorManager?.unregisterListener(sensorListener);

    }

    override fun onStop() {
        super.onStop()
        ShakeDetector.stop()
        surface.onPause()
    }


    override fun onStart() {
        super.onStart()
        surface.onResume()
    }

    private fun stopPlaying() {
        Log.e(
            "EyesDuty",
            "Stop playing called. Playing stream before $playingStream player before $player"
        )
        playingStream = null
        player?.release()
        player = null
    }

    private fun playCurrentStreamIfNotPlaying() {
        Log.e(
            "EyesDuty",
            "playCurrentStreamIfNotPlaying called. Current stream: ${StreamManager.getCurrentStream()}"
        )
        val stream = StreamManager.getCurrentStream()
        if (stream == null) {
            // no stream, release player
            stopPlaying()
            return
        }
        if (stream != playingStream || player == null) {
            // something else is playing, reset exo and stream
            Log.e("EyesDuty", "something else is playing, reset exo and stream")
            Log.e("EyesDuty", "S: $stream PS: $playingStream")

            stopPlaying()
            createExoPlayerInstanceIfNeeded()
            try {
                val mediaSource: MediaSource = RtspMediaSource.Factory().setForceUseRtpTcp(true)
                    .createMediaSource(MediaItem.fromUri(stream.url))

                player?.setMediaSource(mediaSource)

            } catch (e: Exception) {
                LOGS += "\n${e.stackTraceToString()}"
                Log.e("DutyEyes", "Eroare", e)
            }
            player?.prepare()
            playingStream = stream
            LOGS += "\nLoading url ${stream.url}\n"
            Log.e("EyesDuty", "Playing new url: ${stream.url}")
        }
    }

    override fun onVideoSizeChanged(videoSize: VideoSize) {
        super.onVideoSizeChanged(videoSize)
        surface.setContentSize(videoSize.width.toFloat(), videoSize.height.toFloat())
        var zoom = surface.engine.contentWidth / surface.engine.containerWidth
        if (zoom < 1f) {
            LOGS += "\nnZoom less than 1, hacking it content: ${surface.engine.contentWidth} container: ${surface.engine.containerWidth}"
            zoom = 1f
        }
        surface.engine.zoomTo(zoom, false)
        Log.e(
            "EyesDuty",
            " video: ${videoSize.width} x ${videoSize.height} container: ${surface.engine.containerWidth} x ${surface.engine.containerHeight} AND ZOOM = $zoom"
        )

        this.fab.setOnClickListener {
            val centerWidth = (surface.engine.containerWidth / zoom) / 2
            val centerHeight = (surface.engine.contentHeight / zoom) / 2
            surface.engine.moveTo(zoom,-centerWidth ,-centerHeight,true);
        }

        val loggerTextView = findViewById<TextView>(R.id.logger)

        val contentWidth = surface.engine.containerWidth

        val loggerText = "Content Width: $contentWidth\nZoom: $zoom"
        loggerTextView.text = loggerText

        LOGS += "\nvideo: ${videoSize.width} x ${videoSize.height} container: ${surface.engine.containerWidth} x ${surface.engine.containerHeight} AND ZOOM = $zoom"
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        super.onPlaybackStateChanged(playbackState)
        loading.isVisible = playbackState == ExoPlayer.STATE_BUFFERING

        retry.isVisible =
            StreamManager.getCurrentStream() != null && playbackState == ExoPlayer.STATE_IDLE
        Log.e("EyesDuty", "Playback state: $playbackState")
        LOGS += "\nPlayback state: $playbackState\n"
    }

    override fun onPlayerError(error: PlaybackException) {
        super.onPlayerError(error)
        Log.e("Error", "Found error", error)
        LOGS += error.stackTraceToString()
    }

    fun invertSign(number: Float, threshold: Float): Float {
        var invertedNumber = -number
        return invertedNumber
    }

    private val sensorListener = object : DutySensorListener() {
        private var previousY: Float? = null

        override fun rotation(sensorEvent: SensorEvent) {
             var x = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
                sensorEvent.values[1] else sensorEvent.values[0]
            var y = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
                sensorEvent.values[0] else sensorEvent.values[1]
            var z = sensorEvent.values[2]
            var invertedZ = z
            if (x < 0){
                x=Math.abs(x)
            }
            else {
                x = -x
            }

            var limit = false;
            val panY = surface.engine.pan.y
            var moving = false;

            if (previousY != null) {
                if (z <= 3.5F && z > 0){
                    invertedZ = 0.0F
                    moving = false
                }
                else if (y > previousY!!) {
                    // Movimento dall'alto verso il basso
                    // Esegui le azioni desiderate
                    moving = true
                     invertedZ = 3.0F

                } else if (y < previousY!!) {
                    // Movimento dal basso verso l'alto
                    // Esegui le azioni desiderate
                    moving = true
                    invertedZ = -3.0F
                }
            }

            previousY = z

            surface.engine.panByRelative(x, invertedZ)
        }
    }


}
