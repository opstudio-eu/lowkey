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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.drawWithContent
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
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.stateDescription
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

/** The prompt's words while Improve rewrites them: faded, yet above 4.5:1 contrast. */
private const val IMPROVING_ALPHA = 0.7f

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
    /** The picture button under the box: shows (or hides) the tray of recent images and Photos. */
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
    /** Flag beside Undo: report the improved prompt and put the earlier words back. */
    fun onReportImproved() {}
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
            .padding(start = 8.dp, end = 8.dp, bottom = 4.dp),
    ) {
        // The header, a quieter tier than the tools: the defaults chip, and ✕. With the chip's choices
        // open (just under it), the AI settings and Done take ✕'s place.
        Row(verticalAlignment = Alignment.CenterVertically) {
            // The chip takes what room is left, and shortens its label only when that runs out.
            Box(Modifier.weight(1f)) { ModelChip(ai, actions) }
            if (ai.optionsOpen) {
                CardIcon(SlimIcons.Gear, "Open the AI settings", header = true) { actions.onOpenSettings(null) }
                HeaderPill("Done", "Done, hide the image options", actions::onToggleOptions)
            } else {
                // Its 48 dp target reaches into the card's margin, so the circle lines up with the box's edge.
                CardIcon(SlimIcons.Close, "Close the image prompt", Modifier.offset(x = 8.dp), header = true, onTap = actions::onClose)
            }
        }
        // Between the header and the box: what scrolls when the room above the keyboard runs out (a short
        // screen, landscape), so the header, the box and Create always stay in reach.
        if (ai.optionsOpen || ai.tasks.isNotEmpty() || ai.images.isNotEmpty()) {
            val scroll = rememberScrollState()
            val fade = with(androidx.compose.ui.platform.LocalDensity.current) { 16.dp.toPx() }
            Column(
                Modifier.weight(1f, fill = false)
                    // A cut-off edge fades out, so what is above or below shows it is there to scroll to.
                    .drawWithContent {
                        drawContent()
                        if (scroll.canScrollBackward) {
                            drawRect(Brush.verticalGradient(listOf(c.background.copy(alpha = 0.85f), Color.Transparent), endY = fade),
                                size = Size(size.width, fade))
                        }
                        if (scroll.canScrollForward) {
                            drawRect(Brush.verticalGradient(listOf(Color.Transparent, c.background.copy(alpha = 0.85f)), startY = size.height - fade,
                                endY = size.height), topLeft = Offset(0f, size.height - fade), size = Size(size.width, fade))
                        }
                    }
                    .verticalScroll(scroll),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (ai.optionsOpen) AiOptions(ai, actions)
                // Keyed, so a row that goes away (its image arrived) can't hand its Stop press to the next one.
                ai.tasks.forEach { task -> androidx.compose.runtime.key(task.id) { RunningRow(task, clock, actions) } }
                if (ai.images.isNotEmpty()) AddedPictures(ai.images, actions)
            }
            // Outside the scroll, so the box always keeps its 8 dp.
            Spacer(Modifier.height(8.dp))
        }
        // The box takes the full width: the words wrap only at its edge.
        PromptField(prompt, onPromptChange, focus,
            when {
                ai.images.isNotEmpty() -> AiAction.EDIT_IMAGE.promptHint
                ai.tasks.isEmpty() -> AiAction.CREATE_IMAGE.promptHint
                else -> "Describe another image…"
            },
            ai.improving, actions, Modifier.fillMaxWidth(),
            // With the choices or the tray open, the box gives them the room; its words scroll inside it.
            maxLines = if (ai.optionsOpen || ai.trayOpen) 2 else 5)
        // Under the box, next to the button it is about (Improve, or Photos in the tray).
        ai.note?.let { note ->
            Text(note.text, color = if (note.warn) c.amber else c.muted, fontSize = 13.sp, maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp).semantics { liveRegion = LiveRegionMode.Polite })
        }
        // The tray opens just above the tools, so the picture button stays where it was tapped.
        if (ai.trayOpen) PictureTray(ai, actions)
        // The tools under the box, like a chat app's: what goes in on the left, Create on the right.
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            AddPictureButton(ai.trayOpen, ai.images.size, actions, Modifier.offset(x = (-4).dp))
            // Improve takes the room between, and shortens its label before Create would.
            Box(Modifier.weight(1f).padding(end = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (ai.beforeImprove != null && !ai.improving) {
                        CardIcon(SlimIcons.Flag, "Report this improved prompt") { actions.onReportImproved() }
                    }
                    ImproveButton(ai.improving, undo = ai.beforeImprove != null && !ai.improving, prompt.text.isNotBlank(), actions)
                }
            }
            val setup = ai.setup
            // Not while Improve rewrites the words: Create would send the old ones. Stop first, or wait.
            // The keyboard's Enter matches it in every state.
            val ready = !ai.improving && (setup != null || prompt.text.isNotBlank())
            val button = AiAction.CREATE_IMAGE.button
            PressTarget(
                when {
                    ai.improving -> "$button, available after Improve finishes or is stopped"
                    setup != null -> "${setup.action}, in the AI settings"
                    else -> button
                },
                Modifier.height(48.dp).then(if (ready) Modifier else Modifier.semantics { disabled() }),
                onTap = when {
                    !ready -> null
                    setup != null -> ({ actions.onOpenSettings(setup.setting) })
                    else -> actions::onSubmit
                }) { pressed ->
                Box(
                    Modifier.fillMaxHeight().padding(vertical = 4.dp).alpha(if (ready) 1f else DIMMED_ALPHA)
                        .background(if (pressed) c.enterPressed else c.enter, RoundedCornerShape(20.dp))
                        .padding(horizontal = 18.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (ai.improving) button else setup?.action ?: button, color = c.enterInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1)
                }
            }
        }
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
    actions: AiComposerActions, modifier: Modifier = Modifier, maxLines: Int = 5,
) {
    val c = LocalKeyboardColors.current
    BasicTextField(
        value = value,
        onValueChange = { next ->
            when {
                typedLineBreak(value.text, next.text) -> actions.onSubmit()
                // Typing (or dictating) while Improve works stops it (the window does): the words typed win.
                // Not readOnly instead: that ends the input session, the keyboard hides, and with it the window.
                else -> onChange(flattenPrompt(next))
            }
        },
        textStyle = TextStyle(color = c.keyText, fontSize = 16.sp, lineHeight = 22.sp),
        cursorBrush = SolidColor(c.enter),
        maxLines = maxLines,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = ImeAction.Go,
            platformImeOptions = PlatformImeOptions(AiComposerLink.PROMPT_FIELD),
        ),
        keyboardActions = TextKeyboardActions(onGo = { actions.onSubmit() }),
        modifier = modifier.focusRequester(focus)
            .semantics { contentDescription = "Image prompt" },
        decorationBox = { inner ->
            // Outlined like a focused text box, as it is; a little faded while Improve rewrites it, still readable.
            Box(
                Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .background(c.key, RoundedCornerShape(12.dp)).border(2.dp, c.enter, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 12.dp).alpha(if (improving) IMPROVING_ALPHA else 1f),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.text.isEmpty()) {
                    Text(hint, color = c.muted, fontSize = 16.sp, lineHeight = 22.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                }
                inner()
            }
        },
    )
}

