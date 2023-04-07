package com.dutyventures.dutyeyes.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.dutyventures.dutyeyes.R
import com.dutyventures.dutyeyes.model.DutyStreamItem
import java.lang.ref.WeakReference

class DutyStreamAdapter(listener: Listener) :
    ListAdapter<DutyStreamItem, DutyStreamAdapter.StreamHolder>(DiffItemCallback()) {

    private val listenerRef = WeakReference(listener)

    private class DiffItemCallback : DiffUtil.ItemCallback<DutyStreamItem>() {
        override fun areItemsTheSame(
            oldItem: DutyStreamItem,
            newItem: DutyStreamItem
        ): Boolean = oldItem.stream.id == newItem.stream.id


        override fun areContentsTheSame(
            oldItem: DutyStreamItem,
            newItem: DutyStreamItem
        ): Boolean {
            return oldItem == newItem
        }
    }

    class StreamHolder(view: View, private val listenerRef: WeakReference<Listener>) :
        RecyclerView.ViewHolder(view) {

        var cameraName: TextView = view.findViewById(R.id.camera_name)
        var text: TextView = view.findViewById(R.id.stream_text)
        var check: ImageView = view.findViewById(R.id.check)

        fun bind(streamItem: DutyStreamItem) {
            check.setImageResource(if (streamItem.selected) R.drawable.ic_baseline_radio_button_checked_24 else R.drawable.ic_baseline_radio_button_unchecked_24)
            text.text = streamItem.stream.safeUrl
            cameraName.text = streamItem.stream.name
            itemView.findViewById<View>(R.id.delete).setOnClickListener {
                listenerRef.get()?.removeStream(streamItem.stream.id)
            }
            itemView.setOnClickListener {
                listenerRef.get()?.selectStream(streamItem.stream.id)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StreamHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_stream, parent, false)
        return StreamHolder(view, listenerRef)
    }

    override fun onBindViewHolder(holder: StreamHolder, position: Int) {
        holder.bind(getItem(position))
    }

    interface Listener {
        fun removeStream(id: String)
        fun selectStream(id: String)
    }
}