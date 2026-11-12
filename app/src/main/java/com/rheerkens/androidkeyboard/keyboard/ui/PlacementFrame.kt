package com.rheerkens.androidkeyboard.keyboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.rheerkens.androidkeyboard.keyboard.KeyboardPlacement
import com.rheerkens.androidkeyboard.keyboard.PlacementFrame
import com.rheerkens.androidkeyboard.keyboard.PlacementMode
import com.rheerkens.androidkeyboard.settings.InputMode
import com.rheerkens.androidkeyboard.ui.SlimIcons
import kotlin.math.roundToInt

/** One coordinate system drives both the visible panel and the IME touch region. */
@Composable
fun KeyboardRoot(state: KeyboardUiState, clock: () -> Long, actions: KeyboardActions) {
    val density = LocalDensity.current
    val colors = if (state.dark) DarkKeyboardColors else LightKeyboardColors
    var menu by remember(state.inputSession, state.profile, state.viewportWidthDp, state.viewportHeightDp, state.keyRows) { mutableStateOf(false) }
    var editing by remember(state.inputSession, state.profile, state.viewportWidthDp, state.viewportHeightDp, state.keyRows) { mutableStateOf(false) }
    var draft by remember(state.inputSession, state.profile, state.viewportWidthDp, state.viewportHeightDp, state.keyRows) { mutableStateOf<KeyboardPlacement?>(null) }
    val placement = draft ?: state.placement
    LaunchedEffect(state.inputSession, state.resizeOnOpen) {
        if (state.resizeOnOpen) { actions.onPrepareResize(); editing = true }
    }
    DisposableEffect(menu, actions) {
        actions.onLayoutBackHandler(if (menu) ({ menu = false; true }) else null)
        onDispose { actions.onLayoutBackHandler(null) }
    }
    val nav = with(density) { WindowInsets.navigationBars.union(WindowInsets.captionBar).getBottom(this).toDp().value }
    val width = state.viewportWidthDp
    val height = state.viewportHeightDp
    val minimized = state.minimized && !editing && !menu
    val compactHeight = when {
        editing || menu -> null
        state.mode == InputMode.VOICE && !state.pickerOpen -> 57f
        else -> null
    }
    val frame = placement.frame(width, height, compactHeight, state.keyRows, minimized = minimized)
    val floating = frame.mode == PlacementMode.FLOATING
    val floatingHandle = floating && !minimized
    val rail = if (frame.mode == PlacementMode.ONE_HANDED && width - frame.width >= 40f) 44f else 0f
    val leftRail = rail > 0 && placement.rightHand
    val panelHeight = frame.height - if (floatingHandle) 24f else 0f
    val displayed = state.copy(rowHeightDp = frame.rowHeight.roundToInt(), minimized = minimized,
        toolsOpen = menu, placement = placement, splitGapDp = frame.splitGap, contentWidthDp = frame.width)

    fun save(value: KeyboardPlacement) { actions.onSavePlacement(state.profile, value); draft = null }
    val routed = object : KeyboardActions by actions {
        override fun onOpenTools() { editing = false; menu = !menu }
        override fun onResizeKeyboard() { actions.onPrepareResize(); menu = false; editing = true }
        override fun onSelectPlacementMode(mode: PlacementMode) {
            save(placement.copy(mode = mode, automatic = false)); menu = false; editing = false
        }
        override fun onOpenDictionary() { menu = false; actions.onOpenDictionary() }
        override fun onOpenKeyboardSettings() { menu = false; actions.onOpenKeyboardSettings() }
        override fun onToggleMode() { menu = false; editing = false; actions.onToggleMode() }
    }
    // Dispose gesture detectors and popups when their coordinate system or editor changes.
    key(state.inputSession, state.profile, frame.mode, width, height, state.keyRows) {
        CompositionLocalProvider(LocalKeyboardColors provides colors) {
            Box(Modifier.fillMaxWidth().height(((if (floating) height else height - frame.top) + nav).dp)
                .background(if (floating) Color.Transparent else colors.background)) {
                Row(Modifier.offset(x = (frame.left - if (leftRail) rail else 0f).dp, y = (if (floating) frame.top else 0f).dp)
                    .width((frame.width + rail).dp).height(frame.height.dp)
                    .onGloballyPositioned { coordinates ->
                        val at = coordinates.positionInWindow()
                        actions.onPlacementBounds(at.x.roundToInt(), at.y.roundToInt(),
                            (at.x + coordinates.size.width).roundToInt(), (at.y + coordinates.size.height).roundToInt(), floating)
                    }) {
                    @Composable fun SideControls() {
                        Column(Modifier.width(rail.dp).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                            StripIconButton(SlimIcons.Move, "Switch one-handed side") { save(placement.copy(rightHand = !placement.rightHand)) }
                            StripIconButton(SlimIcons.Resize, "Use full-width keyboard") { save(placement.copy(mode = PlacementMode.NORMAL, automatic = false)) }
                        }
                    }
                    if (leftRail) SideControls()
                    Column(Modifier.width(frame.width.dp)) {
                        Box(Modifier.height(panelHeight.dp).fillMaxWidth()) {
                            Box(if (editing) Modifier.clearAndSetSemantics {} else Modifier) {
                                KeyboardContent(displayed, clock, routed)
                            }
                            if (editing) ResizeOverlay(placement, frame, width, height, state.keyRows, { draft = it }, ::save) { editing = false }
                        }
                        if (floatingHandle) {
                            PlacementDragTarget("Move floating keyboard", Modifier.fillMaxWidth().height(24.dp).background(colors.background),
                                placement, { base, delta -> base.move(width, height, delta.x, delta.y, state.keyRows) }, { draft = it }, ::save,
                                movements = moveActions)
                        }
                    }
                    if (rail > 0f && !leftRail) SideControls()
                }
            }
        }
    }
}

