@file:OptIn(kotlinx.serialization.InternalSerializationApi::class)

package io.kinescope.demo.offlinedrm

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import io.kinescope.demo.R
import io.kinescope.demo.shorts.OfflineVideoPlayerActivity
import io.kinescope.sdk.shorts.AppJson
import io.kinescope.sdk.shorts.download.VideoDownloadManager
import io.kinescope.sdk.shorts.models.VideoData
import java.nio.charset.StandardCharsets


@UnstableApi
class OfflineDrmDemoActivity : AppCompatActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var emptyText: TextView
    private var adapter: Adapter? = null

    private val downloadListener = object : DownloadManager.Listener {
        override fun onDownloadChanged(
            downloadManager: DownloadManager,
            download: Download,
            finalException: Exception?
        ) {
            if (download.state == Download.STATE_COMPLETED) {
                runOnUiThread { refreshList() }
            }
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
        refreshList()
    }

    override fun onDestroy() {
        VideoDownloadManager.removeDownloadListener(downloadListener)
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        refreshList()
    }

    private fun refreshList() {
        val items = loadCompletedOfflineDrmItems()
        adapter = Adapter(items, ::onOfflineItemClick, ::onDeleteClick)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        if (items.isEmpty()) {
            recycler.visibility = View.GONE
            emptyText.visibility = View.VISIBLE
        } else {
            recycler.visibility = View.VISIBLE
            emptyText.visibility = View.GONE
        }
    }

    private fun onOfflineItemClick(videoData: VideoData, downloadId: String) {
        val videoDataJson = try {
            AppJson.encodeToString(VideoData.serializer(), videoData)
        } catch (e: Exception) {
            Log.e(TAG, "encode VideoData", e)
            Toast.makeText(this, "Ошибка данных", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Всегда используем основной плеер для открытия скачанных видео
        val intent = Intent(this, OfflineMainPlayerActivity::class.java).apply {
            putExtra(OfflineMainPlayerActivity.EXTRA_VIDEO_DATA_JSON, videoDataJson)
            putExtra(OfflineMainPlayerActivity.EXTRA_DOWNLOAD_ID, downloadId)
        }
        startActivity(intent)
    }

    private fun onDeleteClick(downloadId: String) {
        VideoDownloadManager.removeDownload(this, downloadId)
        Toast.makeText(this, "Видео удалено", Toast.LENGTH_SHORT).show()
        refreshList()
    }

    private fun loadCompletedOfflineDrmItems(): List<OfflineDrmItem> {
        val result = mutableListOf<OfflineDrmItem>()
        val downloads = VideoDownloadManager.getAllCompletedDownloads(this)
        for (download in downloads) {
            val data = download.request.data
            if (data == null || data.isEmpty()) continue
            val json = try {
                String(data, StandardCharsets.UTF_8)
            } catch (e: Exception) {
                Log.e(TAG, "decode request.data", e)
                continue
            }
            val videoData = try {
                AppJson.decodeFromString(VideoData.serializer(), json)
            } catch (e: Exception) {
                Log.e(TAG, "parse VideoData: $json", e)
                continue
            }
            if (videoData.hlsLink.isBlank()) continue
            result.add(OfflineDrmItem(download, videoData))
        }
        return result
    }

    private data class OfflineDrmItem(val download: Download, val videoData: VideoData)

    private class Adapter(
        private val items: List<OfflineDrmItem>,
        private val onItemClick: (VideoData, String) -> Unit,
        private val onDeleteClick: (String) -> Unit
    ) : RecyclerView.Adapter<Adapter.VH>() {

        class VH(view: View) : RecyclerView.ViewHolder(view) {
            val title: TextView = view.findViewById(R.id.title)
            val drmBadge: TextView = view.findViewById(R.id.drmBadge)
            val btnDelete: ImageButton = view.findViewById(R.id.btnDelete)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_offline_drm, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.title.text = item.videoData.title
            holder.drmBadge.visibility = if (item.videoData.drm?.widevine?.licenseUrl.isNullOrBlank()) View.GONE else View.VISIBLE
            holder.itemView.setOnClickListener {
                onItemClick(item.videoData, item.download.request.id)
            }
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
