# Kinescope API 404 troubleshooting

## Problem

All endpoints that return video lists respond with **HTTP 404**:
- `GET /v1/vod?project_id=...` → 404
- `GET /v1/projects/{projectId}/videos` → 404
- `GET /v1/projects/{projectId}/items` → 404
- `GET /v1/items?project_id=...` → 404

But:
- `GET /v1/projects` → **200 OK** (works)
- `GET /v1/projects/{projectId}` → needs checking

## Possible causes

1. **API has no video list endpoint** — you may need to fetch videos by ID
2. **Wrong request format** — different parameters may be required
3. **Different base URL** — API might be on another domain
4. **Insufficient token scope** — token may not have permission to list videos

## Solutions

### Solution 1: Fetch videos by ID (if you know the IDs)

If you know video IDs, fetch them one by one with `KinescopeUrls` and your `KinescopeVideoProvider`:

```kotlin
val kinescopeUrls = KinescopeUrls(videoProvider = myProvider)
val video1 = kinescopeUrls.getVideoById("video-id-1")
val video2 = kinescopeUrls.getVideoById("video-id-2")
// getVideoById is suspend — call from a coroutine
```

### Solution 2: Use project details

Try to get project details — the response might include a video list:

```kotlin
val projectDetail = apiService.getProject(projectId, apiToken)
// Check the response structure — it may contain a video list
```

### Solution 3: Check Kinescope API docs

Refer to the official Kinescope API documentation:
- [Developer documentation](https://kinescope.ru/teams/dev)
- Or contact Kinescope support

### Solution 4: Use the kotlin-kinescope-player library

Review the `kotlin-kinescope-player` source:
- [GitHub repository](https://github.com/kinescope/kotlin-kinescope-player)
- See how video fetching is implemented there

### Solution 5: Temporary workaround — use hardcoded data

While the API is unavailable, you can use hardcoded data:

```kotlin
val videos = KinescopeUrls().getNextVideoUrls() // Uses hardcoded list
```

## What to do next

1. **Run the app** and check logs — `/v1/items` or `/v1/projects/{projectId}` might work

2. **Inspect project details** — logs should show what `/v1/projects/{projectId}` returns

3. **If nothing works** — use the hardcoded workaround until you find the correct endpoint

## Next steps

After running the app, check logs for:
- Which endpoint worked (if any)
- What `/v1/projects/{projectId}` returns — it may include a video list
- The structure of the API response
