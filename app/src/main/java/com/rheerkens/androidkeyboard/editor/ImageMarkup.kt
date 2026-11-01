package com.rheerkens.androidkeyboard.editor

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.net.Uri
import java.io.IOException
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.max
import kotlin.math.min

/** One annotation, in image pixels, so the screen and the exported image draw the same thing. */
sealed interface Mark {
    data class Stroke(val points: List<Pair<Float, Float>>, val color: Int, val width: Float, val alpha: Int) : Mark
    /**
     * A rectangle or oval spanning the two corners, outlined or [filled], or an arrow or line from
     * (x1, y1) to (x2, y2). Only rectangles and ovals can be filled. A rectangle or oval is turned
     * by [rotation] degrees (clockwise on screen) about the middle of its box; lines and arrows
     * turn by moving their ends, so theirs stays 0.
     */
    data class Shape(
        val kind: ShapeKind, val x1: Float, val y1: Float, val x2: Float, val y2: Float, val color: Int, val width: Float,
        val filled: Boolean = false, val rotation: Float = 0f,
    ) : Mark

    /** A text note; (x, y) is its top-left corner, and it wraps at [maxWidth]. */
    data class Label(val x: Float, val y: Float, val text: String, val color: Int, val size: Float, val maxWidth: Float) : Mark
}

enum class ShapeKind {
    RECTANGLE, OVAL, ARROW, LINE;

    val fillable get() = this == RECTANGLE || this == OVAL
}

/**
 * A mark "color" that blurs what is beneath it instead of painting: the covered pixels are
 * replaced by a coarse, blurred copy when the image is rendered (see [blurShader]), so the export
 * keeps no sharp copy of them. Transparent, so a plain [Paint] given it by mistake draws nothing.
 */
const val BLUR: Int = 0x00FFFFFF

/** Whether this mark blurs the image under it (see [BLUR]). Notes never blur. */
val Mark.blurs: Boolean get() = when (this) {
    is Mark.Stroke -> color == BLUR
    is Mark.Shape -> color == BLUR
    is Mark.Label -> false
}

/**
 * Sizes relative to the image, so marks look the same on a phone screenshot and a tablet one.
 * Stroke and text sizes take a level from 1 to [MAX_LEVEL] (the editor's slider; 3 is the default).
 */
class MarkScale(width: Int, height: Int) {
    private val unit = min(width, height).toFloat()
    /** Notes wrap at 60% of the image width, so a long remark stays on the image. */
    val labelWidth = width * 0.6f
    fun stroke(level: Int) = max(2f, unit * 0.0027f * level)
    fun highlighter(level: Int) = stroke(level) * 4
    /** The blur brush is wide enough at the default level to cover a line of screenshot text. */
    fun blurBrush(level: Int) = stroke(level) * 5
    fun text(level: Int) = max(14f, unit * 0.0167f * level)

    companion object {
        const val DEFAULT_LEVEL = 3
        const val MAX_LEVEL = 10
    }
}

/**
 * Draws [marks] onto an Android canvas in image coordinates. Blur marks paint with [blur] (see
 * [blurShader]): the blurred image as it was before them. Without one they draw nothing, so call
 * [applyMarks] to render blur marks in order.
 */
