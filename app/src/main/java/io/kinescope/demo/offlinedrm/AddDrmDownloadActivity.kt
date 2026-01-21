@file:OptIn(kotlinx.serialization.InternalSerializationApi::class)

package io.kinescope.demo.offlinedrm

import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.kinescope.demo.R
import io.kinescope.sdk.shorts.AppJson
import io.kinescope.sdk.shorts.drm.DrmConfigurator
import io.kinescope.sdk.shorts.drm.DrmContentProtection
import io.kinescope.sdk.shorts.drm.DrmHelper
import io.kinescope.sdk.shorts.download.VideoDownloadManager
import io.kinescope.sdk.shorts.download.VideoDownloadService
import io.kinescope.sdk.shorts.models.VideoData
import io.kinescope.sdk.shorts.utils.KinescopeUrls
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID

@UnstableApi
class AddDrmDownloadActivity : AppCompatActivity() {

    private var recycler: RecyclerView? = null
    private var adapter: Adapter? = null
    private val downloadingIds = mutableSetOf<String>()
    private var currentTempPlayer: ExoPlayer? = null
    private val drmConfigurator = DrmConfigurator(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_drm_download)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }

        VideoDownloadManager.initialize(this)
        val list = KinescopeUrls().getNextVideoUrls()
            .filter { !it.drm?.widevine?.licenseUrl.isNullOrBlank() }

        recycler = findViewById(R.id.recyclerAddDrm)
        adapter = Adapter(list, downloadingIds) { videoData -> startDrmDownload(videoData) }
        recycler?.layoutManager = LinearLayoutManager(this)
        recycler?.adapter = adapter
    }

    override fun onDestroy() {
        currentTempPlayer?.release()
        currentTempPlayer = null
        super.onDestroy()
    }

    private fun startDrmDownload(videoData: VideoData) {
        val contentId = generateStableContentId(videoData.hlsLink)
        if (currentTempPlayer != null) {
            Toast.makeText(this, "Дождитесь окончания", Toast.LENGTH_SHORT).show()
            return
        }
        val existing = VideoDownloadManager.getDownloadById(contentId)
        if (existing != null && existing.state == Download.STATE_COMPLETED) {
            Toast.makeText(this, "Видео уже скачано", Toast.LENGTH_SHORT).show()
            return
        }
        val licenseUrl = videoData.drm?.widevine?.licenseUrl
        if (licenseUrl.isNullOrBlank()) {
            Toast.makeText(this, "Нет DRM лицензии", Toast.LENGTH_SHORT).show()
            return
        }

        downloadingIds.add(contentId)
        adapter?.notifyDataSetChanged()
        Toast.makeText(this, "Получение DRM-ключей…", Toast.LENGTH_SHORT).show()

        val drmHelper = DrmHelper(this, drmConfigurator) { vd, pssh ->
            val cId = generateStableContentId(vd.hlsLink)
            val prot = DrmContentProtection(
                schemeUri = C.WIDEVINE_UUID.toString(),
                licenseUrl = vd.drm?.widevine?.licenseUrl,
                schemeUuid = C.WIDEVINE_UUID
            )
            drmConfigurator.downloadOfflineLicense(
                videoUrl = vd.hlsLink,
                drmContentProtection = prot,
                contentId = cId,
                psshData = pssh
            ) { keySetId ->
                runOnUiThread {
                    currentTempPlayer?.release()
                    currentTempPlayer = null
                    if (keySetId != null) {
                        startOfflineDownload(vd, keySetId)
                        Toast.makeText(this, "Скачивание запущено", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Ошибка загрузки лицензии", Toast.LENGTH_SHORT).show()
                    }
                    downloadingIds.remove(cId)
                    adapter?.notifyDataSetChanged()
                }
            }
        }

        val player = ExoPlayer.Builder(this).build()
        currentTempPlayer = player
        drmHelper.setOfflineDownloadPending(videoData)
        val mediaItem = MediaItem.Builder()
            .setUri(videoData.hlsLink)
            .setDrmUuid(C.WIDEVINE_UUID)
            .setDrmLicenseUri(licenseUrl)
            .setDrmMultiSession(true)
            .build()
        player.setMediaItem(mediaItem)
        drmHelper.attachToPlayer(player)
        player.prepare()
    }

    private fun startOfflineDownload(videoData: VideoData, keySetId: ByteArray) {
        if (videoData.hlsLink.isBlank()) return
        val contentId = generateStableContentId(videoData.hlsLink)
        val existing = VideoDownloadManager.getDownloadById(contentId)
        if (existing != null && existing.state == Download.STATE_COMPLETED) return
        try {
            val videoDataJson = try {
                AppJson.encodeToString(VideoData.serializer(), videoData)
            } catch (e: Exception) {
                Log.e(TAG, "serialize VideoData", e)
                "{}"
            }
            val req = DownloadRequest.Builder(contentId, videoData.hlsLink.toUri())
                .setMimeType(MimeTypes.APPLICATION_M3U8)
                .setData(videoDataJson.toByteArray(StandardCharsets.UTF_8))
                .setKeySetId(keySetId)
                .build()
            VideoDownloadService.startDownload(this, req)
        } catch (e: Exception) {
            Log.e(TAG, "startOfflineDownload", e)
            Toast.makeText(this, "Ошибка начала загрузки", Toast.LENGTH_SHORT).show()
        }
    }

    private fun generateStableContentId(url: String?): String {
        if (url.isNullOrBlank()) return UUID.randomUUID().toString()
        return try {
            val stable = url.substringBefore("?")
            val digest = MessageDigest.getInstance("SHA-256").digest(stable.toByteArray())
            Base64.encodeToString(digest, Base64.NO_WRAP or Base64.NO_PADDING)
        } catch (e: Exception) {
            UUID.randomUUID().toString()
        }
    }

    private class Adapter(
        private val items: List<VideoData>,
        private val downloadingIds: Set<String>,
        private val onDownloadClick: (VideoData) -> Unit
    ) : RecyclerView.Adapter<Adapter.VH>() {

        class VH(view: View) : RecyclerView.ViewHolder(view) {
            val title: TextView = view.findViewById(R.id.title)
            val drmBadge: TextView = view.findViewById(R.id.drmBadge)
            val btnDownload: Button = view.findViewById(R.id.btnDownload)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_add_drm_download, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val v = items[position]
            holder.title.text = v.title
            holder.drmBadge.visibility =
                if (v.drm?.widevine?.licenseUrl.isNullOrBlank()) View.GONE else View.VISIBLE
            val contentId = try {
                val stable = v.hlsLink.substringBefore("?")
                val digest = MessageDigest.getInstance("SHA-256").digest(stable.toByteArray())
                Base64.encodeToString(digest, Base64.NO_WRAP or Base64.NO_PADDING)
            } catch (_: Exception) {
                ""
            }
            val downloading = contentId in downloadingIds
            holder.btnDownload.isEnabled = !downloading
            holder.btnDownload.text = if (downloading) "…" else "Скачать"
            holder.btnDownload.setOnClickListener { onDownloadClick(v) }
        }

        override fun getItemCount(): Int = items.size
    }

    companion object {
        private const val TAG = "AddDrmDownload"
    }
}
