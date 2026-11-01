package com.rheerkens.androidkeyboard.ime

import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * Lifecycle, saved-state, and ViewModel owners for Compose inside an InputMethodService,
 * which has none of its own. Driven by the service:
 * onCreate → CREATED, onStartInputView → RESUMED, onFinishInputView → CREATED, onDestroy → DESTROYED.
 *
 * [attachTo] must be called on the IME window's decor view; otherwise Compose fails with
 * "ViewTreeLifecycleOwner not found".
 */
class ImeLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val registry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry
    override val viewModelStore: ViewModelStore = ViewModelStore()

    fun onCreate() {
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        registry.currentState = Lifecycle.State.CREATED
    }

    fun onShow() {
        if (registry.currentState != Lifecycle.State.DESTROYED) registry.currentState = Lifecycle.State.RESUMED
    }

    fun onHide() {
        if (registry.currentState.isAtLeast(Lifecycle.State.STARTED)) registry.currentState = Lifecycle.State.CREATED
    }

    fun onDestroy() {
        registry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
    }

    fun attachTo(view: View) {
        view.setViewTreeLifecycleOwner(this)
        view.setViewTreeSavedStateRegistryOwner(this)
        view.setViewTreeViewModelStoreOwner(this)
    }
}
