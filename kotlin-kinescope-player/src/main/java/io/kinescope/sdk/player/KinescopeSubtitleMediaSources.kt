package io.kinescope.sdk.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.dash.DashChunkSource
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.dash.DefaultDashChunkSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import androidx.media3.extractor.text.SubtitleExtractor
import io.kinescope.sdk.models.videos.KinescopeVideo
import io.kinescope.sdk.models.videos.KinescopeVideoSubtitle

@UnstableApi
internal object KinescopeSubtitleMediaSources {

  private const val USER_AGENT = "KinescopeAndroidVideoKotlin"

  fun createDashMediaSource(
      dashLink: String,
      referer: String,
  ): DashMediaSource {
    val httpFactory = createHttpDataSourceFactory(referer)
    val dashChunkSourceFactory: DashChunkSource.Factory =
        DefaultDashChunkSource.Factory(httpFactory)

    return DashMediaSource.Factory(dashChunkSourceFactory, httpFactory)
        .setManifestParser(KinescopeDashManifestParser())
        .setLoadErrorHandlingPolicy(KinescopeErrorHandlingPolicy())
        .createMediaSource(
            MediaItem.Builder()
                .setUri(Uri.parse(dashLink))
                .setDrmConfiguration(
                    MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID).build(),
                )
                .setMimeType(MimeTypes.APPLICATION_MPD)
                .build(),
        )
  }

  fun createDashWithSideloadedSubtitles(
      video: KinescopeVideo,
      referer: String,
  ): MediaSource {
    val dashLink = video.dashLink.orEmpty()
    val dashSource = createDashMediaSource(dashLink, referer)
    if (video.subtitles.isEmpty()) {
      return dashSource
    }

    val httpFactory = createHttpDataSourceFactory(referer)
    val parserFactory = DefaultSubtitleParserFactory()
    val subtitleSources = video.subtitles.map { subtitle ->
      createSubtitleMediaSource(subtitle, httpFactory, parserFactory)
    }

    return MergingMediaSource(false, false, dashSource, *subtitleSources.toTypedArray())
  }

  private fun createSubtitleMediaSource(
      subtitle: KinescopeVideoSubtitle,
      httpFactory: DefaultHttpDataSource.Factory,
      parserFactory: DefaultSubtitleParserFactory,
  ): ProgressiveMediaSource {
    val format = Format.Builder()
        .setSampleMimeType(resolveSubtitleMimeType(subtitle.url))
        .setLanguage(subtitle.language.takeIf { it.isNotBlank() })
        .setLabel(subtitle.description.takeIf { it.isNotBlank() } ?: subtitle.language)
        .build()
    val extractorsFactory: ExtractorsFactory = ExtractorsFactory {
      arrayOf(SubtitleExtractor(parserFactory.create(format), format))
    }
    return ProgressiveMediaSource.Factory(httpFactory, extractorsFactory)
        .createMediaSource(MediaItem.fromUri(Uri.parse(subtitle.url)))
  }

  private fun resolveSubtitleMimeType(url: String): String =
      when {
        url.contains(".vtt", ignoreCase = true) -> MimeTypes.TEXT_VTT
        url.contains(".ttml", ignoreCase = true) -> MimeTypes.APPLICATION_TTML
        url.contains(".srt", ignoreCase = true) -> MimeTypes.APPLICATION_SUBRIP
        else -> MimeTypes.TEXT_VTT
      }

  private fun createHttpDataSourceFactory(referer: String): DefaultHttpDataSource.Factory {
    val headers = mutableMapOf(
        "Origin" to "*/*",
        "x-drm-type" to "widevine",
        "Referer" to referer,
    )
    return DefaultHttpDataSource.Factory()
        .setUserAgent(USER_AGENT)
        .setDefaultRequestProperties(headers)
  }
}
