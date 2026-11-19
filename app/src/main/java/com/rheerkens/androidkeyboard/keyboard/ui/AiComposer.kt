package com.rheerkens.androidkeyboard.keyboard.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rheerkens.androidkeyboard.ai.AiService
import com.rheerkens.androidkeyboard.ai.AiSetup
import com.rheerkens.androidkeyboard.ai.AiTask
import com.rheerkens.androidkeyboard.ai.ImageModel
import com.rheerkens.androidkeyboard.ai.ImageQuality
import com.rheerkens.androidkeyboard.keyboard.KeyRollover
import com.rheerkens.androidkeyboard.ui.SlimIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/*
 * The keyboard's AI: the composer floats just above the strip (like a chat app's message box, with
 * the defaults chip and Create in it), its choices take the keys' place while picked, and a card
 * above the strip offers a finished image. The app moves up above them, so its text box stays in view.
 */

/** Controls with nothing to do yet (Create before anything is typed). */
internal const val DIMMED_ALPHA = 0.45f

/** Between the keyboard's top and the card above it. */
private val CARD_GAP = 4.dp

/**
 * Shown just above the keyboard (an [AbovePopup] anchored to the keyboard's top): the composer while
 * a prompt is open, otherwise a finished image, if any. Its height goes to [KeyboardActions.onAiOverlayHeight],
 * so the app moves up above it instead of being covered.
 */
@Composable
internal fun AiOverlay(
    ai: AiUi, widthDp: Float, viewportHeightDp: Float, clock: () -> Long, actions: KeyboardActions, rollover: KeyRollover,
) {
    val width = (widthDp - 12f).coerceAtLeast(200f).dp
    // The room above the keyboard (its top on the screen: the keyboard's own window is only as tall
    // as the keys), so nothing grows off the screen in landscape or with a floating keyboard near the top.
    var above by remember { mutableStateOf(viewportHeightDp.dp) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val gap = with(density) { CARD_GAP.roundToPx() }
    val report = remember(actions, gap) { { height: Int -> actions.onAiOverlayHeight(if (height > 0) height + gap else 0) } }
    val showing = when {
        ai.promptOpen -> 1
        ai.ready != null -> 2
        ai.failure != null -> 3
        else -> 0
    }
    DisposableEffect(showing) {
        if (showing == 0) report(0)
        onDispose { report(0) }
    }
    Box(Modifier.fillMaxWidth().height(0.dp).onGloballyPositioned { coordinates ->
        above = with(density) { coordinates.positionOnScreen().y.toDp() }
    }) {
        val maxHeight = (above - 16.dp).coerceIn(96.dp, 420.dp)
        when (showing) {
            1 -> AbovePopup(gap = CARD_GAP) { AiComposerBox(ai, width, maxHeight, clock, actions, rollover, report) }
            // Keyed by the card's image or failure: a newer one starts a fresh card, so a press still held
            // on the old one can't act on it.
            2 -> ai.ready?.let { ready ->
                androidx.compose.runtime.key(ready.entry.key) { AbovePopup(gap = CARD_GAP) { AiReadyCard(ready, width, maxHeight, actions, report) } }
            }
            3 -> ai.failure?.let { failure ->
                androidx.compose.runtime.key(failure.id) { AbovePopup(gap = CARD_GAP) { AiFailureCard(failure, width, actions, report) } }
            }
        }
    }
}

/** The prompt, typed with the keys (or dictated with the mic), with the defaults chip and Create. */
@Composable
private fun AiComposerBox(
    ai: AiUi, width: Dp, maxHeight: Dp, clock: () -> Long, actions: KeyboardActions, rollover: KeyRollover, report: (Int) -> Unit,
) {
    val c = LocalKeyboardColors.current
    val action = ai.promptAction ?: return
    // Closed some other way (Back, another field): keys still held were meant for the prompt.
    DisposableEffect(rollover) { onDispose { rollover.cancelAll() } }
    Column(
        Modifier.width(width).heightIn(max = maxHeight).onSizeChanged { report(it.height) }
            .shadow(8.dp, RoundedCornerShape(16.dp))
            .background(c.background, RoundedCornerShape(16.dp))
            .border(1.dp, c.divider, RoundedCornerShape(16.dp))
            .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
    ) {
        // Everything but the chip and Create scrolls when there is little room above the keyboard
        // (landscape, a floating keyboard), so the running rows' Stop and the prompt stay reachable
        // and the row with Create is always shown.
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            // Keyed, so a row that goes away (its image arrived) can't hand its Stop press to the next one.
            ai.tasks.forEach { task -> androidx.compose.runtime.key(task.id) { RunningRow(task, clock, actions) } }
            ai.note?.let { note ->
                Text(note.text, color = if (note.warn) c.amber else c.muted, fontSize = 13.sp, maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp).semantics { liveRegion = LiveRegionMode.Polite })
            }
            PromptText(ai, if (ai.tasks.isEmpty()) action.promptHint else "Describe another image…", action.label, actions)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            ModelChip(ai, actions)
            Spacer(Modifier.weight(1f))
            val setup = ai.setup
            val ready = setup != null || (ai.prompt.isNotBlank() && !ai.dictating)
            val description = when {
                setup != null -> "${setup.action}, in the AI settings"
                ai.dictating -> "${action.label}, after the dictation"
                else -> action.button
            }
            PressTarget(description, Modifier.height(48.dp),
                onTap = when {
                    setup != null -> ({ actions.onOpenAiSettings(setup.setting) })
                    ready -> ({ rollover.flush(); actions.onAiPromptSubmit() })
                    else -> null
                }) { pressed ->
                Box(
                    Modifier.fillMaxHeight().padding(vertical = 4.dp).alpha(if (ready) 1f else DIMMED_ALPHA)
                        .background(if (pressed) c.enterPressed else c.enter, RoundedCornerShape(20.dp))
                        .padding(horizontal = 18.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(setup?.action ?: action.button, color = c.enterInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1)
                }
            }
        }
    }
}

