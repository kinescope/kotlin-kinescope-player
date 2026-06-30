package io.kinescope.sdk.player.tracks

import android.content.Context
import android.os.Build
import androidx.media3.common.Format
import io.kinescope.sdk.R
import java.util.Locale

internal object AudioTrackLabels {

    fun label(context: Context, format: Format, fallbackIndex: Int): String {
        val uiLocale = context.uiLocale()

        format.language
            ?.trim()
            ?.takeIf { it.isNotEmpty() && !it.equals("und", ignoreCase = true) }
            ?.let { code ->
                return formatLanguageName(code, uiLocale)
            }

        format.label
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it }

        return context.getString(R.string.settings_audio_track_default, fallbackIndex + 1)
    }

    fun disambiguate(context: Context, labels: List<String>): List<String> {
        if (labels.size <= 1) {
            return labels
        }
        val duplicateNames = labels.groupingBy { it }.eachCount().filter { it.value > 1 }.keys
        if (duplicateNames.isEmpty()) {
            return labels
        }
        return labels.mapIndexed { index, title ->
            if (title in duplicateNames) {
                context.getString(R.string.settings_audio_track_numbered, title, index + 1)
            } else {
                title
            }
        }
    }

    private fun formatLanguageName(code: String, uiLocale: Locale): String {
        val normalized = code.replace('_', '-')
        val languageLocale = Locale.forLanguageTag(normalized)
        val display = languageLocale.getDisplayName(uiLocale).trim()
        if (display.isNotEmpty() && !display.equals(normalized, ignoreCase = true)) {
            return display.replaceFirstChar { char ->
                if (char.isLowerCase()) char.titlecase(uiLocale) else char.toString()
            }
        }
        return normalized.uppercase(uiLocale)
    }

    private fun Context.uiLocale(): Locale {
        val config = resources.configuration
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.locales[0]
        } else {
            @Suppress("DEPRECATION")
            config.locale
        }
    }
}
