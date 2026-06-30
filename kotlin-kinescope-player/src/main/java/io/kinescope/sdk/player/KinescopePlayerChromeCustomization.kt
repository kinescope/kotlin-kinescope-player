package io.kinescope.sdk.player

import androidx.annotation.DrawableRes
import io.kinescope.sdk.settings.KinescopeSettingsView

/**
 * Describes an app-defined control bar button rendered in the player chrome.
 */
data class KinescopeChromeButton(
    val id: String,
    @DrawableRes val iconRes: Int,
    val contentDescription: String? = null,
    val onClick: () -> Unit,
)

/**
 * Optional chrome and settings-menu customization applied on top of [KinescopePlayerOptions].
 *
 * Use [io.kinescope.sdk.view.KinescopePlayerView.configureChrome] to attach it to a player view.
 */
class KinescopePlayerChromeCustomization {

    private val _customButtons = mutableListOf<KinescopeChromeButton>()
    val customButtons: List<KinescopeChromeButton> get() = _customButtons

    internal var settingsMenuConfigurator: (KinescopeSettingsView.() -> Unit)? = null
        private set

    /**
     * Adds a button to the options strip (before subtitles / chapters / settings).
     * Multiple buttons are supported.
     */
    fun addButton(
        id: String,
        @DrawableRes iconRes: Int,
        contentDescription: String? = null,
        onClick: () -> Unit,
    ) {
        _customButtons.add(
            KinescopeChromeButton(
                id = id,
                iconRes = iconRes,
                contentDescription = contentDescription,
                onClick = onClick,
            ),
        )
    }

    fun clearButtons() {
        _customButtons.clear()
    }

    /**
     * Mutates the built-in [KinescopeSettingsView] after default parameters are registered.
     *
     * Example — add a one-tap action row:
     * ```
     * configureSettingsMenu {
     *     addCustomParameter(
     *         id = "share",
     *         title = "Share",
     *         icon = R.drawable.ic_share,
     *         isAction = true,
     *     )
     *     onParameterAction = { parameter ->
     *         if (parameter is KinescopeSettingsView.Parameter.Custom && parameter.id == "share") {
     *             shareCurrentVideo()
     *         }
     *     }
     * }
     * ```
     */
    fun configureSettingsMenu(block: KinescopeSettingsView.() -> Unit) {
        settingsMenuConfigurator = block
    }
}
