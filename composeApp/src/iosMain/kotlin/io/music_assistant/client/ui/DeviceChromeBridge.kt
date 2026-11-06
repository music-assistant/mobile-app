package io.music_assistant.client.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.unit.dp
import io.music_assistant.client.ui.utils.DeviceChrome
import io.music_assistant.client.ui.utils.FoldRegion
import io.music_assistant.client.ui.utils.VerticalBarArea

/** Lets Swift report the geometry it reads from UIKit. Values are in points. */
object DeviceChromeBridge {
    /** The last reported geometry. `MainViewController` gives it to `App`. */
    var deviceChrome by mutableStateOf(DeviceChrome.None)
        private set

    fun setVerticalBarArea(trailingInset: Double, top: Double, bottom: Double, end: Double) = publish {
        it.copy(
            verticalBarArea = VerticalBarArea(
                trailingInset = trailingInset.dp,
                top = top.dp,
                bottom = bottom.dp,
                end = end.dp,
            ),
        )
    }

    fun clearVerticalBarArea() = publish { it.copy(verticalBarArea = null) }

    fun setPageSplit(start: Double, end: Double) = publish {
        it.copy(pageSplit = FoldRegion(start = start.dp, end = end.dp))
    }

    fun clearPageSplit() = publish { it.copy(pageSplit = null) }

    fun setFoldRegion(start: Double, end: Double) = publish {
        it.copy(foldRegion = FoldRegion(start = start.dp, end = end.dp), horizontalFoldRegion = null)
    }

    fun setHorizontalFoldRegion(top: Double, bottom: Double) = publish {
        it.copy(foldRegion = null, horizontalFoldRegion = FoldRegion(start = top.dp, end = bottom.dp))
    }

    fun clearFoldRegion() = publish { it.copy(foldRegion = null, horizontalFoldRegion = null) }

    private inline fun publish(change: (DeviceChrome) -> DeviceChrome) {
        val next = change(deviceChrome)
        if (next == deviceChrome) return
        deviceChrome = next
        // This write comes from outside composition, so nothing else tells Compose about it.
        Snapshot.sendApplyNotifications()
    }
}
