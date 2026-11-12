package com.rheerkens.androidkeyboard.companion.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.rheerkens.androidkeyboard.AppGraph
import com.rheerkens.androidkeyboard.clipboard.PhotoAccess
import com.rheerkens.androidkeyboard.clipboard.RecentScreenshots

/**
 * Photo access for Recent screenshots, shared by the Home setup step and the Settings switch.
 * Re-read on every resume, since it can change in system settings while the app is away.
 */
@Stable
class PhotoAccessState internal constructor(private val context: Context) {
    private val screenshots = RecentScreenshots(context)
    internal var launcher: ManagedActivityResultLauncher<Array<String>, Map<String, Boolean>>? = null

    var access by mutableStateOf(screenshots.access())
        private set

    /** Android won't show the photos dialog again: [request] opens app settings instead. */
    var deniedForever by mutableStateOf(screenshots.deniedForever(context as? Activity))
        private set

    fun refresh() {
        access = screenshots.access()
        deniedForever = screenshots.deniedForever(context as? Activity)
    }

    /**
     * Asks for all photos so Recent screenshots can turn on: the system dialog, or app settings
     * when Android won't show the dialog again.
     */
    fun request() {
        val launcher = launcher
        if (deniedForever || launcher == null) {
            openAppSettings(context)
        } else {
            screenshots.markRequested()
            launcher.launch(RecentScreenshots.REQUEST)
        }
    }
}

@Composable
fun rememberPhotoAccess(): PhotoAccessState {
    val context = LocalContext.current
    val state = remember(context) { PhotoAccessState(context) }
    state.launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        state.refresh()
        // Asking always means turning Recent screenshots on. Limited access keeps the setting on
        // too: Home and Settings then ask for all photos, and screenshots appear once allowed.
        AppGraph.settings.update { it.copy(recentScreenshots = state.access != PhotoAccess.NONE) }
    }
    LifecycleResumeEffect(state) {
        state.refresh()
        onPauseOrDispose { }
    }
    return state
}

internal fun openAppSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
    )
}
