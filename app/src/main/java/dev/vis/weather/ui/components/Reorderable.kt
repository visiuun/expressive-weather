package dev.vis.weather.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/**
 * A short list whose rows can be dragged into a new order. Each row gets a [handle] modifier: put it
 * on a drag-handle icon to drag straight away (the whole row also drags after a long press).
 * [onReorder] is called once, when the drag ends. Screen readers get "move up / move down" actions.
 */
@Composable
fun <T : Any> ReorderableColumn(
    items: List<T>,
    onReorder: (List<T>) -> Unit,
    moveUpLabel: String,
    moveDownLabel: String,
    modifier: Modifier = Modifier,
    spacing: Dp = 2.dp,
    haptics: Boolean = true,
    row: @Composable (index: Int, count: Int, item: T, handle: Modifier, dragging: Boolean) -> Unit,
) {
    var list by remember { mutableStateOf(items) }
    var dragging by remember { mutableStateOf<T?>(null) }
    if (dragging == null && list != items) list = items
    var offset by remember { mutableFloatStateOf(0f) }
    val heights = remember { HashMap<T, Int>() }
    val spacingPx = with(LocalDensity.current) { spacing.toPx() }
    val haptic = LocalHapticFeedback.current
    val commit by rememberUpdatedState(onReorder)
    fun tick(type: HapticFeedbackType) { if (haptics) haptic.performHapticFeedback(type) }

    fun move(item: T, by: Int) {
        val i = list.indexOf(item)
        val j = i + by
        if (i < 0 || j !in list.indices) return
        list = list.toMutableList().apply { add(j, removeAt(i)) }
    }

    suspend fun PointerInputScope.drag(item: T, afterLongPress: Boolean) {
        val start = { _: androidx.compose.ui.geometry.Offset ->
            dragging = item
            offset = 0f
            tick(HapticFeedbackType.GestureThresholdActivate)
        }
        val end = {
            dragging = null
            offset = 0f
            tick(HapticFeedbackType.GestureEnd)
            commit(list)
        }
        val onDrag = { change: androidx.compose.ui.input.pointer.PointerInputChange, amount: androidx.compose.ui.geometry.Offset ->
            change.consume()
            offset += amount.y
            val i = list.indexOf(item)
            val next = list.getOrNull(i + 1)
            val prev = list.getOrNull(i - 1)
            val nh = next?.let { heights[it] } ?: 0
            val ph = prev?.let { heights[it] } ?: 0
            if (next != null && offset > nh / 2f) {
                move(item, 1)
                offset -= nh + spacingPx
                tick(HapticFeedbackType.SegmentFrequentTick)
            } else if (prev != null && offset < -ph / 2f) {
                move(item, -1)
                offset += ph + spacingPx
                tick(HapticFeedbackType.SegmentFrequentTick)
            }
        }
        if (afterLongPress) detectDragGesturesAfterLongPress(start, end, end, onDrag)
        else detectDragGestures(start, end, end, onDrag)
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing)) {
        list.forEachIndexed { i, item ->
            key(item) {
                val isDragging = dragging == item
                val lift by animateFloatAsState(if (isDragging) 1f else 0f, label = "lift")
                val handle = Modifier.pointerInput(item) { drag(item, afterLongPress = false) }
                Box(
                    Modifier
                        .onSizeChanged { heights[item] = it.height }
                        .zIndex(if (isDragging) 1f else 0f)
                        .graphicsLayer {
                            translationY = if (isDragging) offset else 0f
                            val s = 1f + 0.03f * lift
                            scaleX = s
                            scaleY = s
                            shadowElevation = 12.dp.toPx() * lift
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
                            clip = lift > 0f
                        }
                        .pointerInput(item) { drag(item, afterLongPress = true) }
                        .semantics {
                            customActions = buildList {
                                if (i > 0) add(CustomAccessibilityAction(moveUpLabel) { move(item, -1); commit(list); true })
                                if (i < list.lastIndex) add(CustomAccessibilityAction(moveDownLabel) { move(item, 1); commit(list); true })
                            }
                        },
                ) {
                    row(i, list.size, item, handle, isDragging)
                }
            }
        }
    }
}
