package io.kinescope.sdk.shorts.interfaces

import io.kinescope.sdk.shorts.models.VideoData
import kotlinx.serialization.InternalSerializationApi

/**
 * Интерфейс для провайдера видео из Kinescope API.
 * Пользователи библиотеки должны реализовать этот интерфейс для подключения к API.
 */
@InternalSerializationApi
interface KinescopeVideoProvider {
    
    /**
     * Загрузить видео по ID
     * @param videoId ID видео в Kinescope
     * @return VideoData или null в случае ошибки
     */
    suspend fun loadVideo(videoId: String): VideoData?
    
    /**
     * Загрузить список видео проекта или папки
     * @param projectId ID проекта (опционально)
     * @param folderId ID папки (опционально, приоритет над projectId)
     * @param limit Лимит количества видео
     * @param offset Смещение для пагинации
     * @return Список VideoData
     */
    suspend fun loadVideos(
        projectId: String? = null,
        folderId: String? = null,
        limit: Int = 50,
        offset: Int = 0
    ): List<VideoData>
}

