package io.kinescope.demo.live

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.media3.common.util.UnstableApi
import io.kinescope.demo.R
import io.kinescope.sdk.player.KinescopeVideoPlayer
import io.kinescope.sdk.view.KinescopePlayerView

@UnstableApi
class LiveActivity : AppCompatActivity() {

    private lateinit var kinescopePlayerView: KinescopePlayerView
    private lateinit var kinescopePlayer: KinescopeVideoPlayer
    private lateinit var watchLiveContainerView: LinearLayout
    private lateinit var watchLiveIdInputView: EditText
    private lateinit var watchLiveBtnView: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_live)

        kinescopePlayerView = findViewById(R.id.player)
        watchLiveContainerView = findViewById(R.id.watch_live_ll)
        watchLiveIdInputView = findViewById(R.id.id_et)
        watchLiveBtnView = findViewById(R.id.watch_live_btn)

        kinescopePlayer = KinescopeVideoPlayer(this)
        kinescopePlayerView.setPlayer(kinescopePlayer)

        watchLiveBtnView.setOnClickListener {
            tryLoadVideo(watchLiveIdInputView.text.toString())
        }
    }

    override fun onStop() {
        super.onStop()
        kinescopePlayer.stop()
    }

    private fun tryLoadVideo(id: String) {
        val liveId = id.takeIf { id.isNotEmpty() } ?: DEFAULT_LIVE_ID

        watchLiveContainerView.isVisible = false
        kinescopePlayer.loadVideo(liveId, { video ->
            if (video?.isLive == true) {
                kinescopePlayerView.enableLiveState(
                    posterUrl = video.poster?.url,
                    startDate = video.live?.startsAt,
                )
            }
            kinescopePlayer.play()
        }) {
            it?.printStackTrace()
        }
    }

    companion object {
        /**
         * Used if the live ID field value is empty
         */
        private const val DEFAULT_LIVE_ID = "5zWMENJ3EawysA4qbqUhar"
    }
}