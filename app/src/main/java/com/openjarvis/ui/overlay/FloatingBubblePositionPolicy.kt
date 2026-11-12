package com.openjarvis.ui.overlay

data class FloatingBubblePosition(
    val xFromEndPx: Int,
    val topPx: Int
)

/** Pure position policy for a draggable bubble in a TOP|END WindowManager window. */
fun snapFloatingBubblePosition(
    screenWidthPx: Int,
    screenHeightPx: Int,
    bubbleWidthPx: Int,
    bubbleHeightPx: Int,
    marginPx: Int,
    currentXFromEndPx: Int,
    currentTopPx: Int
): FloatingBubblePosition {
    val safeMargin = marginPx.coerceAtLeast(0)
    val centerX = screenWidthPx - currentXFromEndPx - bubbleWidthPx / 2
    val xFromEnd = if (centerX < screenWidthPx / 2) {
        (screenWidthPx - bubbleWidthPx - safeMargin).coerceAtLeast(safeMargin)
    } else {
        safeMargin
    }
    val maxTop = (screenHeightPx - bubbleHeightPx - safeMargin).coerceAtLeast(safeMargin)
    return FloatingBubblePosition(
        xFromEndPx = xFromEnd,
        topPx = currentTopPx.coerceIn(safeMargin, maxTop)
    )
}
