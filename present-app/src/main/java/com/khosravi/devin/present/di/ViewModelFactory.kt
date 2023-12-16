package com.khosravi.devin.present.di

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.khosravi.devin.present.data.FilterRepository
import com.khosravi.devin.present.date.CalenderProxy
import com.khosravi.devin.present.present.ReaderViewModel
import java.lang.IllegalArgumentException

class ViewModelFactory(
    private val application: Application,
    private val filterRepository: FilterRepository,
    val calendar: CalenderProxy,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ReaderViewModel::class.java)) {
            return ReaderViewModel(application, filterRepository, calendar) as T
        }
        throw IllegalArgumentException()
    }
}