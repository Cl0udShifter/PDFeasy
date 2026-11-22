package com.cl0udshifter.pdfeasy.ui.reader

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
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
    val scrollMainAnim = Animatable(0f)
    val scrollCrossAnim = Animatable(0f)
}

class ViewportGestureContext(
    val coroutineScope: CoroutineScope,
    val motionState: ViewportMotionState,
    val snapshotProvider: () -> PageLayoutSnapshot,
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
        ctx.coroutineScope.launch {
            ctx.motionState.scrollMainAnim.stop()
            ctx.motionState.scrollCrossAnim.stop()
        }
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
        val snapshot = ctx.snapshotProvider()

        if (pressedCount >= 2) {
            wasMultiTouch = true
            applyMultiTouchTransform(event, snapshot, ctx)
        } else if (!wasMultiTouch) {
            val activeChange = event.changes.firstOrNull { it.pressed }
            val released = if (activeChange == null) event.changes.firstOrNull() else null
            if (activeChange != null) {
                velocityTracker.addPosition(activeChange.uptimeMillis, activeChange.position)
                lastPosition = activeChange.position
                lastUpTime = activeChange.uptimeMillis
                val delta = activeChange.positionChange()
                totalDragPx += delta.getDistance()
                applyDragIfPastSlop(activeChange, delta, totalDragPx, snapshot, ctx)
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
    snapshot: PageLayoutSnapshot,
    ctx: ViewportGestureContext,
) {
    if (totalDragPx > viewConfiguration.touchSlop) {
        activeChange.consume()
        applySingleFingerPan(delta, snapshot, ctx)
    }
}

private fun applyMultiTouchTransform(
    event: PointerEvent,
    snapshot: PageLayoutSnapshot,
    ctx: ViewportGestureContext,
) {
    val zoomChange = event.calculateZoom()
    val pan = event.calculatePan()
    val centroid = event.calculateCentroid(useCurrent = true)
    if (zoomChange == 1f && pan == Offset.Zero) return

    event.changes.forEach { it.consume() }
    val motion = ctx.motionState
    val oldZoom = motion.zoomScale
    val newZoom = (oldZoom * zoomChange).coerceIn(ReaderViewModel.MIN_ZOOM, ReaderViewModel.MAX_ZOOM)
    val ratio = if (oldZoom > 0f) newZoom / oldZoom else 1f
    motion.zoomScale = newZoom

    val isVertical = snapshot.scrollMode == ScrollMode.CONTINUOUS_VERTICAL
    val centroidMain = if (isVertical) centroid.y else centroid.x
    val centroidCross = if (isVertical) centroid.x else centroid.y
    val panMain = if (isVertical) pan.y else pan.x
    val panCross = if (isVertical) pan.x else pan.y

    val nextMain = (motion.scrollMainAnim.value + centroidMain) * ratio - centroidMain - panMain
    val nextCross = (motion.scrollCrossAnim.value + centroidCross) * ratio - centroidCross - panCross
    val (maxMain, maxCross) = scaledMaxScroll(snapshot, newZoom, ratio)

    ctx.coroutineScope.launch {
        motion.scrollMainAnim.snapTo(nextMain.coerceIn(0f, maxMain))
        motion.scrollCrossAnim.snapTo(nextCross.coerceIn(0f, maxCross))
    }
}

private fun applySingleFingerPan(
    delta: Offset,
    snapshot: PageLayoutSnapshot,
    ctx: ViewportGestureContext,
) {
    val isVertical = snapshot.scrollMode == ScrollMode.CONTINUOUS_VERTICAL
    val deltaMain = if (isVertical) -delta.y else -delta.x
    val deltaCross = if (isVertical) -delta.x else -delta.y
    val motion = ctx.motionState
    val nextMain = (motion.scrollMainAnim.value + deltaMain).coerceIn(0f, snapshot.maxScrollMainPx)
    val nextCross = (motion.scrollCrossAnim.value + deltaCross).coerceIn(0f, snapshot.maxScrollCrossPx)
    ctx.coroutineScope.launch {
        motion.scrollMainAnim.snapTo(nextMain)
        motion.scrollCrossAnim.snapTo(nextCross)
    }
}

private fun launchFlingDecay(
    velocityTracker: VelocityTracker,
    ctx: ViewportGestureContext,
    decay: DecayAnimationSpec<Float>,
) {
    val snapshot = ctx.snapshotProvider()
    val isVertical = snapshot.scrollMode == ScrollMode.CONTINUOUS_VERTICAL
    val velocity = velocityTracker.calculateVelocity()
    val mainVelocity = if (isVertical) -velocity.y else -velocity.x
    val crossVelocity = if (isVertical) -velocity.x else -velocity.y
    val motion = ctx.motionState

    if (abs(mainVelocity) > GestureConstants.FLING_MIN_VELOCITY) {
        launchBoundedDecay(ctx.coroutineScope, motion.scrollMainAnim, mainVelocity, snapshot.maxScrollMainPx, decay)
    }
    if (snapshot.maxScrollCrossPx > 0f && abs(crossVelocity) > GestureConstants.FLING_MIN_VELOCITY) {
        launchBoundedDecay(ctx.coroutineScope, motion.scrollCrossAnim, crossVelocity, snapshot.maxScrollCrossPx, decay)
    }
}

private fun launchBoundedDecay(
    scope: CoroutineScope,
    animatable: Animatable<Float, AnimationVector1D>,
    velocity: Float,
    upperBound: Float,
    decay: DecayAnimationSpec<Float>,
) {
    scope.launch {
        animatable.updateBounds(lowerBound = 0f, upperBound = upperBound)
        animatable.animateDecay(velocity, decay)
        animatable.updateBounds(lowerBound = null, upperBound = null)
    }
}

private suspend fun toggleDoubleTapZoom(
    tapOffset: Offset,
    ctx: ViewportGestureContext,
) {
    val motion = ctx.motionState
    val startZoom = motion.zoomScale
    val targetZoom =
        if (startZoom > GestureConstants.DOUBLE_TAP_THRESHOLD_ZOOM) {
            ReaderViewModel.MIN_ZOOM
        } else {
            GestureConstants.DOUBLE_TAP_ZOOM
        }
    val snapshot = ctx.snapshotProvider()
    val isVertical = snapshot.scrollMode == ScrollMode.CONTINUOUS_VERTICAL
    val tapMain = if (isVertical) tapOffset.y else tapOffset.x
    val tapCross = if (isVertical) tapOffset.x else tapOffset.y
    val ratio = if (startZoom > 0f) targetZoom / startZoom else 1f
    val (targetMaxMain, targetMaxCross) = scaledMaxScroll(snapshot, targetZoom, ratio)
    val endMain = ((motion.scrollMainAnim.value + tapMain) * ratio - tapMain).coerceIn(0f, targetMaxMain)
    val endCross = ((motion.scrollCrossAnim.value + tapCross) * ratio - tapCross).coerceIn(0f, targetMaxCross)

    val zoomAnim = Animatable(startZoom)
    val animSpec = tween<Float>(durationMillis = GestureConstants.DOUBLE_TAP_ANIM_MS)
    coroutineScope {
        launch { zoomAnim.animateTo(targetZoom, animSpec) { motion.zoomScale = value } }
        launch { motion.scrollMainAnim.animateTo(endMain, animSpec) }
        launch { motion.scrollCrossAnim.animateTo(endCross, animSpec) }
    }
}

private fun scaledMaxScroll(
    snapshot: PageLayoutSnapshot,
    newZoom: Float,
    ratio: Float,
): Pair<Float, Float> =
    if (snapshot.scrollMode == ScrollMode.CONTINUOUS_VERTICAL) {
        val maxMain = (snapshot.totalMainExtentPx * ratio - snapshot.viewportHeightPx).coerceAtLeast(0f)
        val maxCross = (snapshot.viewportWidthPx * newZoom - snapshot.viewportWidthPx).coerceAtLeast(0f)
        maxMain to maxCross
    } else {
        val maxMain = (snapshot.totalMainExtentPx * ratio - snapshot.viewportWidthPx).coerceAtLeast(0f)
        val maxCross = (snapshot.maxCrossExtentPx * ratio - snapshot.viewportHeightPx).coerceAtLeast(0f)
        maxMain to maxCross
    }
