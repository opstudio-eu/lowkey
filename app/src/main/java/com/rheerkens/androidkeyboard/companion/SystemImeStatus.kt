package com.rheerkens.androidkeyboard.companion

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.rheerkens.androidkeyboard.ime.SlimKeyboardService
import kotlinx.coroutines.delay

/** Refresh after returning from Android settings or dismissing its keyboard picker. */
@Composable
internal fun rememberImeStatus(): SystemImeStatus.Status {
    val context = LocalContext.current
    var status by remember(context) { mutableStateOf(SystemImeStatus.read(context)) }
    LifecycleResumeEffect(context) {
        status = SystemImeStatus.read(context)
        onPauseOrDispose { }
    }
    val focused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(context, focused) {
        status = SystemImeStatus.read(context)
        delay(600)
        status = SystemImeStatus.read(context)
    }
    return status
}

/** Real system state for the setup checklist. */
object SystemImeStatus {
    data class Status(val enabled: Boolean, val selected: Boolean, val currentLabel: String?)

    private fun component(context: Context) = ComponentName(context, SlimKeyboardService::class.java)

    fun read(context: Context): Status {
        val imm = context.getSystemService(InputMethodManager::class.java)
        val ours = component(context)
        val enabledList = imm.enabledInputMethodList
        val enabled = enabledList.any { it.component == ours }
        val currentInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            imm.currentInputMethodInfo
        } else {
            val id = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            imm.inputMethodList.firstOrNull { it.id == id }
        }
        val currentComponent = currentInfo?.component
            ?: Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
                ?.let(ComponentName::unflattenFromString)
        val label = currentInfo?.loadLabel(context.packageManager)?.toString()
        return Status(enabled = enabled, selected = currentComponent == ours, currentLabel = label)
    }

    fun openInputMethodSettings(context: Context) {
        context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun showPicker(context: Context) {
        context.getSystemService(InputMethodManager::class.java).showInputMethodPicker()
    }
}
