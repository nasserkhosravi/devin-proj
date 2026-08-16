package com.khosravi.devin.present

import android.app.Application
import com.khosravi.devin.present.di.AppComponent
import com.khosravi.devin.present.di.DaggerAppComponent
import com.khosravi.devin.present.notification.LatestLogNotificationObserver

class PresentApplication : Application() {
    lateinit var appComponent: AppComponent
        private set

    private lateinit var latestLogNotificationObserver: LatestLogNotificationObserver

    override fun onCreate() {
        super.onCreate()
        appComponent = DaggerAppComponent.builder()
            .context(this)
            .application(this)
            .build()

        latestLogNotificationObserver = LatestLogNotificationObserver(this).apply {
            register()
        }
    }

}
