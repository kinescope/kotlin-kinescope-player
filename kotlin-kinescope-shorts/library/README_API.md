# Kinescope API Integration

The library provides interfaces to connect to the Kinescope API. You implement them yourself. When using `io.kinescope:kotlin-kinescope-player`, the `io.kinescope.sdk.shorts` package is available along with the player.

## Configuration (`KinescopeShortsConfig`)

```kotlin
import io.kinescope.sdk.shorts.KinescopeShortsConfig

KinescopeShortsConfig.API_KEY = "your-dashboard-api-token"
KinescopeShortsConfig.PROJECT_ID = null
KinescopeShortsConfig.FOLDER_ID = null
KinescopeShortsConfig.FEED_LIMIT = 50
```

Pass these values into your `KinescopeVideoProvider` / `KinescopeUrls` (see below).

A Dashboard OkHttp example (`ApiKinescopeVideoProvider`) is in the sample app module only — not in this library AAR.

## Architecture

### KinescopeVideoProvider interface

The library provides the `KinescopeVideoProvider` interface for connecting to the API:

```kotlin
interface KinescopeVideoProvider {
    suspend fun loadVideo(videoId: String): VideoData?
    suspend fun loadVideos(
        projectId: String? = null,
        folderId: String? = null,
        limit: Int = 50,
        offset: Int = 0
    ): List<VideoData>
}
```

### KinescopeUrls

The `KinescopeUrls` class uses the provider to fetch videos:

```kotlin
val kinescopeVideo = KinescopeUrls(
    videoProvider = yourVideoProvider,
    projectId = KinescopeShortsConfig.PROJECT_ID,
    folderId = KinescopeShortsConfig.FOLDER_ID,
    limit = KinescopeShortsConfig.FEED_LIMIT,
)

val videos = kinescopeVideo.getVideosFromApi()
```

`KinescopeUrls` is provider-only and does not ship with a built-in sample feed. `limit` maps to Dashboard `per_page` when the provider forwards it (demo / sample providers do).

## Implementing the provider

Implement `KinescopeVideoProvider` to connect to the API. Example:

```kotlin
class MyKinescopeVideoProvider(
    private val apiToken: String
) : KinescopeVideoProvider {
    
    private val apiService = // Your API client (Retrofit, Ktor, etc.)
    
    override suspend fun loadVideo(videoId: String): VideoData? {
        // Your implementation to load video by ID
        return try {
            val response = apiService.getVideo(videoId)
            convertToVideoData(response)
        } catch (e: Exception) {
            null
        }
    }
    
    override suspend fun loadVideos(
        projectId: String?,
        folderId: String?,
        limit: Int,
        offset: Int
    ): List<VideoData> {
        // Your implementation to load video list
        return try {
            val response = apiService.getVideos(projectId, folderId, limit, offset)
            response.data.map { convertToVideoData(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    private fun convertToVideoData(kinescopeData: KinescopeVideoData): VideoData {
        // Convert API data to VideoData
        return VideoData(
            hlsLink = kinescopeData.hls_link ?: kinescopeData.player?.hls ?: "",
            drm = kinescopeData.drm?.widevine?.licenseUrl?.let { url ->
                DrmInfo(widevine = WidevineInfo(licenseUrl = url))
            },
            title = kinescopeData.title ?: "Untitled",
            subtitle = kinescopeData.subtitle,
            description = kinescopeData.description,
            posterUrl = kinescopeData.player?.poster
        )
    }
}
```

## Usage

```kotlin
// Create provider
val videoProvider = MyKinescopeVideoProvider(apiToken = "your-token")

// Use with KinescopeUrls
val kinescopeVideo = KinescopeUrls(
    videoProvider = videoProvider,
    projectId = "your-project-id",
    folderId = "your-folder-id", // optional
    limit = 50,                  // optional, default 50
)

// Load videos
lifecycleScope.launch {
    val videos = kinescopeVideo.getVideosFromApi()
    // Use videos
}
```

## Data models

The library provides data models for the API:

- `VideoData` — main video model used by the library
- `KinescopeVideoResponse` — API response for a single video
- `KinescopeVideoListResponse` — API response for a video list
- `KinescopeVideoData` — video data from the API

## API Endpoints

Real endpoints used by the SDK (there is no `GET /v1/vod/{videoId}` on `api.kinescope.io`):

**Dashboard API** — `https://api.kinescope.io/`

- **Video catalog:** `GET /v1/videos/?page=&per_page=&project_id=&folder_id=` — `KinescopeApiHelper.getAllVideos(page, perPage, projectId, folderId)`
- **Player templates:** `GET/POST/PUT/DELETE /v1/players`

**Playback metadata** — `https://kinescope.io/`

- **Single video (HLS, metadata):** `GET /{video_id}.json?sdk=android` — `KinescopeFetch` / `KinescopeVideoPlayer.loadVideo()`

**DRM license** — `https://license.kinescope.io/`

- **Widevine license:** `GET /v1/vod/{video_id}/acquire/widevine?token=`

For Shorts, implement `KinescopeVideoProvider` using catalog + `/{video_id}.json`. See [API_USAGE_GUIDE.md](../../API_USAGE_GUIDE.md).

## Dependencies

To implement the provider you need:

- HTTP client (Retrofit, Ktor, OkHttp, etc.)
- JSON parser (Kotlinx Serialization, Gson, etc.)
- Coroutines for async operations

The library does not include these so you can choose your own stack.
