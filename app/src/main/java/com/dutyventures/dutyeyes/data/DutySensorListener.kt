package com.dutyventures.dutyeyes.data

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener

abstract class DutySensorListener : SensorEventListener {
    override fun onSensorChanged(sensorEvent: SensorEvent) {
        rotation(sensorEvent)
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {

    }

    open fun rotation(sensorEvent: SensorEvent) {}
}