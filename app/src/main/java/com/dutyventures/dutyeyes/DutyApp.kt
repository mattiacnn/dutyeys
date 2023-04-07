package com.dutyventures.dutyeyes

import android.app.Application
import com.orhanobut.hawk.Hawk

class DutyApp : Application() {

    companion object {
        fun useExo(): Boolean = Hawk.get("exo") ?: true
        fun setUseExo(exo: Boolean): Boolean = Hawk.put("exo", exo)
    }

    override fun onCreate() {
        super.onCreate()
        Hawk.init(this).build();
    }
}