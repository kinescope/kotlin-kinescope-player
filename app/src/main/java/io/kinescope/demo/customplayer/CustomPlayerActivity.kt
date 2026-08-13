package io.kinescope.demo.customplayer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import io.kinescope.demo.R
import io.kinescope.demo.application.KinescopeSDKDemoApplication
import io.kinescope.sdk.api.isDashboardPlayerDeleteRestriction
import io.kinescope.sdk.api.readApiErrorMessage
import io.kinescope.sdk.models.players.KinescopeCreatePlayerRequest
import io.kinescope.sdk.models.players.KinescopePlayerTemplate
import io.kinescope.sdk.models.players.KinescopeUpdatePlayerRequest
import io.kinescope.sdk.models.players.applyTo
import io.kinescope.sdk.models.players.syncLegacyChromeFlags
import io.kinescope.sdk.models.players.toPlayerSettings
import io.kinescope.demo.KinescopeDemoConfig
import io.kinescope.demo.KinescopeViewModel
import io.kinescope.sdk.playlist.KinescopePlaylistItem
import io.kinescope.sdk.player.KinescopeContentOrientationController
import io.kinescope.sdk.player.KinescopePictureInPictureSession
import io.kinescope.sdk.player.KinescopePlayerOptions
import io.kinescope.sdk.player.KinescopeVideoPlayer
import io.kinescope.sdk.R as SdkR
import io.kinescope.sdk.view.KinescopePlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@UnstableApi
class CustomPlayerActivity : AppCompatActivity() {

    private val viewModel: KinescopeViewModel by viewModels {
        KinescopeViewModel.Factory((application as KinescopeSDKDemoApplication).apiHelper)
    }

    private lateinit var playerView: KinescopePlayerView
    private lateinit var fullscreenPlayerView: KinescopePlayerView
    private lateinit var player: KinescopeVideoPlayer
    private var isVideoFullscreen = false
    private var settingsScrollView: View? = null
    private var playerLayoutParamsBackup: ConstraintLayout.LayoutParams? = null
    private lateinit var pipSession: KinescopePictureInPictureSession
    private lateinit var orientationController: KinescopeContentOrientationController
    private var suppressUiCallbacks = false
    private var selectedTemplate: KinescopePlayerTemplate? = null
    private var hasRequestedVideo = false
    private var currentVideoId: String? = null

    private lateinit var textSelectedTemplate: TextView
    private lateinit var buttonUpdateTemplate: MaterialButton
    private lateinit var buttonDeleteTemplate: MaterialButton

