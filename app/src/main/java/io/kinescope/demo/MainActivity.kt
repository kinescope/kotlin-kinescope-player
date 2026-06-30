package io.kinescope.demo

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.WindowManager
import com.google.android.material.button.MaterialButton
import io.kinescope.demo.chapters.ChaptersActivity
import io.kinescope.demo.customplayer.CustomPlayerActivity
import io.kinescope.demo.live.LiveActivity
import io.kinescope.demo.playlist.PlaylistActivity
import io.kinescope.demo.subtitles.SubtitlesActivity
import io.kinescope.demo.drm.DrmViewingActivity
import io.kinescope.demo.offlinedrm.OfflineDrmDemoActivity
import io.kinescope.demo.shorts.ShortsActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE);

        val btnPlaylist = findViewById<MaterialButton>(R.id.btn_playlist)
        val btnSubtitles = findViewById<MaterialButton>(R.id.btn_subtitles)
        val btnChapters = findViewById<MaterialButton>(R.id.btn_chapters)
        val btnDrmViewing = findViewById<MaterialButton>(R.id.btn_drm_viewing)
        val btnCustomPlayer = findViewById<MaterialButton>(R.id.btn_custom_player)
        val btnLive = findViewById<MaterialButton>(R.id.btn_live)
        val btnShorts = findViewById<MaterialButton>(R.id.btn_shorts)
        val btnOfflineDrm = findViewById<MaterialButton>(R.id.btn_offline_drm)

        btnPlaylist.setOnClickListener {
            val intent =  Intent(this, PlaylistActivity::class.java)
            startActivity(intent);
        }

        btnSubtitles.setOnClickListener {
            startActivity(Intent(this, SubtitlesActivity::class.java))
        }

        btnChapters.setOnClickListener {
            startActivity(Intent(this, ChaptersActivity::class.java))
        }

        btnDrmViewing.setOnClickListener {
            startActivity(Intent(this, DrmViewingActivity::class.java))
        }

        btnCustomPlayer.setOnClickListener {
            startActivity(Intent(this, CustomPlayerActivity::class.java))
        }

        btnLive.setOnClickListener {
            val intent = Intent(this, LiveActivity::class.java)
            startActivity(intent)
        }

        btnShorts.setOnClickListener {
            val intent = Intent(this, ShortsActivity::class.java)
            startActivity(intent)
        }

        btnOfflineDrm.setOnClickListener {
            startActivity(Intent(this, OfflineDrmDemoActivity::class.java))
        }
    }


    override fun onStart() {
        super.onStart()
    }
}