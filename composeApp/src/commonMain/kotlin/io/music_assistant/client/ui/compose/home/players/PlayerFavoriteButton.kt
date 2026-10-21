package io.music_assistant.client.ui.compose.home.players

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.ui.compose.common.LocalDislikeSupported
import io.music_assistant.client.ui.compose.common.items.ItemAction
import io.music_assistant.client.ui.compose.common.items.ItemActionMenuItems
import io.music_assistant.client.ui.compose.common.items.favoriteActions
import io.music_assistant.client.ui.theme.favoriteTint
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.cd_disliked
import musicassistantclient.composeapp.generated.resources.cd_favorite
import org.jetbrains.compose.resources.stringResource

/**
 * The heart of the full player. With [LocalDislikeSupported] (server 2.11), a tap opens the
 * favorite menu, and a dislike shows in the place of the heart, like the web app. Without it,
 * a tap toggles the like.
 */
@Composable
fun PlayerFavoriteButton(
    item: AppMediaItem,
    tint: Color,
    onFavoriteChange: (AppMediaItem, Boolean?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dislikeSupported = LocalDislikeSupported.current
    val liked = item.favorite == true
    val disliked = dislikeSupported && item.favorite == false
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        IconButton(
            modifier = modifier,
            onClick = {
                if (dislikeSupported) menuOpen = true else onFavoriteChange(item, if (liked) null else true)
            },
        ) {
            Icon(
                imageVector = when {
                    liked -> Icons.Filled.Favorite
                    disliked -> Icons.Filled.ThumbDown
                    else -> Icons.Outlined.FavoriteBorder
                },
                contentDescription = stringResource(if (disliked) Res.string.cd_disliked else Res.string.cd_favorite),
                tint = if (liked) favoriteTint else tint,
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            ItemActionMenuItems(null, favoriteActions(item.favorite, dislikeSupported)) { action ->
                menuOpen = false
                (action as? ItemAction.SetFavorite)?.let { onFavoriteChange(item, it.favorite) }
            }
        }
    }
}