fun drawMarks(canvas: Canvas, marks: List<Mark>, blur: Shader? = null) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    for (mark in marks) when (mark) {
        is Mark.Stroke -> {
            paint.style = Paint.Style.STROKE
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeJoin = Paint.Join.ROUND
            paint.strokeWidth = mark.width
            if (!paint.paintWith(mark.color, blur)) continue
            // A blur covers fully, even when drawn with the highlighter.
            if (!mark.blurs) paint.alpha = mark.alpha
            canvas.drawPath(pathOf(mark.points), paint)
        }
        is Mark.Shape -> {
            // A filled shape keeps its outline too, so it covers the same area as the outlined one.
            paint.style = if (mark.filled && mark.kind.fillable) Paint.Style.FILL_AND_STROKE else Paint.Style.STROKE
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeJoin = Paint.Join.ROUND
            paint.strokeWidth = mark.width
            if (!paint.paintWith(mark.color, blur)) continue
            val box = RectF(min(mark.x1, mark.x2), min(mark.y1, mark.y2), max(mark.x1, mark.x2), max(mark.y1, mark.y2))
            when (mark.kind) {
                // A turned box is drawn as a turned path rather than on a turned canvas, so a blur
                // shader stays lined up with the image.
                ShapeKind.RECTANGLE, ShapeKind.OVAL -> if (mark.rotation != 0f) {
                    val path = Path().apply {
                        if (mark.kind == ShapeKind.RECTANGLE) addRect(box, Path.Direction.CW) else addOval(box, Path.Direction.CW)
                        transform(Matrix().apply { setRotate(mark.rotation, box.centerX(), box.centerY()) })
                    }
                    canvas.drawPath(path, paint)
                } else if (mark.kind == ShapeKind.RECTANGLE) {
                    canvas.drawRect(box, paint)
                } else {
                    canvas.drawOval(box, paint)
                }
                ShapeKind.ARROW -> canvas.drawPath(arrowPath(mark), paint)
                ShapeKind.LINE -> canvas.drawLine(mark.x1, mark.y1, mark.x2, mark.y2, paint)
            }
        }
        is Mark.Label -> {
            val text = labelPaint(mark)
            val layout = labelLayout(mark, text)
            canvas.save()
            canvas.translate(mark.x, mark.y)
            // An outline in the opposite tone keeps remarks readable on any background.
            text.style = Paint.Style.STROKE
            text.strokeWidth = mark.size / 6f
            text.strokeJoin = Paint.Join.ROUND
            text.color = if (isLight(mark.color)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            layout.draw(canvas)
            text.style = Paint.Style.FILL
            text.color = mark.color
            layout.draw(canvas)
            canvas.restore()
        }
    }
}

/** Sets a solid [color], or the [blur] shader for [BLUR]; false if there is nothing to paint with. */
private fun Paint.paintWith(color: Int, blur: Shader?): Boolean {
    if (color == BLUR) {
        if (blur == null) return false
        shader = blur
        this.color = 0xFF000000.toInt()
    } else {
        shader = null
        this.color = color
    }
    alpha = 255
    return true
}

/**
 * Draws [marks] onto [target] (mutable, image-sized) in order. Each blur mark blurs the target as
 * it is at that point, so a blur covers the image and every mark beneath it, and later marks sit
 * on top of it.
 */
fun applyMarks(target: Bitmap, marks: List<Mark>) {
    val canvas = Canvas(target)
    var start = 0
    marks.forEachIndexed { i, mark ->
        if (mark.blurs) {
            drawMarks(canvas, marks.subList(start, i))
            drawMarks(canvas, listOf(mark), blurShader(target, blurCell(mark, target.width, target.height)))
            start = i + 1
        }
    }
    drawMarks(canvas, marks.subList(start, marks.size))
}

/**
 * The blur's cell size in image pixels for [mark] on a [width] × [height] image. At least the
 * image's short side / [BLUR_CELLS] (about 11 px on a phone screenshot), and at least a quarter of
 * what the mark covers across: its line width, or the short side of a filled shape. Anything that
 * fits under the mark, however large its text, spans at most about four cells before the blur.
 * Sizes are the minimum times a power of two, so the canvas can keep a few blurred copies ready.
 */
fun blurCell(mark: Mark, width: Int, height: Int): Float {
    val min = max(4f, min(width, height) / BLUR_CELLS)
    val across = when (mark) {
        is Mark.Stroke -> mark.width
        is Mark.Shape -> if (mark.filled && mark.kind.fillable) {
            min(abs(mark.x2 - mark.x1), abs(mark.y2 - mark.y1)) + mark.width
        } else {
            mark.width
        }
        is Mark.Label -> 0f
    }
    var cell = min
    while (cell * BLUR_SPAN < across) cell *= 2
    return cell
}