/**
 * The prompt so far, with dictated words still being heard underlined at the cursor, and a steady
 * caret (the app's own caret may blink in its field above; this one says where the keys type). A tap
 * places the cursor; the pointing stick moves it.
 */
@Composable
private fun PromptText(ai: AiUi, hint: String, label: String, actions: KeyboardActions) {
    val c = LocalKeyboardColors.current
    val prompt = ai.prompt
    val provisional = ai.provisional
    val at = ai.cursor.coerceIn(0, prompt.length)
    val scroll = rememberScrollState()
    var layout by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    // Dictated words show where they will go: at the cursor, with the space they will get.
    val before = prompt.substring(0, at)
    val heard = if (provisional.isEmpty()) "" else (if (before.isEmpty() || before.endsWith(' ')) "" else " ") + provisional
    val caretAt = at + heard.length
    LaunchedEffect(prompt, provisional, at) {
        layout?.let { result ->
            val rect = result.getCursorRect(caretAt.coerceAtMost(result.layoutInput.text.length))
            val viewport = scroll.viewportSize
            if (rect.bottom > scroll.value + viewport) scroll.scrollTo((rect.bottom - viewport).toInt().coerceAtLeast(0))
            else if (rect.top < scroll.value) scroll.scrollTo(rect.top.toInt())
        }
    }
    Box(
        Modifier.fillMaxWidth().heightIn(min = 40.dp, max = 104.dp)
            // Outlined like a focused text box: the keys type here, not into the app.
            .background(c.key, RoundedCornerShape(10.dp)).border(2.dp, c.enter, RoundedCornerShape(10.dp))
            .verticalScroll(scroll)
            // No live region: the keys already speak each letter, so the prompt isn't read out on every key.
            .clearAndSetSemantics {
                contentDescription = "$label prompt" + if (prompt.isEmpty() && provisional.isEmpty()) ", empty. $hint" else ": $prompt $provisional"
            }
            .padding(horizontal = 10.dp, vertical = 10.dp),
    ) {
        if (prompt.isEmpty() && provisional.isEmpty()) {
            // Like a text box: the caret sits at the start, over the hint, which lines up with typed text.
            Text(hint, color = c.muted, fontSize = 15.sp, lineHeight = 20.sp, maxLines = 1, modifier = Modifier.padding(start = 5.dp))
            Caret(Modifier.align(Alignment.CenterStart))
        } else {
            val text = buildAnnotatedString {
                append(before)
                if (heard.isNotEmpty()) withStyle(SpanStyle(textDecoration = TextDecoration.Underline, color = c.muted)) { append(heard) }
                append(prompt.substring(at))
            }
            val caret = c.enter
            Text(
                text, color = c.keyText, fontSize = 15.sp, lineHeight = 20.sp,
                onTextLayout = { layout = it },
                modifier = Modifier.fillMaxWidth()
                    .pointerInput(prompt, heard.isEmpty()) {
                        detectTapGestures { offset ->
                            val result = layout ?: return@detectTapGestures
                            // Words still being heard aren't the prompt's yet: the cursor stays outside them.
                            if (heard.isEmpty()) actions.onAiPromptCursor(result.getOffsetForPosition(offset))
                        }
                    }
                    .drawWithContent {
                        drawContent()
                        val result = layout ?: return@drawWithContent
                        val rect = result.getCursorRect(caretAt.coerceAtMost(result.layoutInput.text.length))
                        val w = with(density) { 2.dp.toPx() }
                        drawRect(caret, topLeft = androidx.compose.ui.geometry.Offset(rect.left.coerceAtMost(size.width - w), rect.top),
                            size = androidx.compose.ui.geometry.Size(w, rect.height))
                    },
            )
        }
    }
}

