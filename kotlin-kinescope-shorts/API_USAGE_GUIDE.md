# Kinescope API Usage Guide

## Overview

The library provides the `KinescopeVideoProvider` interface to connect to the Kinescope API. You need to implement this interface yourself.

## Step 1: Implementing KinescopeVideoProvider

Create a class that implements `KinescopeVideoProvider`:

```kotlin
import com.kotlin.kinescope.shorts.interfaces.KinescopeVideoProvider
import com.kotlin.kinescope.shorts.models.VideoData
import com.kotlin.kinescope.shorts.models.DrmInfo
import com.kotlin.kinescope.shorts.models.WidevineInfo

class MyKinescopeVideoProvider(
    private val apiToken: String
) : KinescopeVideoProvider {
    
    // Your API client (Retrofit, Ktor, etc.)
    private val apiService = createApiService()
    
    override suspend fun loadVideo(videoId: String): VideoData? {
        return try {
            val response = apiService.getVideo(videoId)
            convertToVideoData(response.data)
        } catch (e: Exception) {
            Log.e("MyKinescopeVideoProvider", "Error loading video: $videoId", e)
            null
        }
    }
    
    override suspend fun loadVideos(
        projectId: String?,
        folderId: String?,
        limit: Int,
        offset: Int
    ): List<VideoData> {
        return try {
            val response = apiService.getVideos(projectId, folderId, limit, offset)
            response.data.mapNotNull { convertToVideoData(it) }
        } catch (e: Exception) {
            Log.e("MyKinescopeVideoProvider", "Error loading videos", e)
            emptyList()
        }
    }
    
    private fun convertToVideoData(kinescopeData: KinescopeVideoData): VideoData? {
        val hlsLink = kinescopeData.hls_link ?: kinescopeData.player?.hls ?: return null
        
        val drm = kinescopeData.drm?.widevine?.licenseUrl?.let { url ->
            DrmInfo(widevine = WidevineInfo(licenseUrl = url))
        }
        
        return VideoData(
            hlsLink = hlsLink,
            drm = drm,
            title = kinescopeData.title ?: "Untitled",
            subtitle = kinescopeData.subtitle,
            description = kinescopeData.description
        )
    }
}
```

## Step 2: Example with Retrofit

If you use Retrofit:

```kotlin
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Header

// API interface
interface KinescopeApiService {
    @GET("v1/vod/{videoId}")
    suspend fun getVideo(
        @Path("videoId") videoId: String,
        @Header("Authorization") token: String? = null
    ): KinescopeVideoResponse
    
    @GET("v1/vod")
    suspend fun getVideos(
        @Query("project_id") projectId: String? = null,
        @Query("folder_id") folderId: String? = null,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0,
        @Header("Authorization") token: String? = null
    ): KinescopeVideoListResponse
}

// Provider implementation
class RetrofitKinescopeVideoProvider(
    private val apiToken: String
) : KinescopeVideoProvider {
    
    private val retrofit = Retrofit.Builder()
        .baseUrl("https://api.kinescope.io/")
        .addConverterFactory(/* your converter */)
        .build()
    
    private val apiService = retrofit.create(KinescopeApiService::class.java)
    
    override suspend fun loadVideo(videoId: String): VideoData? {
        return try {
            val response = apiService.getVideo(videoId, "Bearer $apiToken")
            convertToVideoData(response.data)
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
        return try {
            val response = apiService.getVideos(projectId, folderId, limit, offset, "Bearer $apiToken")
            response.data.mapNotNull { convertToVideoData(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    // ... convertToVideoData
}
```

## Step 3: Using with KinescopeUrls

After implementing the provider, use it with `KinescopeUrls`:

