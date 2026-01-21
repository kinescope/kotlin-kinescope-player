# Kinescope API Integration

The library provides interfaces to connect to the Kinescope API. You implement them yourself. When using `com.github.kinescope:kotlin-kinescope-player`, the `com.kotlin.kinescope.shorts` package is available along with the player.

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
    videoProvider = yourVideoProvider, // Your KinescopeVideoProvider implementation
    projectId = "your-project-id",
    folderId = "your-folder-id"
)

val videos = kinescopeVideo.getVideosFromApi()
```

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
            description = kinescopeData.description
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
    projectId = "your-project-id"
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

Kinescope API endpoints:

- **Get video:** `GET https://api.kinescope.io/v1/vod/{videoId}`
- **Video list:** `GET https://api.kinescope.io/v1/vod?project_id={projectId}&limit={limit}&offset={offset}`
- **Video list by folder:** `GET https://api.kinescope.io/v1/vod?folder_id={folderId}&limit={limit}&offset={offset}`

## Dependencies

To implement the provider you need:

- HTTP client (Retrofit, Ktor, OkHttp, etc.)
- JSON parser (Kotlinx Serialization, Gson, etc.)
- Coroutines for async operations

The library does not include these so you can choose your own stack.
