 package com.khosravi.devin.present.di

import com.khosravi.devin.present.date.CalendarType
import com.khosravi.devin.present.date.CalendarProxy
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
class AppModule {

    @Singleton
    @Provides
    fun calendarProxy(): CalendarProxy = CalendarProxy(CalendarType.PERSIAN)

}