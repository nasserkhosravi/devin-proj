package ir.khosravi.devin.present

import android.app.Application
import ir.khosravi.devin.present.di.AppComponent
import ir.khosravi.devin.present.di.DaggerAppComponent

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