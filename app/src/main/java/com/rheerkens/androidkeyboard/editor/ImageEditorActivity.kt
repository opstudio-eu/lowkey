package com.rheerkens.androidkeyboard.editor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.KeyEvent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rheerkens.androidkeyboard.AppGraph
import com.rheerkens.androidkeyboard.clipboard.ClipboardHistory
import com.rheerkens.androidkeyboard.companion.theme.SlimTheme
import com.rheerkens.androidkeyboard.ui.SlimIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Crop, draw on, highlight and add remarks to a clipboard image or screenshot, opened by sliding
 * up from the keyboard's preview. Attach, like a chat app's image send screen, inserts the caption
 * and the edited image into the field the editor was opened from (see [PendingAttachment]) and also
 * puts the image on the clipboard (so it becomes the newest
 * chip in the keyboard) and returns to the app the user came from. Runs in its own task.
 */
class ImageEditorActivity : ComponentActivity() {
    private val keys = EditorKeys()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AppGraph.init(this)
        val uri = intent.data
        if (uri == null) {
            finishAndRemoveTask()
            return
        }
        setContent {
            val settings by AppGraph.settings.settings.collectAsStateWithLifecycle()
            SlimTheme(settings.theme) {
                ImageEditor(uri, keys, onCancel = ::finishAndRemoveTask, onAttach = ::onAttach)
            }
        }
    }

    /** Ctrl+Z undoes, Ctrl+Shift+Z or Ctrl+Y redoes, unless a focused text field used the key. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.isCtrlPressed) {
            when {
                keyCode == KeyEvent.KEYCODE_Z && !event.isShiftPressed -> { keys.undo(); return true }
                keyCode == KeyEvent.KEYCODE_Z || keyCode == KeyEvent.KEYCODE_Y -> { keys.redo(); return true }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun onAttach(saved: Uri?, caption: String) {
        if (saved == null) {
            Toast.makeText(this, "Couldn't save the edited image", Toast.LENGTH_SHORT).show()
            return
        }
        getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newUri(contentResolver, "Edited image", saved))
        val source = PendingAttachment.sourceEditor
        if (source != null) {
            PendingAttachment.offer(Attachment(saved.toString(), "image/png", caption, source, System.currentTimeMillis()))
        } else {
            Toast.makeText(this, "Copied. Tap it in the keyboard to paste.", Toast.LENGTH_SHORT).show()
        }
        finishAndRemoveTask()
    }

    companion object {
        private const val DIR = "edits"
        private const val KEEP = 5

        /** Saves [image] as a PNG in the app cache (newest [KEEP] kept) and returns its shareable URI. */
        fun save(context: Context, image: Bitmap): Uri? = try {
            val dir = File(context.cacheDir, DIR).apply { mkdirs() }
            dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(KEEP - 1)?.forEach { it.delete() }
            val file = File(dir, "edit-${System.currentTimeMillis()}.png")
            file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
            FileProvider.getUriForFile(context, "${context.packageName}${ClipboardHistory.AUTHORITY_SUFFIX}", file)
        } catch (_: IOException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}

/** Undo and redo for the current editor, called from hardware keyboard shortcuts. */
private class EditorKeys {
    var undo: () -> Unit = {}
    var redo: () -> Unit = {}
}

/** Everything undo restores: the marks and the crop. */
private data class EditState(val marks: List<Mark>, val crop: Rect)

/** Editor tools; shapes show as icon chips. Rectangle sits next to Pen: it's the main way to mark a section. */
private enum class Tool(val label: String, val shape: ShapeKind? = null, val icon: ImageVector? = null) {
    CROP("Crop"),
    PEN("Pen", icon = SlimIcons.Pencil),
    RECTANGLE("Rectangle", ShapeKind.RECTANGLE, SlimIcons.Rectangle),
    OVAL("Circle", ShapeKind.OVAL, SlimIcons.Oval),
    ARROW("Arrow", ShapeKind.ARROW, SlimIcons.Arrow),
    LINE("Line", ShapeKind.LINE, SlimIcons.Line),
    HIGHLIGHT("Highlight"),
    ERASER("Eraser", icon = SlimIcons.Eraser),
    TEXT("Text"),
}

private val PALETTE = listOf(0xFFE53935, 0xFFFDD835, 0xFF1E88E5, 0xFF43A047, 0xFF000000, 0xFFFFFFFF).map { it.toInt() }

/**
 * What the canvas shows under the marks still being drawn: the image with every committed mark up
 * to the last blur applied as pixels (the same [applyMarks] the export uses), and, for a blur being
 * drawn, the blurred look of everything committed. Adding marks updates the image in place at
 * once; other changes under a blur (undo, erase, moving a note) rebuild it in the background,
 * newest request only, while the previous image stays on screen.
 */
private class BlurPreview(private val source: Bitmap, private val scope: kotlinx.coroutines.CoroutineScope) {
    private class Base(val marks: List<Mark>, val image: Bitmap)

    /** The newest finished image; state, so the canvas redraws when a rebuild lands. */
    private var base by mutableStateOf(Base(emptyList(), source))
    private var wanted: List<Mark> = emptyList()
    private var rebuild: kotlinx.coroutines.Job? = null
    private val worker = Dispatchers.Default.limitedParallelism(1)

    /** The image to draw under [marks], and how many of [marks] it already shows. */
    fun base(marks: List<Mark>): Pair<Bitmap, Int> {
        val upTo = marks.subList(0, marks.indexOfLast { it.blurs } + 1)
        val shown = base
        if (upTo != wanted) {
            wanted = upTo.toList()
            rebuild?.cancel()
            rebuild = null
            // A cancelled rebuild may still be running, but it only reads the untouched source.
            val extends = upTo.size > shown.marks.size && upTo.subList(0, shown.marks.size) == shown.marks
            if (extends) {
                // New marks on top: only they are drawn, on the main thread, so a finished blur never blinks.
                val image = if (shown.image === source) source.copy(Bitmap.Config.ARGB_8888, true) else shown.image
                applyMarks(image, upTo.subList(shown.marks.size, upTo.size))
                base = Base(wanted, image)
            } else if (upTo.isEmpty()) {
                base = Base(wanted, source)
            } else {
                val target = wanted
                rebuild = scope.launch {
                    val image = withContext(worker) { source.copy(Bitmap.Config.ARGB_8888, true).also { applyMarks(it, target) } }
                    if (target == wanted) base = Base(target, image)
                }
            }
        }
        val now = base
        // While a rebuild runs, the previous image stands in; draw the marks it doesn't show.
        val count = if (marks.size >= now.marks.size && marks.subList(0, now.marks.size) == now.marks) now.marks.size
            else (marks.indexOfLast { it.blurs } + 1)
        return now.image to count
    }

