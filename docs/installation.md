# Installation

**Step 1.** Add the JitPack repository to your root `settings.gradle` / `build.gradle`:

```groovy
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

**Step 2.** Add the dependency to your module. Replace `<LATEST_VERSION>` with the current version from the [JitPack badge](../README.md):

```groovy
dependencies {
    implementation 'com.github.kinescope:kotlin-kinescope-player:<LATEST_VERSION>'
}
```

One dependency includes the player, Shorts (`io.kinescope.sdk.shorts`), and the offline download pipeline. `VideoDownloadService` and required permissions are merged from the library manifest.
