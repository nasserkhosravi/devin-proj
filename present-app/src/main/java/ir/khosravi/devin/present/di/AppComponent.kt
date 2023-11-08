package ir.khosravi.devin.present.di

import android.app.Application
import android.content.Context
import dagger.BindsInstance
import dagger.Component
import ir.khosravi.devin.present.present.LogActivity
import ir.khosravi.devin.present.PresentApplication


@Component(modules = [ViewModelModule::class])
interface AppComponent {

    fun inject(app: PresentApplication)

    fun inject(activity: LogActivity)

    @Component.Builder
    interface Builder {
        @BindsInstance
        fun context(appContext: Context): Builder

        @BindsInstance
        fun application(appContext: Application): Builder

        fun build(): AppComponent
    }
}