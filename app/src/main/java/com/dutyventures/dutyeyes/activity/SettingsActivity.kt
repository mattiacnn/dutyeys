package com.dutyventures.dutyeyes.activity

import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.dutyventures.dutyeyes.DutyApp
import com.dutyventures.dutyeyes.R
import com.dutyventures.dutyeyes.data.StreamManager
import com.dutyventures.dutyeyes.ui.DutyStreamAdapter

class SettingsActivity : AppCompatActivity(), DutyStreamAdapter.Listener {

    private val adapter = DutyStreamAdapter(this)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        findViewById<SwitchCompat>(R.id.switch_compat).apply {
            isChecked = DutyApp.useExo()
            setOnCheckedChangeListener { _, checked ->
                DutyApp.setUseExo(checked)
                Toast.makeText(
                    this@SettingsActivity,
                    context.getString(R.string.reopen_app_player_change),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        findViewById<View>(R.id.back).setOnClickListener { finish() }
        findViewById<View>(R.id.add).setOnClickListener { addStream() }
        findViewById<RecyclerView>(R.id.recycler).apply {
            adapter = this@SettingsActivity.adapter
            layoutManager = LinearLayoutManager(this@SettingsActivity)
        }
        findViewById<View>(R.id.debug).setOnClickListener {
            val shareIntent = Intent()
            shareIntent.action = Intent.ACTION_SEND
            shareIntent.type = "text/plain"
            shareIntent.putExtra(Intent.EXTRA_TEXT, ExoStreamActivity.LOGS);
            startActivity(Intent.createChooser(shareIntent, "Debug logs"))
        }

    }

    override fun onResume() {
        super.onResume()
        updateUi()
    }

    private fun updateUi() {
        adapter.submitList(StreamManager.getAllStreamItems())
    }

    private fun addStream() {
        startActivity(Intent(this, AddStreamActivity::class.java))
    }

    override fun removeStream(id: String) {
        StreamManager.removeStream(id)
        updateUi()
    }

    override fun selectStream(id: String) {
        StreamManager.selectStream(id)
        updateUi()
    }
}