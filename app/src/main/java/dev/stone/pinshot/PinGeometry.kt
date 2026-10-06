package dev.stone.pinshot

import kotlin.math.hypot

/** Screen-space geometry, independent of the integer-sized overlay surface. */
internal class PinGeometry(var x: Float, var y: Float, var width: Float, var aspectRatio: Float) {
    val height get() = width / aspectRatio

    fun isInResizeHandle(touchX: Float, touchY: Float, handleSize: Float): Boolean {
        // Let the shared boundary belong to dragging, including the center of a 48dp pin.
        return touchX > maxOf(x, x + width - handleSize) && touchX < x + width &&
            touchY > maxOf(y, y + height - handleSize) && touchY < y + height
    }

    fun constrain(left: Float, top: Float, right: Float, bottom: Float, minimumVisibleSize: Float) {
        val visibleWidth = minOf(minimumVisibleSize, width, (right - left).coerceAtLeast(0f))
        val visibleHeight = minOf(minimumVisibleSize, height, (bottom - top).coerceAtLeast(0f))
        x = x.coerceIn(left - width + visibleWidth, right - visibleWidth)
        y = y.coerceIn(top - height + visibleHeight, bottom - visibleHeight)
    }

    /** Reserve the last reachable patch for dragging if the resize handle covers all of it. */
    fun shouldDragInsteadOfResize(left: Float, top: Float, right: Float, bottom: Float,
        handleSize: Float): Boolean {
        val imageRight = x + width
        val imageBottom = y + height
        val partiallyOutside = x < left || y < top || imageRight > right || imageBottom > bottom
        val visibleLeft = maxOf(x, left)
        val visibleTop = maxOf(y, top)
        val visibleRight = minOf(imageRight, right)
        val visibleBottom = minOf(imageBottom, bottom)
        return partiallyOutside && visibleLeft < visibleRight && visibleTop < visibleBottom &&
            visibleLeft >= imageRight - handleSize && visibleTop >= imageBottom - handleSize
    }
}

internal data class PinTouch(val id: Int, val x: Float, val y: Float)

/** Rebase on pointer changes and after every sample, including at size/position limits. */
internal class PinchTransform(private val minimumSpan: Float) {
    private var first: PinTouch? = null
    private var second: PinTouch? = null

    fun rebase(points: List<PinTouch>) {
        val a = points.find { it.id == first?.id } ?: points.firstOrNull()
        val b = points.find { it.id == second?.id && it.id != a?.id }
            ?: points.firstOrNull { it.id != a?.id }
        first = a
        second = b
    }

    fun move(points: List<PinTouch>, geometry: PinGeometry, minWidth: Float, maxWidth: Float) {
        val oldA = first
        val oldB = second
        val a = points.find { it.id == oldA?.id }
        val b = points.find { it.id == oldB?.id }
        if (oldA == null || oldB == null || a == null || b == null) {
            rebase(points)
            return
        }
        val before = hypot(oldB.x - oldA.x, oldB.y - oldA.y)
        val after = hypot(b.x - a.x, b.y - a.y)
        if (before >= minimumSpan && after >= minimumSpan) {
            val width = (geometry.width * after / before).coerceIn(minWidth, maxWidth)
            val scale = width / geometry.width
            geometry.x = (a.x + b.x) / 2f - ((oldA.x + oldB.x) / 2f - geometry.x) * scale
            geometry.y = (a.y + b.y) / 2f - ((oldA.y + oldB.y) / 2f - geometry.y) * scale
            geometry.width = width
        }
        first = a
        second = b
    }
}
