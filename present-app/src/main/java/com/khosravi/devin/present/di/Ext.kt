package com.khosravi.devin.present.di

import android.app.Activity
import androidx.fragment.app.Fragment
import com.khosravi.devin.present.PresentApplication

fun Activity.getAppComponent() = (applicationContext as PresentApplication).appComponent

fun Fragment.getAppComponent() = activity!!.getAppComponent()