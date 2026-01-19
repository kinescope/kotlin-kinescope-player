package io.kinescope.sdk.shorts.view

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.media3.exoplayer.offline.Download
import androidx.recyclerview.widget.RecyclerView
import io.kinescope.sdk.shorts.AppJson
import io.kinescope.sdk.R
import io.kinescope.sdk.shorts.download.VideoDownloadManager
import io.kinescope.sdk.shorts.models.VideoData
import kotlinx.serialization.InternalSerializationApi
import kotlin.compareTo
import kotlin.div

@InternalSerializationApi
@OptIn(androidx.media3.common.util.UnstableApi::class)
class DownloadPopupAdapter(
    private val context: Context,
    private var downloads: MutableList<Download>,
) : RecyclerView.Adapter<DownloadPopupAdapter.ViewHolder>() {

    private var onItemClicked: ((Download) -> Unit)? = null
    private var onDeleteClicked: ((Download) -> Unit)? = null

    private val progressUpdateHandler = Handler(Looper.getMainLooper())
    private var isProgressUpdateRunning = false
    private val downloadSpeeds = mutableMapOf<String, Pair<Long, Long>>()
    private val previousProgress = mutableMapOf<String, Pair<Int, Long>>()
    private val isFirstUpdate = mutableMapOf<String, Boolean>()
    private val animatedProgress = mutableMapOf<String, Int>()
    private val activeAnimators = mutableMapOf<String, android.animation.ValueAnimator>()

    fun setOnItemClicked(listener: (Download) -> Unit) {
        onItemClicked = listener
    }

    fun setOnDeleteClicked(listener: (Download) -> Unit) {
        onDeleteClicked = listener
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val titleVideo: TextView = view.findViewById(R.id.titleVideo)
        val deleteBtn: ImageButton = view.findViewById(R.id.btnDelete)
        val progressBar: ProgressBar = view.findViewById(R.id.progressBar)
        val progressText: TextView = view.findViewById(R.id.progressText)
        val downloadStatus: TextView = view.findViewById(R.id.downloadStatus)
        val storageInfo: TextView = view.findViewById(R.id.storageInfo)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_download_popup, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val download = downloads[position]
        bindDownload(holder, download, position)
    }

    private fun bindDownload(holder: ViewHolder, download: Download, position: Int) {
        // Устанавливаем заголовок (только один раз)
        if (holder.titleVideo.tag != download.request.id) {
            holder.titleVideo.text = try {
                val savedData = AppJson.decodeFromString<VideoData>(String(download.request.data))
                savedData.title ?: "Без названия"
            } catch (e: Exception) {
                "Видео ${position + 1}"
            }
            holder.titleVideo.tag = download.request.id
        }

        updateDownloadProgress(holder, download)

        if (position == 0) {
            updateStorageInfo(holder)
        } else {
            holder.storageInfo.visibility = View.GONE
        }

        holder.itemView.setOnClickListener {
            onItemClicked?.invoke(download)
        }
        holder.deleteBtn.setOnClickListener {
            onDeleteClicked?.invoke(download)
        }
    }

    private fun updateDownloadProgress(holder: ViewHolder, download: Download) {
        val downloadId = download.request.id
        val (currentPercent, currentBytes) = VideoDownloadManager.getDownloadProgress(download)

        // Инициализируем флаг первого обновления
        if (!isFirstUpdate.containsKey(downloadId)) {
            isFirstUpdate[downloadId] = true
            animatedProgress[downloadId] = currentPercent
        }

        val previous = previousProgress[downloadId]
        val shouldUpdate = previous == null ||
                currentPercent != previous.first ||
                download.state != Download.STATE_DOWNLOADING ||
                Math.abs(currentBytes - previous.second) > 50 * 1024

        if (!shouldUpdate) {
            return
        }

        previousProgress[downloadId] = Pair(currentPercent, currentBytes)

        when (download.state) {
            Download.STATE_DOWNLOADING -> {
                val totalSize = download.contentLength

                // Скрываем progressbar и progressText
                holder.progressBar.visibility = View.GONE
                holder.progressText.visibility = View.GONE
                holder.downloadStatus.visibility = View.VISIBLE

                val downloadedMB = currentBytes / (1024 * 1024)
                val totalMB = if (totalSize > 0) totalSize / (1024 * 1024) else 0
                val speed = calculateDownloadSpeed(downloadId, currentBytes)

                // Показываем размер видео, если он известен
                val newStatusText = if (totalSize > 0) {
                    "Скачивается: ${downloadedMB}MB / ${totalMB}MB ($speed)"
                } else {
                    "Скачивается: ${downloadedMB}MB ($speed)"
                }
                if (holder.downloadStatus.text != newStatusText) {
                    holder.downloadStatus.text = newStatusText
                }

                // Удаляем из отслеживания скорости
                downloadSpeeds.remove(downloadId)
                previousProgress.remove(downloadId)
            }

            Download.STATE_COMPLETED -> {
                // Скрываем progressbar и progressText
                holder.progressBar.visibility = View.GONE
                holder.progressText.visibility = View.GONE
                holder.downloadStatus.visibility = View.VISIBLE

                val downloadedMB = currentBytes / (1024 * 1024)
                val totalMB = if (download.contentLength > 0) download.contentLength / (1024 * 1024) else downloadedMB
                val statusText = "Завершено: ${downloadedMB}MB (размер: ${totalMB}MB)"
                if (holder.downloadStatus.text != statusText) {
                    holder.downloadStatus.text = statusText
                }
            }

            Download.STATE_FAILED -> {
                holder.progressBar.visibility = View.GONE
                holder.progressText.visibility = View.GONE

                if (holder.downloadStatus.text != "Ошибка загрузки") {
                    holder.downloadStatus.text = "Ошибка загрузки"
                }

                // Удаляем из отслеживания скорости
                downloadSpeeds.remove(downloadId)
                previousProgress.remove(downloadId)
            }

            else -> {
                holder.progressBar.visibility = View.GONE
                holder.progressText.visibility = View.GONE
                val stateText = "Статус: ${getStateText(download.state)}"

                if (holder.downloadStatus.text != stateText) {
                    holder.downloadStatus.text = stateText
                }
            }
        }
    }
    private fun animateProgress(
        holder: ViewHolder,
        downloadId: String,
        start: Int,
        end: Int,
        currentBytes: Long
    ) {
        if (start == end) {
            animatedProgress[downloadId] = end
            return
        }

        // Останавливаем предыдущую анимацию для этого downloadId, если она есть
        activeAnimators[downloadId]?.cancel()

        // Используем более короткую анимацию для плавности при частых обновлениях
        val duration = if (Math.abs(end - start) > 10) 300L else 150L

        val progressAnimator = android.animation.ValueAnimator.ofInt(start, end)
        progressAnimator.duration = duration
        progressAnimator.interpolator = android.view.animation.LinearInterpolator()
        progressAnimator.addUpdateListener { animation ->
            val animatedValue = animation.animatedValue as Int
            animatedProgress[downloadId] = animatedValue
            holder.progressBar.progress = animatedValue
            holder.progressText.text = "$animatedValue%"
        }
        progressAnimator.addListener(object : android.animation.Animator.AnimatorListener {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                activeAnimators.remove(downloadId)
                // Убеждаемся, что финальное значение установлено
                animatedProgress[downloadId] = end
                holder.progressBar.progress = end
                holder.progressText.text = "$end%"
            }
            override fun onAnimationStart(animation: android.animation.Animator) {}
            override fun onAnimationCancel(animation: android.animation.Animator) {
                activeAnimators.remove(downloadId)
            }
            override fun onAnimationRepeat(animation: android.animation.Animator) {}
        })

        activeAnimators[downloadId] = progressAnimator
        progressAnimator.start()
    }
    private fun calculateDownloadSpeed(downloadId: String, bytesDownloaded: Long): String {
        val now = System.currentTimeMillis()
        val previous = downloadSpeeds[downloadId]

        val speed = if (previous != null) {
            val bytesDiff = bytesDownloaded - previous.first
            val timeDiff = now - previous.second
            if (timeDiff > 0) bytesDiff * 1000 / timeDiff else 0
        } else {
            0
        }

        downloadSpeeds[downloadId] = Pair(bytesDownloaded, now)

        return when {
            speed > 1024 * 1024 -> "${speed / (1024 * 1024)} MB/s"
            speed > 1024 -> "${speed / 1024} KB/s"
            else -> "$speed B/s"
        }
    }

    fun updateDownloadsList(newDownloads: List<Download>) {
        // Сохраняем текущие данные анимации
        val oldAnimatedProgress = animatedProgress.toMap()
        val oldFirstUpdate = isFirstUpdate.toMap()

        val oldList = downloads.toList()
        downloads.clear()
        downloads.addAll(newDownloads)

        newDownloads.forEach { download ->
            val downloadId = download.request.id
            if (oldAnimatedProgress.containsKey(downloadId)) {
                animatedProgress[downloadId] = oldAnimatedProgress[downloadId]!!
            }
            if (oldFirstUpdate.containsKey(downloadId)) {
                isFirstUpdate[downloadId] = oldFirstUpdate[downloadId]!!
            }
        }
        if (oldList.size == newDownloads.size) {
            for (i in downloads.indices) {
                if (i < oldList.size) {
                    val oldDownload = oldList[i]
                    val newDownload = downloads[i]

                    if (oldDownload.request.id != newDownload.request.id ||
                        oldDownload.state != newDownload.state ||
                        oldDownload.bytesDownloaded != newDownload.bytesDownloaded) {
                        notifyItemChanged(i)
                    }
                }
            }
        } else {
            notifyDataSetChanged()
        }
    }

    fun updateDownloadItem(downloadId: String) {
        val index = downloads.indexOfFirst { it.request.id == downloadId }
        if (index != -1) {
            try {
                val updatedDownload = VideoDownloadManager.getUpdatedDownload(downloadId)
                if (updatedDownload != null) {
                    val oldDownload = downloads[index]

                    // Обновляем только если есть изменения
                    if (oldDownload.state != updatedDownload.state ||
                        oldDownload.bytesDownloaded != updatedDownload.bytesDownloaded ||
                        oldDownload.contentLength != updatedDownload.contentLength) {

                        downloads[index] = updatedDownload

                        // Для активных загрузок обновляем напрямую через ViewHolder без notifyItemChanged
                        // Это предотвращает перерисовку и прерывание анимации
                        if (updatedDownload.state == Download.STATE_DOWNLOADING) {
                            val holder = (recyclerView?.findViewHolderForAdapterPosition(index) as? ViewHolder)
                            if (holder != null) {
                                // Обновляем прогресс напрямую без перерисовки ViewHolder
                                updateDownloadProgressDirectly(holder, updatedDownload)
                            } else {
                                // Если ViewHolder не найден, используем стандартное обновление
                                notifyItemChanged(index)
                            }
                        } else {
                            // Для неактивных загрузок используем стандартное обновление
                            notifyItemChanged(index)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("DownloadAdapter", "Error updating download item: $downloadId", e)
            }
        }
    }

    // Прямое обновление прогресса без перерисовки ViewHolder
    private fun updateDownloadProgressDirectly(holder: ViewHolder, download: Download) {
        val downloadId = download.request.id
        val (currentPercent, currentBytes) = VideoDownloadManager.getDownloadProgress(download)
        val totalSize = download.contentLength

        // Скрываем progressbar и progressText
        holder.progressBar.visibility = View.GONE
        holder.progressText.visibility = View.GONE
        holder.downloadStatus.visibility = View.VISIBLE

        // Обновляем текст статуса
        val downloadedMB = currentBytes / (1024 * 1024)
        val totalMB = if (totalSize > 0) totalSize / (1024 * 1024) else 0
        val speed = calculateDownloadSpeed(downloadId, currentBytes)

        // Показываем размер видео, если он известен
        val newStatusText = if (totalSize > 0) {
            "Скачивается: ${downloadedMB}MB / ${totalMB}MB ($speed)"
        } else {
            "Скачивается: ${downloadedMB}MB ($speed)"
        }
        if (holder.downloadStatus.text != newStatusText) {
            holder.downloadStatus.text = newStatusText
        }

        // Сохраняем текущий прогресс
        previousProgress[downloadId] = Pair(currentPercent, currentBytes)
    }

    private var recyclerView: RecyclerView? = null

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        this.recyclerView = recyclerView
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        this.recyclerView = null
        stopProgressUpdates()
    }

    private fun getStateText(state: Int): String {
        return when (state) {
            Download.STATE_STOPPED -> "Остановлено"
            Download.STATE_FAILED -> "Ошибка"
            else -> "Неизвестно"
        }
    }

    private fun updateStorageInfo(holder: ViewHolder) {
        val newStorageInfo = VideoDownloadManager.getStorageInfo(context)
        if (holder.storageInfo.text != newStorageInfo) {
            holder.storageInfo.text = newStorageInfo
        }
        holder.storageInfo.visibility = View.VISIBLE
    }

    override fun getItemCount() = downloads.size

    // Запуск автоматического обновления прогресса
    fun startProgressUpdates() {
        if (!isProgressUpdateRunning) {
            isProgressUpdateRunning = true
            progressUpdateHandler.post(progressUpdateRunnable)
            Log.d("DownloadAdapter", "Progress updates started")
        }
    }

    // Остановка автоматического обновления
    fun stopProgressUpdates() {
        isProgressUpdateRunning = false
        progressUpdateHandler.removeCallbacks(progressUpdateRunnable)
        // Останавливаем все активные анимации
        activeAnimators.values.forEach { it.cancel() }
        activeAnimators.clear()
        downloadSpeeds.clear()
        previousProgress.clear()
        isFirstUpdate.clear()
        animatedProgress.clear()
        Log.d("DownloadAdapter", "Progress updates stopped")
    }

    private val progressUpdateRunnable = object : Runnable {
        override fun run() {
            if (!isProgressUpdateRunning) return

            try {
                // Обновляем только активные загрузки с интервалом
                var hasActiveDownloads = false
                downloads.forEachIndexed { index, download ->
                    if (download.state == Download.STATE_DOWNLOADING || download.state == Download.STATE_QUEUED) {
                        updateDownloadItem(download.request.id)
                        hasActiveDownloads = true
                    }
                }

                // Если есть активные загрузки, продолжаем обновление, иначе останавливаем
                if (hasActiveDownloads) {
                    progressUpdateHandler.postDelayed(this, 1000) // 1 секунда
                } else {
                    // Нет активных загрузок - увеличиваем интервал
                    progressUpdateHandler.postDelayed(this, 3000) // 3 секунды
                }
            } catch (e: Exception) {
                Log.e("DownloadAdapter", "Error in progress update", e)
                progressUpdateHandler.postDelayed(this, 5000) // При ошибке ждем дольше
            }
        }
    }



}