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
import com.google.android.exoplayer2.util.EventLogger
import com.google.android.exoplayer2.video.VideoSize
import com.otaliastudios.zoom.ZoomSurfaceView
import kotlin.math.abs


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
            nextStream()
        }

        setupGyro()
    }

    private fun setupGyro() {
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager? ?: return
        sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }

    private fun nextStream() {
        if (StreamManager.nextStream()) {
            stopPlaying()
            playCurrentStreamIfNotPlaying()
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
                player?.setMediaItem(MediaItem.fromUri(stream.url))
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

    private val sensorListener = object : DutySensorListener() {
        override fun rotation(sensorEvent: SensorEvent) {
            val x = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
                sensorEvent.values[1] else sensorEvent.values[0]
            val y = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
                sensorEvent.values[0] else sensorEvent.values[1]

//            findViewById<TextView>(R.id.gyro).isVisible = true
//            findViewById<TextView>(R.id.gyro).text = buildString {
//                appendLine("Current X: ".plus(currentX))
//                appendLine("Zoom: ${surface.engine.zoom}")
//                appendLine("CONTAINER H: ${surface.engine.containerHeight}")
//                appendLine("CONTENT H: ${surface.engine.contentHeight}")
//
//                appendLine("CW: ${surface.engine.containerWidth}")
//                appendLine("CTW: ${surface.engine.contentWidth}")
//                appendLine("X: ${sensorEvent.values[0]}")
//                appendLine("X: ${sensorEvent.values[1]}")
//                appendLine("X: ${sensorEvent.values[2]}")
//
//
//                appendLine("PAN BY $x and $y ")
//            }

            if (abs(x.toInt()) == 0 && abs(y.toInt()) == 0) {
                return
            }

            surface.engine.panByRelative(x, y)
        }
    }


}
