package ir.khosravi.devin.present.di

import android.app.Application
import dagger.Module
import dagger.Provides
import ir.khosravi.devin.present.data.FilterRepository


@Module
class ViewModelModule {

    @Provides
    fun viewModelFactory(application: Application, filterRepository: FilterRepository) =
        ViewModelFactory(application, filterRepository)

}

