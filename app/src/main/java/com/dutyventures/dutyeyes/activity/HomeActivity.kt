package com.dutyventures.dutyeyes.activity

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.dutyventures.dutyeyes.DutyApp

class HomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (DutyApp.useExo()) {
            startActivity(Intent(this, ExoStreamActivity::class.java))
        } else {
            startActivity(Intent(this, VLCStreamActivity::class.java))
        }
        finish()
    }
}