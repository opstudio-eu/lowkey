package com.rheerkens.androidkeyboard.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Simple 24 dp stroke icons drawn for this project (shapes follow the concept 4 mockup).
 * Tinted by `Icon` like any other vector.
 */
object SlimIcons {
    val Tools by lazy { stroke("tools", "M4 7h16M4 17h16M8 4v6M16 14v6") }
    val Resize by lazy { stroke("resize", "M8 4H4v4M16 20h4v-4M4 4l6 6M20 20l-6 -6") }
    val Move by lazy { stroke("move", "M12 3v18M3 12h18M9 6l3 -3 3 3M9 18l3 3 3 -3M6 9l-3 3 3 3M18 9l3 3 -3 3") }
    val Split by lazy { stroke("split", "M3 6h7v12H3zM14 6h7v12h-7z") }
    val OneHand by lazy { stroke("oneHand", "M4 6h11v12H4zM19 8l3 4 -3 4") }
    val Floating by lazy { stroke("floating", "M3 4h18v16H3zM8 10h10v7H8z") }
    private fun stroke(name: String, vararg paths: String, width: Float = 1.8f, filled: String? = null) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach { d ->
                addPath(
                    pathData = addPathNodes(d),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = width,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
            if (filled != null) addPath(pathData = addPathNodes(filled), fill = SolidColor(Color.Black))
        }.build()

    private fun circle(cx: Float, cy: Float, r: Float) =
        "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0"

    val Mic = stroke(
        "mic",
        "M9 6a3 3 0 0 1 6 0v5a3 3 0 0 1 -6 0z",
        "M5.5 11a6.5 6.5 0 0 0 13 0M12 17.5V21M8.5 21h7",
    )
    val Keyboard = stroke(
        "keyboard",
        "M4.5 6h15a2 2 0 0 1 2 2v8a2 2 0 0 1 -2 2h-15a2 2 0 0 1 -2 -2v-8a2 2 0 0 1 2 -2z",
        "M8 15h8",
        filled = "M6 9h1.4v1.4h-1.4zM9 9h1.4v1.4h-1.4zM12 9h1.4v1.4h-1.4zM15 9h1.4v1.4h-1.4zM17.6 9h1.4v1.4h-1.4z" +
            "M6 11.8h1.4v1.4h-1.4zM9 11.8h1.4v1.4h-1.4zM12 11.8h1.4v1.4h-1.4zM15 11.8h1.4v1.4h-1.4zM17.6 11.8h1.4v1.4h-1.4z",
    )
    val Globe = stroke(
        "globe",
        circle(12f, 12f, 8.5f),
        "M12 3.5c-2.1 2.3 -3.8 5.2 -3.8 8.5s1.7 6.2 3.8 8.5c2.1 -2.3 3.8 -5.2 3.8 -8.5s-1.7 -6.2 -3.8 -8.5z",
        "M3.5 12h17M5 7.6h14M5 16.4h14",
    )
    val Backspace = stroke(
        "backspace",
        "M9 5.5h10.5a1.5 1.5 0 0 1 1.5 1.5v10a1.5 1.5 0 0 1 -1.5 1.5H9L3 12z",
        "M12 9.5l5 5M17 9.5l-5 5",
    )
    val Shift = stroke("shift", "M12 4l7.5 7.5h-4v7h-7v-7h-4z")
    val ShiftFilled = stroke("shiftFilled", "M12 4l7.5 7.5h-4v7h-7v-7h-4z", filled = "M12 4l7.5 7.5h-4v7h-7v-7h-4z")
    val CapsLock = stroke(
        "capsLock", "M12 4l7.5 7.5h-4v4h-7v-4h-4z", "M8.5 19.5h7",
        filled = "M12 4l7.5 7.5h-4v4h-7v-4h-4z",
    )
    val Enter = stroke("enter", "M19 6v5.5a2 2 0 0 1 -2 2H6", "M9.5 10L6 13.5 9.5 17")
    val Book = stroke(
        "book",
        "M5 5a2 2 0 0 1 2 -2h12v14H7a2 2 0 0 0 -2 2z",
        "M5 19a2 2 0 0 0 2 2h12v-4",
        "M9 7.5h6M9 10.5h4",
    )
    val Clipboard = stroke(
        "clipboard", "M9 5H6a2 2 0 0 0 -2 2v13h16V7a2 2 0 0 0 -2 -2h-3",
        "M9 3h6v5H9zM8 12h8M8 16h6",
    )
    val Stop = stroke("stop", filled = "M8.5 6h7a2.5 2.5 0 0 1 2.5 2.5v7a2.5 2.5 0 0 1 -2.5 2.5h-7a2.5 2.5 0 0 1 -2.5 -2.5v-7a2.5 2.5 0 0 1 2.5 -2.5z")
    val Check = stroke("check", "M5 12.5l4.5 4.5L19 7.5", width = 2.2f)
    val Close = stroke("close", "M6 6l12 12M18 6L6 18")
    val Plus = stroke("plus", "M12 5v14M5 12h14")
    val Minus = stroke("minus", "M5 12h14")
    val Undo = stroke("undo", "M8.5 13.5L4 9l4.5 -4.5", "M4 9h10a5.5 5.5 0 0 1 0 11h-3.5")
    val Redo = stroke("redo", "M15.5 13.5L20 9l-4.5 -4.5", "M20 9H10a5.5 5.5 0 0 0 0 11h3.5")
    val Download = stroke("download", "M12 4v11M7.5 10.5L12 15l4.5 -4.5M5 19.5h14")
    val Trash = stroke("trash", "M5 7h14M10 7V4.5h4V7M7 7l1 13h8l1 -13")
    val Edit = stroke("edit", "M4 20h4L19 9l-4 -4L4 16z", "M13.5 6.5l4 4")
    val Search = stroke("search", circle(10.5f, 10.5f, 6f), "M15 15l5 5")
    val Gear = stroke(
        "gear",
        circle(12f, 12f, 3f),
        "M12 2.8v2.6M12 18.6v2.6M2.8 12h2.6M18.6 12h2.6M5.5 5.5l1.8 1.8M16.7 16.7l1.8 1.8M5.5 18.5l1.8 -1.8M16.7 7.3l1.8 -1.8",
        circle(12f, 12f, 6.6f),
    )
    val Home = stroke("home", "M4 11l8 -7 8 7", "M6 9.5V20h4.5v-5.5h3V20H18V9.5")
    val Models = stroke(
        "models",
        "M12 3l8 4.5v9L12 21l-8 -4.5v-9z",
        "M4 7.5l8 4.5 8 -4.5M12 12v9",
    )
    val Settings = stroke(
        "settings",
        "M4 7h10M18 7h2M4 17h4M12 17h8",
        circle(16f, 7f, 2f),
        circle(10f, 17f, 2f),
    )
    val Back = stroke("back", "M19 12H5M11 6l-6 6 6 6")
    val ChevronRight = stroke("chevronRight", "M9.5 6l6 6 -6 6")
    val ChevronDown = stroke("chevronDown", "M6.5 9.5l5.5 5.5 5.5 -5.5", width = 2f)
    val ChevronUp = stroke("chevronUp", "M6.5 14.5l5.5 -5.5 5.5 5.5", width = 2f)
    val More = stroke("more", filled = circle(12f, 5.5f, 1.6f) + circle(12f, 12f, 1.6f) + circle(12f, 18.5f, 1.6f))
    val Info = stroke("info", circle(12f, 12f, 8.5f), "M12 11v5.5M12 7.8v.2", width = 1.8f)
    val Send = stroke("send", "M4.5 12L19.5 4.5 15 19.5l-3 -6z", "M12 13.5l7.5 -9")
    val Pulse = stroke("pulse", "M3 12h4l2 -5 4 10 2 -5h6")
    val Rectangle = stroke("rectangle", "M4.5 6.5h15v11h-15z", width = 2f)
    val Oval = stroke("oval", "M3.5 12a8.5 6 0 1 0 17 0a8.5 6 0 1 0 -17 0", width = 2f)
    val Arrow = stroke("arrow", "M5 19L18.5 5.5", "M10.5 5.5h8v8", width = 2f)
    val Line = stroke("line", "M5 19L19 5", width = 2f)
    val Pencil = stroke("pencil", "M4.5 19.5l1 -4.5L16 4.5a2.1 2.1 0 0 1 3 3L8.5 18z", "M14 6.5l3 3")
    val Eraser = stroke(
        "eraser",
        "M10 20h9",
        "M5.4 15.6l8.9 -8.9a2 2 0 0 1 2.8 0l1.6 1.6a2 2 0 0 1 0 2.8L10.4 19.4a2 2 0 0 1 -2.8 0l-2.2 -2.2a1.1 1.1 0 0 1 0 -1.6z",
        "M9.5 11.5l4.5 4.5",
    )
}
