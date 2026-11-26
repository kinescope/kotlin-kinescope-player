# Offline downloads

The player artifact (`io.kinescope:kotlin-kinescope-player`) includes an offline download pipeline: `VideoDownloadService` (declared in the library manifest) and [`DownloadVideoOffline`](../kotlin-kinescope-player/src/main/java/io/kinescope/sdk/download/DownloadVideoOffline.kt) as the entry point. All classes are in the `io.kinescope.sdk.download` package (before 0.1.22 some of them were in `io.kinescope.sdk.shorts.download` / `.drm`). The Shorts artifact has no offline downloads.

Download notification icon: set `VideoDownloadService.notificationIconResId = R.drawable.your_icon` before the first download.

## Basic flow

1. Initialize at app startup: `DownloadVideoOffline.initialize(context)`
2. Prefer [Quality selection](#quality-selection) so only one height is cached. A bare master-playlist `DownloadRequest` **without** stream keys downloads **all** variants.
3. For **DASH**: use `MimeTypes.APPLICATION_MPD` and the `.mpd` manifest URI with the same helpers.
4. Subscribe to changes: `DownloadVideoOffline.addDownloadListener(context, listener)`; in `onDestroy` call `removeDownloadListener(listener)`.
5. Playback: `download.request.toMediaItem()` + `DownloadVideoOffline.getDownloadCache(context)` with `CacheDataSource`; for DRM set `setDrmKeySetId`. For DASH use `DashMediaSource` with the same cache and keySetId. Offline `CacheDataSource` must use `upstream = null` and **must not** set `FLAG_IGNORE_CACHE_ON_ERROR`.

## Download cache

The download `SimpleCache` must use **`NoOpCacheEvictor`** (Media3 requirement for `DownloadManager`). An LRU size cap (e.g. 300 MB) deletes spans mid-download — progress appears to roll back — and can mark a download completed while segments are missing (intermittent Source error on open). Soft UI quotas are fine; automatic eviction of download spans is not.

## Quality selection

By default a bare `DownloadRequest` on a master playlist downloads **all** variants.
To download a **single** quality, list heights then build a filtered request:

```kotlin
DownloadVideoOffline.listDownloadQualities(
    context,
    hlsUri,
    qualityMap = listOf(
        OfflineDownloadQualityHelper.QualityMapHint(height = 720, name = "720p"),
        // from embed JSON quality_map — labels use height / short side / digits in name
    ),
) { result ->
    result.onSuccess { qualities ->
        val chosen = qualities.first() // or show a picker (labels from quality_map.name)
        DownloadVideoOffline.startDownloadWithQuality(
            context = context,
            contentId = contentId,
            manifestUri = hlsUri,
            videoHeightPx = chosen.height,
            data = metadata,
            keySetId = keySetId, // optional DRM
        )
    }
}
```

Low-level API: [`OfflineDownloadQualityHelper`](../kotlin-kinescope-player/src/main/java/io/kinescope/sdk/download/OfflineDownloadQualityHelper.kt)
(Media3 `DownloadHelper` + stream keys).

Offline playback must use `download.request.toMediaItem()` so `streamKeys` match the cached tracks.

## DRM offline keys flow (Widevine)

Offline keys are obtained via **Media3 `OfflineLicenseHelper`** and **Widevine CDM**. The app stores a **`keySetId`** (offline license identifier) and passes it to **`DownloadRequest`**.

```mermaid
flowchart
    subgraph meta["Video metadata"]
        VD["Widevine licenseUrl from your video metadata"]
    end

    subgraph pssh["PSSH extraction"]
        EP["ExoPlayer plays HLS/DASH with DRM"]
        DH["DrmHelper.attachToPlayer"]
        AN["AnalyticsListener / tracks / videoFormat"]
        EX["Extract SchemeData for C.WIDEVINE_UUID"]
        EP --> DH --> AN --> EX
    end

    subgraph lic["Offline license (keySetId)"]
        DC["DrmConfigurator.downloadOfflineLicense"]
        HCB["HttpMediaDrmCallback(licenseUrl)"]
        DSM["DefaultDrmSessionManager + FrameworkMediaDrm"]
        OLH["OfflineLicenseHelper.downloadLicense(format)"]
        NET["HTTPS → license server"]
        KS["keySetId: ByteArray"]
        DC --> HCB --> DSM --> OLH --> NET --> KS
    end

    subgraph store["App storage"]
        SP["SharedPreferences drm_licenses<br/>saveOfflineLicenseToStorage(contentId, keySetId)"]
    end

    subgraph dl["Segment download"]
        DR["DownloadRequest.setKeySetId(keySetId)"]
        DVO["DownloadVideoOffline.startDownload → VideoDownloadService"]
        KS --> SP
        KS --> DR --> DVO
    end

    VD --> HCB
    EX -->|"psshData"| DC
```

```mermaid
sequenceDiagram
    participant UI as Your Activity (e.g. AddDrmDownloadActivity)
    participant DH as DrmHelper
    participant XP as ExoPlayer
    participant DC as DrmConfigurator
    participant OLH as OfflineLicenseHelper
    participant LS as License URL
    participant SP as SharedPreferences
    participant DM as DownloadVideoOffline

    UI->>DH: setOfflineDownloadPending(metadata) / attachToPlayer
    XP->>DH: drmInitData from stream
    DH->>UI: callback(metadata, PSSH)

    UI->>DC: downloadOfflineLicense(..., psshData)
    DC->>OLH: downloadLicense(format with PSSH)
    OLH->>LS: HTTP license request/response
    OLH-->>DC: keySetId

    DC->>SP: saveOfflineLicenseToStorage(contentId, keySetId)
    UI->>DM: DownloadRequest.setKeySetId(keySetId), startDownload
```

| Step | Where in the project |
|------|----------------------|
| License URL | `DrmContentProtection.licenseUrl` → `HttpMediaDrmCallback` in [`DrmConfigurator`](../kotlin-kinescope-player/src/main/java/io/kinescope/sdk/download/DrmConfigurator.kt) |
| PSSH | [`DrmHelper<T>`](../kotlin-kinescope-player/src/main/java/io/kinescope/sdk/download/DrmHelper.kt) parses `DrmInitData` for `C.WIDEVINE_UUID` |
| Offline license request | `OfflineLicenseHelper.downloadLicense(format)` with `Format` containing `DrmInitData(SchemeData(WIDEVINE, psshData))` |
| Offline identifier | `keySetId` — CDM offline license; passed to `DownloadRequest` |
| Disk backup | `DrmConfigurator.saveOfflineLicenseToStorage` — stores `keySetId` in SharedPreferences by `contentId` |
| Start file download | `DownloadVideoOffline.startDownload` → `VideoDownloadService` |

## DRM download example

```kotlin
import io.kinescope.sdk.download.DownloadVideoOffline
import io.kinescope.sdk.download.DrmConfigurator
import io.kinescope.sdk.download.DrmContentProtection
import io.kinescope.sdk.download.DrmHelper

val drmConfigurator = DrmConfigurator(context)
// T — your metadata type (here MyVideo with id / hlsLink / licenseUrl)
val drmHelper = DrmHelper<MyVideo>(context, drmConfigurator) { video, pssh ->
    val protection = DrmContentProtection(
        schemeUri = C.WIDEVINE_UUID.toString(),
        licenseUrl = video.licenseUrl,
        schemeUuid = C.WIDEVINE_UUID,
    )
    drmConfigurator.downloadOfflineLicense(
        videoUrl = video.hlsLink,
        drmContentProtection = protection,
        contentId = video.id,
        psshData = pssh,
    ) { keySetId ->
        keySetId ?: return@downloadOfflineLicense
        val request = DownloadRequest.Builder(video.id, Uri.parse(video.hlsLink))
            .setMimeType(MimeTypes.APPLICATION_M3U8)
            .setKeySetId(keySetId)
            .build()
        DownloadVideoOffline.startDownload(context, request)
    }
}

drmHelper.setOfflineDownloadPending(video)
drmHelper.attachToPlayer(exoPlayer) // exoPlayer plays the DRM stream to get the PSSH
```

Full working example: `AddDrmDownloadActivity` in the demo app (`app/.../offlinedrm/`).

The app receives **`keySetId`** after a CDM exchange with the license server; playback and decryption are handled inside Widevine.
