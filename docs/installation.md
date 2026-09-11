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
    implementation 'io.kinescope:kotlin-kinescope-player:0.1.7'
}
```

Kotlin DSL:

```kotlin
dependencies {
    implementation("io.kinescope:kotlin-kinescope-player:0.1.7")
}
```

This single artifact includes the player, Shorts (`io.kinescope.sdk.shorts`), and offline download helpers.

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

No new releases or support are guaranteed on JitPack. Migrate to `io.kinescope:kotlin-kinescope-player:0.1.7` on Maven Central when you can.
