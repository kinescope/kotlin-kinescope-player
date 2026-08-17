@file:OptIn(kotlinx.serialization.InternalSerializationApi::class)

package io.kinescope.demo.offlinedrm

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.imageview.ShapeableImageView
import io.kinescope.demo.R
import io.kinescope.sdk.shorts.AppJson
import io.kinescope.sdk.shorts.download.VideoDownloadManager
import io.kinescope.sdk.shorts.models.VideoData
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@UnstableApi
class OfflineDrmDemoActivity : AppCompatActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var emptyText: TextView
    private var adapter: Adapter? = null
    private var progressUpdateJob: Job? = null

    private val downloadListener = object : DownloadManager.Listener {
        override fun onDownloadChanged(
            downloadManager: DownloadManager,
            download: Download,
            finalException: Exception?,
        ) {
            runOnUiThread { refreshList() }
        }

        override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
            runOnUiThread { refreshList() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_offline_drm_demo)

        recycler = findViewById(R.id.recyclerOfflineDrm)
        emptyText = findViewById(R.id.emptyText)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        findViewById<FloatingActionButton>(R.id.fabAddDownload).setOnClickListener {
            startActivity(Intent(this, AddDrmDownloadActivity::class.java))
        }

        VideoDownloadManager.initialize(this)
        VideoDownloadManager.addDownloadListener(this, downloadListener)
        recycler.layoutManager = LinearLayoutManager(this)
        refreshList()
        startProgressUpdates()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        refreshList()
    }

    override fun onDestroy() {
        progressUpdateJob?.cancel()
        progressUpdateJob = null
        VideoDownloadManager.removeDownloadListener(downloadListener)
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        refreshList()
    }

    private fun startProgressUpdates() {
        progressUpdateJob?.cancel()
        progressUpdateJob = CoroutineScope(Dispatchers.Main).launch {
            while (isActive) {
                delay(500)
                if (VideoDownloadManager.getActiveDownloads(this@OfflineDrmDemoActivity).isNotEmpty()) {
                    refreshList()
                }
            }
        }
    }

    private fun refreshList() {
        val items = loadOfflineListItems()
        adapter = Adapter(items, ::onOfflineItemClick, ::onDeleteClick)
        recycler.adapter = adapter

        if (items.isEmpty()) {
            recycler.visibility = View.GONE
            emptyText.visibility = View.VISIBLE
        } else {
            recycler.visibility = View.VISIBLE
            emptyText.visibility = View.GONE
        }
    }

    private fun onOfflineItemClick(item: OfflineListItem) {
        if (item.isActive) {
            Toast.makeText(this, "Дождитесь окончания загрузки", Toast.LENGTH_SHORT).show()
            return
        }
        val videoData = item.videoData ?: return
        val videoDataJson = try {
            AppJson.encodeToString(VideoData.serializer(), videoData)
        } catch (e: Exception) {
            Log.e(TAG, "encode VideoData", e)
            Toast.makeText(this, "Ошибка данных", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(this, OfflineMainPlayerActivity::class.java).apply {
            putExtra(OfflineMainPlayerActivity.EXTRA_VIDEO_DATA_JSON, videoDataJson)
            putExtra(OfflineMainPlayerActivity.EXTRA_DOWNLOAD_ID, item.download.request.id)
            putExtra(
                OfflineMainPlayerActivity.EXTRA_UP_NAVIGATION,
                OfflineMainPlayerActivity.UP_NAV_OFFLINE_DRM_LIST,
            )
        }
        startActivity(intent)
    }

    private fun onDeleteClick(downloadId: String) {
        VideoDownloadManager.removeDownload(this, downloadId)
        Toast.makeText(this, "Видео удалено", Toast.LENGTH_SHORT).show()
        refreshList()
    }

    private fun loadOfflineListItems(): List<OfflineListItem> {
        val active = VideoDownloadManager.getActiveDownloads(this).map { download ->
            OfflineListItem(
                download = download,
                videoData = parseVideoData(download),
                isActive = true,
            )
        }
        val completed = VideoDownloadManager.getAllCompletedDownloads(this).mapNotNull { download ->
            val videoData = parseVideoData(download) ?: return@mapNotNull null
            if (videoData.hlsLink.isBlank()) return@mapNotNull null
            OfflineListItem(download = download, videoData = videoData, isActive = false)
        }
        return active + completed
    }

    private fun parseVideoData(download: Download): VideoData? {
        val data = download.request.data ?: return null
        if (data.isEmpty()) return null
        val json = try {
            String(data, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "decode request.data", e)
            return null
        }
        return try {
            AppJson.decodeFromString(VideoData.serializer(), json)
        } catch (e: Exception) {
            Log.e(TAG, "parse VideoData: $json", e)
            null
        }
    }

    private data class OfflineListItem(
        val download: Download,
        val videoData: VideoData?,
        val isActive: Boolean,
    )

    private class Adapter(
        private val items: List<OfflineListItem>,
        private val onItemClick: (OfflineListItem) -> Unit,
        private val onDeleteClick: (String) -> Unit,
    ) : RecyclerView.Adapter<Adapter.VH>() {

        class VH(view: View) : RecyclerView.ViewHolder(view) {
            val index: TextView = view.findViewById(R.id.index)
            val thumbnail: ShapeableImageView = view.findViewById(R.id.thumbnail)
            val playOverlay: ImageView = view.findViewById(R.id.playOverlay)
            val title: TextView = view.findViewById(R.id.title)
            val progressBar: ProgressBar = view.findViewById(R.id.progressBar)
            val progressText: TextView = view.findViewById(R.id.progressText)
            val btnDelete: ImageButton = view.findViewById(R.id.btnDelete)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_offline_drm, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.index.text = (position + 1).toString()
            val title = item.videoData?.title?.takeIf { it.isNotBlank() } ?: "Загрузка…"
            holder.title.text = title

            val posterUrl = item.videoData?.posterUrl
            if (posterUrl.isNullOrBlank()) {
                Glide.with(holder.thumbnail).clear(holder.thumbnail)
                holder.thumbnail.setImageDrawable(null)
            } else {
                Glide.with(holder.thumbnail)
                    .load(posterUrl)
                    .centerCrop()
                    .placeholder(R.drawable.bg_saved_videos_thumbnail)
                    .error(R.drawable.bg_saved_videos_thumbnail)
                    .into(holder.thumbnail)
            }

            if (item.isActive) {
                holder.playOverlay.isVisible = false
                holder.progressBar.isVisible = true
                holder.progressText.isVisible = true
                val (percent, bytesDownloaded) = VideoDownloadManager.getDownloadProgress(item.download)
                val indeterminate = percent <= 0 && bytesDownloaded <= 0 ||
                    item.download.state == Download.STATE_QUEUED ||
                    item.download.state == Download.STATE_RESTARTING
                holder.progressBar.isIndeterminate = indeterminate
                if (!indeterminate) {
                    holder.progressBar.progress = percent.coerceIn(0, 100)
                }
                holder.progressText.text =
                    VideoDownloadManager.formatDownloadProgressLabel(item.download)
                holder.btnDelete.isEnabled = true
            } else {
                holder.playOverlay.isVisible = true
                holder.progressBar.isIndeterminate = false
                holder.progressBar.isVisible = false
                holder.progressText.isVisible = false
                holder.btnDelete.isEnabled = true
            }

            holder.itemView.setOnClickListener { onItemClick(item) }
            holder.btnDelete.setOnClickListener {
                onDeleteClick(item.download.request.id)
            }
        }

        override fun getItemCount(): Int = items.size
    }

    companion object {
        private const val TAG = "OfflineDrmDemo"
    }
}
