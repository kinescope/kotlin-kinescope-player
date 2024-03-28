package io.kinescope.sdk.analytics

import android.content.Context
import io.kinescope.sdk.utils.EMPTY
import java.util.UUID

class KinescopeAnalyticsPlayerIdStorage(
    context: Context
) {

    private val sharedPreferences = context.getSharedPreferences(
        FILE_NAME,
        Context.MODE_PRIVATE
    )

    fun getPlayerId(): String =
        with(sharedPreferences) {
            val playerId = sharedPreferences.getString(KEY_PLAYER_ID, String.EMPTY)
            if (playerId.isNullOrEmpty()) {
                val newPlayerId = UUID.randomUUID().toString()
                sharedPreferences.edit()
                    .putString(KEY_PLAYER_ID, newPlayerId)
                    .apply()
                return newPlayerId
            }
            playerId
        }

    companion object {
        private const val FILE_NAME = "kinescope_sdk_shared_preferences"
        private const val KEY_PLAYER_ID = "played_id"
    }
}