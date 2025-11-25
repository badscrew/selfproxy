package com.sshtunnel.android

import android.app.Application
import com.sshtunnel.logging.Logger
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class SSHTunnelProxyApp : Application() {
    
    @Inject
    lateinit var logger: Logger
}
