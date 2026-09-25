package com.masstamilan.app.core.util

import timber.log.Timber

object Logger {
    fun init() {
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