    /** A shape being moved, the marks below and above it, and the image with the marks below. */
    private class Between(val below: List<Mark>, val moving: Mark, val above: List<Mark>, val image: Bitmap)
    private var aboveWanted: Between? = null
    private var aboveDone: Between? = null
    /** State, so the canvas redraws when a background render lands. */
    private var aboveShaders by mutableStateOf<List<android.graphics.Shader?>>(emptyList())
    private var aboveJob: kotlinx.coroutines.Job? = null

    /**
     * For [moving], a shape being moved between [below] and [above]: the blur paint for each blur
     * mark in [above] (null for the others), each from the image as the export has it at that
     * mark, moving shape included. The first step of a drag renders at once; later steps render
     * in the background, newest only, while the previous paints stand in.
     */
    fun above(below: List<Mark>, moving: Mark, above: List<Mark>): List<android.graphics.Shader?> {
        if (above.none { it.blurs }) return List(above.size) { null }
        val wanted = aboveWanted
        val sameDrag = wanted != null && wanted.below == below && wanted.above == above
        if (sameDrag && wanted.moving == moving) return aboveShaders
        val next = Between(
            below.toList(), moving, above.toList(),
            if (sameDrag) wanted.image else source.copy(Bitmap.Config.ARGB_8888, true).also { applyMarks(it, below) },
        )
        aboveWanted = next
        if (!sameDrag || aboveDone?.let { it.below != below || it.above != above } != false) {
            aboveJob?.cancel()
            aboveShaders = render(next)
            aboveDone = next
        } else if (aboveJob?.isActive != true) {
            renderNewest()
        }
        return aboveShaders
    }

    private fun renderNewest() {
        val target = aboveWanted ?: return
        aboveJob = scope.launch {
            val shaders = withContext(worker) { render(target) }
            if (aboveWanted?.let { it.below == target.below && it.above == target.above } == true) {
                aboveShaders = shaders
                aboveDone = target
                if (aboveWanted !== target) renderNewest()
            }
        }
    }

    /** One pass from the image with the marks below: the moving shape, then each mark above. */
    private fun render(at: Between): List<android.graphics.Shader?> {
        val image = at.image.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = android.graphics.Canvas(image)
        val paints = (listOf(at.moving) + at.above).map { m ->
            val paint = if (m.blurs) blurShader(image, blurCell(m, source.width, source.height)) else null
            drawMarks(canvas, listOf(m), paint)
            paint
        }
        image.recycle()
        return paints.drop(1)
    }

    private var liveMarks: List<Mark>? = null
    private var live: Bitmap? = null
    private val liveShaders = HashMap<Float, android.graphics.Shader>()

    /** The blur paint, at [cell] (see [blurCell]), for a blur mark drawn on top of all of [marks]. */
    fun live(marks: List<Mark>, cell: Float): android.graphics.Shader {
        if (marks != liveMarks) {
            live?.recycle()
            liveShaders.clear()
            val (image, count) = base(marks)
            val fresh = count == marks.indexOfLast { it.blurs } + 1 && base.marks == marks.subList(0, count)
            live = if (fresh) {
                image.copy(Bitmap.Config.ARGB_8888, true).also { drawMarks(android.graphics.Canvas(it), marks.subList(count, marks.size)) }
            } else {
                source.copy(Bitmap.Config.ARGB_8888, true).also { applyMarks(it, marks) }
            }
            liveMarks = marks.toList()
        }
        return liveShaders.getOrPut(cell) { blurShader(live!!, cell) }
    }
}

/** Image-to-view mapping: the shown part of the image, scaled to fit and centered. */
private class Fit(val scale: Float, val dx: Float, val dy: Float) {
    fun toImage(p: Offset) = Offset((p.x - dx) / scale, (p.y - dy) / scale)
    fun toView(x: Float, y: Float) = Offset(x * scale + dx, y * scale + dy)
}

private fun fitOf(view: Size, shown: Rect, inset: Float): Fit {
    val w = (view.width - 2 * inset).coerceAtLeast(1f)
    val h = (view.height - 2 * inset).coerceAtLeast(1f)
    val s = min(w / shown.width, h / shown.height)
    return Fit(s, (view.width - shown.width * s) / 2 - shown.left * s, (view.height - shown.height * s) / 2 - shown.top * s)
}

private enum class CropHandle { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, MOVE }

/** The corner within [grab] view pixels of [p], else MOVE inside the crop, else null. */
private fun cropHandleAt(p: Offset, crop: Rect, fit: Fit, grab: Float): CropHandle? {
    val corners = listOf(
        CropHandle.TOP_LEFT to fit.toView(crop.left, crop.top),
        CropHandle.TOP_RIGHT to fit.toView(crop.right, crop.top),
        CropHandle.BOTTOM_LEFT to fit.toView(crop.left, crop.bottom),
        CropHandle.BOTTOM_RIGHT to fit.toView(crop.right, crop.bottom),
    )
    val (corner, at) = corners.minBy { (_, c) -> hypot(c.x - p.x, c.y - p.y) }
    return when {
        hypot(at.x - p.x, at.y - p.y) <= grab -> corner
        crop.contains(fit.toImage(p)) -> CropHandle.MOVE
        else -> null
    }
}

/** [crop] after dragging [handle] by [d] image pixels, kept inside [full] and at least [minSide]. */
private fun moveCrop(crop: Rect, handle: CropHandle, d: Offset, full: Rect, minSide: Float): Rect = when (handle) {
    CropHandle.TOP_LEFT -> crop.copy(
        left = (crop.left + d.x).coerceIn(0f, crop.right - minSide),
        top = (crop.top + d.y).coerceIn(0f, crop.bottom - minSide),
    )
    CropHandle.TOP_RIGHT -> crop.copy(
        right = (crop.right + d.x).coerceIn(crop.left + minSide, full.right),
        top = (crop.top + d.y).coerceIn(0f, crop.bottom - minSide),
    )
    CropHandle.BOTTOM_LEFT -> crop.copy(
        left = (crop.left + d.x).coerceIn(0f, crop.right - minSide),
        bottom = (crop.bottom + d.y).coerceIn(crop.top + minSide, full.bottom),
    )
    CropHandle.BOTTOM_RIGHT -> crop.copy(
        right = (crop.right + d.x).coerceIn(crop.left + minSide, full.right),
        bottom = (crop.bottom + d.y).coerceIn(crop.top + minSide, full.bottom),
    )
    CropHandle.MOVE -> crop.translate(
        d.x.coerceIn(-crop.left, full.right - crop.right),
        d.y.coerceIn(-crop.top, full.bottom - crop.bottom),
    )
}