/** The defaults, like a chat app's model chip: "Flare · Fast · ≈$0.01". Tap to change them over the keys. */
@Composable
private fun ModelChip(ai: AiUi, actions: KeyboardActions) {
    val c = LocalKeyboardColors.current
    val label = chipLabel(ai)
    PressTarget(
        (if (ai.optionsOpen) "Close image options. " else "Image options. ") + label,
        Modifier.height(48.dp).widthIn(max = 260.dp),
        onTap = actions::onToggleAiOptions,
    ) { pressed ->
        Row(
            Modifier.fillMaxHeight().padding(vertical = 4.dp)
                .background(if (pressed) c.keyPressed else c.key, RoundedCornerShape(20.dp))
                .then(if (ai.optionsOpen) Modifier.border(1.5.dp, c.enter, RoundedCornerShape(20.dp)) else Modifier)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(7.dp).background(if (ai.setup != null) c.amber else c.green, CircleShape))
            Spacer(Modifier.width(6.dp))
            Text(label, color = c.keyText, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false))
            Spacer(Modifier.width(4.dp))
            Icon(if (ai.optionsOpen) SlimIcons.ChevronUp else SlimIcons.ChevronDown, null, tint = c.muted,
                modifier = Modifier.size(14.dp))
        }
    }
}

/**
 * Always the service first: "OpenAI · Flare · Fast · ≈$0.01", "Your server · Fast", or what it still needs,
 * "OpenAI · key needed". The own server's model shows in the options.
 */
internal fun chipLabel(ai: AiUi): String {
    ai.setup?.let { return "${if (ai.service == AiService.OPENAI) "OpenAI" else "Your server"} · ${it.chip}" }
    return if (ai.service == AiService.OPENAI) {
        val model = ImageModel.entries.firstOrNull { it.id == ai.imageModel }?.short ?: ai.imageModel
        "OpenAI · $model · ${ai.imageQuality.label} · ${ai.imageQuality.cost.replace("about ", "≈")}"
    } else {
        "Your server · ${ai.imageQuality.label}"
    }
}

/**
 * A finished image: its thumbnail (tap it, the text or ⌃ for a larger preview), Insert where the app
 * takes images, and dismiss. Nothing goes in until a tap. The app moves up above the whole card,
 * preview included, so its text box stays in view for pasting.
 */