```kotlin
import com.kotlin.kinescope.shorts.utils.KinescopeUrls
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private fun loadVideos() {
    CoroutineScope(Dispatchers.Main).launch {
        try {
            // Create provider
            val videoProvider = MyKinescopeVideoProvider(apiToken = "your-token")
            
            // Use with KinescopeUrls
            val kinescopeVideo = KinescopeUrls(
                videoProvider = videoProvider,
                projectId = "your-project-id",
                folderId = "your-folder-id" // optional
            )
            
            // Load videos
            val videos = kinescopeVideo.getVideosFromApi()
            
            if (videos.isEmpty()) {
                // Fallback to hardcoded if API returns empty list
                val fallbackVideos = kinescopeVideo.getNextVideoUrls()
                setupViewPager(fallbackVideos)
            } else {
                setupViewPager(videos)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error loading videos", e)
            // Fallback to hardcoded on error
            val kinescopeVideo = KinescopeUrls()
            val fallbackVideos = kinescopeVideo.getNextVideoUrls()
            setupViewPager(fallbackVideos)
        }
    }
}
```

## Step 4: Loading a single video by ID

```kotlin
lifecycleScope.launch {
    val videoProvider = MyKinescopeVideoProvider(apiToken = "your-token")
    val kinescopeVideo = KinescopeUrls(videoProvider = videoProvider)
    
    // Get one video by ID
    val video = kinescopeVideo.getVideoById("b4081a51-e5a3-4586-8e6a-72e2f3eb3075")
    
    video?.let {
        // Use the video
        player.loadVideo(it)
    }
}
```

## Where to get the parameters

### API Token
1. Go to the [Kinescope dashboard](https://kinescope.io/)
2. Open project settings
3. Find the "API" or "Tokens" section
4. Create a new token or use an existing one

**Note:** The token is optional if your videos are public.

### Project ID
1. In the Kinescope dashboard
2. Open your project
3. Project ID is usually in the URL or project settings
4. Format: `750f771f-92aa-433d-9b13-6979c38a5d6c`

### Video ID
1. Open the video in Kinescope
2. Video ID is in the video URL
3. Format: `b4081a51-e5a3-4586-8e6a-72e2f3eb3075`

## API Endpoints

Kinescope API endpoints:

- **Get video:** `GET https://api.kinescope.io/v1/vod/{videoId}`
- **Video list:** `GET https://api.kinescope.io/v1/vod?project_id={projectId}&limit={limit}&offset={offset}`
- **Video list by folder:** `GET https://api.kinescope.io/v1/vod?folder_id={folderId}&limit={limit}&offset={offset}`

## Error handling

```kotlin
lifecycleScope.launch {
    try {
        val videoProvider = MyKinescopeVideoProvider(apiToken)
        val kinescopeVideo = KinescopeUrls(videoProvider = videoProvider, projectId = "your-project-id")
        val videos = kinescopeVideo.getVideosFromApi()
        
        when {
            videos.isEmpty() -> {
                // No videos
                showMessage("No videos found")
            }
            else -> {
                // Loaded successfully
                adapter.updateVideos(videos)
            }
        }
    } catch (e: Exception) {
        // Error handling
        Log.e("API", "Error loading videos", e)
        showError("Failed to load videos: ${e.message}")
        
        // Fallback to hardcoded
        val fallback = KinescopeUrls().getNextVideoUrls()
        adapter.updateVideos(fallback)
    }
}
```

## Dependencies

To implement the provider you need:

- HTTP client (Retrofit, Ktor, OkHttp, etc.)
- JSON parser (Kotlinx Serialization, Gson, etc.)
- Coroutines for async work

Example with Retrofit and Kotlinx Serialization:

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.1")
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
}
```

## Debugging

Add logging in your provider for API debugging:

```kotlin
override suspend fun loadVideo(videoId: String): VideoData? {
    return try {
        Log.d("MyKinescopeVideoProvider", "Loading video: $videoId")
        val response = apiService.getVideo(videoId)
        Log.d("MyKinescopeVideoProvider", "Video loaded: ${response.data.title}")
        convertToVideoData(response.data)
    } catch (e: Exception) {
        Log.e("MyKinescopeVideoProvider", "Error loading video: $videoId", e)
        null
    }
}
```
