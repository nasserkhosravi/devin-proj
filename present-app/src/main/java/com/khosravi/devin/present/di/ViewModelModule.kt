package com.khosravi.devin.present.di

import android.app.Application
import com.khosravi.devin.present.data.CacheRepository
import dagger.Module
import dagger.Provides
import com.khosravi.devin.present.data.FilterRepository
import com.khosravi.devin.present.date.CalendarProxy


@Module
class ViewModelModule {

    @Provides
    fun viewModelFactory(
        application: Application,
        filterRepository: FilterRepository,
        calendar: CalendarProxy,
        cacheRepository: CacheRepository,
    ) = ViewModelFactory(application, calendar, filterRepository, cacheRepository)

}

