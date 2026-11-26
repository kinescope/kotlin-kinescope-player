# Installation

## Recommended — Maven Central

The SDK is published to **Maven Central** under the `io.kinescope` namespace.

**Step 1.** Ensure `mavenCentral()` is in your root `settings.gradle` / `build.gradle` (usually already present):

```groovy
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
```

**Step 2.** Add the dependency to your module:

```groovy
dependencies {
    // Player (+ offline downloads, DRM, Cast)
    implementation 'io.kinescope:kotlin-kinescope-player:0.1.22'

    // Vertical Shorts feed (optional)
    implementation 'io.kinescope:kotlin-kinescope-shorts:0.1.22'
}
```

Kotlin DSL:

```kotlin
dependencies {
    implementation("io.kinescope:kotlin-kinescope-player:0.1.22")
    implementation("io.kinescope:kotlin-kinescope-shorts:0.1.22") // optional
}
```

| Artifact | Contains |
|----------|----------|
| `kotlin-kinescope-player` | Player (`KinescopePlayerView`), offline downloads with DRM (`io.kinescope.sdk.download`), Cast, Dashboard API, server watermark |
| `kotlin-kinescope-shorts` | Vertical Shorts feed (`io.kinescope.sdk.shorts`) — online playback only |

The artifacts do not depend on each other: add one or both. Up to 0.1.21 the player artifact included Shorts; since 0.1.22 Shorts is added separately (see [CHANGELOG](../CHANGELOG.md)).

See [features.md](features.md) for what is included in the dependency.

---

## Legacy — JitPack (unsupported)

**JitPack is no longer supported** for new integrations. Prefer Maven Central above.

Existing apps that already resolve the SDK from JitPack can keep their current setup so builds do not break. Coordinates that were used historically:

```groovy
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}

dependencies {
    // Tag, commit SHA, or older JitPack version string — keep whatever you already pin
    implementation 'com.github.kinescope:kotlin-kinescope-player:<YOUR_EXISTING_VERSION>'
}
```

No new releases or support are guaranteed on JitPack. Migrate to `io.kinescope:kotlin-kinescope-player:0.1.22` on Maven Central when you can.
