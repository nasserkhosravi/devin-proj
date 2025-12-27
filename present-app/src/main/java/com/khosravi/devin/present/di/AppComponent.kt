package com.khosravi.devin.present.di

import android.app.Application
import android.content.Context
import dagger.BindsInstance
import dagger.Component
import com.khosravi.devin.present.present.LogActivity
import com.khosravi.devin.present.PresentApplication
import com.khosravi.devin.present.arch.BaseActivity
import com.khosravi.devin.present.present.LogExportDialog
import com.khosravi.devin.present.present.http.HttpLogDetailActivity
import com.khosravi.devin.present.present.ImportLogActivity
import com.khosravi.devin.present.present.StarterActivity
import javax.inject.Singleton


@Component(modules = [AppModule::class, ViewModelModule::class])
@Singleton
interface AppComponent {

    fun inject(app: PresentApplication)

    fun inject(baseActivity: BaseActivity)

    fun inject(activity: StarterActivity)

    fun inject(activity: LogActivity)

    fun inject(activity: ImportLogActivity)

    fun inject(activity: HttpLogDetailActivity)

    fun inject(dialog: LogExportDialog)

    @Component.Builder
    interface Builder {
        @BindsInstance
        fun context(appContext: Context): Builder

        @BindsInstance
        fun application(appContext: Application): Builder

        fun build(): AppComponent
    }
}