package io.kinescope.demo.application

import android.app.Application
import io.kinescope.demo.KinescopeDemoConfig
import io.kinescope.sdk.api.KinescopeApiConfig
import io.kinescope.sdk.api.KinescopeApiHelper

class KinescopeSDKDemoApplication : Application() {
    lateinit var apiHelper : KinescopeApiHelper

    override fun onCreate() {
        super.onCreate()
        apiHelper = KinescopeApiConfig.createApiHelper(KinescopeDemoConfig.API_KEY)
    }
}