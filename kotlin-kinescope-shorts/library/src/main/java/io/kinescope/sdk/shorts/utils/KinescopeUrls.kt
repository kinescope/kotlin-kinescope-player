package io.kinescope.sdk.shorts.utils

import io.kinescope.sdk.shorts.interfaces.KinescopeVideoProvider
import io.kinescope.sdk.shorts.models.DrmInfo
import io.kinescope.sdk.shorts.models.VideoData
import io.kinescope.sdk.shorts.models.WidevineInfo
import kotlinx.serialization.InternalSerializationApi

@InternalSerializationApi
class KinescopeUrls(
    private val videoProvider: KinescopeVideoProvider? = null,
    private val projectId: String? = null,
    private val folderId: String? = null
) {

    suspend fun getVideosFromApi(): List<VideoData> {
        if (videoProvider == null) {
            return getNextVideoUrls()
        }
        
        return try {
            videoProvider.loadVideos(projectId = projectId, folderId = folderId, limit = 50, offset = 0)
        } catch (e: Exception) {
            getNextVideoUrls()
        }
    }

    suspend fun getVideoById(videoId: String): VideoData? {
        if (videoProvider == null) {
            return null
        }
        
        return try {
            videoProvider.loadVideo(videoId)
        } catch (e: Exception) {
            null
        }
    }

    fun getNextVideoUrls(): List<VideoData> {
        return listOf(
            VideoData(
                title = "Попугай (DRM)",
                hlsLink = "https://kinescope.io/b4081a51-e5a3-4586-8e6a-72e2f3eb3075/master.m3u8?expires=1754387422&kinescope_project_id=750f771f-92aa-433d-9b13-6979c38a5d6c&sign=14b08ff6dd0453f1c10a13fd62f622f6&token=",
                drm = DrmInfo(
                    widevine = WidevineInfo(
                        licenseUrl = "https://license.kinescope.io/v1/vod/b4081a51-e5a3-4586-8e6a-72e2f3eb3075/acquire/widevine?token="
                    )
                ),
            ),


            VideoData(
                title = "Мужик (DRM)",
                hlsLink = "https://kinescope.io/272e9a64-7010-4145-a733-325039a05e0c/master.m3u8?expires=1748253789&kinescope_project_id=750f771f-92aa-433d-9b13-6979c38a5d6c&sign=745ccad1dcaaebe0ac0e7b5a3dd182f6&token=",
                drm = DrmInfo(
                    widevine = WidevineInfo(
                        licenseUrl = "https://license.kinescope.io/v1/vod/272e9a64-7010-4145-a733-325039a05e0c/acquire/widevine?token="
                    )
                ),
            ),

            VideoData(
                title = " Машина (NO DRM)",
                hlsLink = "https://kinescope.io/4797386a-f42d-4c65-81a6-83431837a557/master.m3u8?expires=1750845912&token=",
                drm = DrmInfo(
                    widevine = WidevineInfo(
                        licenseUrl = ""
                    )
                ),
            ),


            VideoData(
                title = "Девушка (DRM)",
                hlsLink = "https://kinescope.io/0344b757-48b9-4635-b4d9-f4d6b1c9be4a/master.m3u8?expires=1750759686&kinescope_project_id=750f771f-92aa-433d-9b13-6979c38a5d6c&sign=2e95f57b101d5a0dd378dcbb4c2decf9&token=",
                drm = DrmInfo(
                    widevine = WidevineInfo(
                        licenseUrl = "https://license.kinescope.io/v1/vod/0344b757-48b9-4635-b4d9-f4d6b1c9be4a/acquire/widevine?token="
                    )
                ),
            ),

            VideoData(
                title = "Время (DRM)",
                hlsLink = "https://kinescope.io/29702b53-3f97-419e-8e45-c576ec1f8365/master.m3u8?expires=1750759482&kinescope_project_id=750f771f-92aa-433d-9b13-6979c38a5d6c&sign=a75a20302eab9e61aea76277bc597f5e&token=",
                drm = DrmInfo(
                    widevine = WidevineInfo(
                        licenseUrl = "https://license.kinescope.io/v1/vod/29702b53-3f97-419e-8e45-c576ec1f8365/acquire/widevine?token="
                    )
                ),
            ),


            VideoData(
                title = "Кто быстрее? (NO DRM)",
                hlsLink = "https://kinescope.io/b9352fb0-8348-4237-b497-eb6af98d3839/master.m3u8?expires=1748522310&token=",
                drm = DrmInfo(
                    widevine = WidevineInfo(
                        licenseUrl = ""
                    )
                ),
            ),


            VideoData(
                title = "Велосипедисты (NO DRM)",
                hlsLink = "https://kinescope.io/d2ca2626-ccbd-47b8-ba13-cb3ce5b92740/master.m3u8?expires=1748522336&token=",
                drm = DrmInfo(
                    widevine = WidevineInfo(
                        licenseUrl = ""
                    )
                ),
            ),


        )
    }
}
