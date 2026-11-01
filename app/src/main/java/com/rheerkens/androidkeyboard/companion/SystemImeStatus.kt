package com.rheerkens.androidkeyboard.companion

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import com.rheerkens.androidkeyboard.ime.SlimKeyboardService

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
