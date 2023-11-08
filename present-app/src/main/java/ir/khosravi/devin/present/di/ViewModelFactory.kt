package ir.khosravi.devin.present.di

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import ir.khosravi.devin.present.present.ReaderViewModel
import java.lang.IllegalArgumentException

class ViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ReaderViewModel::class.java)) {
            return ReaderViewModel(application) as T
        }
        throw IllegalArgumentException()
    }
}