package com.dutyventures.dutyeyes.activity

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.text.InputType
import android.text.method.ScrollingMovementMethod
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.dutyventures.dutyeyes.R
import com.dutyventures.dutyeyes.data.NetworkResponse
import com.dutyventures.dutyeyes.data.OnvifManager
import com.dutyventures.dutyeyes.data.StreamManager
import kotlinx.coroutines.launch


class AddStreamActivity : AppCompatActivity() {

    private lateinit var ip: EditText
    private lateinit var http: Spinner
    private lateinit var port: EditText
    private lateinit var user: EditText
    private lateinit var password: EditText
    private lateinit var cameraName: EditText
    private lateinit var logText: TextView
    private lateinit var progress: View
    private lateinit var addView: View
    private lateinit var mainStream: CheckBox

    private lateinit var mainHandler: Handler
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_stream)
        loadUi()
    }

    private fun loadUi() {
        mainHandler = Handler(mainLooper)
        ip = findViewById(R.id.ip)
        http = findViewById(R.id.https)
        port = findViewById(R.id.port)
        user = findViewById(R.id.username)
        password = findViewById(R.id.password)
        logText = findViewById(R.id.log_text)
        cameraName = findViewById(R.id.name)
        progress = findViewById(R.id.progress)
        addView = findViewById(R.id.add)
        mainStream = findViewById(R.id.main_stream)
        logText.movementMethod = ScrollingMovementMethod()
        findViewById<View>(R.id.back).setOnClickListener {
            finish()
        }
        findViewById<View>(R.id.url).setOnClickListener {
            addStreamByUrl()
        }
        addView.setOnClickListener {
            val inputMethodManager =
                getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
            inputMethodManager.hideSoftInputFromWindow(password.windowToken, 0)
            clickedAdd()
        }
    }

    private fun addStreamByUrl() {
        val builder: AlertDialog.Builder = AlertDialog.Builder(this)
        builder.setTitle(R.string.stream_url)
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_TEXT
        builder.setView(input)
        builder.setPositiveButton(
            R.string.add_stream_url
        ) { _, _ ->
            StreamManager.addStreamByUrl(input.text.toString().trim())
            finish()
        }
        builder.setNegativeButton(R.string.cancel, null)
        builder.show()
    }

    private fun showLoading(loading: Boolean) {
        mainHandler.post {
            addView.isInvisible = loading
            progress.isVisible = loading
        }
    }

    private fun clickedAdd() {
        showLoading(true)
        addDebugText("Trying to fetch ")

        val ip = StringBuilder()
        ip.append("http")
        if (http.selectedItemPosition != 0) {
            ip.append("s")
        }
        ip.append("://").append(this.ip.text.toString())
        if (port.text.toString().isNotEmpty()) {
            ip.append(":").append(port.text)
        }
        val manager = OnvifManager(
            user = user.text.toString(),
            password = password.text.toString(),
            ip = ip.toString(),
            firstProfile = mainStream.isChecked
        )
        addDebugText("IP: $ip")
        lifecycleScope.launch {
            when (val result = manager.getStreamUrl()) {
                is NetworkResponse.Error -> {
                    ExoStreamActivity.LOGS += result.errorMessage
                    addDebugText("Error.\n${result.errorMessage}")
                    mainHandler.post {
                        Toast.makeText(
                            this@AddStreamActivity,
                            getString(R.string.error_onvif),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                is NetworkResponse.Success -> {
                    addDebugText("Found stream url: '${result.result}'")
                    val secondPart = result.result.substringAfter("rtsp://")
                    val authenticatedUrl = "rtsp://${user.text}:${password.text}@$secondPart"
                    StreamManager.addStream(
                        name = cameraName.text.toString(),
                        url = authenticatedUrl,
                        safeUrl = result.result
                    )
                    mainHandler.post {
                        Toast.makeText(
                            this@AddStreamActivity,
                            getString(R.string.stream_added),
                            Toast.LENGTH_SHORT
                        ).show()
                        finish()
                    }
                }
            }
            showLoading(false)
        }
    }

    private fun addDebugText(text: String) {
        mainHandler.post {
            var currentText = logText.text.toString()
            if (currentText.isNotEmpty()) {
                currentText += "\n"
            }
            currentText += text
            logText.text = currentText
        }
    }
}