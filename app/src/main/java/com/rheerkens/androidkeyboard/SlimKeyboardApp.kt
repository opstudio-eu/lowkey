package com.rheerkens.androidkeyboard

import android.app.Application
import com.rheerkens.androidkeyboard.updates.Updates

class SlimKeyboardApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
        AppGraph.crashLog.install()
        Updates.trackVisibleActivities(this)
    }

    @Suppress("DEPRECATION") // Older Android versions still deliver running-memory callbacks.
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // UI_HIDDEN alone isn't memory pressure. An open speech stream finishes safely before
        // its native resources are freed; downloaded files are never removed here.
        if (level == TRIM_MEMORY_RUNNING_LOW || level == TRIM_MEMORY_RUNNING_CRITICAL ||
            level >= TRIM_MEMORY_BACKGROUND
        ) {
            AppGraph.speechEngine.unload()
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        AppGraph.speechEngine.unload()
    }
}
