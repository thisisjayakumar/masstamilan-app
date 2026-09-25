package com.masstamilan.app.core.util

import com.masstamilan.app.BuildConfig
import timber.log.Timber

object Logger {
    fun init() {
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