@Composable
private fun ImageEditor(uri: Uri, keys: EditorKeys, onCancel: () -> Unit, onAttach: (Uri?, String) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val image by produceState<Result<Bitmap>?>(null, uri) {
        value = withContext(Dispatchers.IO) { loadEditable(context.contentResolver, uri) }
            ?.let { Result.success(it) } ?: Result.failure(IOException("unreadable"))
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
            val bitmap = image?.getOrNull()
            when {
                image == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                bitmap == null -> Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
                    Text("This image can't be opened anymore.", style = MaterialTheme.typography.bodyLarge)
                    TextButton(onClick = onCancel) { Text("Close") }
                }
                else -> EditorBody(bitmap, keys, onCancel, onAttach)
            }
        }
    }
}

@Composable
private fun ColumnScope.EditorBody(bitmap: Bitmap, keys: EditorKeys, onCancel: () -> Unit, onAttach: (Uri?, String) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val full = remember(bitmap) { Rect(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat()) }
    val sizes = remember(bitmap) { MarkScale(bitmap.width, bitmap.height) }
    val marks = remember(bitmap) { mutableStateListOf<Mark>() }
    var stroke by remember { mutableStateOf<List<Pair<Float, Float>>?>(null) }
    var tool by remember { mutableStateOf(Tool.PEN) }
    /** A palette color or [BLUR]. Notes can't blur, so they use [solidColor], the last color picked. */
    var color by remember { mutableStateOf(PALETTE[0]) }
    var solidColor by remember { mutableStateOf(PALETTE[0]) }
    /** Whether new rectangles and circles are filled with the color (or blurred inside). */
    var fill by remember { mutableStateOf(false) }
    val preview = remember(bitmap) { BlurPreview(bitmap, scope) }
    /** Line thickness for pen, highlighter and shapes, and the size of new notes (1 to MAX_LEVEL). */
    var strokeLevel by remember { mutableIntStateOf(MarkScale.DEFAULT_LEVEL) }
    var textLevel by remember { mutableIntStateOf(MarkScale.DEFAULT_LEVEL) }
    var shapeDraft by remember { mutableStateOf<Mark.Shape?>(null) }
    /**
     * The placed shape with handles, by index into marks: the one just drawn, or one tapped with a
     * shape tool. Cleared on a tool change and on undo or redo, so it never points at another mark.
     */
    var selected by remember { mutableStateOf<Int?>(null) }
    /** The selected shape while a finger moves or resizes it; committed (undoably) on release. */
    var shapeEdit by remember { mutableStateOf<Mark.Shape?>(null) }
    /** Whether a shape has been moved or resized yet; until then a selected shape shows how. */
    var shapeHintSeen by remember { mutableStateOf(false) }
    var crop by remember(bitmap) { mutableStateOf(full) }
    /** The note being added (index null, at a tapped spot) or edited (index of an existing one). */
    var labelDraft by remember { mutableStateOf<LabelDraft?>(null) }
    var caption by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    /** Where a finger is on the canvas while drawing or dragging a crop handle; shown in the loupe. */
    var touch by remember { mutableStateOf<Offset?>(null) }
    /** The canvas corner the loupe is docked in while a finger is down (see [LoupePlacement]). */
    var loupeCorner by remember { mutableStateOf<Int?>(null) }
    val editorSettings by AppGraph.settings.settings.collectAsStateWithLifecycle()
    val loupeCorners by rememberUpdatedState(editorSettings.loupeCorners.corners)
    val loupeTool by rememberUpdatedState(tool != Tool.TEXT)
    val inset = with(density) { 16.dp.toPx() }
    val shown = if (tool == Tool.CROP) full else crop
    /**
     * Two-finger zoom; back to the whole image whenever what is shown changes. One state for the
     * editor's lifetime: the pinch handler never restarts, so a state keyed on [shown] would leave
     * it zooming a discarded copy after the first crop.
     */
    var viewZoom by remember { mutableStateOf(ViewZoom()) }
    var zoomShows by remember { mutableStateOf(shown) }
    if (zoomShows != shown) {
        zoomShows = shown
        viewZoom = ViewZoom()
    }
    val fit = fitOf(canvasSize.toSize(), shown, inset).let { f ->
        Fit(f.scale * viewZoom.zoom, f.dx * viewZoom.zoom + viewZoom.pan.x, f.dy * viewZoom.zoom + viewZoom.pan.y)
    }
    val history = remember(bitmap) { EditHistory<EditState>() }
    fun snapshot() = EditState(marks.toList(), crop)
    fun restore(state: EditState) {
        selected = null
        shapeEdit = null
        marks.clear()
        marks.addAll(state.marks)
        crop = state.crop
    }
    /** Every undoable change goes through here. */
    fun change(block: () -> Unit) {
        history.record(snapshot())
        block()
    }
    fun undo() { history.undo(snapshot())?.let(::restore) }
    fun redo() { history.redo(snapshot())?.let(::restore) }
    SideEffect {
        keys.undo = ::undo
        keys.redo = ::redo
    }

    /**
     * Line width for new marks. With the blur swatch, lines are drawn wide, so one drag covers a
     * line of text; a filled shape keeps its normal edge, so it covers just the dragged box.
     */
    fun lineWidth(filledShape: Boolean = false) = when {
        color == BLUR && !filledShape -> sizes.blurBrush(strokeLevel)
        tool == Tool.HIGHLIGHT -> sizes.highlighter(strokeLevel)
        else -> sizes.stroke(strokeLevel)
    }
    val filledShape = fill && tool.shape?.fillable == true

    /**
     * The rotate knob of a selected rectangle or oval, in image pixels: beyond its top edge, or
     * the first of its bottom, right and left edges where the knob is fully on the canvas. A shape
     * that fills the canvas gets its top knob pulled onto the canvas. Null for lines and arrows,
     * which turn by dragging an end.
     */
    fun knobOf(shape: Mark.Shape, fit: Fit): Pair<Float, Float>? {
        if (!shape.kind.fillable) return null
        val distance = with(density) { KNOB_DISTANCE.toPx() } / fit.scale
        val margin = with(density) { (KNOB_RADIUS + 4.dp).toPx() }
        val w = canvasSize.width.toFloat()
        val h = canvasSize.height.toFloat()
        fun onCanvas(p: Pair<Float, Float>) = fit.toView(p.first, p.second).let { v ->
            v.x in margin..w - margin && v.y in margin..h - margin
        }
        KnobSide.entries.map { shape.knobPoint(distance, it) }.firstOrNull(::onCanvas)?.let { return it }
        if (w < 2 * margin || h < 2 * margin) return shape.knobPoint(distance)
        val top = shape.knobPoint(distance).let { fit.toView(it.first, it.second) }
        return fit.toImage(Offset(top.x.coerceIn(margin, w - margin), top.y.coerceIn(margin, h - margin))).let { it.x to it.y }
    }

    /** Line width of a shape at [level]: wide for a blurred outline, as when drawing it. */
    fun shapeWidth(level: Int, shapeColor: Int, filled: Boolean) =
        if (shapeColor == BLUR && !filled) sizes.blurBrush(level) else sizes.stroke(level)
    /** The thickness level a shape was drawn at. */
    fun levelOf(shape: Mark.Shape) = (1..MarkScale.MAX_LEVEL).minBy {
        abs(shapeWidth(it, shape.color, shape.filled && shape.kind.fillable) - shape.width)
    }

    fun currentStroke(points: List<Pair<Float, Float>>) =
        Mark.Stroke(points, color, lineWidth(), if (tool == Tool.HIGHLIGHT && color != BLUR) 110 else 255)

    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onCancel) { Icon(SlimIcons.Close, "Cancel") }
        Text("Edit image", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (viewZoom.zoom > 1f) TextButton(onClick = { viewZoom = ViewZoom() }) { Text("Fit") }
        // Read the marks and crop so the buttons update with every change.
        marks.size; crop
        IconButton(enabled = history.canUndo, onClick = ::undo) { Icon(SlimIcons.Undo, "Undo") }
        IconButton(enabled = history.canRedo, onClick = ::redo) { Icon(SlimIcons.Redo, "Redo") }
    }

    Box(
        Modifier
            .weight(1f)
            .fillMaxWidth()
            // A zoomed image must not paint over the top bar or the caption below.
            .clipToBounds()
            .background(Color(0xFF101418))
            .onSizeChanged { canvasSize = it },
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = "Image being edited" }
                // Watch the finger without consuming anything, so the loupe follows every tool's gesture.
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        // After a two-finger zoom, the finger left behind doesn't draw, so no loupe.
                        var zooming = false
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val down = event.changes.filter { it.pressed }
                            if (down.size >= 2) zooming = true else if (down.isEmpty()) zooming = false
                            val at = down.singleOrNull()?.position?.takeIf { loupeTool && !zooming }
                            touch = at
                            loupeCorner = at?.let {
                                LoupePlacement.corner(
                                    it, loupeCorner, loupeCorners, size.width.toFloat(), size.height.toFloat(),
                                    LOUPE_RADIUS.toPx(), LOUPE_MARGIN.toPx(), (LOUPE_RADIUS + 56.dp).toPx(),
                                )
                            }
                        }
                    }
                }
                // Two fingers zoom and pan. Taking their events first cancels a stroke, shape or
                // drag the first finger started, so nothing is drawn.
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val down = event.changes.count { it.pressed }
                            if (down == 0) break
                            if (down < 2) continue
                            viewZoom = viewZoom.pinch(
                                event.calculateZoom(), event.calculateCentroid(), event.calculatePan(),
                                size.width.toFloat(), size.height.toFloat(),
                            )
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
                .pointerInput(tool, fit.scale, fit.dx, fit.dy) {
                    when (tool) {
                        // Strokes and crop handles start where the finger went down, not where the
                        // drag passed the touch slop: a quick drag would otherwise lose its start
                        // or grab the wrong crop handle.
                        Tool.PEN, Tool.HIGHLIGHT -> awaitEachGesture {
                            val down = awaitFirstDown()
                            fit.toImage(down.position).let { stroke = listOf(it.x to it.y) }
                            val completed = drag(down.id) { change ->
                                change.consume()
                                val p = fit.toImage(change.position)
                                stroke = stroke.orEmpty() + (p.x to p.y)
                            }
                            if (completed) stroke?.let { s -> change { marks += currentStroke(s) } }
                            stroke = null
                        }
                        // Drag from one corner to the other (or from tail to tip); tiny drags are ignored.
                        // A selected shape is moved by dragging it and resized by its handles; a
                        // tap selects the shape under it, or clears the selection on empty space.
                        Tool.RECTANGLE, Tool.OVAL, Tool.ARROW, Tool.LINE -> awaitEachGesture {
                            val down = awaitFirstDown()
                            val kind = tool.shape ?: return@awaitEachGesture
                            val a = fit.toImage(down.position)
                            val reach = SHAPE_GRAB.toPx() / fit.scale
                            /** Selects the topmost visible shape under a tap, or nothing on empty space. */
                            fun tapSelect() {
                                selected = shapeAt(marks, a.x, a.y, reach).takeIf { it >= 0 && crop.contains(a) }
                            }
                            val index = selected
                            val picked = index?.let { marks.getOrNull(it) as? Mark.Shape }
                            // The rotate knob comes first: it sits outside the box, clear of the corners.
                            val knob = picked?.let { knobOf(it, fit) }
                            val onKnob = knob != null && hypot(knob.first - a.x, knob.second - a.y) <= HANDLE_GRAB.toPx() / fit.scale
                            val handle = if (onKnob) null else picked?.let { handleAt(it, a.x, a.y, HANDLE_GRAB.toPx() / fit.scale) }
                            if (index != null && picked != null && (onKnob || handle != null || grabsBody(picked, a.x, a.y, reach))) {
                                // A tap, even inside the selected box, selects what is under it instead.
                                val dragStart = awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                                if (dragStart == null) {
                                    if (currentEvent.changes.none { it.pressed } && handle == null && !onKnob) tapSelect()
                                    return@awaitEachGesture
                                }
                                val (hx, hy) = handle?.let { picked.handlePoint(it) } ?: (0f to 0f)
                                // Follows the finger from where the drag passed the touch slop, so
                                // nothing jumps. The shape stops at the crop's edge rather than
                                // leaving where it can be seen and tapped.
                                val start = fit.toImage(dragStart.position)
                                val area = Area(crop.left, crop.top, crop.right, crop.bottom)
                                fun editedAt(position: Offset) = fit.toImage(position).let { p ->
                                    when {
                                        // Turns about the middle, following the finger's angle from where it went down.
                                        onKnob -> picked.rotatedWithin(picked.rotationFollowing(a.x, a.y, p.x, p.y), area)
                                        handle != null -> picked.resizedWithin(handle, hx + p.x - start.x, hy + p.y - start.y, area)
                                        else -> picked.movedWithin(p.x - start.x, p.y - start.y, area)
                                    }
                                }
                                // Undo or redo mid-drag clears the selection: then this drag is dropped.
                                fun stillOurs() = selected == index && marks.getOrNull(index) == picked
                                shapeEdit = if (onKnob) editedAt(dragStart.position) else picked
                                // A pinch restarts this handler mid-drag; the draft must not outlive it.
                                try {
                                    val completed = drag(dragStart.id) { change ->
                                        change.consume()
                                        if (stillOurs()) shapeEdit = editedAt(change.position)
                                    }
                                    val edited = shapeEdit
                                    if (completed && edited != null && edited != picked && stillOurs()) {
                                        change { marks[index] = edited }
                                        shapeHintSeen = true
                                    }
                                } finally {
                                    shapeEdit = null
                                }
                                return@awaitEachGesture
                            }
                            fun to(p: Offset) = fit.toImage(p).let { b ->
                                // Lines snap to horizontal, vertical or 45° when close to one.
                                val (x, y) = if (kind == ShapeKind.LINE) snapLineEnd(a.x, a.y, b.x, b.y) else b.x to b.y
                                Mark.Shape(kind, a.x, a.y, x, y, color, lineWidth(fill && kind.fillable), fill && kind.fillable)
                            }
                            shapeDraft = to(down.position)
                            val completed = drag(down.id) { change ->
                                change.consume()
                                shapeDraft = to(change.position)
                            }
                            val drawn = shapeDraft
                            if (completed && drawn != null &&
                                hypot(drawn.x2 - drawn.x1, drawn.y2 - drawn.y1) * fit.scale > 12.dp.toPx()
                            ) {
                                change { marks += drawn }
                                selected = marks.lastIndex
                            } else if (completed) {
                                tapSelect()
                            }
                            shapeDraft = null
                        }
                        // Removes every mark the eraser touches, along the whole path of the finger.
                        Tool.ERASER -> awaitEachGesture {
                            val down = awaitFirstDown()
                            var recorded = false
                            var last = down.position
                            fun eraseAt(p: Offset) {
                                val at = fit.toImage(p)
                                val r = ERASER_RADIUS.toPx() / fit.scale
                                val hit = marks.indices.filter { i ->
                                    val m = marks[i]
                                    if (m is Mark.Label) labelBounds(m).let { b ->
                                        at.x in b.left - r..b.right + r && at.y in b.top - r..b.bottom + r
                                    } else touches(m, at.x, at.y, r)
                                }
                                if (hit.isEmpty()) return
                                if (!recorded) { history.record(snapshot()); recorded = true }
                                hit.asReversed().forEach { marks.removeAt(it) }
                            }
                            // Check points along a fast swipe too, not just where events land.
                            fun eraseTo(p: Offset) {
                                val steps = ((p - last).getDistance() / ERASER_RADIUS.toPx()).toInt().coerceAtLeast(1)
                                for (i in 1..steps) eraseAt(last + (p - last) * (i / steps.toFloat()))
                                last = p
                            }
                            eraseAt(down.position)
                            drag(down.id) { change ->
                                change.consume()
                                eraseTo(change.position)
                            }
                        }
                        // Tap empty space to add a note, tap a note to edit it, drag a note to move it.
                        Tool.TEXT -> awaitEachGesture {
                            val down = awaitFirstDown()
                            val at = fit.toImage(down.position)
                            val hit = marks.indexOfLast { it is Mark.Label && labelBounds(it).contains(at.x, at.y) }
                            val dragStart = awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                            if (dragStart == null) {
                                if (currentEvent.changes.none { it.pressed }) {
                                    labelDraft = if (hit >= 0) LabelDraft(hit, at) else LabelDraft(null, at)
                                }
                            } else if (hit >= 0) {
                                history.record(snapshot())
                                var last = down.position
                                fun moveBy(to: Offset) {
                                    val d = (to - last) / fit.scale
                                    last = to
                                    val label = marks[hit] as Mark.Label
                                    marks[hit] = label.copy(
                                        x = (label.x + d.x).coerceIn(0f, full.right - 24f),
                                        y = (label.y + d.y).coerceIn(0f, full.bottom - 24f),
                                    )
                                }
                                moveBy(dragStart.position)
                                drag(dragStart.id) { change ->
                                    change.consume()
                                    moveBy(change.position)
                                }
                            }
                        }
                        Tool.CROP -> awaitEachGesture {
                            val down = awaitFirstDown()
                            val handle = cropHandleAt(down.position, crop, fit, 40.dp.toPx()) ?: return@awaitEachGesture
                            val minSide = min(full.width, full.height) * 0.08f
                            var last = down.position
                            var recorded = false
                            drag(down.id) { change ->
                                change.consume()
                                if (!recorded) { history.record(snapshot()); recorded = true }
                                val d = (change.position - last) / fit.scale
                                last = change.position
                                crop = moveCrop(crop, handle, d, full, minSide)
                            }
                        }
                    }
                },
        ) {
            val handleRadius = 9.dp.toPx() / fit.scale
            val line = 2.dp.toPx() / fit.scale
            // A plain copy: Compose's state list compares by identity, so the blur preview's caches
            // (which compare mark lists) would miss on every frame and rebuild the image each time.
            val marks = marks.toList()
            fun scene(nc: android.graphics.Canvas) {
                nc.save()
                nc.translate(fit.dx, fit.dy)
                nc.scale(fit.scale, fit.scale)
                if (tool != Tool.CROP) nc.clipRect(crop.left, crop.top, crop.right, crop.bottom)
                val editIndex = selected?.takeIf { shapeEdit != null && it in marks.indices }
                val moving = shapeEdit
                if (editIndex == null || moving == null) {
                    // Blurs are pixels like the export's; the marks after the last one stay vectors.
                    val (base, blurred) = preview.base(marks)
                    nc.drawBitmap(base, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
                    drawMarks(nc, marks.subList(blurred, marks.size))
                } else {
                    // A shape being moved or resized keeps its place among the marks: the image
                    // with the marks below it, then the shape, then the marks above it, without
                    // re-blurring every frame. A blur above it blurs the image without the shape,
                    // so it hides the moving shape where they overlap, as the blurred copy would.
                    val below = marks.subList(0, editIndex)
                    val above = marks.subList(editIndex + 1, marks.size)
                    val (base, blurred) = preview.base(below)
                    nc.drawBitmap(base, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
                    drawMarks(nc, below.subList(blurred, below.size))
                    val moved = if (moving.blurs) preview.live(below, blurCell(moving, bitmap.width, bitmap.height)) else null
                    drawMarks(nc, listOf(moving), moved)
                    val paints = preview.above(below, moving, above)
                    above.forEachIndexed { i, m -> drawMarks(nc, listOf(m), paints[i]) }
                }
                val draft = stroke?.let { currentStroke(it) } ?: shapeDraft
                if (draft != null) {
                    val blur = if (draft.blurs) preview.live(marks, blurCell(draft, bitmap.width, bitmap.height)) else null
                    drawMarks(nc, listOf(draft), blur)
                }
                if (tool == Tool.CROP) {
                    val dim = Paint().apply { this.color = 0x99000000.toInt() }
                    nc.drawRect(0f, 0f, full.right, crop.top, dim)
                    nc.drawRect(0f, crop.bottom, full.right, full.bottom, dim)
                    nc.drawRect(0f, crop.top, crop.left, crop.bottom, dim)
                    nc.drawRect(crop.right, crop.top, full.right, crop.bottom, dim)
                    val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.STROKE
                        strokeWidth = line
                        this.color = 0xFFFFFFFF.toInt()
                    }
                    nc.drawRect(crop.left, crop.top, crop.right, crop.bottom, edge)
                    val knob = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0xFFFFFFFF.toInt() }
                    for ((x, y) in listOf(crop.left to crop.top, crop.right to crop.top, crop.left to crop.bottom, crop.right to crop.bottom)) {
                        nc.drawCircle(x, y, handleRadius, knob)
                    }
                }
                nc.restore()
                // Handles aren't clipped to the crop, so a corner hanging over its edge can be grabbed.
                val picked = if (editIndex != null) moving
                    else selected?.takeIf { tool.shape != null && shapeDraft == null }?.let { marks.getOrNull(it) as? Mark.Shape }
                if (picked != null) {
                    nc.save()
                    nc.translate(fit.dx, fit.dy)
                    nc.scale(fit.scale, fit.scale)
                    drawSelection(
                        nc, picked, line, handleRadius, knobOf(picked, fit), KNOB_RADIUS.toPx() / fit.scale,
                        KNOB_DISTANCE.toPx() / fit.scale,
                    )
                    nc.restore()
                }
            }
            drawIntoCanvas { c ->
                scene(c.nativeCanvas)
                val at = touch ?: return@drawIntoCanvas
                val brush = when (tool) {
                    // Moving or resizing a shape shows the crosshair, not a brush.
                    Tool.RECTANGLE, Tool.OVAL, Tool.ARROW, Tool.LINE -> if (shapeEdit != null) 0f else lineWidth(filledShape)
                    Tool.PEN, Tool.HIGHLIGHT -> lineWidth(filledShape)
                    Tool.ERASER -> 2 * ERASER_RADIUS.toPx() / fit.scale
                    else -> 0f
                }
                val radius = LOUPE_RADIUS.toPx()
                val center = LoupePlacement.corners(size.width, size.height, radius, LOUPE_MARGIN.toPx())[loupeCorner ?: 0]
                drawLoupe(
                    c.nativeCanvas, at, center, radius, ::scene, brush * fit.scale,
                    if (tool == Tool.ERASER || color == BLUR) 0xFFFFFFFF.toInt() else color, density.density,
                )
            }
        }
        // Until the first move or resize, a selected shape says how; on the canvas, so it's never
        // scrolled away with the tools. It takes no touches.
        val chosen = selected?.takeIf { tool.shape != null }?.let { marks.getOrNull(it) as? Mark.Shape }
        if (!shapeHintSeen && chosen != null && shapeEdit == null && shapeDraft == null && touch == null) {
            Text(
                if (chosen.kind.fillable) "Drag to move · corners resize · knob turns · tap outside to draw"
                else "Drag to move · drag an end to resize · tap outside to draw",
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(10.dp)
                    .background(Color(0xE6202428), CircleShape)
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        // A delete button beside the selected shape: above its right side if there's room, else the
        // first other spot around it that stays clear of every handle and the rotate knob (so it
        // never takes a touch meant for them), else the spot farthest from them. Hidden while the
        // shape is being changed.
        if (chosen != null && shapeEdit == null && shapeDraft == null && canvasSize.width > 0) {
            val grips = (handlesOf(chosen).map { it.second } + listOfNotNull(knobOf(chosen, fit)))
                .map { fit.toView(it.first, it.second) }
            val button = with(density) { DELETE_BUTTON.toPx() }
            val half = button / 2
            val pad = with(density) { (KNOB_RADIUS + 10.dp).toPx() } + half
            val clear = with(density) { (DELETE_BUTTON / 2 + HANDLE_GRAB).toPx() }
            val left = grips.minOf { it.x }
            val right = grips.maxOf { it.x }
            val top = grips.minOf { it.y }
            val bottom = grips.maxOf { it.y }
            val midX = (left + right) / 2
            val midY = (top + bottom) / 2
            val spots = listOf(
                Offset(right - half, top - pad), Offset(left + half, top - pad), Offset(midX, top - pad),
                Offset(right - half, bottom + pad), Offset(left + half, bottom + pad), Offset(midX, bottom + pad),
                Offset(right + pad, midY), Offset(left - pad, midY), Offset(midX, midY),
            ).map {
                Offset(
                    it.x.coerceIn(half, max(half, canvasSize.width - half)),
                    it.y.coerceIn(half, max(half, canvasSize.height - half)),
                )
            }
            fun room(at: Offset) = grips.minOf { (it - at).getDistance() }
            val spot = spots.firstOrNull { room(it) >= clear } ?: spots.maxBy(::room)
            val x = spot.x - half
            val y = spot.y - half
            Box(
                Modifier
                    .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                    .size(DELETE_BUTTON)
                    .clip(CircleShape)
                    .background(Color(0xE6202428))
                    .clickable(role = Role.Button, onClickLabel = "Delete") {
                        val i = selected ?: return@clickable
                        if (marks.getOrNull(i) is Mark.Shape) {
                            selected = null
                            change { marks.removeAt(i) }
                        }
                    }
                    .semantics { contentDescription = "Delete shape" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(SlimIcons.Trash, null, Modifier.size(20.dp), tint = Color.White)
            }
        }
    }

    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tool.entries.forEach { t ->
            FilterChip(
                selected = tool == t,
                onClick = {
                    tool = t
                    selected = null
                },
                label = {
                    if (t.icon != null) Icon(t.icon, t.label, Modifier.size(20.dp)) else Text(t.label)
                },
            )
        }
        Spacer(Modifier.width(8.dp))
        if (tool == Tool.CROP) {
            TextButton(enabled = crop != full, onClick = { change { crop = full } }) { Text("Reset crop") }
        } else {
            if (tool == Tool.TEXT) {
                Text(
                    "Tap to add a note · drag a note to move it · tap it to edit",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    // One row of options under the tools, always [OPTION_ROW] tall so the image never jumps.
    val isText = tool == Tool.TEXT
    val level = if (isText) textLevel else strokeLevel
    fun setLevel(v: Int) {
        val clamped = v.coerceIn(1, MarkScale.MAX_LEVEL)
        if (isText) textLevel = clamped else strokeLevel = clamped
    }
    val optionRow = Modifier.fillMaxWidth().height(OPTION_ROW).padding(start = 16.dp, end = 12.dp)
    when (tool) {
        Tool.CROP -> {}
        // The eraser has no colour or thickness; its hint takes the same space.
        Tool.ERASER -> Row(optionRow, verticalAlignment = Alignment.CenterVertically) {
            Hint("Drag over a drawing to remove it · Undo brings it back")
        }
        // Colors and blur, the fill switch for rectangles and circles, then one compact size
        // stepper: line thickness (wider with blur), or note size in Text mode, in pixels on the
        // image. All apply to new marks.
        // With a shape selected, they show and change that shape (undoably), and new shapes follow.
        else -> Row(optionRow, verticalAlignment = Alignment.CenterVertically) {
            val styled = selected?.takeIf { tool.shape != null }?.let { marks.getOrNull(it) as? Mark.Shape }
            fun restyle(update: (Mark.Shape) -> Mark.Shape) {
                val i = selected ?: return
                val s = marks.getOrNull(i) as? Mark.Shape ?: return
                val next = update(s)
                if (next != s) change { marks[i] = next }
            }
            fun Mark.Shape.inColor(c: Int) = copy(color = c, width = shapeWidth(levelOf(this), c, filled && kind.fillable))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                val picked = if (isText) solidColor else styled?.color ?: color
                PALETTE.forEach { c ->
                    Swatch(c, c == picked, "Color") {
                        color = c
                        solidColor = c
                        if (styled != null) restyle { it.inColor(c) }
                    }
                }
                // Notes can't blur.
                if (!isText) Swatch(BLUR, picked == BLUR, "Blur") {
                    color = BLUR
                    if (styled != null) restyle { it.inColor(BLUR) }
                }
            }
            Spacer(Modifier.weight(1f))
            if ((styled?.kind ?: tool.shape)?.fillable == true) {
                FillSwitch(styled?.filled ?: fill) { on ->
                    fill = on
                    if (styled != null) restyle { it.copy(filled = on, width = shapeWidth(levelOf(it), it.color, on)) }
                }
                Spacer(Modifier.width(8.dp))
            }
            if (styled != null) {
                // Steps to the next width that differs: on a small image, low levels share a width.
                val shownLevel = levelOf(styled)
                SizeStepper(shownLevel, styled.width.roundToInt(), isText = false) { v ->
                    val filled = styled.filled && styled.kind.fillable
                    fun at(l: Int) = shapeWidth(l, styled.color, filled)
                    val next = if (v > shownLevel) (1..MarkScale.MAX_LEVEL).firstOrNull { at(it) > styled.width + 0.01f }
                        else (MarkScale.MAX_LEVEL downTo 1).firstOrNull { at(it) < styled.width - 0.01f }
                    if (next != null) {
                        setLevel(next)
                        restyle { it.copy(width = at(next)) }
                    }
                }
            } else {
                val px = (if (isText) sizes.text(level) else lineWidth(filledShape)).roundToInt()
                SizeStepper(level, px, isText, ::setLevel)
            }
        }
    }
    if (tool != Tool.CROP) Spacer(Modifier.height(10.dp))

    // Caption: sent as text with the image, like a chat app's image send screen.
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            caption, { caption = it }, Modifier.weight(1f),
            placeholder = { Text("Add a caption…") },
            maxLines = 4,
        )
        Spacer(Modifier.width(10.dp))
        Button(
            enabled = !saving,
            onClick = {
                saving = true
                scope.launch {
                    val saved = withContext(Dispatchers.Default) {
                        val edited = renderEdit(bitmap, marks.toList(), RectF(crop.left, crop.top, crop.right, crop.bottom))
                        ImageEditorActivity.save(context, edited)
                    }
                    saving = false
                    onAttach(saved, caption.trim())
                }
            },
        ) {
            Icon(SlimIcons.Send, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Attach")
        }
    }

    labelDraft?.let { draft ->
        val existing = draft.index?.let { marks.getOrNull(it) as? Mark.Label }
        var text by remember(draft) { mutableStateOf(existing?.text.orEmpty()) }
        val focus = remember { FocusRequester() }
        fun close() { labelDraft = null }
        AlertDialog(
            onDismissRequest = ::close,
            title = { Text(if (existing == null) "Add a note" else "Edit note") },
            text = {
                OutlinedTextField(
                    text, { text = it }, Modifier.fillMaxWidth().focusRequester(focus),
                    placeholder = { Text("Note on the image") }, maxLines = 5,
                )
                LaunchedEffect(Unit) { focus.requestFocus() }
            },
            confirmButton = {
                TextButton(enabled = text.isNotBlank(), onClick = {
                    val index = draft.index
                    change {
                        if (existing != null && index != null) {
                            marks[index] = existing.copy(text = text.trim())
                        } else {
                            marks += Mark.Label(draft.at.x, draft.at.y, text.trim(), solidColor, sizes.text(textLevel), sizes.labelWidth)
                        }
                    }
                    close()
                }) { Text(if (existing == null) "Add" else "Save") }
            },
            dismissButton = {
                Row {
                    if (existing != null) {
                        TextButton(onClick = {
                            draft.index?.let { i -> change { marks.removeAt(i) } }
                            close()
                        }) { Text("Delete") }
                    }
                    TextButton(onClick = ::close) { Text("Cancel") }
                }
            },
        )
    }
}

private data class LabelDraft(val index: Int?, val at: Offset)

private val LOUPE_RADIUS = 60.dp
/** How far from a selected shape's handle a finger still grabs it, on screen. */
private val HANDLE_GRAB = 24.dp
/** How far from a shape's line a tap selects it, and from a selected shape a drag moves it. */
private val SHAPE_GRAB = 14.dp
/** How far the rotate knob sits beyond a selected box's edge, and its size, on screen. */
private val KNOB_DISTANCE = 30.dp
private val KNOB_RADIUS = 12.dp
/** The delete button next to a selected shape. */
private val DELETE_BUTTON = 40.dp

/**
 * Marks [shape] as selected, in image coordinates: a dashed box around a rectangle or oval (turned
 * with it), a round handle on each corner or end, each [line] wide and [radius] in size on screen,
 * and the rotate [knob] ([knobRadius]) on a [stem]-long line toward the shape, if it has one.
 */
private fun drawSelection(
    nc: android.graphics.Canvas,
    shape: Mark.Shape,
    line: Float,
    radius: Float,
    knob: Pair<Float, Float>?,
    knobRadius: Float,
    stem: Float,
) {
    val shade = 0x99000000.toInt()
    val white = 0xFFFFFFFF.toInt()
    if (shape.kind.fillable) {
        // A gap outside the outline, so the dashes never hide the shape's own edge.
        val pad = shape.width / 2 + 3 * line
        val box = RectF(
            min(shape.x1, shape.x2) - pad, min(shape.y1, shape.y2) - pad,
            max(shape.x1, shape.x2) + pad, max(shape.y1, shape.y2) + pad,
        )
        val dash = android.graphics.DashPathEffect(floatArrayOf(4 * line, 3 * line), 0f)
        nc.save()
        nc.rotate(shape.rotation, shape.centerX, shape.centerY)
        for ((c, w) in listOf(shade to 2.5f * line, white to line)) {
            nc.drawRect(box, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = w
                color = c
                pathEffect = dash
            })
        }
        nc.restore()
    }
    if (knob != null) {
        // The stem runs from the knob toward the middle of the shape, as far as the box's edge.
        val (kx, ky) = knob
        val (cx, cy) = shape.centerX to shape.centerY
        val length = hypot(kx - cx, ky - cy)
        val reach = min(stem, length)
        val (sx, sy) = if (length > 0f) (kx + (cx - kx) * reach / length) to (ky + (cy - ky) * reach / length) else cx to cy
        val stemPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
        for ((c, w) in listOf(shade to 2.5f * line, white to line)) {
            stemPaint.color = c
            stemPaint.strokeWidth = w
            nc.drawLine(sx, sy, kx, ky, stemPaint)
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        fill.color = shade
        nc.drawCircle(kx, ky, knobRadius + line, fill)
        fill.color = white
        nc.drawCircle(kx, ky, knobRadius, fill)
        // A circling arrow on the knob, so it reads as "turn" rather than another handle.
        val r = knobRadius * 0.5f
        val glyph = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = line * 0.9f
            strokeCap = Paint.Cap.ROUND
            color = 0xFF202428.toInt()
        }
        nc.drawArc(RectF(kx - r, ky - r, kx + r, ky + r), -60f, 280f, false, glyph)
        val tip = Math.toRadians(220.0)
        val tx = kx + r * kotlin.math.cos(tip).toFloat()
        val ty = ky + r * kotlin.math.sin(tip).toFloat()
        val head = r * 0.6f
        nc.drawLine(tx, ty, tx + head, ty, glyph)
        nc.drawLine(tx, ty, tx, ty + head, glyph)
    }
    val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    for ((_, p) in handlesOf(shape)) {
        knobPaint.color = shade
        nc.drawCircle(p.first, p.second, radius + line, knobPaint)
        knobPaint.color = white
        nc.drawCircle(p.first, p.second, radius, knobPaint)
    }
}
/** The eraser's reach around the finger, on screen. */
private val ERASER_RADIUS = 12.dp
private val LOUPE_MARGIN = 12.dp

/**
 * Draws a magnified circle of the canvas around [at], centred on [center] (a corner away from the
 * finger). A ring at its centre marks the exact spot, sized like the [brush] (canvas px) in the
 * drawing [color].
 */
private fun drawLoupe(
    nc: android.graphics.Canvas,
    at: Offset,
    center: Offset,
    radius: Float,
    scene: (android.graphics.Canvas) -> Unit,
    brush: Float,
    color: Int,
    density: Float,
) {
    val zoom = 2f
    val cx = center.x
    val cy = center.y
    val circle = Path().apply { addCircle(cx, cy, radius, Path.Direction.CW) }
    nc.drawCircle(cx, cy + 2 * density, radius + 1 * density, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0x66000000 })
    nc.save()
    nc.clipPath(circle)
    nc.drawColor(0xFF101418.toInt())
    nc.translate(cx, cy)
    nc.scale(zoom, zoom)
    nc.translate(-at.x, -at.y)
    scene(nc)
    nc.restore()
    val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2 * density
        this.color = 0xFFFFFFFF.toInt()
    }
    nc.drawCircle(cx, cy, radius, ring)
    // The spot under the finger: the brush outline in its colour, or a small crosshair.
    val mark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        this.color = if (brush > 0f) color or 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
    }
    val dot = max(brush * zoom / 2, 4 * density)
    nc.drawCircle(cx, cy, dot, ring.apply { strokeWidth = 3.5f * density; this.color = 0x99000000.toInt() })
    nc.drawCircle(cx, cy, dot, mark)
    if (brush <= 0f) {
        val arm = 10 * density
        nc.drawLine(cx - arm, cy, cx - dot, cy, mark)
        nc.drawLine(cx + dot, cy, cx + arm, cy, mark)
        nc.drawLine(cx, cy - arm, cx, cy - dot, mark)
        nc.drawLine(cx, cy + dot, cx, cy + arm, mark)
    }
}

