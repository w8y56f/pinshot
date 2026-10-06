package dev.stone.pinshot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinGeometryTest {
    private fun close(expected: Float, actual: Float) = assertEquals(expected, actual, .001f)
    private fun constrain(g: PinGeometry, visible: Float = 30f) =
        g.constrain(10f, 20f, 310f, 620f, visible)
    private fun dragOnly(g: PinGeometry) =
        g.shouldDragInsteadOfResize(10f, 20f, 310f, 620f, 24f)

    @Test fun eachEdgeKeepsFixedStripRegardlessOfImageSize() {
        for (size in listOf(200f, 2000f)) {
            val left = PinGeometry(-10000f, 100f, size, 1f)
            constrain(left)
            close(40f, left.x + left.width)
            val right = PinGeometry(10000f, 100f, size, 1f)
            constrain(right)
            close(280f, right.x)
            val top = PinGeometry(100f, -10000f, size, 1f)
            constrain(top)
            close(50f, top.y + top.height)
            val bottom = PinGeometry(100f, 10000f, size, 1f)
            constrain(bottom)
            close(590f, bottom.y)
        }
    }

    @Test fun allFourCornersKeep30By30Patch() {
        for (x in listOf(-10000f, 10000f)) {
            for (y in listOf(-10000f, 10000f)) {
                val g = PinGeometry(x, y, 2000f, 2f)
                constrain(g)
                close(30f, minOf(g.x + g.width, 310f) - maxOf(g.x, 10f))
                close(30f, minOf(g.y + g.height, 620f) - maxOf(g.y, 20f))
            }
        }
    }

    @Test fun smallImagesRemainFullyInsideSafeArea() {
        for (position in listOf(-10000f, 10000f)) {
            val g = PinGeometry(position, position, 30f, 2f)
            constrain(g)
            assertTrue(g.x >= 10f && g.x + g.width <= 310f)
            assertTrue(g.y >= 20f && g.y + g.height <= 620f)
        }
        val narrow = PinGeometry(-10000f, -10000f, 30f, .1f)
        constrain(narrow)
        close(10f, narrow.x)
        close(50f, narrow.y + narrow.height)
    }

    @Test fun retentionUsesDensityConvertedPixels() {
        val g = PinGeometry(10000f, 10000f, 2000f, 1f)
        constrain(g, 30f * 4f)
        close(190f, g.x)
        close(500f, g.y)
    }

    @Test fun safeAreaSmallerThanRetentionRemainsCovered() {
        val g = PinGeometry(-10000f, 10000f, 200f, 1f)
        g.constrain(10f, 20f, 40f, 45f, 30f)
        close(40f, g.x + g.width)
        close(20f, g.y)
    }

    @Test fun lastVisibleHandlePatchCanBeDraggedBack() {
        val g = PinGeometry(-10000f, -10000f, 2000f, 2f)
        constrain(g)
        // The normal 30dp patch still contains drag space beside the handle.
        assertFalse(dragOnly(g))
        // A smaller reachable area can still leave only the handle exposed.
        g.x = -10000f
        g.y = -10000f
        g.constrain(10f, 20f, 30f, 40f, 30f)
        assertTrue(g.shouldDragInsteadOfResize(10f, 20f, 30f, 40f, 24f))
    }

    @Test fun movingHandleOnlyPatchInwardRestoresResize() {
        val g = PinGeometry(-170f, -160f, 200f, 1f)
        assertTrue(dragOnly(g))
        // Moving inward exposes image outside the handle; the next gesture can resize again.
        g.x += 20f
        assertFalse(dragOnly(g))
    }

    @Test fun minimumPinLeavesCenterLeftAndTopForDragging() {
        val g = PinGeometry(10f, 20f, 48f, 1f)
        assertFalse(g.isInResizeHandle(34f, 44f, 24f))
        assertFalse(g.isInResizeHandle(20f, 60f, 24f))
        assertFalse(g.isInResizeHandle(50f, 30f, 24f))
        assertTrue(g.isInResizeHandle(50f, 60f, 24f))
    }

    @Test fun resizeHitTestChecksAllFourBoundaries() {
        val g = PinGeometry(10f, 20f, 48f, 1f)
        for (x in listOf(33.9f, 34f, 58f, 58.1f)) {
            assertFalse(g.isInResizeHandle(x, 55f, 24f))
        }
        for (y in listOf(43.9f, 44f, 68f, 68.1f)) {
            assertFalse(g.isInResizeHandle(45f, y, 24f))
        }
        for (x in listOf(34.1f, 57.9f)) {
            for (y in listOf(44.1f, 67.9f)) {
                assertTrue(g.isInResizeHandle(x, y, 24f))
            }
        }
    }

    @Test fun handleCannotExtendBeyondSmallImage() {
        val g = PinGeometry(10f, 20f, 12f, 1f)
        assertFalse(g.isInResizeHandle(5f, 25f, 24f))
        assertFalse(g.isInResizeHandle(15f, 15f, 24f))
        assertTrue(g.isInResizeHandle(15f, 25f, 24f))
    }

    @Test fun ordinaryAndFullyVisibleSmallImagesKeepResizeHandle() {
        assertFalse(dragOnly(PinGeometry(50f, 100f, 200f, 1f)))
        assertFalse(dragOnly(PinGeometry(50f, 100f, 30f, 1f)))
        assertFalse(dragOnly(PinGeometry(-142f, 100f, 200f, 1f)))
        assertFalse(dragOnly(PinGeometry(262f, 572f, 200f, 1f)))
    }
}
