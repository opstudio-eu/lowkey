package com.rheerkens.androidkeyboard.keyboard.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions as TextKeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PlatformImeOptions
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rheerkens.androidkeyboard.ai.AiAction
import com.rheerkens.androidkeyboard.ai.AiComposerLink
import com.rheerkens.androidkeyboard.ai.AiService
import com.rheerkens.androidkeyboard.ai.AiSetup
import com.rheerkens.androidkeyboard.ai.AiTask
import com.rheerkens.androidkeyboard.ai.ImageApi
import com.rheerkens.androidkeyboard.ai.ImageModel
import com.rheerkens.androidkeyboard.ai.ImageQuality
import com.rheerkens.androidkeyboard.clipboard.ClipEntry
import com.rheerkens.androidkeyboard.ui.SlimIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/*
 * The keyboard's AI: the prompt window sits just above the keyboard, over the app, which doesn't move
 * (a real text box, typed into like any app's, with the defaults chip and Create). A card above the
 * strip offers a finished image; the app moves up above that card, so its text box stays in view.
 */

/** Controls with nothing to do yet (Create before anything is typed). */
internal const val DIMMED_ALPHA = 0.45f

/** Between the keyboard's top and the card above it. */
private val CARD_GAP = 4.dp

/**
 * Shown just above the keyboard (an [AbovePopup] anchored to the keyboard's top): a finished image or a
 * failure, if any. Its height goes to [KeyboardActions.onAiOverlayHeight], so the app moves up above it
 * instead of being covered.
 */
@Composable
internal fun AiOverlay(ai: AiUi, widthDp: Float, viewportHeightDp: Float, actions: KeyboardActions) {
    val width = (widthDp - 12f).coerceAtLeast(200f).dp
    // The room above the keyboard (its top on the screen: the keyboard's own window is only as tall
    // as the keys), so nothing grows off the screen in landscape or with a floating keyboard near the top.
    var above by remember { mutableStateOf(viewportHeightDp.dp) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val gap = with(density) { CARD_GAP.roundToPx() }
    val report = remember(actions, gap) { { height: Int -> actions.onAiOverlayHeight(if (height > 0) height + gap else 0) } }
    val showing = when {
        ai.ready != null -> 1
        ai.failure != null -> 2
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
            // Keyed by the card's image or failure: a newer one starts a fresh card, so a press still held
            // on the old one can't act on it.
            1 -> ai.ready?.let { ready ->
                androidx.compose.runtime.key(ready.entry.key) { AbovePopup(gap = CARD_GAP) { AiReadyCard(ready, width, maxHeight, actions, report) } }
            }
            2 -> ai.failure?.let { failure ->
                androidx.compose.runtime.key(failure.id) { AbovePopup(gap = CARD_GAP) { AiFailureCard(failure, width, actions, report) } }
            }
        }
    }
}

/** What the prompt window's controls do ([com.rheerkens.androidkeyboard.ai.AiPromptActivity]). */
internal interface AiComposerActions {
    /** Create (or Enter): the image starts in the background and the window closes. */
    fun onSubmit()
    /** ✕, Back or a tap outside: the window closes; its words come back next time. */
    fun onClose()
    fun onStop(id: Long)
    /** The defaults chip: shows (or hides) the service, model and quality under the prompt. */
    fun onToggleOptions()
    /** The AI settings page (key, server address, token); with [setting], that row's editor opens. */
    fun onOpenSettings(setting: String?)
    /** The service, model or quality picked under the chip, remembered for the next images. */
    fun onSelectService(service: AiService)
    fun onSelectModel(id: String)
    fun onSelectQuality(quality: ImageQuality)
    /** The picture button left of the box: shows (or hides) the tray of recent images and Photos. */
    fun onToggleTray()
    /** Photos in the tray: the system's photo picker. */
    fun onPickPhotos()
    /** A recent image in the tray, added to the prompt. */
    fun onAddRecent(entry: ClipEntry)
    /** ✕ on an added picture: it isn't sent. */
    fun onRemoveImage(id: Long)
    /** Improve: the AI rewrites the prompt; again while it works stops it. */
    fun onImprove()
    /** Undo after Improve: the words from before. */
    fun onUndoImprove()
}