private val OPTION_ROW = 32.dp

@Composable
private fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
}

/** A round swatch of [color], or the frosted blur swatch for [BLUR]. */
@Composable
private fun Swatch(color: Int, selected: Boolean, name: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(28.dp)
            .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Gray, CircleShape)
            .padding(4.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = if (selected) "Selected ${name.lowercase()}" else name },
    ) {
        Canvas(Modifier.fillMaxSize()) { if (color == BLUR) drawBlurSwatch() else drawRect(Color(color)) }
    }
}

/** Soft overlapping blobs on grey, like a frosted pane: reads as "blurred" at swatch size. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBlurSwatch() {
    drawRect(Color(0xFFB8C2CC))
    val r = size.minDimension
    for ((at, tint) in listOf(
        Offset(0.3f, 0.35f) to Color(0xFFE53935), Offset(0.72f, 0.4f) to Color(0xFF1E88E5), Offset(0.45f, 0.78f) to Color(0xFFFDD835),
    )) {
        val center = Offset(at.x * size.width, at.y * size.height)
        drawCircle(Brush.radialGradient(listOf(tint.copy(alpha = 0.85f), tint.copy(alpha = 0f)), center, r * 0.45f), r * 0.45f, center)
    }
}

/**
 * The fill on/off switch for rectangles and circles: a small ring that fills in when on, like the
 * shape it draws. Not a color: it's drawn in the text color, apart from the swatches.
 */
