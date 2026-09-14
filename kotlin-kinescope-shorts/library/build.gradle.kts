plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.24"
    id("com.vanniktech.maven.publish")
}

group = "io.kinescope"
version = "0.1.17"

android {
    namespace = "io.kinescope.sdk.shorts"
    compileSdk = 35

    defaultConfig {
        minSdk = 21
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")

    val media3Version = "1.8.0"
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-exoplayer-hls:$media3Version")
    implementation("androidx.media3:media3-ui:$media3Version")
    implementation("androidx.media3:media3-datasource:$media3Version")
    implementation("androidx.media3:media3-common:$media3Version")
    implementation("androidx.media3:media3-database:$media3Version")
    implementation("androidx.media3:media3-cast:$media3Version")

    // Poster thumbnails (thumbnailView)
    implementation("com.github.bumptech.glide:glide:4.16.0")

    implementation("androidx.collection:collection-ktx:1.5.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.activity:activity:1.8.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}

mavenPublishing {
    // Explicit Central Portal — default in 0.30.0 is legacy OSSRH (402 on stagingProfiles).
    publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()
    coordinates("io.kinescope", "kotlin-kinescope-shorts", "0.1.17")
    pom {
        name.set("kotlin-kinescope-shorts")
        description.set("Kinescope Shorts: vertical video feed for Android")
        inceptionYear.set("2022")
        url.set("https://github.com/kinescope/kotlin-kinescope-player")
        licenses {
            license {
                name.set("The Apache Software License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("kinescope")
                name.set("Kinescope")
                url.set("https://kinescope.io")
            }
        }
        scm {
            url.set("https://github.com/kinescope/kotlin-kinescope-player")
            connection.set("scm:git:git://github.com/kinescope/kotlin-kinescope-player.git")
            developerConnection.set("scm:git:ssh://git@github.com/kinescope/kotlin-kinescope-player.git")
        }
    }
}
