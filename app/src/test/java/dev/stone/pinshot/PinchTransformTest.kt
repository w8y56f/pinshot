package dev.stone.pinshot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinchTransformTest {
    private fun point(id: Int, x: Float, y: Float = 100f) = PinTouch(id, x, y)
    private fun close(expected: Float, actual: Float) = assertEquals(expected, actual, .001f)

    @Test fun stationaryFingerKeepsItsImagePoint() {
        val geometry = PinGeometry(20f, 40f, 200f, 2f)
        val pinch = PinchTransform(8f)
        pinch.rebase(listOf(point(4, 60f), point(9, 160f)))
        pinch.move(listOf(point(4, 60f), point(9, 260f)), geometry, 48f, 2000f)
        close(400f, geometry.width)
        close(60f, geometry.x + .2f * geometry.width)
        close(100f, geometry.y + .6f * geometry.height)
    }

    @Test fun symmetricExpansionKeepsCenter() {
        val geometry = PinGeometry(0f, 0f, 200f, 1f)
        val pinch = PinchTransform(8f)
        pinch.rebase(listOf(point(0, 50f), point(1, 150f)))
        pinch.move(listOf(point(0, 0f), point(1, 200f)), geometry, 48f, 2000f)
        close(400f, geometry.width)
        close(100f, geometry.x + geometry.width / 2f)
        close(100f, geometry.y + geometry.height / 2f)
    }

    @Test fun parallelMotionPansWithoutScaling() {
        val geometry = PinGeometry(0f, 0f, 200f, 1f)
        val pinch = PinchTransform(8f)
        pinch.rebase(listOf(point(0, 50f), point(1, 150f)))
        pinch.move(listOf(point(0, 75f, 120f), point(1, 175f, 120f)), geometry, 48f, 2000f)
        close(200f, geometry.width)
        close(25f, geometry.x)
        close(20f, geometry.y)
    }

    @Test fun pointerIndicesCanSwapWithoutJumping() {
        val geometry = PinGeometry(0f, 0f, 200f, 1f)
        val pinch = PinchTransform(8f)
        pinch.rebase(listOf(point(7, 50f), point(2, 150f)))
        pinch.move(listOf(point(2, 150f), point(7, 50f)), geometry, 48f, 2000f)
        close(200f, geometry.width)
        close(0f, geometry.x)
    }

    @Test fun reverseMotionRespondsImmediatelyAtBothSizeLimits() {
        val geometry = PinGeometry(0f, 0f, 200f, 1f)
        val pinch = PinchTransform(8f)
        pinch.rebase(listOf(point(0, 0f), point(1, 100f)))
        pinch.move(listOf(point(0, 0f), point(1, 400f)), geometry, 100f, 300f)
        close(300f, geometry.width)
        pinch.move(listOf(point(0, 0f), point(1, 360f)), geometry, 100f, 300f)
        close(270f, geometry.width)
        pinch.move(listOf(point(0, 0f), point(1, 50f)), geometry, 100f, 300f)
        close(100f, geometry.width)
        pinch.move(listOf(point(0, 0f), point(1, 55f)), geometry, 100f, 300f)
        close(110f, geometry.width)
    }

    @Test fun replacingFingerRebasesWithoutChangingImage() {
        val geometry = PinGeometry(0f, 0f, 200f, 1f)
        val pinch = PinchTransform(8f)
        pinch.rebase(listOf(point(0, 50f), point(1, 150f)))
        pinch.rebase(listOf(point(0, 50f), point(1, 150f), point(2, 250f)))
        pinch.rebase(listOf(point(0, 50f), point(2, 250f)))
        pinch.move(listOf(point(0, 50f), point(2, 250f)), geometry, 48f, 2000f)
        close(200f, geometry.width)
        close(0f, geometry.x)
        pinch.rebase(listOf(point(2, 250f)))
        pinch.rebase(listOf(point(2, 250f), point(3, 350f)))
        pinch.move(listOf(point(2, 250f), point(3, 350f)), geometry, 48f, 2000f)
        close(200f, geometry.width)
    }

    @Test fun tinySpanAndCrossingFingersNeverExplode() {
        val geometry = PinGeometry(0f, 0f, 200f, 1f)
        val pinch = PinchTransform(8f)
        pinch.rebase(listOf(point(0, 100f), point(1, 100f)))
        pinch.move(listOf(point(0, 100f), point(1, 102f)), geometry, 48f, 2000f)
        pinch.move(listOf(point(0, 100f), point(1, 120f)), geometry, 48f, 2000f)
        close(200f, geometry.width)
        assertTrue(geometry.x.isFinite() && geometry.y.isFinite())
        pinch.move(listOf(point(0, 100f), point(1, 130f)), geometry, 48f, 2000f)
        close(300f, geometry.width)
    }

    @Test fun slowSubpixelMovementAccumulates() {
        val geometry = PinGeometry(0f, 0f, 200f, 1f)
        val pinch = PinchTransform(8f)
        pinch.rebase(listOf(point(0, 50f), point(1, 150f)))
        for (step in 1..100) {
            val offset = step * .1f
            pinch.move(listOf(point(0, 50f + offset), point(1, 150f + offset)), geometry, 48f, 2000f)
        }
        close(10f, geometry.x)
        close(200f, geometry.width)
    }

    @Test fun edgeClampDoesNotCreateReverseMovementDeadZone() {
        val geometry = PinGeometry(240f, 0f, 200f, 1f)
        val pinch = PinchTransform(8f)
        pinch.rebase(listOf(point(0, 250f), point(1, 350f)))
        pinch.move(listOf(point(0, 450f), point(1, 550f)), geometry, 48f, 2000f)
        geometry.constrain(0f, 0f, 300f, 600f, 30f)
        close(270f, geometry.x)
        pinch.move(listOf(point(0, 449f), point(1, 549f)), geometry, 48f, 2000f)
        geometry.constrain(0f, 0f, 300f, 600f, 30f)
        close(269f, geometry.x)
    }
}
