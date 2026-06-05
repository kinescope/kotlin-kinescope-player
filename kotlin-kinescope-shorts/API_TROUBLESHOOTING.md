# Kinescope API troubleshooting

## Scope of this document

Troubleshooting for **Dashboard API** (`KinescopeApiHelper`) and **Shorts** (`KinescopeVideoProvider`).

| Use case | Client | Endpoints | Documentation |
|----------|--------|-----------|---------------|
| Demo / player templates / Playlist | `KinescopeApiHelper` | `GET /v1/videos/`, `/v1/players` | [README — Dashboard REST API](../README.md) |
| Shorts vertical feed | `KinescopeVideoProvider` | `GET /v1/videos/` + `GET /{id}.json` | [API_USAGE_GUIDE.md](API_USAGE_GUIDE.md) |
| Player playback | `KinescopeVideoPlayer` | `GET /{id}.json` on `kinescope.io` | [README — Quick start](../README.md) |

> There is **no** `GET /v1/vod/{videoId}` on `api.kinescope.io` in the SDK. `/v1/vod/` appears only in **DRM license URLs** on `license.kinescope.io`.

If **Playlist test** or **Custom Player test** fail, check `KinescopeDemoConfig.API_KEY` first.

---

## HTTP 401 / 403 — unauthorized or forbidden

### Symptoms

- `GET /v1/videos/` or `/v1/players` returns **401** or **403**
- API error codes: `100101 AUTH_HEADER_NOT_FOUND`, `100102 UNAUTHORIZED`, `100103 ACCESS_DENIED`

### Solutions

1. Set a valid API key: `KinescopeApiConfig.createApiHelper("your-api-key")`
2. Pass header: `Authorization: Bearer YOUR_API_TOKEN`
3. Create or refresh the token in the [Kinescope dashboard](https://kinescope.io/)

---

## HTTP 404 — resource not found

### Symptoms

- **404** on `GET /v1/videos/` or `GET /v1/players/{id}`
- API error code: `404404 NOT_FOUND`

### Common causes

1. **Wrong endpoint** — do not use legacy paths such as `/v1/vod`, `/v1/projects/{id}/videos`, `/v1/items`. Use `GET /v1/videos/` for the catalog.
2. **Wrong video or player id** — check the id in the dashboard.
3. **Wrong base URL** — catalog and players: `https://api.kinescope.io/`; playback metadata: `https://kinescope.io/{id}.json`.

### Solutions

```kotlin
// Catalog (Playlist test)
apiHelper.getAllVideos().collect { response ->
    val videos = response.data // id + title
}

// Single video for Shorts or player
// GET https://kinescope.io/{video_id}.json?sdk=android
```

---

## Shorts feed: empty list or missing HLS

### Problem

`KinescopeUrls.getVideosFromApi()` returns an empty list or videos without playback URLs.

### Causes

1. `GET /v1/videos/` returns ids only — Shorts needs a second request to `/{video_id}.json` for `hlsLink`
2. Invalid or missing API key
3. Provider not implemented — `KinescopeUrls()` without provider uses hardcoded fallback

### Solutions

#### Solution 1: Use SDK clients in your provider

See [API_USAGE_GUIDE — SdkKinescopeVideoProvider](API_USAGE_GUIDE.md): catalog via `KinescopeApiHelper`, per-video metadata via `KinescopeFetch`.

#### Solution 2: Load videos by known IDs

```kotlin
val kinescopeUrls = KinescopeUrls(videoProvider = myProvider)
val video = kinescopeUrls.getVideoById("video-id-1")
// getVideoById is suspend — call from a coroutine
```

#### Solution 3: Temporary fallback — hardcoded list

```kotlin
val videos = KinescopeUrls().getNextVideoUrls()
```

---

## Related documentation

- Shorts API integration: [API_USAGE_GUIDE.md](API_USAGE_GUIDE.md)
- Dashboard API & player templates: [README.md](../README.md)
- HTTP codes & error codes: [API_USAGE_GUIDE.md](API_USAGE_GUIDE.md)
