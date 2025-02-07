package com.khosravi.devin.present.data

import javax.inject.Inject

//The class hold config of features.
class UserSettings @Inject constructor() {

    val isEnableTagAsFilter: Boolean
        get() = true
}