@Composable
private fun ResizeOverlay(placement: KeyboardPlacement, frame: PlacementFrame, width: Float, height: Float, rows: Int,
    preview: (KeyboardPlacement?) -> Unit, save: (KeyboardPlacement) -> Unit, done: () -> Unit) {
    val c = LocalKeyboardColors.current
    val floating = frame.mode == PlacementMode.FLOATING
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .45f)).border(2.dp, c.enter, RoundedCornerShape(12.dp))
        .pointerInput(Unit) { detectTapGestures {} }) {
        PlacementDragTarget("Resize top edge", Modifier.align(Alignment.TopCenter).size(48.dp, 28.dp), placement,
            { base, delta -> base.resizeVertical(width, height, delta.y, false, rows) }, preview, save)
        PlacementDragTarget("Resize bottom edge", Modifier.align(Alignment.BottomCenter).size(48.dp, 28.dp), placement,
            { base, delta -> base.resizeVertical(width, height, delta.y, true, rows) }, preview, save,
            movements = listOf("Increase" to Offset(0f, 8f), "Decrease" to Offset(0f, -8f)))
        if (frame.mode == PlacementMode.NORMAL || floating) {
            for (left in listOf(true, false)) {
                PlacementDragTarget(if (left) "Resize left edge" else "Resize right edge",
                    Modifier.align(if (left) Alignment.CenterStart else Alignment.CenterEnd).size(28.dp, 48.dp), placement,
                    { base, delta -> base.resizeWidth(width, height, delta.x, fromLeft = left) }, preview, save,
                    movements = listOf("Increase" to Offset(if (left) -8f else 8f, 0f), "Decrease" to Offset(if (left) 8f else -8f, 0f)))
            }
        } else if (frame.mode == PlacementMode.ONE_HANDED) {
            val right = floating || !placement.rightHand
            PlacementDragTarget("Resize keyboard width", Modifier.align(if (right) Alignment.CenterEnd else Alignment.CenterStart).size(28.dp, 48.dp), placement,
                { base, delta -> base.resizeWidth(width, height, delta.x) }, preview, save,
                movements = listOf("Increase" to Offset(if (right) 8f else -8f, 0f), "Decrease" to Offset(if (right) -8f else 8f, 0f)))
        }
        if (frame.mode == PlacementMode.SPLIT) {
            PlacementDragTarget("Resize split gap", Modifier.align(Alignment.TopCenter).offset(x = (frame.splitGap / 2f).dp, y = 52.dp).size(28.dp, 40.dp), placement,
                { base, delta -> base.copy(splitGap = (frame.splitGap + delta.x * 2f).coerceIn(16f, (width - 360f).coerceAtLeast(16f))) }, preview, save,
                movements = listOf("Increase" to Offset(8f, 0f), "Decrease" to Offset(-8f, 0f)))
        }
        Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
            StripIconButton(SlimIcons.Undo, "Reset keyboard size and position") {
                save(KeyboardPlacement(mode = placement.mode, automatic = placement.automatic, rightHand = placement.rightHand))
            }
            PlacementDragTarget("Move keyboard", Modifier.size(60.dp), placement,
                { base, delta -> base.move(width, height, delta.x, delta.y, rows) }, preview, save, icon = true,
                movements = if (floating || frame.mode == PlacementMode.NORMAL) moveActions else moveActions.take(2))
            StripIconButton(SlimIcons.Check, "Finish changing keyboard size and position", onTap = done)
        }
    }
}

private val moveActions = listOf("Move up" to Offset(0f, -8f), "Move down" to Offset(0f, 8f),
    "Move left" to Offset(-8f, 0f), "Move right" to Offset(8f, 0f))

/** Accumulate motion from gesture start; recomposition never changes the gesture's baseline. */
@Composable
private fun PlacementDragTarget(label: String, modifier: Modifier, value: KeyboardPlacement,
    transform: (KeyboardPlacement, Offset) -> KeyboardPlacement, preview: (KeyboardPlacement?) -> Unit,
    save: (KeyboardPlacement) -> Unit, icon: Boolean = false,
    movements: List<Pair<String, Offset>> = listOf("Increase" to Offset(0f, -8f), "Decrease" to Offset(0f, 8f))) {
    val density = LocalDensity.current.density
    val latestValue by rememberUpdatedState(value)
    val latestTransform by rememberUpdatedState(transform)
    val latestPreview by rememberUpdatedState(preview)
    val latestSave by rememberUpdatedState(save)
    val c = LocalKeyboardColors.current
    Box(modifier.semantics {
        contentDescription = label
        customActions = movements.map { (name, delta) ->
            CustomAccessibilityAction(name) { latestSave(latestTransform(latestValue, delta)); true }
        }
    }.pointerInput(Unit) {
        var start = latestValue
        var delta = Offset.Zero
        var next = start
        var operation = latestTransform
        detectDragGestures(
            onDragStart = { start = latestValue; operation = latestTransform; delta = Offset.Zero; next = start },
            onDragCancel = { latestPreview(null) },
            onDragEnd = { latestSave(next) },
        ) { change, amount ->
            change.consume(); delta += amount / density
            next = operation(start, delta); latestPreview(next)
        }
    }, contentAlignment = Alignment.Center) {
        if (icon) Icon(SlimIcons.Move, null, tint = c.keyText, modifier = Modifier.size(28.dp))
        else Box(Modifier.size(20.dp, 6.dp).background(c.enter, RoundedCornerShape(3.dp)))
    }
}
