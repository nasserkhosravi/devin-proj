 package com.khosravi.devin.present.di

import android.content.Context
import com.khosravi.devin.present.data.AppPref
import com.khosravi.devin.present.date.CalendarType
import com.khosravi.devin.present.date.CalendarProxy
import com.khosravi.devin.present.update.UpdateChecker
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
class AppModule {

    @Singleton
    @Provides
    fun calendarProxy(): CalendarProxy = CalendarProxy(CalendarType.PERSIAN)

    @Singleton
    @Provides
    fun updateChecker(context: Context, appPref: AppPref): UpdateChecker = UpdateChecker(context, appPref)

}