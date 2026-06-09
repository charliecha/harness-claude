package com.harnessclaude.llm

import android.app.Application
import timber.log.Timber

/**
 * Application entry; initialises Timber so all FR-004 components have
 * a logger available regardless of which Activity is on top.
 */
class LlmLoaderApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