/**
 * A heavily blurred, opaque copy of [image] as an image-sized shader for blur marks. The image is
 * first reduced to one pixel per [cell] (each step averages 2 × 2 pixels), which throws away the
 * fine detail, then put on white, so transparent parts become opaque and the blur fully replaces
 * what it covers, box-blurred, and scaled back up smoothly.
 */
fun blurShader(image: Bitmap, cell: Float): Shader {
    val w = ceil(image.width / cell).toInt().coerceAtLeast(1)
    val h = ceil(image.height / cell).toInt().coerceAtLeast(1)
    var small = image
    while (small.width / 2 >= w && small.height / 2 >= h) {
        val half = Bitmap.createScaledBitmap(small, small.width / 2, small.height / 2, true)
        if (small !== image) small.recycle()
        small = half
    }
    val cells = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    Canvas(cells).apply {
        drawColor(0xFFFFFFFF.toInt())
        drawBitmap(small, null, android.graphics.Rect(0, 0, w, h), Paint(Paint.FILTER_BITMAP_FLAG))
    }
    if (small !== image) small.recycle()
    val pixels = IntArray(w * h)
    cells.getPixels(pixels, 0, w, 0, 0, w, h)
    boxBlur(pixels, w, h, BLUR_RADIUS, passes = 3)
    cells.setPixels(pixels, 0, w, 0, 0, w, h)
    return BitmapShader(cells, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
        setLocalMatrix(Matrix().apply { setScale(image.width / w.toFloat(), image.height / h.toFloat()) })
    }
}

private const val BLUR_CELLS = 96f
/** How many cells, at most, a mark's width or a filled shape's short side spans. */
private const val BLUR_SPAN = 4f
private const val BLUR_RADIUS = 2

/**
 * Blurs ARGB [pixels] ([width] × [height]) in place: [passes] horizontal and vertical box blurs of
 * [radius], which together approach a Gaussian blur. Edges repeat the border pixel.
 */
fun boxBlur(pixels: IntArray, width: Int, height: Int, radius: Int, passes: Int = 3) {
    if (radius <= 0 || width <= 0 || height <= 0) return
    val line = IntArray(max(width, height))
    repeat(passes) {
        for (y in 0 until height) blurLine(pixels, y * width, 1, width, radius, line)
        for (x in 0 until width) blurLine(pixels, x, width, height, radius, line)
    }
}

/** One box blur along [count] pixels starting at [start], [stride] apart. */
private fun blurLine(pixels: IntArray, start: Int, stride: Int, count: Int, radius: Int, line: IntArray) {
    for (i in 0 until count) line[i] = pixels[start + i * stride]
    val span = 2 * radius + 1
    var a = 0; var r = 0; var g = 0; var b = 0
    for (k in -radius..radius) {
        val p = line[k.coerceIn(0, count - 1)]
        a += p ushr 24; r += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; b += p and 0xFF
    }
    for (i in 0 until count) {
        pixels[start + i * stride] = ((a / span) shl 24) or ((r / span) shl 16) or ((g / span) shl 8) or (b / span)
        val out = line[(i - radius).coerceIn(0, count - 1)]
        val into = line[(i + radius + 1).coerceIn(0, count - 1)]
        a += (into ushr 24) - (out ushr 24)
        r += ((into shr 16) and 0xFF) - ((out shr 16) and 0xFF)
        g += ((into shr 8) and 0xFF) - ((out shr 8) and 0xFF)
        b += (into and 0xFF) - (out and 0xFF)
    }
}

/** Where a note sits on the image, for moving and editing it. */
fun labelBounds(label: Mark.Label): RectF {
    val layout = labelLayout(label, labelPaint(label))
    return RectF(label.x, label.y, label.x + layout.width, label.y + layout.height)
}

private fun labelPaint(label: Mark.Label) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
    textSize = label.size
    typeface = Typeface.DEFAULT_BOLD
}

