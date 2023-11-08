package ir.khosravi.devin.present.di

import android.app.Application
import dagger.Module
import dagger.Provides


@Module()
class ViewModelModule {

    @Provides
    fun viewModelFactory(application: Application) = ViewModelFactory(application)

}