/**
 * The prompt window's card, just above the keyboard: images being made (each with Stop), the prompt,
 * then the defaults chip, ✕ and Create; the chip's choices open below them. The prompt is a real text
 * field with the focus, so the keyboard, its mic and other voice tools type into it.
 */
@Composable
internal fun AiPromptCard(
    ai: AiUi,
    prompt: TextFieldValue,
    onPromptChange: (TextFieldValue) -> Unit,
    focus: FocusRequester,
    clock: () -> Long,
    actions: AiComposerActions,
    modifier: Modifier = Modifier,
) {
    val c = LocalKeyboardColors.current
    Column(
        modifier.shadow(8.dp, RoundedCornerShape(16.dp))
            .background(c.background, RoundedCornerShape(16.dp))
            .border(1.dp, c.divider, RoundedCornerShape(16.dp))
            .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
    ) {
        if (ai.tasks.isNotEmpty()) {
            Column(Modifier.heightIn(max = 160.dp).verticalScroll(rememberScrollState())) {
                // Keyed, so a row that goes away (its image arrived) can't hand its Stop press to the next one.
                ai.tasks.forEach { task -> androidx.compose.runtime.key(task.id) { RunningRow(task, clock, actions) } }
            }
        }
        ai.note?.let { note ->
            Text(note.text, color = if (note.warn) c.amber else c.muted, fontSize = 13.sp, maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp).semantics { liveRegion = LiveRegionMode.Polite })
        }
        if (ai.images.isNotEmpty()) AddedPictures(ai.images, actions)
        Row(verticalAlignment = Alignment.Bottom) {
            AddPictureButton(ai.trayOpen, ai.images.size < ImageApi.MAX_INPUT_IMAGES, actions)
            PromptField(prompt, onPromptChange, focus,
                when {
                    ai.images.isNotEmpty() -> AiAction.EDIT_IMAGE.promptHint
                    ai.tasks.isEmpty() -> AiAction.CREATE_IMAGE.promptHint
                    else -> "Describe another image…"
                },
                ai.improving, actions, Modifier.weight(1f))
        }
        if (ai.beforeImprove != null && !ai.improving) ImprovedLine(actions)
        if (ai.trayOpen) PictureTray(ai, actions)
        Row(verticalAlignment = Alignment.CenterVertically) {
            // The chip takes what room is left, and shortens its label only when that runs out.
            Box(Modifier.weight(1f)) { ModelChip(ai, actions) }
            CardIcon(SlimIcons.Close, "Close the image prompt", actions::onClose)
            Spacer(Modifier.width(4.dp))
            val setup = ai.setup
            val ready = setup != null || prompt.text.isNotBlank()
            val button = AiAction.CREATE_IMAGE.button
            PressTarget(if (setup != null) "${setup.action}, in the AI settings" else button, Modifier.height(48.dp),
                onTap = when {
                    setup != null -> ({ actions.onOpenSettings(setup.setting) })
                    ready -> actions::onSubmit
                    else -> null
                }) { pressed ->
                Box(
                    Modifier.fillMaxHeight().padding(vertical = 4.dp).alpha(if (ready) 1f else DIMMED_ALPHA)
                        .background(if (pressed) c.enterPressed else c.enter, RoundedCornerShape(20.dp))
                        .padding(horizontal = 18.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(setup?.action ?: button, color = c.enterInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1)
                }
            }
        }
        if (ai.optionsOpen) AiOptions(ai, actions)
    }
}

/**
 * [next], the prompt as typed, on one line and within the length limit: line breaks (pasted, or typed
 * by a physical keyboard's Enter) become spaces, and the cursor stays where it was meant to be.
 */
internal fun flattenPrompt(next: TextFieldValue): TextFieldValue {
    if (next.text.none { it == '\n' || it == '\r' } && next.text.length <= ImageApi.MAX_PROMPT_CHARS) return next
    fun flat(text: String) = text.replace(Regex("[\\r\\n]+"), " ")
    val text = flat(next.text).take(ImageApi.MAX_PROMPT_CHARS)
    // Each run of breaks becomes one space: the cursor moves by what was taken out before it.
    fun at(index: Int) = flat(next.text.substring(0, index.coerceIn(0, next.text.length))).length.coerceAtMost(text.length)
    return TextFieldValue(text, TextRange(at(next.selection.start), at(next.selection.end)))
}