@Composable
private fun FillSwitch(on: Boolean, onChange: (Boolean) -> Unit) {
    val tint = MaterialTheme.colorScheme.onSurface
    val dot by androidx.compose.animation.core.animateFloatAsState(if (on) 1f else 0f, label = "fill")
    Canvas(
        Modifier
            .size(32.dp)
            .toggleable(on, role = Role.Switch, onValueChange = onChange)
            .semantics { contentDescription = "Fill" }
            .padding(6.dp),
    ) {
        val r = size.minDimension / 2
        drawCircle(tint, r - 0.75.dp.toPx(), style = Stroke(1.5.dp.toPx()))
        if (dot > 0f) drawCircle(tint, (r - 3.5.dp.toPx()) * dot)
    }
}

@Composable
private fun SizeStepper(level: Int, px: Int, isText: Boolean, setLevel: (Int) -> Unit) {
    SizeStep(SlimIcons.Minus, if (isText) "Smaller text" else "Thinner", enabled = level > 1) { setLevel(level - 1) }
    Text(
        "$px px",
        style = MaterialTheme.typography.labelMedium,
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = Modifier.width(44.dp).semantics {
            contentDescription = if (isText) "Text size $px pixels" else "Thickness $px pixels"
        },
    )
    SizeStep(SlimIcons.Plus, if (isText) "Larger text" else "Thicker", enabled = level < MarkScale.MAX_LEVEL) { setLevel(level + 1) }
}

/** A small round − or + button for the size stepper. */
@Composable
private fun SizeStep(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(28.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = if (enabled) 1f else 0.3f), CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon, null, Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.3f),
        )
    }
}
