package io.music_assistant.client.ui

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.unit.dp
import io.music_assistant.client.ui.utils.DeviceChrome
import io.music_assistant.client.ui.utils.FoldRegion
import io.music_assistant.client.ui.utils.VerticalBarArea

/** Lets Swift report the geometry it reads from UIKit. Values are in points. */
object DeviceChromeBridge {
    fun setVerticalBarArea(trailingInset: Double, top: Double, bottom: Double, end: Double) = publish {
        DeviceChrome.verticalBarArea = VerticalBarArea(
            trailingInset = trailingInset.dp,
            top = top.dp,
            bottom = bottom.dp,
            end = end.dp,
        )
    }

    fun clearVerticalBarArea() = publish {
        DeviceChrome.verticalBarArea = null
    }

    fun setPageSplit(start: Double, end: Double) = publish {
        DeviceChrome.pageSplit = FoldRegion(start = start.dp, end = end.dp)
    }

    fun clearPageSplit() = publish {
        DeviceChrome.pageSplit = null
    }

    fun setFoldRegion(start: Double, end: Double) = publish {
        DeviceChrome.foldRegion = FoldRegion(start = start.dp, end = end.dp)
        DeviceChrome.horizontalFoldRegion = null
    }

    fun setHorizontalFoldRegion(top: Double, bottom: Double) = publish {
        DeviceChrome.horizontalFoldRegion = FoldRegion(start = top.dp, end = bottom.dp)
        DeviceChrome.foldRegion = null
    }

    fun clearFoldRegion() = publish {
        DeviceChrome.foldRegion = null
        DeviceChrome.horizontalFoldRegion = null
    }

    /** These writes come from outside composition, so nothing else tells Compose about them. */
    private inline fun publish(write: () -> Unit) {
        write()
        Snapshot.sendApplyNotifications()
    }
}
