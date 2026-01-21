package com.kotlin.kinescope.shorts.drm

import android.content.Context
import android.util.Base64
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.DrmInitData
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.Util
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.drm.HttpMediaDrmCallback
import androidx.media3.exoplayer.drm.OfflineLicenseHelper

@OptIn(androidx.media3.common.util.UnstableApi::class)
class DrmConfigurator(private val context: Context) {
    companion object {
        private const val TAG = "DrmConfigurator"
        private const val PREF_FILE_NAME = "drm_licenses"
        private const val PREF_KEY_LICENSE_PREFIX = "license_"
        private const val PREF_KEY_LICENSE_ORDER = "pref_key_license_order"
        private const val MAX_LICENSES = 10
    }

    fun downloadOfflineLicense(
        videoUrl: String,
        drmContentProtection: DrmContentProtection,
        contentId: String,
        psshData: ByteArray?,
        callback: (ByteArray?) -> Unit
    ) {
        Thread {
            var keySetId: ByteArray? = null
            try {
                val dataSourceFactory = DefaultHttpDataSource.Factory()
                    .setUserAgent(Util.getUserAgent(context, "KinescopeShorts"))

                val mediaDrmCallback = HttpMediaDrmCallback(
                    drmContentProtection.licenseUrl ?: return@Thread,
                    dataSourceFactory
                )

                val drmSessionManager = DefaultDrmSessionManager.Builder()
                    .setUuidAndExoMediaDrmProvider(C.WIDEVINE_UUID) { uuid ->
                        FrameworkMediaDrm.newInstance(uuid)
                    }
                    .build(mediaDrmCallback)

                val offlineLicenseHelper = OfflineLicenseHelper(
                    drmSessionManager,
                    androidx.media3.exoplayer.drm.DrmSessionEventListener.EventDispatcher()
                )

                val format = Format.Builder()
                    .setId("offline-$contentId")
                    .setContainerMimeType(MimeTypes.APPLICATION_M3U8)
                    .setDrmInitData(
                        DrmInitData(
                            DrmInitData.SchemeData(
                                C.WIDEVINE_UUID,
                                MimeTypes.VIDEO_MP4,
                                psshData ?: byteArrayOf()
                            )
                        )
                    )
                    .build()

                keySetId = offlineLicenseHelper.downloadLicense(format)

                if (keySetId != null) {
                    saveOfflineLicenseToStorage(contentId, keySetId)
                }
            } catch (e: Exception) {
            } finally {
                callback(keySetId)
            }
        }.start()
    }

     fun saveOfflineLicenseToStorage(contentId: String, keySetId: ByteArray) {
        try {
            val prefs = context.getSharedPreferences(PREF_FILE_NAME, Context.MODE_PRIVATE)
            val editor = prefs.edit()
            val orderList = (prefs.getStringSet(PREF_KEY_LICENSE_ORDER, mutableSetOf())?.toMutableList() ?: mutableListOf())
                .apply { remove(contentId); add(contentId) }
            if (orderList.size > MAX_LICENSES) {
                val oldest = orderList.removeAt(0)
                editor.remove(PREF_KEY_LICENSE_PREFIX + oldest)
            }
            editor.putStringSet(PREF_KEY_LICENSE_ORDER, orderList.toSet())
            editor.putString(PREF_KEY_LICENSE_PREFIX + contentId, Base64.encodeToString(keySetId, Base64.DEFAULT))
            editor.apply()
        } catch (e: Exception) {
        }
    }

    fun loadOfflineLicenseFromStorage(contentId: String): ByteArray? {
        return try {
            val prefs = context.getSharedPreferences(PREF_FILE_NAME, Context.MODE_PRIVATE)
            val base64 = prefs.getString(PREF_KEY_LICENSE_PREFIX + contentId, null)
            base64?.let { Base64.decode(it, Base64.DEFAULT) }
        } catch (e: Exception) {
            null
        }
    }

    fun clearAllOfflineLicenses() {
        try {
            val prefs = context.getSharedPreferences(PREF_FILE_NAME, Context.MODE_PRIVATE)
            val editor = prefs.edit()
            prefs.getStringSet(PREF_KEY_LICENSE_ORDER, setOf())?.forEach { editor.remove(PREF_KEY_LICENSE_PREFIX + it) }
            editor.remove(PREF_KEY_LICENSE_ORDER).apply()
        } catch (e: Exception) {
        }
    }
}





