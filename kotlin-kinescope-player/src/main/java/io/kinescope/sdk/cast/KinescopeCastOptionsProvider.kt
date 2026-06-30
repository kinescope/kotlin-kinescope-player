package io.kinescope.sdk.cast

import android.content.Context
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider

/**
 * Google Cast configuration for the Kinescope custom receiver.
 * Registered via `OPTIONS_PROVIDER_CLASS_NAME` in the library manifest merge.
 */
class KinescopeCastOptionsProvider : OptionsProvider {

    override fun getCastOptions(context: Context): CastOptions =
        CastOptions.Builder()
            .setReceiverApplicationId(RECEIVER_APP_ID)
            .build()

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? = null

    companion object {
        const val RECEIVER_APP_ID = "29646999"
    }
}
