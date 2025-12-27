package com.khosravi.devin.present.arch

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.khosravi.devin.present.data.AppPref
import com.khosravi.devin.present.di.getAppComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import javax.inject.Inject

abstract class BaseActivity : AppCompatActivity(), CoroutineScope by MainScope() {
    @Inject
    lateinit var appPref: AppPref

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must be called before super.onCreate()
        applyTheme()
        super.onCreate(savedInstanceState)
    }

    private fun applyTheme() {
        // Dagger injection must happen before accessing appPref
        getAppComponent().inject(this)
        val themeMode = appPref.theme
        AppCompatDelegate.setDefaultNightMode(themeMode)
    }
}