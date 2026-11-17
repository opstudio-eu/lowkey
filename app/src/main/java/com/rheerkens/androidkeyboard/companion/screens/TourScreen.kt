package com.rheerkens.androidkeyboard.companion.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rheerkens.androidkeyboard.AppGraph
import com.rheerkens.androidkeyboard.companion.Destination
import com.rheerkens.androidkeyboard.companion.SystemImeStatus
import com.rheerkens.androidkeyboard.companion.TourGoal
import com.rheerkens.androidkeyboard.companion.TourProgress
import com.rheerkens.androidkeyboard.companion.TourStep
import com.rheerkens.androidkeyboard.companion.insertedWords
import com.rheerkens.androidkeyboard.companion.theme.isDark
import com.rheerkens.androidkeyboard.ui.SlimIcons

/** What a tour step starts with: its practice text, the cursor at the end. */
private fun practiceField(step: TourStep) = TextFieldValue(step.practice, TextRange(step.practice.length))

private val progressSaver = listSaver<TourProgress, Int>(
    save = { listOf(it.step.ordinal, it.chars, it.lines, it.selected, it.wordsDeleted, it.dictatedWords) },
    restore = { TourProgress(TourStep.entries[it[0]], it[1], it[2], it[3], it[4], it[5]) },
)

/**
 * Learn the gestures: a few steps that each ask for one thing on the keyboard, in a practice
 * field, and tick it off when the keyboard reports it was done. Then a list of what else is worth
 * knowing. Every step can be skipped, and so can the whole tour.
 */
