package com.khosravi.devin.present.di

import android.app.Application
import com.khosravi.devin.present.data.UserSettings
import com.khosravi.devin.present.data.CacheRepository
import dagger.Module
import dagger.Provides
import com.khosravi.devin.present.data.FilterRepository
import com.khosravi.devin.present.date.CalendarProxy
import com.khosravi.devin.present.export.PublicApiHandler


@Module
class ViewModelModule {

    @Provides
    fun viewModelFactory(
        application: Application,
        filterRepository: FilterRepository,
        calendar: CalendarProxy,
        cacheRepository: CacheRepository,
        userSettings: UserSettings,
        generalApiChecker: PublicApiHandler,
    ) = ViewModelFactory(application, calendar, filterRepository, cacheRepository, userSettings, generalApiChecker)

}