/** A line break was typed (one more break and one more character than before): Enter, from a physical keyboard. */
internal fun typedLineBreak(before: String, after: String): Boolean =
    after.length == before.length + 1 && after.count { it == '\n' } == before.count { it == '\n' } + 1

/**
 * The prompt: a text field like any app's, marked ([AiComposerLink.PROMPT_FIELD]) so the keyboard
 * shows Create on Enter. A typed line break creates the image too; pasted ones become spaces.
 */
@Composable
private fun PromptField(
    value: TextFieldValue, onChange: (TextFieldValue) -> Unit, focus: FocusRequester, hint: String, improving: Boolean,
    actions: AiComposerActions, modifier: Modifier = Modifier,
) {
    val c = LocalKeyboardColors.current
    BasicTextField(
        value = value,
        onValueChange = { next ->
            when {
                typedLineBreak(value.text, next.text) -> actions.onSubmit()
                // While Improve rewrites them, the words stay as they are (Stop, the same button, ends it). Not
                // readOnly: that ends the input session, the keyboard hides, and with it the window.
                improving -> {}
                else -> onChange(flattenPrompt(next))
            }
        },
        textStyle = TextStyle(color = c.keyText, fontSize = 16.sp, lineHeight = 22.sp),
        cursorBrush = SolidColor(c.enter),
        maxLines = 5,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = ImeAction.Go,
            platformImeOptions = PlatformImeOptions(AiComposerLink.PROMPT_FIELD),
        ),
        keyboardActions = TextKeyboardActions(onGo = { actions.onSubmit() }),
        modifier = modifier.padding(bottom = 2.dp).focusRequester(focus)
            .semantics { contentDescription = "Image prompt" },
        decorationBox = { inner ->
            // Outlined like a focused text box, as it is. Improve sits at its end, like a chat app's mic.
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .background(c.key, RoundedCornerShape(10.dp)).border(2.dp, c.enter, RoundedCornerShape(10.dp))
                    .padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Box(Modifier.weight(1f).padding(vertical = 12.dp).alpha(if (improving) DIMMED_ALPHA else 1f),
                    contentAlignment = Alignment.CenterStart) {
                    if (value.text.isEmpty()) {
                        Text(hint, color = c.muted, fontSize = 16.sp, lineHeight = 22.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                    }
                    inner()
                }
                ImproveButton(improving, value.text.isNotBlank(), actions)
            }
        },
    )
}

/**
 * Improve, at the end of the box: the AI rewrites the prompt into a more vivid, specific one. While
 * it works it shows a ring, and a tap stops it.
 */
