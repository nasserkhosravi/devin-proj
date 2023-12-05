package com.khosravi.devin.present

import android.app.Application
import com.khosravi.devin.present.di.AppComponent
import com.khosravi.devin.present.di.DaggerAppComponent

class PresentApplication : Application() {
    lateinit var appComponent: AppComponent
        private set

    override fun onCreate() {
        super.onCreate()
        appComponent = DaggerAppComponent.builder()
            .context(this)
            .application(this)
            .build()

    }

}