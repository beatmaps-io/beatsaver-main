package io.beatmaps.playlist

import io.beatmaps.api.MapDetailWithOrder
import kotlin.math.ceil

fun reorderMaps(maps: List<MapDetailWithOrder>, start: Int, end: Int): List<MapDetailWithOrder>? {
    if (start == end) {
        return maps
    }

    val elem = maps[start]
    val mutable = maps.filterIndexed { idx, _ -> idx != start }

    val previousOrder = if (end <= 0) 0f else mutable[end - 1].order
    val nextOrder = if (end > mutable.size - 1) mutable[end - 1].order + 2 else mutable[end].order

    val midOrder = (previousOrder + nextOrder) / 2
    val nextWholeOrder = ceil(previousOrder)
    val previousIsWhole = nextWholeOrder == previousOrder

    val newOrder = if (nextOrder - previousOrder > 1) {
        if (previousIsWhole) {
            previousOrder + 1 // 1, 4 -> 2
        } else {
            nextWholeOrder // 1.1, 4 -> 2
        }
    } else if (nextWholeOrder != ceil(nextOrder) && !previousIsWhole) {
        nextWholeOrder // 1.6, 2.1 -> 2
    } else {
        midOrder // 1, 2 -> 1.5
    }

    return mutable.take(end) + elem.copy(order = newOrder) + mutable.drop(end)
}
