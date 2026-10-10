package io.music_assistant.client.ui.compose.home.players

/**
 * The full player order to save after the user dragged [movedId] in a list that leaves some
 * players out.
 *
 * [fullOrder] is every player id in its order before the drag; [reorderedVisible] is the shown
 * ids in their new order. Only the dragged player moves: it goes right below the shown player
 * now above it, or to the top. Every other player, shown or not, keeps its place. Without this,
 * left-out players would be missing from the saved order and drop to the bottom of the list
 * once they are shown again.
 */
fun mergeReorderedPlayers(fullOrder: List<String>, reorderedVisible: List<String>, movedId: String): List<String> {
    val above = reorderedVisible.getOrNull(reorderedVisible.indexOf(movedId) - 1)
    return (fullOrder - movedId).toMutableList().apply {
        add(above?.let { indexOf(it) + 1 } ?: 0, movedId)
    }
}