@Composable
private fun ImproveButton(improving: Boolean, hasText: Boolean, actions: AiComposerActions) {
    val c = LocalKeyboardColors.current
    val enabled = improving || hasText
    PressTarget(if (improving) "Stop improving the prompt" else "Improve the prompt with AI", Modifier.height(44.dp),
        onTap = if (enabled) actions::onImprove else null) { pressed ->
        Row(
            Modifier.fillMaxHeight().padding(vertical = 6.dp).alpha(if (enabled) 1f else DIMMED_ALPHA)
                .background(if (pressed) c.keyPressed else c.keyMod, RoundedCornerShape(16.dp))
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (improving) {
                CircularProgressIndicator(Modifier.size(14.dp), color = c.enter, trackColor = c.keyPressed, strokeWidth = 2.dp)
            } else {
                Icon(SlimIcons.Wand, null, tint = c.enter, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(6.dp))
            Text(if (improving) "Stop" else "Improve", color = c.keyText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1)
        }
    }
}

/** Under the box after Improve, until the words change: what happened, and Undo. */
@Composable
private fun ImprovedLine(actions: AiComposerActions) {
    val c = LocalKeyboardColors.current
    Row(Modifier.fillMaxWidth().padding(start = 52.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(SlimIcons.Wand, null, tint = c.enter, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text("Prompt improved", color = c.muted, fontSize = 13.sp, maxLines = 1,
            modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite })
        PanelButton("Undo", "Undo, bring back your own words", icon = SlimIcons.Undo, background = c.keyMod, height = 40.dp,
            onTap = actions::onUndoImprove)
    }
}

/** Left of the box, like a chat app's attach button: opens the tray of pictures to add. */
@Composable
private fun AddPictureButton(open: Boolean, room: Boolean, actions: AiComposerActions) {
    val c = LocalKeyboardColors.current
    PressTarget(if (open) "Hide the pictures" else "Add a picture to the prompt", Modifier.size(48.dp).padding(bottom = 2.dp),
        onTap = actions::onToggleTray) { pressed ->
        Box(
            Modifier.fillMaxSize().padding(end = 4.dp, top = 2.dp, bottom = 2.dp).alpha(if (room || open) 1f else DIMMED_ALPHA)
                .background(
                    when {
                        pressed -> c.keyPressed
                        open -> c.keyOn
                        else -> c.keyMod
                    },
                    RoundedCornerShape(10.dp),
                )
                .then(if (open) Modifier.border(1.5.dp, c.enter, RoundedCornerShape(10.dp)) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Icon(SlimIcons.ImageAdd, null, tint = c.keyText, modifier = Modifier.size(22.dp))
        }
    }
}

/** The pictures that go with the prompt, each with ✕; the service changes or combines them. */
@Composable
private fun AddedPictures(images: List<com.rheerkens.androidkeyboard.ai.PromptImage>, actions: AiComposerActions) {
    val c = LocalKeyboardColors.current
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        images.forEachIndexed { i, image ->
            androidx.compose.runtime.key(image.id) {
                Box(Modifier.size(64.dp)) {
                    val thumb = image.thumbnail
                    Box(
                        Modifier.align(Alignment.BottomStart).size(56.dp).clip(RoundedCornerShape(10.dp)).background(c.key)
                            .border(1.dp, c.divider, RoundedCornerShape(10.dp)),
                    ) {
                        if (thumb != null) {
                            Image(thumb.asImageBitmap(), "Picture ${i + 1} for the prompt", Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop)
                        }
                    }
                    PressTarget("Remove picture ${i + 1}", Modifier.align(Alignment.TopEnd).size(30.dp),
                        onTap = { actions.onRemoveImage(image.id) }) { pressed ->
                        Box(
                            Modifier.fillMaxSize().padding(3.dp)
                                .background(if (pressed) c.keyPressed else c.keyMod, CircleShape)
                                .border(1.dp, c.divider, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(SlimIcons.Close, null, tint = c.keyText, modifier = Modifier.size(12.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.width(4.dp))
        }
        Text(if (images.size == 1) "Goes with your prompt" else "${images.size} pictures go with your prompt",
            color = c.muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 6.dp))
    }
}

/**
 * The + tray: Photos (the system's photo picker, which needs no permission), then the images in the
 * clipboard row and recent screenshots. A tap adds one; a chip in the keyboard's clipboard row does too.
 */
@Composable
private fun PictureTray(ai: AiUi, actions: AiComposerActions) {
    val c = LocalKeyboardColors.current
    val room = ai.images.size < ImageApi.MAX_INPUT_IMAGES
    Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp)) {
        Text(
            if (room) "Add a picture to change or combine" else "That's ${ImageApi.MAX_INPUT_IMAGES} pictures, the most for one prompt",
            color = if (room) c.muted else c.amber, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp).semantics { heading() },
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).alpha(if (room) 1f else DIMMED_ALPHA),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PressTarget("Choose from your photos", Modifier.size(64.dp), onTap = if (room) actions::onPickPhotos else null) { pressed ->
                Column(
                    Modifier.fillMaxSize().background(if (pressed) c.keyPressed else c.keyMod, RoundedCornerShape(10.dp)),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    Icon(SlimIcons.Photos, null, tint = c.keyText, modifier = Modifier.size(22.dp))
                    Text("Photos", color = c.keyText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
            ai.recentImages.forEach { entry ->
                androidx.compose.runtime.key(entry.key) {
                    val thumb = when (entry) {
                        is ClipEntry.Image -> entry.thumbnail
                        is ClipEntry.Screenshot -> entry.thumbnail
                        is ClipEntry.Text -> null
                    }
                    val label = if (entry is ClipEntry.Screenshot) "Add this screenshot" else "Add this copied image"
                    PressTarget(label, Modifier.size(64.dp), onTap = if (room) ({ actions.onAddRecent(entry) }) else null) { pressed ->
                        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)).background(c.key)
                            .border(if (pressed) 2.dp else 1.dp, if (pressed) c.enter else c.divider, RoundedCornerShape(10.dp))) {
                            if (thumb != null) {
                                Image(thumb.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            }
                        }
                    }
                }
            }
        }
        if (ai.recentImages.isEmpty()) {
            Text("Images you copy and new screenshots show here too", color = c.muted, fontSize = 12.sp, maxLines = 2,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp))
        }
    }
}

/** The defaults, like a chat app's model chip: "Flare · Fast · ≈$0.01". Tap for the choices. */
@Composable
private fun ModelChip(ai: AiUi, actions: AiComposerActions, modifier: Modifier = Modifier) {
    val c = LocalKeyboardColors.current
    val label = chipLabel(ai)
    PressTarget(
        (if (ai.optionsOpen) "Close image options. " else "Image options. ") + label,
        modifier.height(48.dp).widthIn(max = 320.dp),
        onTap = actions::onToggleOptions,
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
 * The defaults chip's choices, under the prompt while open: the service, then its model (or what it
 * still needs, or the server's address), then the quality, each one row of switches. A shortcut opens
 * the AI settings; Done folds them away.
 */
@Composable
private fun AiOptions(ai: AiUi, actions: AiComposerActions) {
    val c = LocalKeyboardColors.current
    val openAi = ai.service == AiService.OPENAI
    Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Row(Modifier.fillMaxWidth().height(46.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Image options", color = c.keyText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f).padding(start = 4.dp).semantics { heading() })
            PanelButton("AI settings", "Open the AI settings", icon = SlimIcons.Settings, background = c.keyMod, height = 46.dp) {
                actions.onOpenSettings(null)
            }
            Spacer(Modifier.width(12.dp))
            PanelButton("Done", "Done, hide the image options", icon = null, background = c.keyMod, height = 46.dp,
                ink = c.enter, onTap = actions::onToggleOptions)
        }
        OptionRow("Service") {
            Segment("OpenAI", null, openAi, Modifier.weight(1f)) { actions.onSelectService(AiService.OPENAI) }
            Segment("Your server", null, !openAi, Modifier.weight(1f)) { actions.onSelectService(AiService.OWN_SERVER) }
        }
        val setup = ai.setup
        when {
            setup != null -> OptionRow(setup.row) {
                InfoRow(setup.value, warn = true, action = setup.action.substringBefore(' '),
                    description = "${setup.action} in the AI settings", modifier = Modifier.weight(1f)) {
                    actions.onOpenSettings(setup.setting)
                }
            }
            openAi -> OptionRow("Model") {
                ImageModel.entries.forEach { model ->
                    Segment(model.short, model.speed, ai.imageModel == model.id, Modifier.weight(1f)) {
                        actions.onSelectModel(model.id)
                    }
                }
            }
            else -> OptionRow("Server") {
                InfoRow(ai.serverHost + ai.serverModel.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(), warn = false,
                    action = "Edit", description = "Edit the server address", modifier = Modifier.weight(1f)) {
                    actions.onOpenSettings(AiSetup.NO_SERVER.setting)
                }
            }
        }
        OptionRow("Quality") {
            ImageQuality.entries.forEach { quality ->
                Segment(quality.label, if (openAi) quality.cost.replace("about ", "≈") else null,
                    ai.imageQuality == quality, Modifier.weight(1f)) { actions.onSelectQuality(quality) }
            }
        }
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
            // Change it: the prompt window opens with this image added.
            CardIcon(SlimIcons.Wand, "Change this image with AI") { actions.onEditAiResult(ready.entry.key) }
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
private fun RunningRow(task: AiTask, clock: () -> Long, actions: AiComposerActions) {
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
            actions.onStop(task.id)
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

/** One switch of a row: the picked one is tinted and outlined. */
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
 * ✨ at the strip's right end: opens the prompt window. While images are being made a ring turns
 * around it, with their number when several.
 */
@Composable
internal fun AiButton(running: Int, actions: KeyboardActions) {
    val c = LocalKeyboardColors.current
    val label = "Create with AI" + when (running) {
        0 -> ""
        1 -> ", 1 image being made"
        else -> ", $running images being made"
    }
    PressTarget(label, Modifier.width(48.dp).height(44.dp), onTap = actions::onAiButton) { pressed ->
        Box(
            Modifier.fillMaxSize().padding(horizontal = 5.dp, vertical = 3.dp)
                .background(if (pressed) c.keyPressed else Color.Transparent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
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
