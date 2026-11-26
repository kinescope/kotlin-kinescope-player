package io.kinescope.sdk.shorts.interfaces

import android.content.Intent

interface ActivityProvider {
    fun getMainActivityIntent(): Intent
    fun pauseCurrentPlayer()
}

