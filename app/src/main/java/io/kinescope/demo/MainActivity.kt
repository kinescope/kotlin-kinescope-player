package io.kinescope.demo

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.widget.AppCompatButton
import io.kinescope.demo.customui.CustomUIActivity
import io.kinescope.demo.live.LiveActivity
import io.kinescope.demo.playlist.PlaylistActivity
import io.kinescope.demo.subtitles.SubtitlesActivity
import io.kinescope.demo.offlinedrm.OfflineDrmDemoActivity
import io.kinescope.demo.shorts.ShortsActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE);

        findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.viewPager2).visibility = android.view.View.GONE
        findViewById<android.widget.LinearLayout>(R.id.main_buttons_layout).visibility = android.view.View.VISIBLE

        val btnPlaylist = findViewById<AppCompatButton>(R.id.btn_playlist)
        val btnSubtitles = findViewById<AppCompatButton>(R.id.btn_subtitles)
        val btnCustomUI = findViewById<AppCompatButton>(R.id.btn_custom_ui)
        val btnLive = findViewById<AppCompatButton>(R.id.btn_live)
        val btnShorts = findViewById<AppCompatButton>(R.id.btn_shorts)
        val btnOfflineDrm = findViewById<AppCompatButton>(R.id.btn_offline_drm)

        btnPlaylist.setOnClickListener {
            val intent =  Intent(this, PlaylistActivity::class.java)
            startActivity(intent);
        }

        btnSubtitles.setOnClickListener {
            val intent =  Intent(this, SubtitlesActivity::class.java)
            startActivity(intent);
        }

        btnCustomUI.setOnClickListener {
            val intent =  Intent(this, CustomUIActivity::class.java)
            startActivity(intent);
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