private fun labelLayout(label: Mark.Label, paint: TextPaint): StaticLayout {
    val width = ceil(Layout.getDesiredWidth(label.text, paint)).toInt().coerceIn(1, label.maxWidth.toInt().coerceAtLeast(1))
    return StaticLayout.Builder.obtain(label.text, 0, label.text.length, paint, width).build()
}

/** The edited image: marks drawn at full resolution, then cropped. */
fun renderEdit(source: Bitmap, marks: List<Mark>, crop: RectF): Bitmap {
    val out = source.copy(Bitmap.Config.ARGB_8888, true)
    applyMarks(out, marks)
    val left = crop.left.toInt().coerceIn(0, out.width - 1)
    val top = crop.top.toInt().coerceIn(0, out.height - 1)
    val width = crop.width().toInt().coerceIn(1, out.width - left)
    val height = crop.height().toInt().coerceIn(1, out.height - top)
    return if (left == 0 && top == 0 && width == out.width && height == out.height) out
        else Bitmap.createBitmap(out, left, top, width, height)
}

/** Decodes [uri] with its longest side at most [maxSide] pixels. Call on an IO dispatcher. */
fun loadEditable(resolver: ContentResolver, uri: Uri, maxSide: Int = 2560): Bitmap? = try {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
        null
    } else {
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inMutable = false
        }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }
} catch (_: IOException) {
    null
} catch (_: SecurityException) {
    null
} catch (_: IllegalArgumentException) {
    null
}

private fun pathOf(points: List<Pair<Float, Float>>): Path = Path().apply {
    val first = points.firstOrNull() ?: return@apply
    moveTo(first.first, first.second)
    if (points.size == 1) {
        lineTo(first.first + 0.1f, first.second)
        return@apply
    }
    // Smooth through midpoints so fast finger strokes don't look jagged.
    for (i in 1 until points.size) {
        val (px, py) = points[i - 1]
        val (x, y) = points[i]
        quadTo(px, py, (px + x) / 2, (py + y) / 2)
    }
    val (lx, ly) = points.last()
    lineTo(lx, ly)
}

/** A shaft with a two-sided head at (x2, y2), sized with the stroke so thick arrows stay legible. */
private fun arrowPath(mark: Mark.Shape): Path = Path().apply {
    val angle = atan2(mark.y2 - mark.y1, mark.x2 - mark.x1)
    val length = hypot(mark.x2 - mark.x1, mark.y2 - mark.y1)
    val head = min(max(mark.width * 4f, 24f), length * 0.5f)
    moveTo(mark.x1, mark.y1)
    lineTo(mark.x2, mark.y2)
    for (side in listOf(-1, 1)) {
        val a = angle + Math.PI.toFloat() + side * HEAD_ANGLE
        moveTo(mark.x2, mark.y2)
        lineTo(mark.x2 + head * cos(a), mark.y2 + head * sin(a))
    }
}

private const val HEAD_ANGLE = 0.45f

private fun isLight(color: Int): Boolean {
    val r = (color shr 16) and 0xFF
    val g = (color shr 8) and 0xFF
    val b = color and 0xFF
    return r * 299 + g * 587 + b * 114 > 150_000
}

/**
 * The end point of a line from ([x1], [y1]) to ([x2], [y2]), turned to exactly horizontal,
 * vertical or diagonal when it is within [toleranceDegrees] of one, keeping its length.
 */
fun snapLineEnd(x1: Float, y1: Float, x2: Float, y2: Float, toleranceDegrees: Double = 5.0): Pair<Float, Float> {
    val dx = (x2 - x1).toDouble()
    val dy = (y2 - y1).toDouble()
    val length = kotlin.math.hypot(dx, dy)
    if (length == 0.0) return x2 to y2
    val angle = Math.toDegrees(kotlin.math.atan2(dy, dx))
    val nearest = kotlin.math.round(angle / 45) * 45
    if (kotlin.math.abs(angle - nearest) > toleranceDegrees) return x2 to y2
    val r = Math.toRadians(nearest)
    return (x1 + length * kotlin.math.cos(r)).toFloat() to (y1 + length * kotlin.math.sin(r)).toFloat()
}
