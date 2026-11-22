package com.cl0udshifter.pdfeasy.ui.reader

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import com.cl0udshifter.pdfeasy.core.model.ScrollMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

@Stable
class ViewportMotionState {
    var viewportWidthPx by mutableFloatStateOf(0f)
    var viewportHeightPx by mutableFloatStateOf(0f)
    var zoomScale by mutableFloatStateOf(ReaderViewModel.MIN_ZOOM)
    var scrollMainPx by mutableFloatStateOf(0f)
    var scrollCrossPx by mutableFloatStateOf(0f)
    var activeAnimationJob: Job? = null

    fun cancelAnimations() {
        activeAnimationJob?.cancel()
        activeAnimationJob = null
    }
}

class ViewportGestureContext(
    val coroutineScope: CoroutineScope,
    val motionState: ViewportMotionState,
    val baseSnapshotProvider: () -> PageLayoutSnapshot,
    val onSingleTap: () -> Unit,
)

private data class GestureSessionResult(
    val wasMultiTouch: Boolean,
    val totalDragPx: Float,
    val lastUpTime: Long,
    val lastPosition: Offset,
)

private object GestureConstants {
    const val DOUBLE_TAP_ZOOM = 2.2f
    const val DOUBLE_TAP_THRESHOLD_ZOOM = 1.25f
    const val DOUBLE_TAP_ANIM_MS = 220
    const val MAX_DOUBLE_TAP_DISTANCE_PX = 96f
    const val FLING_MIN_VELOCITY = 60f
}

suspend fun PointerInputScope.detectReaderGestures(ctx: ViewportGestureContext) {
    var pendingTapJob: Job? = null
    var lastTapTime = 0L
    var lastTapOffset = Offset.Zero
    val decay = exponentialDecay<Float>()

    awaitEachGesture {
        val firstDown = awaitFirstDown(requireUnconsumed = false)
        ctx.motionState.cancelAnimations()
        val velocityTracker = VelocityTracker().apply {
            addPosition(firstDown.uptimeMillis, firstDown.position)
        }
        val result = trackPointersUntilRelease(ctx, firstDown.uptimeMillis, firstDown.position, velocityTracker)

        if (!result.wasMultiTouch && result.totalDragPx <= viewConfiguration.touchSlop) {
            val isDoubleTap =
                (result.lastUpTime - lastTapTime) <= viewConfiguration.doubleTapTimeoutMillis &&
                    (result.lastPosition - lastTapOffset).getDistance() <= GestureConstants.MAX_DOUBLE_TAP_DISTANCE_PX

            if (isDoubleTap) {
                pendingTapJob?.cancel()
                pendingTapJob = null
                lastTapTime = 0L
                ctx.motionState.activeAnimationJob =
                    ctx.coroutineScope.launch { toggleDoubleTapZoom(result.lastPosition, ctx) }
            } else {
                lastTapTime = result.lastUpTime
                lastTapOffset = result.lastPosition
                pendingTapJob?.cancel()
                pendingTapJob =
                    ctx.coroutineScope.launch {
                        delay(viewConfiguration.doubleTapTimeoutMillis)
                        ctx.onSingleTap()
                    }
            }
        } else if (!result.wasMultiTouch) {
            launchFlingDecay(velocityTracker, ctx, decay)
        }
    }
}

private suspend fun AwaitPointerEventScope.trackPointersUntilRelease(
    ctx: ViewportGestureContext,
    initialTime: Long,
    initialPosition: Offset,
    velocityTracker: VelocityTracker,
): GestureSessionResult {
    var wasMultiTouch = false
    var totalDragPx = 0f
    var lastUpTime = initialTime
    var lastPosition = initialPosition

    do {
        val event = awaitPointerEvent()
        val pressedCount = event.changes.count { it.pressed }
        val baseSnapshot = ctx.baseSnapshotProvider()

        if (pressedCount >= 2) {
            wasMultiTouch = true
            ctx.motionState.cancelAnimations()
            applyMultiTouchTransform(event, baseSnapshot, ctx.motionState)
        } else if (!wasMultiTouch) {
            val activeChange = event.changes.firstOrNull { it.pressed }
            val released = if (activeChange == null) event.changes.firstOrNull() else null
            if (activeChange != null) {
                velocityTracker.addPosition(activeChange.uptimeMillis, activeChange.position)
                lastPosition = activeChange.position
                lastUpTime = activeChange.uptimeMillis
                val delta = activeChange.positionChange()
                totalDragPx += delta.getDistance()
                applyDragIfPastSlop(activeChange, delta, totalDragPx, baseSnapshot, ctx.motionState)
            } else if (released != null) {
                lastUpTime = released.uptimeMillis
                lastPosition = released.position
            }
        }
    } while (event.changes.any { it.pressed })

    return GestureSessionResult(wasMultiTouch, totalDragPx, lastUpTime, lastPosition)
}

private fun AwaitPointerEventScope.applyDragIfPastSlop(
    activeChange: PointerInputChange,
    delta: Offset,
    totalDragPx: Float,
    baseSnapshot: PageLayoutSnapshot,
    motion: ViewportMotionState,
) {
    if (totalDragPx > viewConfiguration.touchSlop) {
        activeChange.consume()
        applySingleFingerPan(delta, baseSnapshot, motion)
    }
}

