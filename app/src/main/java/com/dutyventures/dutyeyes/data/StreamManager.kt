package com.dutyventures.dutyeyes.data

import android.util.Log
import com.dutyventures.dutyeyes.model.DutyStream
import com.dutyventures.dutyeyes.model.DutyStreamItem
import com.orhanobut.hawk.Hawk
import java.util.*

object StreamManager {
    private const val key = "stream_list_key"

    private var streams = listOf<DutyStream>()

    fun load() {
        streams = getLocalStreams();
        Log.e("EyesDuty", "Stream loaded")

    }

    private fun getLocalStreams(): List<DutyStream> {
        return Hawk.get(key) ?: emptyList()
    }

    private var currentStreamId: String? = null

    fun addStream(name: String, url: String, safeUrl: String) {
        val streams = this.streams.toMutableList()
        streams.add(
            DutyStream(
                id = UUID.randomUUID().toString(),
                name = name,
                url = url,
                safeUrl = safeUrl
            )
        )
        Hawk.put(key, streams)
        this.streams = streams
    }

    fun addStreamByUrl(url: String) {
        val streams = this.streams.toMutableList()
        streams.add(
            DutyStream(
                id = UUID.randomUUID().toString(),
                name = "Custom stream",
                url = url,
                safeUrl = url
            )
        )
        Hawk.put(key, streams)
        this.streams = streams
    }

    fun getCurrentStream(): DutyStream? {
        return streams.find { it.id == currentStreamId } ?: streams.firstOrNull()
    }

    fun nextStream(): Boolean {
        val currentStream = getCurrentStream()
        if (currentStream == null) {
            currentStreamId = streams.firstOrNull()?.id
            return currentStreamId != null
        }
        val indexOfCurrentStream = streams.indexOf(currentStream)
        if (indexOfCurrentStream == -1) {
            currentStreamId = streams.firstOrNull()?.id
            return currentStreamId != null
        }
        val nextIndex = when {
            indexOfCurrentStream >= streams.size - 1 -> 0
            else -> indexOfCurrentStream + 1
        }
        val lastStreamId = currentStreamId
        currentStreamId = streams.getOrNull(nextIndex)?.id
        return lastStreamId != currentStreamId
    }

    fun getNextStreamId(): DutyStream? {
        val currentStream = getCurrentStream()

        val currentIndex = streams.indexOfFirst { it.id == currentStream?.id }
        val nextIndex = if (currentIndex != -1 && currentIndex < streams.size - 1) currentIndex + 1 else 0
        return streams.getOrNull(nextIndex)
    }


    fun getAllStreamItems(): List<DutyStreamItem> {
        val currentStreamId = getCurrentStream()?.id
        return streams.map {
            DutyStreamItem(
                stream = it,
                selected = it.id == currentStreamId
            )
        }
    }

    fun removeStream(id: String) {
        var streams = this.streams.toList()
        streams = streams.filter { it.id != id }
        this.streams = streams
        Hawk.put(key, streams)
    }

    fun selectStream(id: String) {
        currentStreamId = id
    }


}