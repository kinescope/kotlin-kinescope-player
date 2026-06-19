# Dashboard REST API (player templates)

Use `KinescopeApiConfig.createApiHelper` to access `api.kinescope.io`. Pass your project API key from the [Kinescope dashboard](https://kinescope.io/).

```kotlin
import io.kinescope.sdk.api.KinescopeApiConfig
import io.kinescope.sdk.models.players.KinescopeCreatePlayerRequest
import io.kinescope.sdk.models.players.applyTo
import io.kinescope.sdk.models.players.toPlayerSettings
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

val apiHelper = KinescopeApiConfig.createApiHelper("your-api-key")

// List templates
lifecycleScope.launch {
    apiHelper.getPlayers()
        .catch { /* handle error */ }
        .collect { response ->
            val templates = response.data
        }
}

// Create template
val request = KinescopeCreatePlayerRequest(
    name = "My player",
    settings = kinescopePlayer.kinescopePlayerOptions.toPlayerSettings(),
)
apiHelper.createPlayer(request).collect { response ->
    val template = response.data
}

// Apply template settings to the player
template.settings?.applyTo(kinescopePlayer.kinescopePlayerOptions)
playerView.applyTemplateOptions()
```

## Available methods

| Method | Endpoint |
|--------|----------|
| `getAllVideos()` | `GET /v1/videos/` |
| `getPlayers()` | `GET /v1/players` |
| `getPlayer(id)` | `GET /v1/players/{id}` |
| `createPlayer(request)` | `POST /v1/players` |
| `updatePlayer(id, request)` | `PUT /v1/players/{id}` |
| `deletePlayer(id)` | `DELETE /v1/players/{id}` |

## Error helpers

```kotlin
import io.kinescope.sdk.api.isDashboardPlayerDeleteRestriction
import io.kinescope.sdk.api.readApiErrorMessage

catch { error ->
    val message = error.readApiErrorMessage()
    val isDashboardRestriction = error.isDashboardPlayerDeleteRestriction()
}
```

## HTTP response codes

| Code | Description | When it occurs |
|------|-------------|----------------|
| 200 | OK | Request completed successfully |
| 400 | Bad Request | Invalid request format or parameters |
| 401 | Unauthorized | Missing or invalid API token |
| 402 | Payment Required | Plan limits exceeded |
| 403 | Forbidden | No permission to perform the operation |
| 404 | Not Found | Resource not found |
| 422 | Unprocessable Entity | Data validation error |
| 429 | Too Many Requests | Rate limit exceeded |
| 500 | Internal Server Error | Server-side error |

## API error codes

| Code | Name | Description |
|------|------|-------------|
| 1 | `IO_ERROR` | Empty request body |
| 100101 | `AUTH_HEADER_NOT_FOUND` | `Authorization` header is missing |
| 100102 | `UNAUTHORIZED` | Invalid token |
| 100103 | `ACCESS_DENIED` | Access denied |
| 100106 | `LIMIT_REACHED` | Limit reached |
| 400101 | `JSON_SYNTAX_ERROR` | JSON syntax error |
| 400201 | `ALREADY_EXISTS` | Resource already exists |
| 402402 | `VALIDATION_ERROR` | Validation error |
| 404404 | `NOT_FOUND` | Resource not found |
| 500500 | `INTERNAL_ERROR` | Internal server error |

Use `readApiErrorMessage()` to parse the error body from failed requests.

## Pagination

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `page` | integer | `1` | Page number |
| `per_page` | integer | `10` | Items per page (max `100`) |

```bash
curl "https://api.kinescope.io/v1/videos?page=2&per_page=25" \
  -H "Authorization: Bearer YOUR_API_TOKEN"
```

`getAllVideos()` returns pagination metadata in `response.meta.pagination`. The built-in SDK method does not pass `page` / `per_page` yet — it fetches the default first page.

## API versions

| Version | Path | Status |
|---------|------|--------|
| v1 | `/v1/*` | Stable — used by `KinescopeApiHelper` (videos, players) |
| v2 | `/v2/*` | Stable — Live API (not wrapped by the SDK yet) |

For video catalog API details see [API_USAGE_GUIDE.md](../kotlin-kinescope-shorts/API_USAGE_GUIDE.md).
