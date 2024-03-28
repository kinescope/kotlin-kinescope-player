package io.kinescope.demo

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import io.kinescope.sdk.models.videos.KinescopeVideoApi

class VideosAdapter(val callback: (String)-> Unit) : RecyclerView.Adapter<VideosAdapter.ViewHolder>() {

    private val arr = ArrayList<KinescopeVideoApi>()

    fun updateData(value:List<KinescopeVideoApi>) {
        arr.clear()
        arr.addAll(value)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideosAdapter.ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_video, parent, false))
    }

    override fun onBindViewHolder(holder: VideosAdapter.ViewHolder, position: Int) {
            holder.bind(arr[position])
    }

    override fun getItemCount(): Int {
        return arr.size
    }

    inner class ViewHolder(private val view:View) :  RecyclerView.ViewHolder(view.rootView), View.OnClickListener {
        private var video: KinescopeVideoApi? = null
        val title: TextView = view.findViewById(R.id.tv_title)

        init {
            view.setOnClickListener(this)
        }

        fun bind(video: KinescopeVideoApi) {
            this.video = video
            title.text = video.title
        }

        override fun onClick(p0: View?) {
            video?.let { callback.invoke(it.id) }
        }

    }
}