@Composable
fun TourScreen(modifier: Modifier, ime: SystemImeStatus.Status, navigate: (Destination) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    var progress by rememberSaveable(stateSaver = progressSaver) { mutableStateOf(TourProgress()) }
    var finished by rememberSaveable { mutableStateOf(false) }
    var field by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(practiceField(TourStep.CURSOR)) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    // Offered once by itself; from here on it is a choice on Home.
    LaunchedEffect(Unit) { AppGraph.settings.update { if (it.tourSeen) it else it.copy(tourSeen = true) } }
    LaunchedEffect(Unit) { AppGraph.gestures.collect { progress = progress.on(it) } }

    // Dictations finished since the dictation step began count for it.
    val sessions by AppGraph.sessionLog.entries.collectAsStateWithLifecycle()
    var sessionsBefore by rememberSaveable { mutableLongStateOf(-1L) }
    LaunchedEffect(progress.step) {
        if (progress.step == TourStep.DICTATE && sessionsBefore < 0) sessionsBefore = sessions.maxOfOrNull { it.sessionId } ?: 0L
    }
    LaunchedEffect(sessions, sessionsBefore) {
        if (sessionsBefore < 0) return@LaunchedEffect
        val words = sessions.filter { it.sessionId > sessionsBefore }.sumOf { it.insertedWords() }
        if (words > progress.dictatedWords) progress = progress.onDictated(words - progress.dictatedWords)
    }

    val models by AppGraph.models.state.collectAsStateWithLifecycle()
    var hasMic by remember { mutableStateOf(AppGraph.hasMicPermission(context)) }
    LifecycleResumeEffect(Unit) { hasMic = AppGraph.hasMicPermission(context); onPauseOrDispose { } }
    val voiceReady = (hasMic || AppGraph.usesTestClips()) && models.activeModel?.id?.let { it in models.installed } == true

    fun open(next: TourProgress) {
        progress = next
        field = practiceField(next.step)
    }
    fun advance() {
        val next = progress.next()
        if (next == null) {
            keyboard?.hide()
            finished = true
        } else open(next)
    }
    // Without voice set up there is nothing to practise in the dictation step: it only offers the setup.
    val needsVoice = progress.step == TourStep.DICTATE && !voiceReady
    // The practice field has the keyboard up from the start of every step.
    LaunchedEffect(progress.step, finished, needsVoice) {
        if (finished || needsVoice) {
            keyboard?.hide()
            return@LaunchedEffect
        }
        withFrameNanos { }
        focus.requestFocus()
        keyboard?.show()
    }

    Column(
        modifier.imePadding().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (finished) {
            TourEnd(onClose = onClose, onAgain = { finished = false; sessionsBefore = -1L; open(TourProgress()) })
            return@Column
        }
        val step = progress.step
        val number = step.ordinal + 1
        val count = TourStep.entries.size
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Step $number of $count", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClose, modifier = Modifier.heightIn(min = 48.dp)) { Text("Skip tour") }
        }
        LinearProgressIndicator(
            progress = { (step.ordinal + if (progress.done) 1f else 0.5f) / count },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(step.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        Text(step.intro, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)

        if (!ime.selected) {
            TourNotice("Lowkey isn't the keyboard in use right now, so it can't see what you do.", "Switch keyboard") {
                SystemImeStatus.showPicker(context)
            }
        } else if (needsVoice) {
            TourNotice(
                "Voice typing isn't set up yet. It needs the microphone and a speech model, a one-time download.",
                "Set up voice",
            ) { navigate(Destination.MODELS) }
        }

        if (!needsVoice) {
            OutlinedTextField(
                value = field,
                onValueChange = { field = it },
                placeholder = { Text(if (step == TourStep.DICTATE) "What you say appears here" else "Practice here") },
                minLines = 3,
                maxLines = 5,
                shape = RoundedCornerShape(18.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("tour_practice")
                    .semantics { contentDescription = "Practice field" },
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { progress.goals.forEach { GoalRow(it) } }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val last = progress.next() == null
            if (!progress.done) {
                TextButton(onClick = ::advance, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(if (last) "Finish without this" else "Skip this step")
                }
            }
            Box(Modifier.weight(1f))
            Button(onClick = ::advance, enabled = progress.done, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(if (last) "Finish" else "Next")
            }
        }
    }
}

/** A goal of the step, with a mark that fills in when the keyboard saw it done. */
@Composable
private fun GoalRow(goal: TourGoal) {
    val colors = MaterialTheme.colorScheme
    val success = if (colors.isDark) Color(0xFF5CD193) else Color(0xFF1E7A4C)
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
            stateDescription = if (goal.done) "Done" else "Not done yet"
            liveRegion = LiveRegionMode.Polite
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(24.dp).background(if (goal.done) success else colors.surfaceContainerHigh, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (goal.done) Icon(SlimIcons.Check, null, Modifier.size(14.dp), tint = if (colors.isDark) colors.surface else Color.White)
        }
        Text(
            goal.text, style = MaterialTheme.typography.bodyLarge,
            color = if (goal.done) colors.onSurface else colors.onSurfaceVariant, modifier = Modifier.weight(1f),
        )
    }
}

/** Why the step can't be done right now, and the one thing that fixes it. */
@Composable
private fun TourNotice(text: String, action: String, onAction: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(18.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp)) { Text(action) }
    }
}

/** What else the keyboard does that nothing on it shows. */
private val moreTips = listOf(
    "Hold a letter for its accents and its number.",
    "Hold the emoji key to switch between your languages.",
    "Tap the three dots beside the microphone for select all, cut, copy, paste, undo and the keyboard's size.",
    "Swipe down on the strip above the keys to shrink the keyboard to a slim bar, and up to bring it back.",
    "Press Space twice for a period.",
    "What you copy waits in the strip above the keys: tap it to paste.",
)

@Composable
private fun TourEnd(onClose: () -> Unit, onAgain: () -> Unit) {
    Text(
        "You know the essentials", style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.padding(top = 8.dp).semantics { heading() },
    )
    Text(
        "A few more things worth trying while you type:",
        style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        moreTips.forEach { tip ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.padding(top = 9.dp).size(6.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                Text(tip, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            }
        }
    }
    Text(
        "This tour stays on Home under Learn the gestures.",
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = onAgain, modifier = Modifier.heightIn(min = 48.dp)) { Text("Start again") }
        Box(Modifier.weight(1f))
        Button(onClick = onClose, modifier = Modifier.heightIn(min = 48.dp)) { Text("Done") }
    }
}