/**
 * Improve, beside the picture button: the AI rewrites the prompt into a more vivid, specific one. While
 * it works it shows a ring and Stop; once done it turns into Undo ([undo]), which brings the words
 * back, until they change.
 */
@Composable
private fun ImproveButton(improving: Boolean, undo: Boolean, hasText: Boolean, actions: AiComposerActions) {
    val c = LocalKeyboardColors.current
    val enabled = improving || undo || hasText
    val description = when {
        improving -> "Stop improving the prompt"
        undo -> "Prompt improved. Undo, bring back your own words"
        else -> "Improve the prompt with AI"
    }
    PressTarget(description,
        Modifier.height(48.dp).semantics {
            liveRegion = LiveRegionMode.Polite
            if (!enabled) disabled()
        },
        onTap = when {
            undo -> actions::onUndoImprove
            enabled -> actions::onImprove
            else -> null
        }) { pressed ->
        // As wide as Improve in every state, so its edge doesn't jump from Improve to Stop to Undo.
        Row(
            Modifier.fillMaxHeight().widthIn(min = 108.dp).padding(vertical = 4.dp).alpha(if (enabled) 1f else DIMMED_ALPHA)
                .background(if (pressed) c.keyPressed else c.keyMod, RoundedCornerShape(20.dp))
                .padding(start = 12.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
                when {
                    improving -> CircularProgressIndicator(Modifier.size(16.dp), color = c.enter, trackColor = c.keyPressed,
                        strokeWidth = 2.dp)
                    undo -> Icon(SlimIcons.Undo, null, tint = c.enter, modifier = Modifier.size(18.dp))
                    else -> Icon(SlimIcons.Wand, null, tint = c.enter, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.width(6.dp))
            Text(
                when {
                    improving -> "Stop"
                    undo -> "Undo"
                    else -> "Improve"
                },
                color = c.keyText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}

/** First of the tools, like a chat app's attach button: opens the tray of pictures to add. */
@Composable
private fun AddPictureButton(open: Boolean, count: Int, actions: AiComposerActions, modifier: Modifier = Modifier) {
    val c = LocalKeyboardColors.current
    val attached = when (count) {
        0 -> ""
        1 -> ", 1 picture attached"
        else -> ", $count pictures attached"
    }
    PressTarget((if (open) "Hide the pictures" else "Add a picture to the prompt") + attached,
        modifier.size(48.dp).semantics { stateDescription = if (open) "Expanded" else "Collapsed" },
        onTap = actions::onToggleTray) { pressed ->
        Box(
            Modifier.fillMaxSize().padding(4.dp)
                .background(
                    when {
                        pressed -> c.keyPressed
                        open -> c.keyOn
                        else -> c.keyMod
                    },
                    CircleShape,
                )
                .then(if (open) Modifier.border(1.5.dp, c.enter, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Icon(SlimIcons.ImageAdd, null, tint = c.keyText, modifier = Modifier.size(22.dp))
        }
        // How many pictures go along, also when their row has scrolled out of view.
        if (count > 0) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(top = 1.dp, end = 1.dp).size(18.dp)
                    .border(1.5.dp, c.background, CircleShape).padding(1.dp).background(c.enter, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("$count", color = c.enterInk, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
    }
}

/** The pictures that go with the prompt, each with ✕; the service changes or combines them. */
@Composable
private fun AddedPictures(images: List<com.rheerkens.androidkeyboard.ai.PromptImage>, actions: AiComposerActions) {
    val c = LocalKeyboardColors.current
    Row(Modifier.fillMaxWidth()) {
        images.forEachIndexed { i, image ->
            androidx.compose.runtime.key(image.id) {
                // A 56 dp picture, and room on its right for a full 48 dp ✕ target that stays off the picture's
                // middle: a tap there (to look at it) doesn't take it out.
                Box(Modifier.width(80.dp).height(56.dp)) {
                    val thumb = image.thumbnail
                    Box(
                        Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)).background(c.key)
                            .border(1.dp, c.divider, RoundedCornerShape(10.dp)),
                    ) {
                        if (thumb != null) {
                            Image(thumb.asImageBitmap(), "Picture ${i + 1} for the prompt", Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop)
                        }
                    }
                    ScrollTap("Remove picture ${i + 1}", Modifier.align(Alignment.TopEnd).size(48.dp),
                        onTap = { actions.onRemoveImage(image.id) }) { pressed ->
                        // On the picture's top-right corner.
                        Box(
                            Modifier.align(Alignment.TopStart).offset(x = 12.dp).size(24.dp)
                                .background(if (pressed) c.keyPressed else c.keyMod, CircleShape)
                                .border(1.dp, c.divider, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(SlimIcons.Close, null, tint = c.keyText, modifier = Modifier.size(12.dp))
                        }
                    }
                }
            }
        }
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
    val title = if (room) "Add a picture to change or combine" else "${ImageApi.MAX_INPUT_IMAGES} of ${ImageApi.MAX_INPUT_IMAGES} pictures added"
    val titleColor = if (room) c.muted else c.keyText
    val photos = @Composable {
        ScrollTap("Choose from your photos", Modifier.size(64.dp), onTap = if (room) actions::onPickPhotos else null) { pressed ->
            Column(
                Modifier.fillMaxSize().background(if (pressed) c.keyPressed else c.keyMod, RoundedCornerShape(10.dp)),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                Icon(SlimIcons.Photos, null, tint = c.keyText, modifier = Modifier.size(22.dp))
                Text("Photos", color = c.keyText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
    if (ai.recentImages.isEmpty()) {
        // Only Photos: one row, with what the tray is for beside it rather than above and below. At the
        // limit only the tile dims; what it says stays readable.
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.alpha(if (room) 1f else DIMMED_ALPHA)) { photos() }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = titleColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
                Text(if (room) "Copied images and new screenshots show up here" else "Remove one to add another",
                    color = c.muted, fontSize = 12.sp, maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
            }
        }
        return
    }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(if (room) title else "$title. Remove one to add another", color = titleColor, fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(bottom = 6.dp).semantics { heading() })
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).alpha(if (room) 1f else DIMMED_ALPHA),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            photos()
            ai.recentImages.forEach { entry ->
                androidx.compose.runtime.key(entry.key) {
                    val thumb = when (entry) {
                        is ClipEntry.Image -> entry.thumbnail
                        is ClipEntry.Screenshot -> entry.thumbnail
                        is ClipEntry.Text -> null
                    }
                    val label = if (entry is ClipEntry.Screenshot) "Add this screenshot" else "Add this copied image"
                    ScrollTap(label, Modifier.size(64.dp), onTap = if (room) ({ actions.onAddRecent(entry) }) else null) { pressed ->
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
    }
}

/** The defaults, like a chat app's model chip: "Flare · Fast · ≈$0.01". Tap for the choices. */
@Composable
private fun ModelChip(ai: AiUi, actions: AiComposerActions, modifier: Modifier = Modifier) {
    val c = LocalKeyboardColors.current
    val label = chipLabel(ai)
    PressTarget(
        "Image options. $label",
        modifier.height(48.dp).widthIn(max = 320.dp)
            .semantics { stateDescription = if (ai.optionsOpen) "Expanded" else "Collapsed" },
        onTap = actions::onToggleOptions,
    ) { pressed ->
        // 32 dp shown: the header is a quieter tier than the 40 dp tools under the box.
        Row(
            Modifier.fillMaxHeight().padding(vertical = 8.dp)
                .background(if (pressed) c.keyPressed else c.key, RoundedCornerShape(16.dp))
                .then(if (ai.optionsOpen) Modifier.border(1.5.dp, c.enter, RoundedCornerShape(16.dp)) else Modifier)
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
    // The header above says what is open, with the AI settings and Done.
    Column(Modifier.fillMaxWidth()) {
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
            CardIcon(SlimIcons.Flag, "Report this image") { actions.onReportAiResult(ready.entry.key) }
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

/**
 * A round icon button on the cards, like the strip's ✕: 34 dp shown, 48 dp to touch. In the prompt
 * card's [header], 32 dp, as tall as the chip beside it.
 */
@Composable
private fun CardIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, modifier: Modifier = Modifier,
    header: Boolean = false, onTap: () -> Unit,
) {
    val c = LocalKeyboardColors.current
    PressTarget(description, modifier.size(48.dp), onTap = onTap) { pressed ->
        Box(Modifier.fillMaxSize().padding(if (header) 8.dp else 7.dp).background(if (pressed) c.keyPressed else c.keyMod, CircleShape),
            contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = c.keyText, modifier = Modifier.size(if (header) 16.dp else 18.dp))
        }
    }
}

/** A text button in the prompt card's header, as tall as the chip: 32 dp shown, 48 dp to touch. */
@Composable
private fun HeaderPill(label: String, description: String, onTap: () -> Unit) {
    val c = LocalKeyboardColors.current
    PressTarget(description, Modifier.height(48.dp), onTap = onTap) { pressed ->
        Box(
            Modifier.fillMaxHeight().padding(vertical = 8.dp)
                .background(if (pressed) c.keyPressed else c.keyMod, RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, color = c.enter, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
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
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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


/**
 * A button inside something that scrolls (the tray, the pictures): a standard click, so a swipe that
 * starts on it scrolls instead of tapping. No [onTap]: disabled.
 */
@Composable
private fun ScrollTap(
    description: String, modifier: Modifier, onTap: (() -> Unit)?,
    content: @Composable androidx.compose.foundation.layout.BoxScope.(pressed: Boolean) -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(
        modifier.clickable(source, indication = null, enabled = onTap != null, role = Role.Button) { onTap?.invoke() }
            .semantics(mergeDescendants = true) { contentDescription = description },
    ) { content(pressed) }
}

/** A label and its switches on one line. */
@Composable
private fun OptionRow(label: String, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    val c = LocalKeyboardColors.current
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = c.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
            modifier = Modifier.width(62.dp))
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

/**
 * Shown in place of the prompt, immediately before the first send to a service.
 * Continue is the only way the request starts.
 */
@Composable
internal fun AiSendNoticeCard(
    notice: com.rheerkens.androidkeyboard.ai.AiSendNotice,
    onContinue: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalKeyboardColors.current
    Column(
        modifier.shadow(8.dp, RoundedCornerShape(16.dp))
            .background(c.background, RoundedCornerShape(16.dp))
            .border(1.dp, c.divider, RoundedCornerShape(16.dp))
            .heightIn(max = 420.dp)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(com.rheerkens.androidkeyboard.ai.AiDisclosure.TITLE, color = c.keyText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { heading() })
        Text(notice.provider, color = c.enter, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        notice.lines.forEach { line ->
            Text(line, color = c.keyText, fontSize = 15.sp, lineHeight = 20.sp)
        }
        Text("Continue sends it. Cancel keeps it on this phone.", color = c.muted, fontSize = 14.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CardButton("Cancel", "Cancel, don't send this", primary = false, onTap = onCancel)
            CardButton("Continue", "Continue, send this", primary = true, onTap = onContinue)
        }
    }
}