@Composable
private fun AiReadyCard(ready: AiReady, width: Dp, maxHeight: Dp, actions: KeyboardActions, report: (Int) -> Unit) {
    val c = LocalKeyboardColors.current
    var preview by remember(ready.entry.key) { mutableStateOf(false) }
    // While the preview is open the card stays; its 30 s start again once it closes.
    LaunchedEffect(preview) { actions.onAiCardHold(preview) }
    Column(
        Modifier.width(width).onSizeChanged { report(it.height) }.shadow(8.dp, RoundedCornerShape(14.dp))
            .background(c.background, RoundedCornerShape(14.dp)).border(1.dp, c.divider, RoundedCornerShape(14.dp))
            .padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
    ) {
        if (preview) {
            val big by produceState<Bitmap?>(null, ready.entry.key) {
                value = withContext(Dispatchers.IO) { actions.loadPreview(ready.entry) }
            }
            val shown = big ?: ready.entry.thumbnail
            if (shown != null) {
                // Sized to the picture, so the rounded corners are the picture's; at most about half
                // the room above the keyboard, so the chat keeps some of its messages.
                val cap = (maxHeight * 0.5f).coerceIn(48.dp, 200.dp)
                Box(Modifier.fillMaxWidth().padding(end = 4.dp, bottom = 6.dp), contentAlignment = Alignment.Center) {
                    Image(shown.asImageBitmap(), "Preview of the image",
                        Modifier.heightIn(max = cap).aspectRatio(shown.width.toFloat() / shown.height.coerceAtLeast(1), matchHeightConstraintsFirst = true)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClickLabel = "Hide the preview") { preview = false },
                        contentScale = ContentScale.Crop)
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            val thumb = ready.entry.thumbnail
            val copiedOnly = !ready.pasteable && ready.onClipboard
            Row(
                Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                    .clickable(enabled = thumb != null, onClickLabel = if (preview) "Hide the preview" else "Preview the image") {
                        preview = !preview
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (thumb != null && !preview) {
                    Image(thumb.asImageBitmap(), null, Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop)
                    Spacer(Modifier.width(10.dp))
                }
                Column(Modifier.weight(1f).padding(vertical = 2.dp).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
                    Text(if (copiedOnly) "Image copied" else "Image ready", color = c.keyText, fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(
                        when {
                            copiedOnly -> "Touch and hold the text box, then tap Paste"
                            !ready.pasteable -> "In your clipboard row"
                            ready.onClipboard -> "Also copied"
                            else -> "Also in your clipboard row"
                        },
                        color = c.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            // The same spot expands and collapses the preview.
            if (thumb != null) {
                CardIcon(if (preview) SlimIcons.ChevronDown else SlimIcons.ChevronUp,
                    if (preview) "Hide the preview" else "Preview the image") { preview = !preview }
            }
            if (ready.pasteable) {
                Spacer(Modifier.width(4.dp))
                CardButton("Insert", "Insert the image", primary = true) { actions.onInsertAiResult(ready.entry.key) }
            }
            CardIcon(SlimIcons.Close, "Dismiss the image card") { actions.onDismissAiResult(ready.entry.key) }
        }
    }
}

/** An action that failed: why, Try again (the composer with its prompt) and dismiss. */
@Composable
private fun AiFailureCard(failure: AiFailure, width: Dp, actions: KeyboardActions, report: (Int) -> Unit) {
    val c = LocalKeyboardColors.current
    Row(
        Modifier.width(width).onSizeChanged { report(it.height) }.shadow(8.dp, RoundedCornerShape(14.dp))
            .background(c.background, RoundedCornerShape(14.dp)).border(1.dp, c.divider, RoundedCornerShape(14.dp))
            .padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).background(c.amber, CircleShape))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(vertical = 2.dp).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
            // Which prompt failed: several can run, and the user may have looked away meanwhile.
            Text("Couldn't create “${failure.prompt}”", color = c.keyText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(failure.message, color = c.muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(4.dp))
        val action = failure.fixLabel ?: "Try again"
        // The way forward, so it is the card's filled button; ✕ stays tonal.
        CardButton(action, if (failure.fixLabel != null) "$action in the AI settings" else "Try again: ${failure.prompt.take(60)}",
            primary = true) { actions.onRetryAiFailure(failure.id) }
        Spacer(Modifier.width(4.dp))
        CardIcon(SlimIcons.Close, "Dismiss") { actions.onDismissAiResult(failure.cardId) }
    }
}

/** A round icon button on the cards, like the strip's ✕: 34 dp shown, 48 dp to touch. */
@Composable
private fun CardIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onTap: () -> Unit) {
    val c = LocalKeyboardColors.current
    PressTarget(description, Modifier.size(48.dp), onTap = onTap) { pressed ->
        Box(Modifier.fillMaxSize().padding(7.dp).background(if (pressed) c.keyPressed else c.keyMod, CircleShape),
            contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = c.keyText, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun CardButton(label: String, description: String, primary: Boolean, onTap: () -> Unit) {
    val c = LocalKeyboardColors.current
    PressTarget(description, Modifier.height(48.dp), onTap = onTap) { pressed ->
        Box(
            Modifier.fillMaxHeight().padding(vertical = 4.dp)
                .background(
                    when {
                        primary && pressed -> c.enterPressed
                        primary -> c.enter
                        pressed -> c.keyPressed
                        else -> c.keyMod
                    },
                    RoundedCornerShape(20.dp),
                ).padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, color = if (primary) c.enterInk else c.keyText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** An image being made: what was asked, how long it has taken, and Stop. */
@Composable
private fun RunningRow(task: AiTask, clock: () -> Long, actions: KeyboardActions) {
    val c = LocalKeyboardColors.current
    var now by remember { mutableLongStateOf(clock()) }
    LaunchedEffect(task.id) {
        while (true) {
            now = clock()
            delay(1_000)
        }
    }
    val seconds = ((now - task.startedAtMs) / 1000).coerceAtLeast(0)
    Row(Modifier.fillMaxWidth().padding(start = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(16.dp), color = c.enter, trackColor = c.keyPressed, strokeWidth = 2.dp)
        Spacer(Modifier.width(10.dp))
        // The timer stays out of TalkBack's reading; the row says what it is.
        Text("“${task.prompt}”", color = c.keyText, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { contentDescription = "Creating: ${task.prompt.take(80)}" })
        Text(if (seconds < 60) "${seconds}s" else "${seconds / 60}:${"%02d".format(seconds % 60)}", color = c.keyText,
            fontSize = 13.sp, modifier = Modifier.padding(horizontal = 6.dp).clearAndSetSemantics {})
        CardButton("Stop", "Stop ${task.action.label.lowercase()}: ${task.prompt.take(60)}", primary = false) {
            actions.onStopAiTask(task.id)
        }
    }
}

/**
 * The defaults chip's choices, where the keys were while it is open: the service, then its model
 * (or what it still needs, or the server's address), then the quality, each one row of switches. A
 * shortcut opens the AI settings; Done goes back to the keys.
 */
@Composable
internal fun AiOptionsPanel(ai: AiUi, height: Dp, actions: KeyboardActions) {
    val c = LocalKeyboardColors.current
    val openAi = ai.service == AiService.OPENAI
    Column(Modifier.fillMaxWidth().height(height).verticalScroll(rememberScrollState()).padding(horizontal = 8.dp)) {
        Row(Modifier.fillMaxWidth().height(46.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Image options", color = c.keyText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f).padding(start = 4.dp).semantics { heading() })
            PanelButton("AI settings", "Open the AI settings", icon = SlimIcons.Settings, background = c.keyMod, height = 46.dp) {
                actions.onOpenAiSettings(null)
            }
            Spacer(Modifier.width(12.dp))
            PanelButton("Done", "Done, back to the keys", icon = null, background = c.keyMod, height = 46.dp,
                ink = c.enter, onTap = actions::onToggleAiOptions)
        }
        OptionRow("Service") {
            Segment("OpenAI", null, openAi, Modifier.weight(1f)) { actions.onSelectAiService(AiService.OPENAI) }
            Segment("Your server", null, !openAi, Modifier.weight(1f)) { actions.onSelectAiService(AiService.OWN_SERVER) }
        }
        val setup = ai.setup
        when {
            setup != null -> OptionRow(setup.row) {
                InfoRow(setup.value, warn = true, action = setup.action.substringBefore(' '),
                    description = "${setup.action} in the AI settings", modifier = Modifier.weight(1f)) {
                    actions.onOpenAiSettings(setup.setting)
                }
            }
            openAi -> OptionRow("Model") {
                ImageModel.entries.forEach { model ->
                    Segment(model.short, model.speed, ai.imageModel == model.id, Modifier.weight(1f)) {
                        actions.onSelectImageModel(model.id)
                    }
                }
            }
            else -> OptionRow("Server") {
                InfoRow(ai.serverHost + ai.serverModel.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(), warn = false,
                    action = "Edit", description = "Edit the server address", modifier = Modifier.weight(1f)) {
                    actions.onOpenAiSettings(AiSetup.NO_SERVER.setting)
                }
            }
        }
        OptionRow("Quality") {
            ImageQuality.entries.forEach { quality ->
                Segment(quality.label, if (openAi) quality.cost.replace("about ", "≈") else null,
                    ai.imageQuality == quality, Modifier.weight(1f)) { actions.onSelectImageQuality(quality) }
            }
        }
    }
}

/** A label and its switches on one line. */
@Composable
private fun OptionRow(label: String, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    val c = LocalKeyboardColors.current
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = c.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
            modifier = Modifier.width(62.dp).padding(start = 4.dp))
        Row(Modifier.weight(1f).height(46.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), content = content)
    }
}

/** One switch of a row: the picked one is tinted and outlined. A swipe on it scrolls the panel instead. */
@Composable
private fun Segment(title: String, detail: String?, selected: Boolean, modifier: Modifier, onTap: () -> Unit) {
    val c = LocalKeyboardColors.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier.fillMaxHeight()
            .selectable(selected, interactionSource = source, indication = null, role = Role.RadioButton, onClick = onTap)
            .semantics(mergeDescendants = true) { contentDescription = title + (detail?.let { ", $it" } ?: "") }
            .background(
                when {
                    pressed -> c.keyPressed
                    selected -> c.keyOn
                    else -> c.key
                },
                shape,
            )
            .then(if (selected) Modifier.border(1.5.dp, c.enter, shape) else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Text(title, color = c.keyText, fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (detail != null) {
            Text(detail, color = c.muted, fontSize = 12.sp, maxLines = 1)
        }
    }
}

/** A value set in the AI settings (the server, a missing key), with a button that opens it there. */
@Composable
private fun InfoRow(text: String, warn: Boolean, action: String, description: String, modifier: Modifier, onTap: () -> Unit) {
    val c = LocalKeyboardColors.current
    // The whole row opens it; the button is the cue.
    Row(
        modifier.fillMaxHeight().clip(RoundedCornerShape(10.dp)).background(c.key)
            .clickable(onClickLabel = description, onClick = onTap).padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = if (warn) c.amber else c.keyText, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f))
        PanelButton(action, description, icon = null, background = c.keyMod, height = 42.dp, onTap = onTap)
    }
}

@Composable
private fun PanelButton(label: String, description: String, icon: androidx.compose.ui.graphics.vector.ImageVector?,
    background: Color, height: Dp = 48.dp, ink: Color? = null, onTap: () -> Unit) {
    val c = LocalKeyboardColors.current
    PressTarget(description, Modifier.height(height), onTap = onTap) { pressed ->
        Row(
            Modifier.fillMaxHeight().padding(vertical = 4.dp)
                .background(if (pressed) c.keyPressed else background, RoundedCornerShape(10.dp))
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, null, tint = c.keyText, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(label, color = ink ?: c.keyText, fontSize = 13.sp, fontWeight = if (ink != null) FontWeight.Bold else FontWeight.SemiBold)
        }
    }
}

/**
 * ✨ at the strip's right end; ✕ in the same place while the composer is open, so one spot opens and
 * closes it. While images are being made a ring turns around ✨, with their number when several.
 */
@Composable
internal fun AiButton(running: Int, composerOpen: Boolean, actions: KeyboardActions) {
    val c = LocalKeyboardColors.current
    val label = (if (composerOpen) "Close the AI prompt" else "Create with AI") + when (running) {
        0 -> ""
        1 -> ", 1 image being made"
        else -> ", $running images being made"
    }
    PressTarget(label, Modifier.width(48.dp).height(44.dp), onTap = actions::onAiButton) { pressed ->
        Box(
            Modifier.fillMaxSize().padding(horizontal = 5.dp, vertical = 3.dp)
                .background(if (pressed || composerOpen) c.keyPressed else Color.Transparent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (composerOpen) {
                Icon(SlimIcons.Close, null, tint = c.keyText, modifier = Modifier.size(20.dp))
            } else {
                if (running > 0) {
                    CircularProgressIndicator(Modifier.size(34.dp), color = c.enter, trackColor = c.enter.copy(alpha = .25f), strokeWidth = 3.dp)
                }
                Icon(SlimIcons.Sparkle, null, tint = c.enter, modifier = Modifier.size(if (running > 0) 18.dp else 22.dp))
                if (running > 1) {
                    Text("$running", color = c.enterInk, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.TopEnd).background(c.enter, RoundedCornerShape(7.dp))
                            .padding(horizontal = 4.dp))
                }
            }
        }
    }
}

/** The prompt's text cursor before anything is typed: steady, so it reads as where the keys type. */
@Composable
private fun Caret(modifier: Modifier = Modifier) {
    val c = LocalKeyboardColors.current
    Box(modifier.width(2.dp).height(20.dp).background(c.enter))
}