    private lateinit var switchAutoplay: SwitchMaterial
    private lateinit var switchMuted: SwitchMaterial
    private lateinit var switchLoop: SwitchMaterial
    private lateinit var switchControls: SwitchMaterial
    private lateinit var switchPlaysinline: SwitchMaterial
    private lateinit var switchKeyboardShortcuts: SwitchMaterial
    private lateinit var switchPictureInPicture: SwitchMaterial
    private lateinit var switchFullscreen: SwitchMaterial
    private lateinit var switchPlaybackRate: SwitchMaterial
    private lateinit var spinnerPreload: Spinner
    private lateinit var spinnerQuality: Spinner
    private lateinit var inputColor: TextInputEditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_custom_player)
        title = getString(R.string.custom_player_title)

        bindViews()
        setupSpinners()

        player = KinescopeVideoPlayer(this)
        playerView = findViewById(R.id.kinescope_player)
        fullscreenPlayerView = findViewById(R.id.kinescope_player_fullscreen)
        settingsScrollView = findViewById(R.id.settings_scroll_view)
        playerLayoutParamsBackup = playerView.layoutParams as ConstraintLayout.LayoutParams
        pipSession = KinescopePictureInPictureSession(
            activity = this,
            playerView = { playerView },
            player = { player },
            additionalPlayerViews = { listOf(fullscreenPlayerView) },
        ).apply {
            onEnteringPip = { applyPictureInPictureLayout() }
            onExitingPip = { restorePictureInPictureLayout() }
        }
        playerView.setIsFullscreen(false)
        fullscreenPlayerView.setIsFullscreen(true)
        playerView.setPlayer(player)
        playerView.onFullscreenButtonCallback = { toggleFullscreen() }
        fullscreenPlayerView.onFullscreenButtonCallback = { toggleFullscreen() }
        orientationController = KinescopeContentOrientationController(
            activity = this,
            playerViews = { listOf(playerView, fullscreenPlayerView) },
        )
        orientationController.attach()
        player.kinescopePlayerOptions.showPlaylistButton = true
        playerView.onPlaylistItemSelected = { item ->
            playPlaylistVideo(item.id)
        }
        playerView.onPlaylistCopyLinkClick = { item ->
            copyPlaylistLink(item.shareUrl)
        }
        fullscreenPlayerView.onPlaylistItemSelected = { item ->
            playPlaylistVideo(item.id)
        }
        fullscreenPlayerView.onPlaylistCopyLinkClick = { item ->
            copyPlaylistLink(item.shareUrl)
        }
        viewModel.allVideos.observe(this) { videos ->
            playerView.setPlaylistItems(
                items = videos.map { video ->
                    KinescopePlaylistItem(
                        id = video.id,
                        title = video.title,
                        durationSeconds = video.duration,
                        posterUrl = video.poster?.thumbnailUrl(),
                        shareUrl = "https://kinescope.io/${video.id}",
                    )
                },
                selectedId = currentVideoId,
            )
            fullscreenPlayerView.setPlaylistItems(
                items = videos.map { video ->
                    KinescopePlaylistItem(
                        id = video.id,
                        title = video.title,
                        durationSeconds = video.duration,
                        posterUrl = video.poster?.thumbnailUrl(),
                        shareUrl = "https://kinescope.io/${video.id}",
                    )
                },
                selectedId = currentVideoId,
            )
        }
        viewModel.getAllVideos()
        playerView.configureChrome {
            addButton(
                id = "demo_share",
                iconRes = SdkR.drawable.ic_attachments,
                contentDescription = getString(R.string.custom_player_demo_action),
            ) {
                Toast.makeText(
                    this@CustomPlayerActivity,
                    R.string.custom_player_demo_action_toast,
                    Toast.LENGTH_SHORT,
                ).show()
            }
            configureSettingsMenu {
                addCustomParameter(
                    id = "demo_share",
                    title = getString(R.string.custom_player_demo_action),
                    icon = SdkR.drawable.ic_attachments,
                    isAction = true,
                )
                onParameterAction = { parameter ->
                    if (parameter is io.kinescope.sdk.settings.KinescopeSettingsView.Parameter.Custom &&
                        parameter.id == "demo_share"
                    ) {
                        Toast.makeText(
                            this@CustomPlayerActivity,
                            R.string.custom_player_demo_action_toast,
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            }
        }
        pipSession.attach()

        bindUiFromOptions(player.kinescopePlayerOptions)
        setupListeners()

        textSelectedTemplate = findViewById(R.id.text_selected_template)
        buttonUpdateTemplate = findViewById(R.id.button_update_template)
        buttonDeleteTemplate = findViewById(R.id.button_delete_template)
        updateSelectedTemplateUi()

        findViewById<MaterialButton>(R.id.button_save_template).setOnClickListener {
            showSaveTemplateDialog()
        }
        findViewById<MaterialButton>(R.id.button_use_template).setOnClickListener {
            loadAndPickTemplate()
        }
        buttonUpdateTemplate.setOnClickListener {
            showUpdateTemplateDialog()
        }
        buttonDeleteTemplate.setOnClickListener {
            showDeleteTemplateDialog()
        }
    }

    override fun onStart() {
        super.onStart()
        if (hasRequestedVideo) return
        hasRequestedVideo = true
        player.loadVideo(
            KinescopeDemoConfig.DEFAULT_VIDEO_ID,
            onSuccess = { video ->
                if (video != null) {
                    currentVideoId = video.id
                    playerView.setSelectedPlaylistItemId(video.id)
                    fullscreenPlayerView.setSelectedPlaylistItemId(video.id)
                    player.play()
                }
            },
            onFailed = { error ->
                val detail = error?.message?.takeIf { it.isNotBlank() }
                val message = detail ?: getString(R.string.custom_player_video_load_error)
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            },
        )
    }

    override fun onStop() {
        pipSession.onStop()
        super.onStop()
    }

    override fun onDestroy() {
        if (::orientationController.isInitialized) {
            orientationController.detach()
        }
        super.onDestroy()
    }

    private fun bindViews() {
        switchAutoplay = bindSettingRow(
            rowId = R.id.row_autoplay,
            iconRes = SdkR.drawable.ic_autoplay,
            labelRes = R.string.custom_player_autoplay,
        )
        switchMuted = bindSettingRow(
            rowId = R.id.row_muted,
            iconRes = SdkR.drawable.ic_volume_off,
            labelRes = R.string.custom_player_muted,
        )
        switchLoop = bindSettingRow(
            rowId = R.id.row_loop,
            iconRes = SdkR.drawable.ic_playlist_player,
            labelRes = R.string.custom_player_loop,
        )
        switchControls = bindSettingRow(
            rowId = R.id.row_controls,
            iconRes = SdkR.drawable.ic_settings,
            labelRes = R.string.custom_player_controls,
            checked = true,
        )
        switchPlaysinline = bindSettingRow(
            rowId = R.id.row_playsinline,
            iconRes = SdkR.drawable.ic_mini_player,
            labelRes = R.string.custom_player_playsinline,
            checked = true,
        )
        switchKeyboardShortcuts = bindSettingRow(
            rowId = R.id.row_keyboard_shortcuts,
            iconRes = SdkR.drawable.ic_video_settings,
            labelRes = R.string.custom_player_keyboard_shortcuts,
            checked = true,
        )
        switchPictureInPicture = bindSettingRow(
            rowId = R.id.row_picture_in_picture,
            iconRes = SdkR.drawable.ic_airplay,
            labelRes = R.string.custom_player_picture_in_picture,
            checked = true,
        )
        switchFullscreen = bindSettingRow(
            rowId = R.id.row_fullscreen,
            iconRes = SdkR.drawable.ic_fullscreen,
            labelRes = R.string.custom_player_fullscreen,
            checked = true,
        )
        switchPlaybackRate = bindSettingRow(
            rowId = R.id.row_playback_rate,
            iconRes = SdkR.drawable.ic_playback_speed,
            labelRes = R.string.custom_player_playback_rate,
            checked = true,
        )
        spinnerPreload = bindSpinnerRow(
            rowId = R.id.row_preload,
            iconRes = SdkR.drawable.ic_clock_player,
            labelRes = R.string.custom_player_preload,
        )
        spinnerQuality = bindSpinnerRow(
            rowId = R.id.row_quality,
            iconRes = SdkR.drawable.ic_quality,
            labelRes = R.string.custom_player_quality,
        )
        inputColor = findViewById(R.id.input_color)
    }

    private fun bindSettingRow(
        rowId: Int,
        iconRes: Int,
        labelRes: Int,
        checked: Boolean = false,
    ): SwitchMaterial {
        val row = findViewById<View>(rowId)
        row.findViewById<ImageView>(R.id.setting_icon).setImageResource(iconRes)
        return row.findViewById<SwitchMaterial>(R.id.setting_switch).apply {
            text = getString(labelRes)
            isChecked = checked
        }
    }

    private fun bindSpinnerRow(rowId: Int, iconRes: Int, labelRes: Int): Spinner {
        val row = findViewById<View>(rowId)
        row.findViewById<ImageView>(R.id.setting_icon).setImageResource(iconRes)
        row.findViewById<TextView>(R.id.setting_label).setText(labelRes)
        return row.findViewById(R.id.setting_spinner)
    }

    private fun setupSpinners() {
        spinnerPreload.adapter = createSpinnerAdapter(R.array.custom_player_preload_values)
        spinnerQuality.adapter = createSpinnerAdapter(R.array.custom_player_quality_values)
    }

    private fun createSpinnerAdapter(arrayResId: Int): ArrayAdapter<String> {
        return ArrayAdapter(
            this,
            R.layout.item_custom_player_spinner_text,
            resources.getStringArray(arrayResId),
        ).apply {
            setDropDownViewResource(R.layout.item_custom_player_spinner_dropdown_text)
        }
    }

    private fun setupListeners() {
        val onChanged: () -> Unit = { if (!suppressUiCallbacks) applyOptionsFromUi() }

        switchAutoplay.setOnCheckedChangeListener { _, _ -> onChanged() }
        switchMuted.setOnCheckedChangeListener { _, _ -> onChanged() }
        switchLoop.setOnCheckedChangeListener { _, _ -> onChanged() }
        switchControls.setOnCheckedChangeListener { _, _ -> onChanged() }
        switchPlaysinline.setOnCheckedChangeListener { _, _ -> onChanged() }
        switchKeyboardShortcuts.setOnCheckedChangeListener { _, _ -> onChanged() }
        switchPictureInPicture.setOnCheckedChangeListener { _, _ -> onChanged() }
        switchFullscreen.setOnCheckedChangeListener { _, _ -> onChanged() }
        switchPlaybackRate.setOnCheckedChangeListener { _, _ -> onChanged() }
        spinnerPreload.onItemSelectedListener = simpleItemSelectedListener {
            if (!suppressUiCallbacks) onChanged()
        }
        spinnerQuality.onItemSelectedListener = simpleItemSelectedListener {
            if (!suppressUiCallbacks) onChanged()
        }
        inputColor.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus && !suppressUiCallbacks) {
                applyOptionsFromUi()
            }
        }
    }

    private fun applyOptionsFromUi() {
        val options = player.kinescopePlayerOptions
        options.autoplay = switchAutoplay.isChecked
        options.muted = switchMuted.isChecked
        options.loop = switchLoop.isChecked
        options.controls = switchControls.isChecked
        options.playsinline = switchPlaysinline.isChecked
        options.keyboardShortcuts = switchKeyboardShortcuts.isChecked
        options.pictureInPicture = switchPictureInPicture.isChecked
        options.fullscreen = switchFullscreen.isChecked
        options.playbackRate = switchPlaybackRate.isChecked
        options.preload = spinnerPreload.selectedItem.toString()
        options.quality = spinnerQuality.selectedItem.toString()

        val colorText = inputColor.text?.toString()?.trim().orEmpty()
        if (colorText.isNotEmpty()) {
            if (!isValidHexColor(colorText)) {
                Toast.makeText(this, R.string.custom_player_invalid_color, Toast.LENGTH_SHORT).show()
                return
            }
            options.accentColor = normalizeHexColor(colorText)
        }

        options.syncLegacyChromeFlags()
        player.applyPlaybackOptions()
        playerView.applyTemplateOptions()
        fullscreenPlayerView.applyTemplateOptions()
    }

    private fun bindUiFromOptions(options: KinescopePlayerOptions) {
        suppressUiCallbacks = true
        switchAutoplay.isChecked = options.autoplay
        switchMuted.isChecked = options.muted
        switchLoop.isChecked = options.loop
        switchControls.isChecked = options.controls
        switchPlaysinline.isChecked = options.playsinline
        switchKeyboardShortcuts.isChecked = options.keyboardShortcuts
        switchPictureInPicture.isChecked = options.pictureInPicture
        switchFullscreen.isChecked = options.fullscreen
        switchPlaybackRate.isChecked = options.playbackRate
        inputColor.setText(options.accentColor)
        setSpinnerValue(spinnerPreload, options.preload)
        setSpinnerValue(spinnerQuality, options.quality)
        suppressUiCallbacks = false
        player.applyPlaybackOptions()
        playerView.applyTemplateOptions()
    }

    private fun bindUiFromTemplate(template: KinescopePlayerTemplate) {
        selectedTemplate = template
        updateSelectedTemplateUi()
        template.settings?.let { settings ->
            settings.applyTo(player.kinescopePlayerOptions)
            bindUiFromOptions(player.kinescopePlayerOptions)
        } ?: bindUiFromOptions(player.kinescopePlayerOptions)
        Toast.makeText(
            this,
            getString(R.string.custom_player_template_applied, template.name),
            Toast.LENGTH_SHORT,
        ).show()
    }

    private fun updateSelectedTemplateUi() {
        val template = selectedTemplate
        if (template == null) {
            textSelectedTemplate.setText(R.string.custom_player_no_template_selected)
            buttonUpdateTemplate.isEnabled = false
            buttonDeleteTemplate.isEnabled = false
        } else {
            val suffix = when {
                template.isDefault || template.default ->
                    " (${getString(R.string.custom_player_delete_default_blocked)})"
                template.isDashboardManaged || !template.canDelete ->
                    getString(R.string.custom_player_template_dashboard_suffix)
                else -> ""
            }
            textSelectedTemplate.text =
                getString(R.string.custom_player_selected_template, template.name) + suffix
            buttonUpdateTemplate.isEnabled = true
            buttonDeleteTemplate.isEnabled = template.canDelete
        }
    }

    private fun showUpdateTemplateDialog() {
        val template = selectedTemplate ?: return
        applyOptionsFromUi()
        val input = EditText(this).apply {
            setText(template.name)
            setSingleLine()
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.custom_player_update_dialog_title)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = input.text?.toString()?.trim().orEmpty()
                if (name.isNotEmpty()) {
                    updateTemplate(template.id, name)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun updateTemplate(playerId: String, name: String) {
        val request = KinescopeUpdatePlayerRequest(
            name = name,
            settings = player.kinescopePlayerOptions.toPlayerSettings(),
        )
        val apiHelper = (application as KinescopeSDKDemoApplication).apiHelper
        lifecycleScope.launch {
            apiHelper.updatePlayer(playerId, request)
                .flowOn(Dispatchers.IO)
                .catch {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@CustomPlayerActivity,
                            R.string.custom_player_update_error,
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
                .collect { response ->
                    selectedTemplate = response.data
                    updateSelectedTemplateUi()
                    Toast.makeText(
                        this@CustomPlayerActivity,
                        getString(R.string.custom_player_update_success, response.data.name),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
        }
    }

    private fun showDeleteTemplateDialog() {
        val template = selectedTemplate ?: return
        if (!template.canDelete) {
            val message = when {
                template.isDefault || template.default ->
                    getString(R.string.custom_player_delete_default_blocked)
                template.isDashboardManaged ->
                    getString(R.string.custom_player_delete_dashboard_blocked)
                else -> getString(R.string.custom_player_delete_dashboard_blocked)
            }
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.custom_player_delete_dialog_title)
            .setMessage(getString(R.string.custom_player_delete_dialog_message, template.name))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                deleteTemplate(template.id)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun deleteTemplate(playerId: String) {
        val apiHelper = (application as KinescopeSDKDemoApplication).apiHelper
        lifecycleScope.launch {
            apiHelper.deletePlayer(playerId)
                .flowOn(Dispatchers.IO)
                .catch { error ->
                    withContext(Dispatchers.Main) {
                        val text = resolveDeleteErrorMessage(error)
                        Toast.makeText(
                            this@CustomPlayerActivity,
                            text,
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
                .collect { response ->
                    if (response.data.success) {
                        selectedTemplate = null
                        updateSelectedTemplateUi()
                        Toast.makeText(
                            this@CustomPlayerActivity,
                            R.string.custom_player_delete_success,
                            Toast.LENGTH_SHORT,
                        ).show()
                    } else {
                        Toast.makeText(
                            this@CustomPlayerActivity,
                            R.string.custom_player_delete_error,
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
        }
    }

    private fun showSaveTemplateDialog() {
        applyOptionsFromUi()
        val input = EditText(this).apply {
            hint = getString(R.string.custom_player_save_dialog_hint)
            setSingleLine()
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.custom_player_save_dialog_title)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = input.text?.toString()?.trim().orEmpty()
                if (name.isNotEmpty()) {
                    saveTemplate(name)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun saveTemplate(name: String) {
        val request = KinescopeCreatePlayerRequest(
            name = name,
            settings = player.kinescopePlayerOptions.toPlayerSettings(),
        )
        val apiHelper = (application as KinescopeSDKDemoApplication).apiHelper
        lifecycleScope.launch {
            apiHelper.createPlayer(request)
                .flowOn(Dispatchers.IO)
                .catch {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@CustomPlayerActivity,
                            R.string.custom_player_save_error,
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
                .collect { response ->
                    selectedTemplate = response.data
                    updateSelectedTemplateUi()
                    Toast.makeText(
                        this@CustomPlayerActivity,
                        getString(R.string.custom_player_save_success, response.data.name),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
        }
    }

    private fun loadAndPickTemplate() {
        val apiHelper = (application as KinescopeSDKDemoApplication).apiHelper
        lifecycleScope.launch {
            apiHelper.getPlayers()
                .flowOn(Dispatchers.IO)
                .catch {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@CustomPlayerActivity,
                            R.string.custom_player_load_error,
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
                .collect { response ->
                    val templates = response.data
                    if (templates.isEmpty()) {
                        Toast.makeText(
                            this@CustomPlayerActivity,
                            R.string.custom_player_load_error,
                            Toast.LENGTH_SHORT,
                        ).show()
                        return@collect
                    }
                    val names = templates.map { template ->
                        template.name + templateListSuffix(template)
                    }.toTypedArray()
                    AlertDialog.Builder(this@CustomPlayerActivity)
                        .setTitle(R.string.custom_player_pick_template_title)
                        .setItems(names) { _, which ->
                            bindUiFromTemplate(templates[which])
                        }
                        .show()
                }
        }
    }

    private fun setSpinnerValue(spinner: Spinner, value: String) {
        val adapter = spinner.adapter as? ArrayAdapter<*> ?: return
        for (index in 0 until adapter.count) {
            if (adapter.getItem(index).toString() == value) {
                spinner.setSelection(index)
                return
            }
        }
    }

    private fun isValidHexColor(value: String): Boolean = runCatching {
        Color.parseColor(normalizeHexColor(value))
    }.isSuccess

    private fun normalizeHexColor(value: String): String {
        val trimmed = value.trim()
        return if (trimmed.startsWith("#")) trimmed else "#$trimmed"
    }

    private fun toggleFullscreen() {
        if (isVideoFullscreen) {
            setFullscreen(false)
            supportActionBar?.show()
            isVideoFullscreen = false
        } else {
            setFullscreen(true)
            supportActionBar?.hide()
            isVideoFullscreen = true
        }
        fullscreenPlayerView.isVisible = isVideoFullscreen
        settingsScrollView?.isVisible = !isVideoFullscreen
        orientationController.setFullscreen(isVideoFullscreen)
    }

    private fun setFullscreen(fullscreen: Boolean) {
        if (fullscreen) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
            )
            KinescopePlayerView.switchTargetView(playerView, fullscreenPlayerView, player)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            KinescopePlayerView.switchTargetView(fullscreenPlayerView, playerView, player)
        }
    }

    override fun onBackPressed() {
        if (isVideoFullscreen) {
            toggleFullscreen()
            return
        }
        super.onBackPressed()
    }

    private fun templateListSuffix(template: KinescopePlayerTemplate): String = when {
        template.isDefault || template.default -> " *"
        template.isDashboardManaged -> getString(R.string.custom_player_template_dashboard_suffix)
        else -> ""
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        pipSession.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
    }

    private fun applyPictureInPictureLayout() {
        settingsScrollView?.isVisible = false
        supportActionBar?.hide()
        playerView.layoutParams = (playerView.layoutParams as ConstraintLayout.LayoutParams).apply {
            height = 0
            bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            topToTop = ConstraintLayout.LayoutParams.PARENT_ID
        }
    }

    private fun restorePictureInPictureLayout() {
        settingsScrollView?.isVisible = true
        supportActionBar?.show()
        playerLayoutParamsBackup?.let { playerView.layoutParams = it }
    }

    private fun resolveDeleteErrorMessage(error: Throwable): String {
        if (error.isDashboardPlayerDeleteRestriction()) {
            return getString(R.string.custom_player_delete_dashboard_blocked)
        }
        val apiMessage = error.readApiErrorMessage()
        if (!apiMessage.isNullOrBlank()) {
            return getString(R.string.custom_player_delete_error_detail, apiMessage)
        }
        return getString(R.string.custom_player_delete_error)
    }

    private fun playPlaylistVideo(videoId: String) {
        currentVideoId = videoId
        playerView.setSelectedPlaylistItemId(videoId)
        fullscreenPlayerView.setSelectedPlaylistItemId(videoId)
        player.loadVideo(videoId, onSuccess = {
            player.play()
        })
    }

    private fun copyPlaylistLink(link: String?) {
        val url = link?.takeIf { it.isNotBlank() } ?: return
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("video_link", url))
        Toast.makeText(this, R.string.playlist_link_copied, Toast.LENGTH_SHORT).show()
    }
}