private fun applyMultiTouchTransform(
    event: PointerEvent,
    baseSnapshot: PageLayoutSnapshot,
    motion: ViewportMotionState,
) {
    val zoomChange = event.calculateZoom()
    val pan = event.calculatePan()
    val centroid = event.calculateCentroid(useCurrent = true)
    if (zoomChange == 1f && pan == Offset.Zero) return

    event.changes.forEach { it.consume() }
    val oldZoom = motion.zoomScale
    val newZoom = (oldZoom * zoomChange).coerceIn(ReaderViewModel.MIN_ZOOM, ReaderViewModel.MAX_ZOOM)
    val isVertical = baseSnapshot.scrollMode == ScrollMode.CONTINUOUS_VERTICAL
    val output =
        ReaderZoomMath.transformScrollForZoom(
            baseSnapshot = baseSnapshot,
            input =
                ZoomTransformInput(
                    oldZoom = oldZoom,
                    newZoom = newZoom,
                    oldScrollMainPx = motion.scrollMainPx,
                    oldScrollCrossPx = motion.scrollCrossPx,
                    centroidMainPx = if (isVertical) centroid.y else centroid.x,
                    centroidCrossPx = if (isVertical) centroid.x else centroid.y,
                    panMainPx = if (isVertical) pan.y else pan.x,
                    panCrossPx = if (isVertical) pan.x else pan.y,
                ),
        )
    motion.zoomScale = output.zoomScale
    motion.scrollMainPx = output.scrollMainPx
    motion.scrollCrossPx = output.scrollCrossPx
}

private fun applySingleFingerPan(
    delta: Offset,
    baseSnapshot: PageLayoutSnapshot,
    motion: ViewportMotionState,
) {
    val isVertical = baseSnapshot.scrollMode == ScrollMode.CONTINUOUS_VERTICAL
    val deltaMain = if (isVertical) -delta.y else -delta.x
    val deltaCross = if (isVertical) -delta.x else -delta.y
    val maxMain = ReaderZoomMath.scaledMaxScrollMain(baseSnapshot, motion.zoomScale)
    val maxCross = ReaderZoomMath.scaledMaxScrollCross(baseSnapshot, motion.zoomScale)
    motion.scrollMainPx = (motion.scrollMainPx + deltaMain).coerceIn(0f, maxMain)
    motion.scrollCrossPx = (motion.scrollCrossPx + deltaCross).coerceIn(0f, maxCross)
}

private fun launchFlingDecay(
    velocityTracker: VelocityTracker,
    ctx: ViewportGestureContext,
    decay: DecayAnimationSpec<Float>,
) {
    val baseSnapshot = ctx.baseSnapshotProvider()
    val isVertical = baseSnapshot.scrollMode == ScrollMode.CONTINUOUS_VERTICAL
    val velocity = velocityTracker.calculateVelocity()
    val mainVelocity = if (isVertical) -velocity.y else -velocity.x
    val crossVelocity = if (isVertical) -velocity.x else -velocity.y
    val motion = ctx.motionState
    val maxMain = ReaderZoomMath.scaledMaxScrollMain(baseSnapshot, motion.zoomScale)
    val maxCross = ReaderZoomMath.scaledMaxScrollCross(baseSnapshot, motion.zoomScale)

    val flingMain = abs(mainVelocity) > GestureConstants.FLING_MIN_VELOCITY
    val flingCross = maxCross > 0f && abs(crossVelocity) > GestureConstants.FLING_MIN_VELOCITY
    if (!flingMain && !flingCross) return

    motion.cancelAnimations()
    motion.activeAnimationJob =
        ctx.coroutineScope.launch {
            coroutineScope {
                if (flingMain) {
                    launchBoundedDecay(mainVelocity, motion.scrollMainPx, maxMain, decay) {
                        motion.scrollMainPx = it
                    }
                }
                if (flingCross) {
                    launchBoundedDecay(crossVelocity, motion.scrollCrossPx, maxCross, decay) {
                        motion.scrollCrossPx = it
                    }
                }
            }
        }
}

private fun CoroutineScope.launchBoundedDecay(
    velocity: Float,
    startValue: Float,
    upperBound: Float,
    decay: DecayAnimationSpec<Float>,
    onUpdate: (Float) -> Unit,
) {
    launch {
        val anim = Animatable(startValue)
        anim.updateBounds(lowerBound = 0f, upperBound = upperBound)
        anim.animateDecay(velocity, decay) { onUpdate(value) }
    }
}

private suspend fun toggleDoubleTapZoom(
    tapOffset: Offset,
    ctx: ViewportGestureContext,
) {
    val motion = ctx.motionState
    val baseSnapshot = ctx.baseSnapshotProvider()
    val startZoom = motion.zoomScale
    val targetZoom =
        if (startZoom > GestureConstants.DOUBLE_TAP_THRESHOLD_ZOOM) {
            ReaderViewModel.MIN_ZOOM
        } else {
            GestureConstants.DOUBLE_TAP_ZOOM
        }
    val startMain = motion.scrollMainPx
    val startCross = motion.scrollCrossPx
    val isVertical = baseSnapshot.scrollMode == ScrollMode.CONTINUOUS_VERTICAL
    val tapMain = if (isVertical) tapOffset.y else tapOffset.x
    val tapCross = if (isVertical) tapOffset.x else tapOffset.y
    val progressAnim = Animatable(0f)
    val animSpec = tween<Float>(durationMillis = GestureConstants.DOUBLE_TAP_ANIM_MS)

    progressAnim.animateTo(1f, animSpec) {
        val stepZoom = startZoom + (targetZoom - startZoom) * value
        val step =
            ReaderZoomMath.transformScrollForZoom(
                baseSnapshot = baseSnapshot,
                input =
                    ZoomTransformInput(
                        oldZoom = startZoom,
                        newZoom = stepZoom,
                        oldScrollMainPx = startMain,
                        oldScrollCrossPx = startCross,
                        centroidMainPx = tapMain,
                        centroidCrossPx = tapCross,
                    ),
            )
        motion.zoomScale = step.zoomScale
        motion.scrollMainPx = step.scrollMainPx
        motion.scrollCrossPx = step.scrollCrossPx
    }
}
