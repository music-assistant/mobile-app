package io.music_assistant.client.ui.compose.common

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.mandatorySystemGestures
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity

/**
 * Keeps a drag that starts in the bottom system-gesture zone away from the scrollable content.
 *
 * On iOS the zone is the home indicator. The swipe-home gesture still delivers its touches to the
 * app while the system scales the window down, so a pager under it reads them as a swipe to the
 * next page. The moves of a pointer that goes down in the zone are consumed in the initial pass,
 * so no scrollable below ever passes its touch slop. On Android the system owns the zone already.
 */
fun Modifier.ignoreDragsFromBottomGestureZone(): Modifier = composed {
    val zoneHeight = WindowInsets.mandatorySystemGestures.getBottom(LocalDensity.current)
    val placement = remember { Placement() }
    onPlaced { placement.coordinates = it }
        .pointerInput(zoneHeight) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                if (!placement.isInBottomZone(down.position, zoneHeight)) return@awaitEachGesture
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    event.changes.filter { it.positionChanged() }.forEach { it.consume() }
                } while (event.changes.any { it.pressed })
            }
        }
}

private class Placement {
    var coordinates: LayoutCoordinates? = null

    fun isInBottomZone(position: Offset, zoneHeight: Int): Boolean {
        val local = coordinates?.takeIf { zoneHeight > 0 && it.isAttached } ?: return false
        val root = local.findRootCoordinates()
        return root.localPositionOf(local, position).y >= root.size.height - zoneHeight
    }
}
