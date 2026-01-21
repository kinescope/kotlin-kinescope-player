package com.kotlin.kinescope.shorts.utils

import com.kotlin.kinescope.shorts.interfaces.KinescopeVideoProvider
import com.kotlin.kinescope.shorts.models.DrmInfo
import com.kotlin.kinescope.shorts.models.VideoData
import com.kotlin.kinescope.shorts.models.WidevineInfo
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
                title = "Попугай",
                hlsLink = "https://kinescope.io/b4081a51-e5a3-4586-8e6a-72e2f3eb3075/master.m3u8?expires=1754387422&kinescope_project_id=750f771f-92aa-433d-9b13-6979c38a5d6c&sign=14b08ff6dd0453f1c10a13fd62f622f6&token=",
                drm = DrmInfo(
                    widevine = WidevineInfo(
                        licenseUrl = "https://license.kinescope.io/v1/vod/b4081a51-e5a3-4586-8e6a-72e2f3eb3075/acquire/widevine?token="
                    )
                ),
            ),


            VideoData(
                title = "Мужик",
                hlsLink = "https://kinescope.io/272e9a64-7010-4145-a733-325039a05e0c/master.m3u8?expires=1748253789&kinescope_project_id=750f771f-92aa-433d-9b13-6979c38a5d6c&sign=745ccad1dcaaebe0ac0e7b5a3dd182f6&token=",
                drm = DrmInfo(
                    widevine = WidevineInfo(
                        licenseUrl = "https://license.kinescope.io/v1/vod/272e9a64-7010-4145-a733-325039a05e0c/acquire/widevine?token="
                    )
                ),
            ),


            VideoData(
                title = "FGFFFFFF",
                hlsLink = "https://kinescope.io/b3dd7b40-d289-4b46-9f82-a208def84186/master.m3u8?expires=1754391359&kinescope_project_id=750f771f-92aa-433d-9b13-6979c38a5d6c&sign=d34b642478007bd1389c87187ea5e143&token=",
                drm = DrmInfo(
                    widevine = WidevineInfo(
                        licenseUrl = "https://license.kinescope.io/v1/vod/b3dd7b40-d289-4b46-9f82-a208def84186/acquire/widevine?token="
                    )
                ),
            ),


            VideoData(
                title = "АААА",
                hlsLink = "https://kinescope.io/4797386a-f42d-4c65-81a6-83431837a557/master.m3u8?expires=1750845912&token=",
                drm = DrmInfo(
                    widevine = WidevineInfo(
                        licenseUrl = ""
                    )
                ),
            ),


            VideoData(
                title = "АААА",
                hlsLink = "https://kinescope.io/0344b757-48b9-4635-b4d9-f4d6b1c9be4a/master.m3u8?expires=1750759686&kinescope_project_id=750f771f-92aa-433d-9b13-6979c38a5d6c&sign=2e95f57b101d5a0dd378dcbb4c2decf9&token=",
                drm = DrmInfo(
                    widevine = WidevineInfo(
                        licenseUrl = "https://license.kinescope.io/v1/vod/0344b757-48b9-4635-b4d9-f4d6b1c9be4a/acquire/widevine?token="
                    )
                ),
            ),

            VideoData(
                title = "АААА[222",
                hlsLink = "https://kinescope.io/29702b53-3f97-419e-8e45-c576ec1f8365/master.m3u8?expires=1750759482&kinescope_project_id=750f771f-92aa-433d-9b13-6979c38a5d6c&sign=a75a20302eab9e61aea76277bc597f5e&token=",
                drm = DrmInfo(
                    widevine = WidevineInfo(
                        licenseUrl = "https://license.kinescope.io/v1/vod/29702b53-3f97-419e-8e45-c576ec1f8365/acquire/widevine?token="
                    )
                ),
            ),


            VideoData(
                title = "АААА[222",
                hlsLink = "https://kinescope.io/76d22a2c-72bb-4e5c-9572-9cdc1a8e7104/master.m3u8?expires=1748522206&token=",
                drm = DrmInfo(
                    widevine = WidevineInfo(
                        licenseUrl = ""
                    )
                ),
            ),


            VideoData(
                title = "Кто быстрее?",
                hlsLink = "https://kinescope.io/b9352fb0-8348-4237-b497-eb6af98d3839/master.m3u8?expires=1748522310&token=",
                drm = DrmInfo(
                    widevine = WidevineInfo(
                        licenseUrl = ""
                    )
                ),
            ),


            VideoData(
                title = "АААА[222",
                hlsLink = "https://kinescope.io/d2ca2626-ccbd-47b8-ba13-cb3ce5b92740/master.m3u8?expires=1748522336&token=",
                drm = DrmInfo(
                    widevine = WidevineInfo(
                        licenseUrl = ""
                    )
                ),
            ),


            VideoData(
                title = "АААА[222",
                hlsLink = "https://kinescope.io/e8fc4c13-1b7b-45a0-bb90-c610e6e38b6a/master.m3u8?expires=1748436266&kinescope_project_id=750f771f-92aa-433d-9b13-6979c38a5d6c&sign=80aadae86eb9c369a497a0a51ff57f75&token=",
                drm = DrmInfo(
                    widevine = WidevineInfo(
                        licenseUrl = "https://license.kinescope.io/v1/vod/e8fc4c13-1b7b-45a0-bb90-c610e6e38b6a/acquire/widevine?token="
                    )
                ),
            ),

            VideoData(
                title = "АААА[222",
                hlsLink = "https://kinescope.io/c123436b-3f19-4584-90c9-168a3a76fec0/master.m3u8?expires=1750332889&token=",
                drm = DrmInfo(
                    widevine = WidevineInfo(
                        licenseUrl = ""
                    )
                ),
            ),


            VideoData(
                title = "АААА[222",
                hlsLink = "https://kinescope.io/6de2f75b-704e-419a-904b-7196af13925e/master.m3u8?expires=1750332937&token=",
                drm = DrmInfo(
                    widevine = WidevineInfo(
                        licenseUrl = ""
                    )
                ),
            ),


            VideoData(
                title = "АААА[222",
                hlsLink = "https://kinescope.io/d9b00d71-0445-4c61-ae28-c55b9c720841/master.m3u8?expires=1750333056&token=",
                drm = DrmInfo(
                    widevine = WidevineInfo(
                        licenseUrl = ""
                    )
                ),
            ),


            VideoData(
                title = "АААА[222",
                hlsLink = "https://kinescope.io/e26e1137-a8d1-43b0-84fa-bd2aa945f0db/master.m3u8?expires=1753189401&token=",
                drm = DrmInfo(
                    widevine = WidevineInfo(
                        licenseUrl = ""
                    )
                ),
            ),


            VideoData(
                title = "АААА[222",
                hlsLink = "https://kinescope.io/3ad9bd89-4627-4013-a370-b8a1cc3b9934/master.m3u8?expires=1753189440&token=",
                drm = DrmInfo(
                    widevine = WidevineInfo(
                        licenseUrl = ""
                    )
                ),
            ),


            VideoData(
                title = "АААА[222",
                hlsLink = "https://kinescope.io/0f2bd839-283f-4dde-b50e-4643c376789d/master.m3u8?expires=1753189463&token=",
                drm = DrmInfo(
                    widevine = WidevineInfo(
                        licenseUrl = ""
                    )
                ),
            ),




            VideoData(
                title = "АААА[222",
                hlsLink = "https://kinescope.io/e3b834a4-3972-43fd-b2b7-69120f77bf27/master.m3u8?expires=1753189514&token=",
                drm = DrmInfo(
                    widevine = WidevineInfo(
                        licenseUrl = ""
                    )
                ),
            ),


            VideoData(
                title = "АААА[222",
                hlsLink = "https://kinescope.io/a7ab95a3-c604-4f31-b27a-89134c14d21d/master.m3u8?expires=1753189540&token=",
                drm = DrmInfo(
                    widevine = WidevineInfo(
                        licenseUrl = ""
                    )
                ),
            ),


            VideoData(
                title = "АААА[222",
                hlsLink = "https://kinescope.io/46297253-3d62-418c-ade2-5bf250289160/master.m3u8?expires=1753189778&token=",
                drm = DrmInfo(
                    widevine = WidevineInfo(
                        licenseUrl = ""
                    )
                ),
            ),

            VideoData(
                title = "АААА[222",
                hlsLink = "https://kinescope.io/58f193e7-aa0d-4b89-9b9f-09b73c502b6d/master.m3u8?expires=1753189832&token=",
                drm = DrmInfo(
                    widevine = WidevineInfo(
                        licenseUrl = ""
                    )
                ),
            ),

            VideoData(
                title = "АААА[222",
                hlsLink = "https://kinescope.io/29c7a05f-10fc-42b4-be1e-447f5a71cd7f/master.m3u8?expires=1753189859&token=",
                drm = DrmInfo(
                    widevine = WidevineInfo(
                        licenseUrl = ""
                    )
                ),
            ),


            VideoData(
                title = "АААА[222",
                hlsLink = "https://kinescope.io/3bd74efa-5ffd-4e70-b64e-137e55586ae7/master.m3u8?expires=1753189917&token=",
                drm = DrmInfo(
                    widevine = WidevineInfo(
                        licenseUrl = ""
                    )
                ),
            ),

            VideoData(
                title = "АААА[222",
                hlsLink = "https://kinescope.io/480604d1-69fe-4745-b0fa-5dba9c689f6e/master.m3u8?expires=1753190168&token=",
                drm = DrmInfo(
                    widevine = WidevineInfo(
                        licenseUrl = ""
                    )
                ),
            ),

            VideoData(
                title = "АААА[222",
                hlsLink = "https://kinescope.io/0fc558ca-da8c-4946-9a0d-faea5bc3750a/master.m3u8?expires=1753190222&token=",
                drm = DrmInfo(
                    widevine = WidevineInfo(
                        licenseUrl = ""
                    )
                ),
            ),

            VideoData(
                title = "АААА[222",
                hlsLink = "https://kinescope.io/d1011ff4-73fa-417a-8a73-17292a278c61/master.m3u8?expires=1753190242&token=",
                drm = DrmInfo(
                    widevine = WidevineInfo(
                        licenseUrl = ""
                    )
                ),
            ),


        )
    }
}
