package com.khosravi.devin.present.e2e

import android.app.Application
import androidx.test.runner.AndroidJUnitRunner

/**
 * Runner for the e2e suite. Before [com.khosravi.devin.present.PresentApplication.onCreate] runs, it stamps
 * the update-check time so the app's start-up `checkInBackgroundIfDue()` never calls GitHub (no network,
 * no surprise update dialog). Tests re-stamp it in [AppState.reset].
 */
class E2eTestRunner : AndroidJUnitRunner() {

    override fun callApplicationOnCreate(app: Application) {
        AppState.suppressUpdateCheck(app)
        super.callApplicationOnCreate(app)
    }
}
