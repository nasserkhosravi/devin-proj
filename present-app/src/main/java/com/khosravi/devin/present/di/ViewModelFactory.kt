package com.khosravi.devin.present.di

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.khosravi.devin.present.data.UserSettings
import com.khosravi.devin.present.data.CacheRepository
import com.khosravi.devin.present.data.FilterRepository
import com.khosravi.devin.present.date.CalendarProxy
import com.khosravi.devin.present.present.ReaderViewModel
import java.lang.IllegalArgumentException

class ViewModelFactory(
    private val application: Application,
    private val calendar: CalendarProxy,
    private val filterRepository: FilterRepository,
    private val cacheRepo: CacheRepository,
    private val userSettings: UserSettings,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ReaderViewModel::class.java)) {
            return ReaderViewModel(application, calendar, filterRepository, cacheRepo, userSettings) as T
        }
        throw IllegalArgumentException()
    }
}