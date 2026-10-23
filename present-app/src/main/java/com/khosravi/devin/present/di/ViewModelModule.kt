package com.khosravi.devin.present.di

import android.app.Application
import com.khosravi.devin.present.data.AppPref
import com.khosravi.devin.present.data.UserSettings
import com.khosravi.devin.present.data.CacheRepository
import dagger.Module
import dagger.Provides
import com.khosravi.devin.present.data.FilterRepository
import com.khosravi.devin.present.data.FilterUsageRepository
import com.khosravi.devin.present.date.CalendarProxy


@Module
class ViewModelModule {

    @Provides
    fun viewModelFactory(
        application: Application,
        filterRepository: FilterRepository,
        filterUsageRepository: FilterUsageRepository,
        calendar: CalendarProxy,
        cacheRepository: CacheRepository,
        userSettings: UserSettings,
        appPref: AppPref,
    ) = ViewModelFactory(application, calendar, filterRepository, filterUsageRepository, cacheRepository, userSettings, appPref)

}

