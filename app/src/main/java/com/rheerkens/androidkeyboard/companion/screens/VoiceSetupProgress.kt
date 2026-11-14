package com.rheerkens.androidkeyboard.companion.screens

import com.rheerkens.androidkeyboard.models.ModelInfo
import com.rheerkens.androidkeyboard.models.ModelStoreState

/** Actual setup progress is independent of a model being considered in the picker. */
internal class VoiceSetupProgress(hasMic: Boolean, state: ModelStoreState, selected: ModelInfo) {
    val activeModel = state.activeModel?.takeIf { it.id in state.installed }
    val changingModel = activeModel?.id != selected.id
    val modelReady = activeModel != null
    val ready = hasMic && modelReady
    val step = if (!hasMic) 1 else if (!modelReady) 2 else 3
}
